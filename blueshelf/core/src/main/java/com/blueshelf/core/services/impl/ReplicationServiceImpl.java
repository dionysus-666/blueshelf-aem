package com.blueshelf.core.services.impl;

import com.blueshelf.core.services.ReplicationService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pushes a content subtree from this (author) instance to the publish instance.
 *
 * <p>Mechanics: serialize the subtree to Sling's JSON content format and POST it to publish using the
 * Sling POST servlet {@code :operation=import} (the same JSON format used by content packages/tests).
 * AEM's real replication serializes with Durbo/FileVault and posts to a "receiver" servlet on publish —
 * same shape: author → HTTP → publish, triggered by an author action.</p>
 *
 * <p>OSGi lessons in this class:</p>
 * <ul>
 *   <li>{@code @Component(service=...)} registers the service; {@code @Designate(ocd=...)} binds a typed
 *       config (an {@code @ObjectClassDefinition} interface) — editable at /system/console/configMgr and
 *       deployable per run mode from ui.config (e.g. {@code config.author/...ReplicationServiceImpl.cfg.json}).</li>
 *   <li>{@code @Activate}/{@code @Modified} receive the config; DS re-invokes on config change without restart.</li>
 * </ul>
 */
@Component(service = ReplicationService.class, immediate = true)
@Designate(ocd = ReplicationServiceImpl.Config.class)
public class ReplicationServiceImpl implements ReplicationService {

    @ObjectClassDefinition(name = "BlueShelf Replication Agent", description = "Pushes content from author to publish")
    public @interface Config {
        @AttributeDefinition(name = "Publish URL", description = "Base URL of the publish instance")
        String publishUrl() default "http://localhost:4503";

        @AttributeDefinition(name = "Transport user")
        String user() default "admin";

        @AttributeDefinition(name = "Transport password", type = org.osgi.service.metatype.annotations.AttributeType.PASSWORD)
        String password() default "admin";

        @AttributeDefinition(name = "Enabled")
        boolean enabled() default true;

        @AttributeDefinition(name = "Dispatcher flush URLs", description = "Dispatcher invalidate endpoints, e.g. http://dispatcher:8080/dispatcher/invalidate.cache. In AEM this is a separate 'Dispatcher Flush' replication agent (usually on publish).")
        String[] dispatcherFlushUrls() default {};

        @AttributeDefinition(name = "Frontend revalidate URL", description = "Optional webhook for headless frontends (Next.js ISR): {path} is replaced by the content path")
        String frontendRevalidateUrl() default "";
    }

    private static final Logger LOG = LoggerFactory.getLogger(ReplicationServiceImpl.class);

    /** JCR protected/system properties that must not be sent (publish sets its own). */
    private static final Set<String> SKIP_PROPS = Set.of(
            "jcr:created", "jcr:createdBy", "jcr:uuid", "jcr:baseVersion", "jcr:predecessors",
            "jcr:versionHistory", "jcr:isCheckedOut", "jcr:mixinTypes");

    private volatile Config config;
    // HTTP/1.1 explicitly: the JDK client tries an h2c upgrade by default, which some Node servers answer by closing the socket
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();

    @Activate
    @Modified
    protected void activate(Config config) {
        this.config = config;
        LOG.info("Replication agent configured: publishUrl={} enabled={}", config.publishUrl(), config.enabled());
    }

