package com.blueshelf.core.services.impl;

import com.blueshelf.core.services.catalog.Store;
import com.fasterxml.jackson.core.type.TypeReference;
import com.blueshelf.core.services.CatalogService;
import com.blueshelf.core.services.catalog.Product;
import com.blueshelf.core.services.catalog.ProductPage;
import com.blueshelf.core.services.catalog.ProductQuery;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Calls the Catalog API over HTTP with the three things every AEM↔backend integration needs:
 * <ol>
 *   <li><b>Config</b> via OSGi metatype (base URL, timeouts, TTLs) — different per environment/run mode.</li>
 *   <li><b>Caching</b> (TTL, in-memory, per instance) — publish instances render the same product list for
 *       thousands of visitors; never hit the backend per page view. Dispatcher caches the HTML on top of this.</li>
 *   <li><b>Resilience</b> — short timeouts, serve <i>stale</i> data on error, and a small circuit breaker so a dead
 *       backend can't tie up all request threads (the classic "AEM is slow" incident is a slow downstream).</li>
 * </ol>
 * In real projects you'd typically use Apache HttpClient (bundled in AEM) + Caffeine/Guava cache; the JDK
 * HttpClient + a tiny TTL map keeps this dependency-free and readable.
 */
@Component(service = CatalogService.class, immediate = true)
@Designate(ocd = CatalogServiceImpl.Config.class)
public class CatalogServiceImpl implements CatalogService {

    @ObjectClassDefinition(name = "BlueShelf Catalog Service", description = "HTTP client for the product catalog API")
    public @interface Config {
        @AttributeDefinition(name = "Base URL", description = "e.g. http://localhost:8081")
        String baseUrl() default "http://localhost:8081";

        @AttributeDefinition(name = "Timeout (ms)", description = "Connect+read timeout per call. Keep it short: this runs inside page rendering.")
        int timeoutMs() default 1500;

        @AttributeDefinition(name = "Cache TTL (seconds)")
        int cacheTtlSeconds() default 60;

        @AttributeDefinition(name = "Stale-if-error (seconds)", description = "How long an expired entry may still be served when the backend fails")
        int staleIfErrorSeconds() default 600;

        @AttributeDefinition(name = "Circuit breaker: failures to open")
        int breakerFailures() default 3;

        @AttributeDefinition(name = "Circuit breaker: open duration (seconds)")
        int breakerOpenSeconds() default 20;
    }

    private static final Logger LOG = LoggerFactory.getLogger(CatalogServiceImpl.class);

    private volatile Config config;
    private volatile HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ---- cache ----
    private record Entry(Object value, long createdAt) {}
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    // ---- circuit breaker ----
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong(0);

    // ---- metrics ----
    private final AtomicLong hits = new AtomicLong(), misses = new AtomicLong(), errors = new AtomicLong(), stale = new AtomicLong();

