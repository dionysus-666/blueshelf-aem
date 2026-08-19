package com.blueshelf.core.models;

import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test using wcm.io Sling Mocks (in AEM projects you'd use AemContext/AemContextExtension from
 * io.wcm.testing.aem-mock — same API plus AEM-specific objects like Page/PageManager).
 *
 * <p>Correlation: SlingContext = an in-memory JCR + Sling resource resolver + Sling Models registry.
 * Think "Spring Boot @DataJpaTest with an H2 DB", but for the content repository.</p>
 *
 * <p>Gotcha: you MUST register your model classes ({@code context.addModelsForClasses} or
 * {@code addModelsForPackage}) — the mock does not scan the classpath.</p>
 */
@ExtendWith(SlingContextExtension.class)
class HeroModelTest {

    // RESOURCERESOLVER_MOCK is fastest; JCR_MOCK / JCR_OAK when you need real JCR semantics (queries etc.)
    private final SlingContext context = new SlingContext(ResourceResolverType.RESOURCERESOLVER_MOCK);

    @BeforeEach
    void setUp() {
        context.addModelsForClasses(HeroModel.class);
        // Load "fixture" content exactly as it would live in the JCR.
        context.load().json("/com/blueshelf/core/models/HeroModelTest.json", "/content/blueshelf/us/en");
    }

    @Test
    void adaptsFromResourceAndResolvesInternalLink() {
        context.currentResource("/content/blueshelf/us/en/jcr:content/root/hero");
        HeroModel hero = context.request().adaptTo(HeroModel.class);

        assertNotNull(hero);
        assertEquals("Back to School TV Deals", hero.getTitle());
        assertEquals("Save up to 40%", hero.getSubtitle());
        assertEquals("Shop now", hero.getCtaLabel());
        assertEquals("/content/blueshelf/us/en/tvs.html", hero.getCtaLink(), ".html appended to internal link");
        assertEquals("yellow", hero.getTheme());
        assertTrue(hero.hasCta());
        assertEquals("/content/blueshelf/us/en/jcr:content/root/hero", hero.getPath());
    }

    @Test
    void defaultsThemeAndHandlesMissingCta() {
        // Adapting from a Resource directly (not request)
        HeroModel hero = context.resourceResolver()
                .getResource("/content/blueshelf/us/en/jcr:content/root/hero-minimal")
                .adaptTo(HeroModel.class);

        assertNotNull(hero, "OPTIONAL injection strategy => model still instantiates with missing props");
        assertEquals("Minimal", hero.getTitle());
        assertEquals("blue", hero.getTheme());
        assertFalse(hero.hasCta());
        assertNull(hero.getCtaLink());
    }

    @Test
    void externalLinkIsLeftUntouched() {
        HeroModel hero = context.resourceResolver()
                .getResource("/content/blueshelf/us/en/jcr:content/root/hero-external")
                .adaptTo(HeroModel.class);
        assertEquals("https://www.bestbuy.com/", hero.getCtaLink());
    }
}