    @Override
    public String replicate(ResourceResolver resolver, String path, Action action) throws ReplicationException {
        if (!config.enabled()) {
            throw new ReplicationException("Replication agent is disabled");
        }
        try {
            if (action == Action.DEACTIVATE) {
                post(path, Map.of(":operation", "delete"));
                flush(path, "Deactivate");
                return "Deactivated " + path;
            }
            Resource res = resolver.getResource(path);
            if (res == null) {
                throw new ReplicationException("No such resource: " + path);
            }
            // (flush happens after the content push, see below)
            ensureAncestors(res);
            String parent = res.getParent().getPath();
            String json = toJson(res);
            post(parent, Map.of(
                    ":operation", "import",
                    ":contentType", "json",
                    ":name", res.getName(),
                    ":content", json,
                    ":replace", "true",
                    ":replaceProperties", "true"));
            LOG.info("Activated {} ({} bytes)", path, json.length());
            flush(path, "Activate");
            return "Activated " + path;
        } catch (IOException | InterruptedException e) {
            throw new ReplicationException("Replication transport failed for " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Dispatcher flush = the SAME protocol AEM's flush agent uses: POST with headers
     * CQ-Action (Activate|Deactivate|Delete), CQ-Handle (content path), CQ-Path (dispatcher path, optional).
     * Order matters in real life: publish must have the new content BEFORE the cache is invalidated,
     * otherwise the first visitor re-caches the old page ("flush before activation" is a classic incident).
     * Failures are logged, not thrown: a dead cache layer must not break authoring.
     */
    private void flush(String path, String action) {
        for (String url : config.dispatcherFlushUrls()) {
            if (url == null || url.isBlank()) continue;
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("CQ-Action", action).header("CQ-Handle", path).header("CQ-Path", path)
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
                LOG.info("Dispatcher flush {} {} -> {} {}", action, path, r.statusCode(), abbreviate(r.body()).trim());
            } catch (Exception e) {
                LOG.warn("Dispatcher flush failed for {} at {}: {}", path, url, e.toString());
            }
        }
        String fe = config.frontendRevalidateUrl();
        if (fe != null && !fe.isBlank()) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(fe.replace("{path}", enc(path)))).timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
                LOG.info("Frontend revalidate {} -> {}", path, r.statusCode());
            } catch (Exception e) {
                LOG.warn("Frontend revalidate failed for {}: {}", path, e.toString());
            }
        }
    }

    /** Create missing ancestor folders on publish (sling:OrderedFolder), top-down. */
    private void ensureAncestors(Resource res) throws IOException, InterruptedException {
        Deque<Resource> chain = new ArrayDeque<>();
        for (Resource p = res.getParent(); p != null && !"/".equals(p.getPath()) && !"/content".equals(p.getPath()); p = p.getParent()) {
            chain.push(p);
        }
        while (!chain.isEmpty()) {
            Resource anc = chain.pop();
            HttpRequest head = auth(HttpRequest.newBuilder(URI.create(config.publishUrl() + anc.getPath() + ".json")).GET()).build();
            int status = http.send(head, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404) {
                String type = anc.getValueMap().get("jcr:primaryType", "sling:OrderedFolder");
                post(anc.getPath(), Map.of("jcr:primaryType", type));
            }
        }
    }

    private void post(String path, Map<String, String> params) throws IOException, InterruptedException {
        String body = params.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest req = auth(HttpRequest.newBuilder(URI.create(config.publishUrl() + path))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))).build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            throw new IOException("publish responded " + resp.statusCode() + " for POST " + path + ": " + abbreviate(resp.body()));
        }
    }

    private HttpRequest.Builder auth(HttpRequest.Builder b) {
        String token = Base64.getEncoder().encodeToString((config.user() + ":" + config.password()).getBytes(StandardCharsets.UTF_8));
        return b.header("Authorization", "Basic " + token).timeout(Duration.ofSeconds(20));
    }

    // ---- serialization: Resource tree -> Sling JSON content format ----

    static String toJson(Resource res) {
        StringBuilder sb = new StringBuilder();
        writeNode(res, sb);
        return sb.toString();
    }

    private static void writeNode(Resource res, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        ValueMap vm = res.getValueMap();
        for (Map.Entry<String, Object> e : vm.entrySet()) {
            if (SKIP_PROPS.contains(e.getKey())) continue;
            Object v = e.getValue();
            if (v == null) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append(quote(e.getKey())).append(':').append(value(v));
        }
        for (Resource child : res.getChildren()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(quote(child.getName())).append(':');
            writeNode(child, sb);
        }
        sb.append('}');
    }

    private static String value(Object v) {
        if (v instanceof Object[]) {
            return Arrays.stream((Object[]) v).map(ReplicationServiceImpl::value).collect(Collectors.joining(",", "[", "]"));
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        if (v instanceof Calendar) {
            // Sling JSON import understands ISO8601 dates for Date properties
            Calendar c = (Calendar) v;
            return quote(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(c.toInstant().atZone(c.getTimeZone().toZoneId())));
        }
        return quote(v.toString());
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "…" : s);
    }
}
