package com.blueshelf.core.services.impl;

import com.blueshelf.core.services.CatalogService;
import com.blueshelf.core.services.catalog.ProductPage;
import com.blueshelf.core.services.catalog.ProductQuery;
import com.sun.net.httpserver.HttpServer;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContext;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContextExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-ish unit test: a throwaway JDK HttpServer plays the Catalog API (no WireMock dependency needed),
 * OsgiContext (wcm.io osgi-mock) activates the component WITH a config map — exactly what OSGi does at runtime.
 */
@ExtendWith(OsgiContextExtension.class)
class CatalogServiceImplTest {

    private final OsgiContext context = new OsgiContext();
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Integer> statusToReturn = new AtomicReference<>(200);
    private CatalogService service;

    private static final String PAGE_JSON = "{\"items\":[{\"sku\":\"BS1\",\"name\":\"TV\",\"price\":100.0,\"salePrice\":80.0,\"extraField\":1}],\"total\":1}";
    // note the extra "lat" field: @JsonIgnoreProperties must swallow it
    private static final String STORES_JSON = "[{\"id\":\"S001\",\"name\":\"Richfield\",\"city\":\"Richfield\",\"zip\":\"55423\",\"lat\":44.86},{\"id\":\"S002\",\"name\":\"Minneapolis\",\"city\":\"Minneapolis\",\"zip\":\"55402\"}]";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/stores", ex -> {
            calls.incrementAndGet();
            byte[] body = STORES_JSON.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(statusToReturn.get() == 200 ? 200 : 500, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        server.createContext("/api/products", ex -> {
            calls.incrementAndGet();
            int status = statusToReturn.get();
            byte[] body = (status == 200 ? PAGE_JSON : "boom").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        service = context.registerInjectActivateService(new CatalogServiceImpl(), Map.of(
                "baseUrl", baseUrl, "timeoutMs", 1000, "cacheTtlSeconds", 60,
                "staleIfErrorSeconds", 600, "breakerFailures", 2, "breakerOpenSeconds", 60));
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @Test
    void parsesAndCaches() {
        ProductPage p1 = service.search(ProductQuery.category("tvs", 6));
        assertEquals(ProductPage.Source.LIVE, p1.getSource());
        assertEquals(1, p1.getTotal());
        assertEquals("BS1", p1.getItems().get(0).getSku());
        assertTrue(p1.getItems().get(0).isOnSale());
        assertEquals(20, p1.getItems().get(0).getSavingsPercent());

        ProductPage p2 = service.search(ProductQuery.category("tvs", 6));
        assertEquals(ProductPage.Source.CACHE, p2.getSource(), "second call served from cache");
        assertEquals(1, calls.get(), "backend called once");
    }

    @Test
    void storesNearParsesCachesAndDegrades() {
        var stores = service.storesNear("55423");
        assertEquals(2, stores.size());
        assertEquals("Richfield", stores.get(0).getName(), "unknown 'lat' field ignored, not fatal");
        int callsAfterFirst = calls.get();
        service.storesNear("55423");
        assertEquals(callsAfterFirst, calls.get(), "second lookup served from cache");
        assertTrue(service.storesNear("  ").isEmpty(), "blank zip -> no backend call, empty list");
        assertTrue(service.storesNear(null).isEmpty());
    }

    @Test
    void servesStaleOnErrorAndOpensBreaker() {
        service.search(ProductQuery.category("tvs", 6));            // warm cache (1 call)
        statusToReturn.set(500);
        // force expiry by re-activating with ttl=0 but keep stale window
        service = context.registerInjectActivateService(new CatalogServiceImpl(), Map.of(
                "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort(), "timeoutMs", 1000,
                "cacheTtlSeconds", 0, "staleIfErrorSeconds", 600, "breakerFailures", 2, "breakerOpenSeconds", 60));
        statusToReturn.set(200);
        service.search(ProductQuery.category("tvs", 6));            // populates the new instance's cache (expires instantly)
        statusToReturn.set(500);

        ProductPage stale = service.search(ProductQuery.category("tvs", 6));
        assertEquals(ProductPage.Source.STALE, stale.getSource(), "expired entry served because backend failed");
        assertEquals(1, stale.getTotal());

        service.search(ProductQuery.category("tvs", 6));            // 2nd failure -> breaker opens
        int callsBefore = calls.get();
        ProductPage afterOpen = service.search(ProductQuery.category("tvs", 6));
        assertEquals(callsBefore, calls.get(), "breaker open: backend NOT called");
        assertEquals(ProductPage.Source.STALE, afterOpen.getSource());
        assertTrue(service.status().contains("breaker=OPEN"));

        ProductPage unknown = service.search(ProductQuery.category("laptops", 6)); // nothing cached for this key
        assertEquals(ProductPage.Source.UNAVAILABLE, unknown.getSource());
        assertTrue(unknown.getItems().isEmpty());
    }
}
