package com.blueshelf.core.models;

import com.blueshelf.core.util.Policies;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Title/Teaser/Style/Page/Container models + policy resolution. Uses RESOURCERESOLVER_MOCK with /conf + /apps fixtures.
 * Note: resourceSuperType resolution for Sling Models needs the component nodes under /apps (loaded from apps.json).
 */
@ExtendWith(SlingContextExtension.class)
class ComponentModelsTest {

    private final SlingContext context = new SlingContext(ResourceResolverType.RESOURCERESOLVER_MOCK);

    @BeforeEach
    void setUp() {
        context.addModelsForPackage("com.blueshelf.core.models");
        context.load().json("/com/blueshelf/core/conf.json", "/conf/blueshelf");
        context.load().json("/com/blueshelf/core/apps.json", "/apps/blueshelf");
        context.load().json("/com/blueshelf/core/page.json", "/content/blueshelf/us/en");
    }

    @Test
    void policyResolutionAndStyles() {
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/hero");
        assertNotNull(Policies.policyFor(context.currentResource()));
        StyleModel style = context.request().adaptTo(StyleModel.class);
        assertEquals("hero--dark", style.getCssClasses());
        assertEquals(2, style.getAvailable().size());
        assertEquals(List.of("2001"), style.getSelected());
        assertFalse(style.isEdit());
        context.request().setParameterMap(Map.of("wcmmode", "edit"));
        assertTrue(context.request().adaptTo(StyleModel.class).isEdit());
        assertTrue(context.request().adaptTo(EditContext.class).isEdit());
    }

    @Test
    void titleFallsBackToPageTitleAndPolicyType() {
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/title");
        TitleModel t = context.request().adaptTo(TitleModel.class);
        assertEquals("Home", t.getText(), "empty jcr:title -> page title");
        assertEquals("h3", t.getType(), "heading level from policy");
    }

    @Test
    void teaserLink() {
        TeaserModel t = context.resourceResolver().getResource("/content/blueshelf/us/en/jcr:content/root/teaser").adaptTo(TeaserModel.class);
        assertEquals("/content/blueshelf/us/en/tvs.html", t.getLink());
        assertEquals("Learn more", t.getLinkText());
        assertFalse(t.isEmpty());
    }

    @Test
    void pageExportsSpaContract() {
        context.currentResource("/content/blueshelf/us/en");
        PageModel page = context.request().adaptTo(PageModel.class);
        assertEquals("Home", page.getTitle());
        assertEquals("blueshelf/components/page", page.getType());
        assertEquals("/content/blueshelf/us/en", page.getPath());
        assertEquals(List.of("root"), page.getItemsOrder());
        @SuppressWarnings("unchecked") Map<String, Object> root = (Map<String, Object>) page.getItems().get("root");
        assertEquals("blueshelf/components/container", root.get(":type"));
        assertEquals(List.of("hero", "title", "teaser", "text"), root.get(":itemsOrder"));
        @SuppressWarnings("unchecked") Map<String, Object> items = (Map<String, Object>) root.get(":items");
        @SuppressWarnings("unchecked") Map<String, Object> hero = (Map<String, Object>) items.get("hero");
        assertEquals("Hi", hero.get("title"));
        @SuppressWarnings("unchecked") Map<String, Object> text = (Map<String, Object>) items.get("text");
        assertEquals("<p>x</p>", text.get("text"), "components without a model export raw properties");
        assertEquals(List.of("Home", "TVs"), page.getNavigation().stream().map(n -> n.get("title")).toList(), "hideInNav respected");
    }

    @Test
    void containerModelDirectly() {
        context.currentResource("/content/blueshelf/us/en/jcr:content/root");
        ContainerModel c = context.request().adaptTo(ContainerModel.class);
        assertEquals(4, c.getItems().size());
        assertEquals("blueshelf/components/container", c.getType());
    }
}
