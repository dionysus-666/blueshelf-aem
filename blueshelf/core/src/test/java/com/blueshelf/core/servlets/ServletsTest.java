package com.blueshelf.core.servlets;

import com.blueshelf.core.services.CatalogService;
import com.blueshelf.core.services.ReplicationService;
import com.blueshelf.core.services.catalog.Product;
import com.blueshelf.core.services.catalog.ProductPage;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Servlet tests: register the servlet with @Reference injection via registerInjectActivateService, call doGet/doPost with mock request/response. */
@ExtendWith(SlingContextExtension.class)
class ServletsTest {

    private final SlingContext context = new SlingContext(ResourceResolverType.RESOURCERESOLVER_MOCK);
    private CatalogService catalog;
    private ReplicationService replication;

    @BeforeEach
    void setUp() {
        catalog = mock(CatalogService.class);
        replication = mock(ReplicationService.class);
        context.registerService(CatalogService.class, catalog);
        context.registerService(ReplicationService.class, replication);
        context.addModelsForPackage("com.blueshelf.core.models");
        context.load().json("/com/blueshelf/core/conf.json", "/conf/blueshelf");
        context.load().json("/com/blueshelf/core/apps.json", "/apps/blueshelf");
        context.load().json("/com/blueshelf/core/page.json", "/content/blueshelf/us/en");
    }

    @Test
    void searchServletReturnsJsonAnd503WhenUnavailable() throws Exception {
        ProductSearchServlet servlet = context.registerInjectActivateService(new ProductSearchServlet());
        Product p = new Product(); p.setSku("BS1"); p.setName("TV"); p.setPrice(9);
        when(catalog.search(any())).thenReturn(new ProductPage(List.of(p), 1, ProductPage.Source.LIVE));
        context.request().setParameterMap(Map.of("q", "tv", "size", "5", "page", "x"));
        servlet.doGet(context.request(), context.response());
        assertEquals(200, context.response().getStatus());
        assertTrue(context.response().getOutputAsString().contains("\"sku\":\"BS1\""));
        assertEquals("private, max-age=30", context.response().getHeader("Cache-Control"));

        when(catalog.search(any())).thenReturn(ProductPage.unavailable());
        context.response().reset();
        servlet.doGet(context.request(), context.response());
        assertEquals(503, context.response().getStatus());
    }

    @Test
    void stylesServlet() throws Exception {
        StylesServlet servlet = context.registerInjectActivateService(new StylesServlet());
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/hero");
        servlet.doGet(context.request(), context.response());
        String out = context.response().getOutputAsString();
        assertTrue(out.contains("\"selected\":[\"2001\"]"), out);
        assertTrue(out.contains("hero--outlined"), out);
    }

    @Test
    void replicationServletValidatesAndDelegates() throws Exception {
        ReplicationServlet servlet = context.registerInjectActivateService(new ReplicationServlet());
        when(replication.replicate(any(), eq("/content/blueshelf/us/en"), eq(ReplicationService.Action.ACTIVATE))).thenReturn("Activated");

        context.request().setParameterMap(Map.of("path", "/etc/hack"));
        servlet.doPost(context.request(), context.response());
        assertEquals(400, context.response().getStatus());

        context.response().reset();
        context.request().setParameterMap(Map.of("path", "/content/blueshelf/nope"));
        servlet.doPost(context.request(), context.response());
        assertEquals(404, context.response().getStatus());

        context.response().reset();
        context.request().setParameterMap(Map.of("path", "/content/blueshelf/us/en", "action", "activate"));
        servlet.doPost(context.request(), context.response());
        assertEquals(200, context.response().getStatus());
        assertTrue(context.response().getOutputAsString().contains("\"ok\":true"));

        context.response().reset();
        when(replication.replicate(any(), eq("/content/blueshelf/us/en"), eq(ReplicationService.Action.DEACTIVATE)))
                .thenThrow(new ReplicationService.ReplicationException("boom"));
        context.request().setParameterMap(Map.of("path", "/content/blueshelf/us/en", "action", "deactivate"));
        servlet.doPost(context.request(), context.response());
        assertEquals(502, context.response().getStatus());
    }

    @Test
    void clientLibraryServletConcatenatesDependenciesFirst() throws Exception {
        ClientLibraryServlet servlet = context.registerInjectActivateService(new ClientLibraryServlet());
        context.currentResource("/apps/blueshelf/clientlibs/clientlib-site");
        context.requestPathInfo().setExtension("css");
        servlet.doGet(context.request(), context.response());
        String out = context.response().getOutputAsString();
        assertTrue(out.indexOf(".base{}") < out.indexOf(".site{}"), "dependency (base) before own files:\n" + out);
        assertEquals("text/css", context.response().getContentType().split(";")[0]);
    }
}
