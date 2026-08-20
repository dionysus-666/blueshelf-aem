package com.blueshelf.core.models;

import com.blueshelf.core.services.CatalogService;
import com.blueshelf.core.services.catalog.Store;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SlingContextExtension.class)
class StoreLocatorModelTest {

    private final SlingContext context = new SlingContext(ResourceResolverType.RESOURCERESOLVER_MOCK);
    private CatalogService catalog;

    private static Store store(String name) { Store s = new Store(); s.setName(name); s.setCity(name); return s; }

    @BeforeEach
    void setUp() {
        catalog = mock(CatalogService.class);
        context.registerService(CatalogService.class, catalog);
        context.addModelsForClasses(StoreLocatorModel.class);
        context.load().json("/com/blueshelf/core/models/StoreLocatorModelTest.json", "/content/blueshelf/us/en");
    }

    @Test
    void usesAuthoredDefaultZip() {
        when(catalog.storesNear("55423")).thenReturn(List.of(store("Richfield")));
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/store-locator");
        StoreLocatorModel m = context.request().adaptTo(StoreLocatorModel.class);
        assertEquals("55423", m.getZip());
        assertFalse(m.isEmpty());
        assertEquals("/content/blueshelf/us/en.html", m.getPageUrl(), "form submits to the PAGE, not the component");
        verify(catalog).storesNear("55423");
    }

    @Test
    void queryParamBeatsDefault() {
        when(catalog.storesNear("60642")).thenReturn(List.of(store("Chicago")));
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/store-locator");
        context.request().setParameterMap(Map.of("zip", " 60642 "));
        StoreLocatorModel m = context.request().adaptTo(StoreLocatorModel.class);
        assertEquals("60642", m.getZip(), "param wins over authored default, trimmed");
        assertEquals("Chicago", m.getStores().get(0).getName());
    }

    @Test
    void noZipAnywhereMeansNoServiceCall() {
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/store-locator-bare");
        StoreLocatorModel m = context.request().adaptTo(StoreLocatorModel.class);
        assertNull(m.getZip());
        assertTrue(m.isEmpty());
        verify(catalog, never()).storesNear(anyString());
    }

    @Test
    void backendDownRendersEmptyState() {
        when(catalog.storesNear(anyString())).thenReturn(List.of()); // service contract: empty on failure
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/store-locator");
        StoreLocatorModel m = context.request().adaptTo(StoreLocatorModel.class);
        assertTrue(m.isEmpty());
        assertEquals("55423", m.getZip(), "zip still shown in the form");
    }
}
