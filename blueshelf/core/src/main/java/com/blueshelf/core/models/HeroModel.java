package com.blueshelf.core.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;

/**
 * Sling Model for the Hero component.
 *
 * <p><b>Correlation:</b> this is the "view-model" / "controller" for one component. HTL binds it with
 * {@code data-sly-use.hero="com.blueshelf.core.models.HeroModel"} and reads getters.
 * In React terms: the node's properties are the <i>props</i>, this class is the <i>component function</i>
 * that derives render-ready values, and the HTL file is the JSX.</p>
 *
 * <p><b>Gotchas (interview favourites):</b></p>
 * <ul>
 *   <li>{@code adaptables}: {@code Resource} vs {@code SlingHttpServletRequest}. Adapting from the request
 *       lets you inject request-scoped things (selectors, WCM mode, current page), but such models
 *       cannot be reached from a plain resource (e.g. {@code resource.adaptTo(HeroModel.class)} returns
 *       {@code null}). Declaring both is common and fine.</li>
 *   <li>{@code defaultInjectionStrategy = OPTIONAL}: without it every missing property makes the whole
 *       model fail to instantiate (returns null in HTL — silently!). With it, fields are just null.</li>
 *   <li>Prefer specific injectors ({@code @ValueMapValue}, {@code @OSGiService}, {@code @ChildResource})
 *       over the generic {@code @Inject} — faster and explicit about the source.</li>
 *   <li>{@code @Exporter(name="jackson")} + {@code resourceType} = Sling Model Exporter: the same model
 *       renders as JSON at {@code <path>.model.json}. This is the backbone of AEM headless / SPA Editor.</li>
 * </ul>
 */
@Model(
        adaptables = {Resource.class, SlingHttpServletRequest.class},
        resourceType = HeroModel.RESOURCE_TYPE,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
@Exporter(name = "jackson", extensions = "json")
public class HeroModel {

    public static final String RESOURCE_TYPE = "blueshelf/components/hero";

    /** Property on the JCR node: jcr:title (authored via dialog). */
    @ValueMapValue(name = "jcr:title")
    private String title;

    @ValueMapValue
    private String subtitle;

    @ValueMapValue
    private String ctaLabel;

    @ValueMapValue
    private String ctaLink;

    @ValueMapValue
    private String theme; // e.g. "blue" | "yellow" (Best Buy palette ;-))

    /** Exercise 1: small promo label. Field name == JCR property name (no `name=` needed). */
    @ValueMapValue
    private String badge;

    /** The resource this model was adapted from — handy for path/debug. */
    @SlingObject
    private Resource resource;

    @Self
    private Resource self; // works when adapted from a Resource; null when from a request

    private String resolvedCtaLink;

    /**
     * Runs after injection. Put derived/"computed" values here rather than in getters,
     * so getters stay cheap (HTL may call them several times).
     */
    @PostConstruct
    protected void init() {
        if (StringUtils.isBlank(theme)) {
            theme = "blue";
        }
        // Internal content links must end in .html to be resolved by Sling/Dispatcher;
        // external links are left alone. (Classic AEM "link checker"/LinkHandler concern.)
        if (StringUtils.isNotBlank(ctaLink) && ctaLink.startsWith("/content/") && !ctaLink.endsWith(".html")) {
            resolvedCtaLink = ctaLink + ".html";
        } else {
            resolvedCtaLink = ctaLink;
        }
    }

    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getCtaLabel() {
        return ctaLabel;
    }

    public String getCtaLink() {
        return resolvedCtaLink;
    }

    public String getTheme() {
        return theme;
    }

    public String getBadge() {
        return badge;
    }

    public boolean hasCta() {
        return StringUtils.isNotBlank(ctaLabel) && StringUtils.isNotBlank(resolvedCtaLink);
    }

    /** Useful for debugging in HTL; excluded from JSON export. */
    @JsonIgnore
    public String getPath() {
        return resource != null ? resource.getPath() : null;
    }
}
