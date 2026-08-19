package com.blueshelf.core.models;

import com.blueshelf.core.util.ComponentExporter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.factory.ModelFactory;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * Page exporter — GET /content/blueshelf/us/en.model.json
 * Registered for BOTH the page component (jcr:content) and cq/Page, so the page URL itself (not only
 * .../jcr:content) answers — like AEM, where /content/site/en.model.json is the headless entry point.
 *
 * Shape (AEM SPA Editor contract): { ":type", ":path", title, navigation, ":items": {root: container...} }
 */
@Model(adaptables = SlingHttpServletRequest.class, resourceType = {"blueshelf/components/page", "cq/Page"},
       defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
@Exporter(name = "jackson", extensions = "json")
public class PageModel {

    @Self private SlingHttpServletRequest request;
    @SlingObject private Resource resource;
    @OSGiService private ModelFactory modelFactory;

    private Resource content;   // the jcr:content node
    private Resource page;      // the cq:Page node
    private final Map<String, Object> items = new LinkedHashMap<>();
    private final List<String> order = new ArrayList<>();
    private final List<Map<String, String>> navigation = new ArrayList<>();

    @PostConstruct
    protected void init() {
        if ("cq:Page".equals(resource.getValueMap().get("jcr:primaryType", String.class))) {
            page = resource; content = resource.getChild("jcr:content");
        } else {
            content = resource; page = resource.getParent();
        }
        if (content == null) return;
        for (Resource child : content.getChildren()) {
            items.put(child.getName(), ComponentExporter.export(request, child, modelFactory));
            order.add(child.getName());
        }
        // simple site navigation: siblings under the language root that are not hidden
        Resource langRoot = page;
        while (langRoot != null && langRoot.getParent() != null && !"en".equals(langRoot.getName())) langRoot = langRoot.getParent();
        if (langRoot != null) {
            navigation.add(Map.of("title", "Home", "path", langRoot.getPath()));
            for (Resource child : langRoot.getChildren()) {
                Resource jc = child.getChild("jcr:content");
                if (jc == null) continue;
                ValueMap vm = jc.getValueMap();
                if (vm.get("hideInNav", false)) continue;
                navigation.add(Map.of("title", vm.get("jcr:title", child.getName()), "path", child.getPath()));
            }
        }
    }

    public String getTitle() { return content == null ? null : content.getValueMap().get("jcr:title", String.class); }
    public String getDescription() { return content == null ? null : content.getValueMap().get("jcr:description", String.class); }
    public String getTemplate() { return content == null ? null : content.getValueMap().get("cq:template", String.class); }
    public String getLanguage() { return "en-US"; }
    public List<Map<String, String>> getNavigation() { return navigation; }
    @JsonProperty(":type") public String getType() { return content == null ? "cq/Page" : content.getResourceType(); }
    @JsonProperty(":path") public String getPath() { return page == null ? resource.getPath() : page.getPath(); }
    @JsonProperty(":items") public Map<String, Object> getItems() { return items; }
    @JsonProperty(":itemsOrder") public List<String> getItemsOrder() { return order; }
}
