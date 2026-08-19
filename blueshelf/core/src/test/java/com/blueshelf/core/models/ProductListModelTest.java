package com.blueshelf.core.models;

import com.blueshelf.core.services.CatalogService;
import com.blueshelf.core.services.catalog.Product;
import com.blueshelf.core.services.catalog.ProductPage;
import com.blueshelf.core.services.catalog.ProductQuery;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SlingContextExtension.class)
class ProductListModelTest {

    private final SlingContext context = new SlingContext(ResourceResolverType.RESOURCERESOLVER_MOCK);
    private CatalogService catalog;

    @BeforeEach
    void setUp() {
        catalog = mock(CatalogService.class);
        // Register the mock as the OSGi service => @OSGiService injects it. No HTTP in model tests.
        context.registerService(CatalogService.class, catalog);
        context.addModelsForClasses(ProductListModel.class, ProductDetailModel.class);
        context.load().json("/com/blueshelf/core/models/ProductModelsTest.json", "/content/blueshelf/us/en");
    }

    private static Product product(String sku, String name) {
        Product p = new Product(); p.setSku(sku); p.setName(name); p.setPrice(10); return p;
    }

    @Test
    void listUsesDialogConfigAndService() {
        when(catalog.search(any())).thenReturn(new ProductPage(List.of(product("BS1", "A"), product("BS2", "B")), 2, ProductPage.Source.LIVE));
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/product-list");

        ProductListModel m = context.request().adaptTo(ProductListModel.class);
        assertNotNull(m);
        assertEquals("Top TVs", m.getTitle());
        assertEquals(2, m.getProducts().size());
        assertTrue(m.isAvailable());
        assertEquals("/content/blueshelf/us/en/product.html/BS1", m.productUrl("BS1"));

        ArgumentCaptor<ProductQuery> q = ArgumentCaptor.forClass(ProductQuery.class);
        verify(catalog).search(q.capture());
        assertEquals("tvs", q.getValue().getCategory());
        assertEquals(4, q.getValue().getSize());
        assertEquals("price-asc", q.getValue().getSort());
    }

    @Test
    void listReadsQueryParamWhenEnabled() {
        when(catalog.search(any())).thenReturn(ProductPage.unavailable());
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/search-results");
        context.request().setParameterMap(java.util.Map.of("q", "oled"));

        ProductListModel m = context.request().adaptTo(ProductListModel.class);
        assertEquals("oled", m.getSearchText());
        assertFalse(m.isAvailable());
        assertTrue(m.isEmpty());
    }

    @Test
    void detailResolvesSkuFromSuffix() {
        when(catalog.getProduct("BS1001")).thenReturn(Optional.of(product("BS1001", "Big TV")));
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/product-detail");
        context.requestPathInfo().setSuffix("/BS1001");

        ProductDetailModel m = context.request().adaptTo(ProductDetailModel.class);
        assertTrue(m.isFound());
        assertEquals("Big TV", m.getProduct().getName());
    }

    @Test
    void detailUnknownSku() {
        when(catalog.getProduct(any())).thenReturn(Optional.empty());
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/product-detail");
        context.requestPathInfo().setSuffix("/NOPE");
        ProductDetailModel m = context.request().adaptTo(ProductDetailModel.class);
        assertTrue(m.isNotFound());
        assertFalse(m.isNoSku());
    }
}
