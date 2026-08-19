package com.blueshelf.core.models;

import com.blueshelf.core.util.Policies;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * Style System (AEM): the template POLICY of a component defines style groups → styles (id, label, css classes).
 * Authors pick styles in the editor; the editor stores the ids on the component node as {@code cq:styleIds};
 * at render time we translate ids → CSS classes. Core Components do this via {@code ComponentStyleInfo};
 * here it's a small model every component can {@code data-sly-use}.
 *
 * Why this matters: it lets authors vary look & feel (dark hero, 2-column, compact card) WITHOUT developers
 * adding dialog fields per variation — CSS classes become authorable configuration, governed per template.
 */
// OPTIONAL: cq:styleIds is usually absent — without this the model fails to instantiate (MissingElementsException)
@Model(adaptables = SlingHttpServletRequest.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class StyleModel {

    @Self
    private SlingHttpServletRequest request;

    @SlingObject
    private Resource resource;

    @ValueMapValue(name = "cq:styleIds")
    private String[] styleIds;

    private String cssClasses = "";
    private final List<Map<String, String>> available = new ArrayList<>();

    @PostConstruct
    protected void init() {
        Resource policy = Policies.policyFor(resource);
        Set<String> selected = styleIds == null ? Set.of() : new HashSet<>(Arrays.asList(styleIds));
        List<String> classes = new ArrayList<>();
        if (policy != null) {
            Resource groups = policy.getChild("cq:styleGroups");
            if (groups != null) {
                for (Resource group : groups.getChildren()) {
                    Resource styles = group.getChild("cq:styles");
                    if (styles == null) continue;
                    for (Resource style : styles.getChildren()) {
                        String id = style.getValueMap().get("cq:styleId", String.class);
                        String cls = style.getValueMap().get("cq:styleClasses", "");
                        available.add(Map.of("id", String.valueOf(id), "label", style.getValueMap().get("cq:styleLabel", ""), "classes", cls,
                                "group", group.getValueMap().get("cq:styleGroupLabel", "")));
                        if (id != null && selected.contains(id) && !cls.isBlank()) classes.add(cls);
                    }
                }
            }
        }
        cssClasses = String.join(" ", classes);
    }

    public List<String> getSelected() { return styleIds == null ? List.of() : Arrays.asList(styleIds); }

    /** Space-separated classes for the selected styles (empty string when none). */
    public String getCssClasses() { return cssClasses; }

    /** All styles the policy offers — the editor's "Styles" tab uses this via JSON. */
    public List<Map<String, String>> getAvailable() { return available; }

    public boolean isEdit() {
        String p = request.getParameter("wcmmode");
        if (p != null) return "edit".equalsIgnoreCase(p);
        javax.servlet.http.Cookie c = request.getCookie("wcmmode");
        return c != null && "edit".equalsIgnoreCase(c.getValue());
    }
}