    @Activate
    @Modified
    protected void activate(Config config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.timeoutMs())).build();
        this.cache.clear();
        LOG.info("CatalogService configured: baseUrl={} timeout={}ms ttl={}s", config.baseUrl(), config.timeoutMs(), config.cacheTtlSeconds());
    }

    @Deactivate
    protected void deactivate() { cache.clear(); }

    @Override
    public ProductPage search(ProductQuery query) {
        String url = config.baseUrl() + "/api/products?category=" + enc(query.getCategory()) + "&q=" + enc(query.getText())
                + "&page=" + query.getPage() + "&size=" + query.getSize() + "&sort=" + enc(query.getSort());
        ProductPage result = cached(query.cacheKey(), ProductPage.class, () -> {
            JsonNode root = mapper.readTree(get(url));
            List<Product> items = new ArrayList<>();
            for (JsonNode n : root.path("items")) items.add(mapper.treeToValue(n, Product.class));
            return new ProductPage(items, root.path("total").asInt(), ProductPage.Source.LIVE);
        });
        return result == null ? ProductPage.unavailable() : result;
    }

    @Override
    public Optional<Product> getProduct(String sku) {
        if (sku == null || sku.isBlank()) return Optional.empty();
        String url = config.baseUrl() + "/api/products/" + enc(sku.trim());
        Product p = cached("sku|" + sku.trim().toUpperCase(), Product.class, () -> {
            String body = get(url);
            return body == null ? null : mapper.readValue(body, Product.class);
        });
        return Optional.ofNullable(p);
    }

    @Override
      public List<Store> storesNear(String zip) {
          if (zip == null || zip.isBlank()) return List.of();
          String url = config.baseUrl() + "/api/stores?zip=" + enc(zip.trim());
          List<Store> stores = cached("stores|" + zip.trim(), List.class, () ->
  {
              String body = get(url);
              return body == null ? null : mapper.readValue(body, new TypeReference<List<Store>>() {});
          });
          return stores == null ? List.of() : stores;
      }

    @Override
    public String status() {
        return String.format("baseUrl=%s cache=%d hits=%d misses=%d stale=%d errors=%d breaker=%s",
                config.baseUrl(), cache.size(), hits.get(), misses.get(), stale.get(), errors.get(), breakerOpen() ? "OPEN" : "closed");
    }

    // ---------------------------------------------------------------------------------------------

    @FunctionalInterface
    private interface Loader<T> { T load() throws Exception; }

    /**
     * Cache-aside with stale-if-error:
     *  fresh hit -> return; expired/missing -> call backend; backend fails -> return expired value if within
     *  staleIfError window (marked STALE for pages), else null.
     */
    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Class<T> type, Loader<T> loader) {
        long now = System.currentTimeMillis();
        Entry e = cache.get(key);
        if (e != null && now - e.createdAt < config.cacheTtlSeconds() * 1000L) {
            hits.incrementAndGet();
            return markCache((T) e.value);
        }
        misses.incrementAndGet();
        if (breakerOpen()) {
            return staleOrNull(e, now, "circuit open");
        }
        try {
            T fresh = loader.load();
            consecutiveFailures.set(0);
            cache.put(key, new Entry(fresh, now));
            evictIfLarge();
            return fresh;
        } catch (Exception ex) {
            errors.incrementAndGet();
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= config.breakerFailures()) {
                openUntil.set(now + config.breakerOpenSeconds() * 1000L);
                LOG.warn("Catalog circuit breaker OPEN for {}s after {} failures (last: {})", config.breakerOpenSeconds(), failures, ex.toString());
            } else {
                LOG.warn("Catalog call failed ({}/{}): {}", failures, config.breakerFailures(), ex.toString());
            }
            return staleOrNull(e, now, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T staleOrNull(Entry e, long now, String reason) {
        if (e != null && now - e.createdAt < (config.cacheTtlSeconds() + config.staleIfErrorSeconds()) * 1000L) {
            stale.incrementAndGet();
            LOG.debug("Serving STALE catalog data ({})", reason);
            Object v = e.value;
            if (v instanceof ProductPage pp) return (T) pp.withSource(ProductPage.Source.STALE);
            return (T) v;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T markCache(T v) {
        if (v instanceof ProductPage pp && pp.getSource() == ProductPage.Source.LIVE) return (T) pp.withSource(ProductPage.Source.CACHE);
        return v;
    }

    private boolean breakerOpen() { return System.currentTimeMillis() < openUntil.get(); }

    private void evictIfLarge() {
        if (cache.size() > 500) {
            long cutoff = System.currentTimeMillis() - config.cacheTtlSeconds() * 1000L;
            cache.entrySet().removeIf(en -> en.getValue().createdAt < cutoff);
        }
    }

    /** @return body, or null for 404 */
    private String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) return null;
        if (resp.statusCode() >= 300) throw new IOException("HTTP " + resp.statusCode() + " from " + url);
        return resp.body();
    }

    private static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }
}
