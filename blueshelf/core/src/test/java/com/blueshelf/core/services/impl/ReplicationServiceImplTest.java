package com.blueshelf.core.services.impl;

import com.blueshelf.core.services.ReplicationService;
import com.sun.net.httpserver.HttpServer;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A stub "publish" + "dispatcher" HttpServer records what the agent sends: import JSON, ancestor creation, flush headers. */
@ExtendWith(SlingContextExtension.class)
class ReplicationServiceImplTest {

    private final SlingContext context = new SlingContext(ResourceResolverType.RESOURCERESOLVER_MOCK);
    private HttpServer server;
    private final List<String> calls = new ArrayList<>();
    private ReplicationService service;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String rec = ex.getRequestMethod() + " " + ex.getRequestURI() + " " + URLDecoder.decode(body, StandardCharsets.UTF_8)
                    + " CQ-Action=" + ex.getRequestHeaders().getFirst("CQ-Action") + " CQ-Handle=" + ex.getRequestHeaders().getFirst("CQ-Handle");
            calls.add(rec);
            int status = ex.getRequestMethod().equals("GET") && ex.getRequestURI().getPath().contains("/us.json") ? 404 : 200;
            byte[] out = "ok".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        service = context.registerInjectActivateService(new ReplicationServiceImpl(), Map.of(
                "publishUrl", base, "user", "admin", "password", "admin", "enabled", true,
                "dispatcherFlushUrls", new String[]{base + "/dispatcher/invalidate.cache"},
                "frontendRevalidateUrl", base + "/api/revalidate?path={path}"));
        context.load().json("/com/blueshelf/core/page.json", "/content/blueshelf/us/en");
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @Test
    void activateImportsJsonCreatesAncestorsAndFlushes() throws Exception {
        String msg = service.replicate(context.resourceResolver(), "/content/blueshelf/us/en", ReplicationService.Action.ACTIVATE);
        assertEquals("Activated /content/blueshelf/us/en", msg);
        // ancestor check: GET /content/blueshelf.json (200 -> exists), GET /content/blueshelf/us.json (404 -> created)
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("GET /content/blueshelf/us.json")));
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("POST /content/blueshelf/us ") && c.contains("jcr:primaryType=")), calls.toString());
        String imp = calls.stream().filter(c -> c.startsWith("POST /content/blueshelf/us ") && c.contains(":operation=import")).findFirst().orElseThrow();
        assertTrue(imp.contains(":name=en"));
        assertTrue(imp.contains("\"jcr:title\":\"Home\""));
        assertTrue(imp.contains("\"cq:styleIds\":[\"2001\"]"), "multi-value exported as array");
        assertFalse(imp.contains("jcr:created"), "protected props skipped");
        assertTrue(calls.stream().anyMatch(c -> c.contains("/dispatcher/invalidate.cache") && c.contains("CQ-Action=Activate") && c.contains("CQ-Handle=/content/blueshelf/us/en")));
        assertTrue(calls.stream().anyMatch(c -> c.contains("/api/revalidate?path=%2Fcontent%2Fblueshelf%2Fus%2Fen") || c.contains("/api/revalidate?path=/content/blueshelf/us/en")));
    }

    @Test
    void deactivateDeletesAndFlushes() throws Exception {
        service.replicate(context.resourceResolver(), "/content/blueshelf/us/en/tvs", ReplicationService.Action.DEACTIVATE);
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("POST /content/blueshelf/us/en/tvs ") && c.contains(":operation=delete")));
        assertTrue(calls.stream().anyMatch(c -> c.contains("CQ-Action=Deactivate")));
    }

    @Test
    void missingResourceAndDisabledAgent() {
        assertThrows(ReplicationService.ReplicationException.class,
                () -> service.replicate(context.resourceResolver(), "/content/nope", ReplicationService.Action.ACTIVATE));
        ReplicationService disabled = context.registerInjectActivateService(new ReplicationServiceImpl(), Map.of("enabled", false));
        assertThrows(ReplicationService.ReplicationException.class,
                () -> disabled.replicate(context.resourceResolver(), "/content/blueshelf/us/en", ReplicationService.Action.ACTIVATE));
    }

    @Test
    void jsonQuoting() {
        context.create().resource("/content/q", "text", "a\"b\\c\nd\te", "num", 3L, "flag", true);
        String json = ReplicationServiceImpl.toJson(context.resourceResolver().getResource("/content/q"));
        assertTrue(json.contains("\"text\":\"a\\\"b\\\\c\\nd\\te\""), json);
        assertTrue(json.contains("\"num\":3") && json.contains("\"flag\":true"), json);
    }
}
