package com.blueshelf.core.models;

import com.blueshelf.core.util.ComponentExporter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.factory.ModelFactory;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Container (parsys) exporter: children as ":items" + ":itemsOrder" — the SPA Editor JSON contract. */
@Model(adaptables = SlingHttpServletRequest.class, resourceType = "blueshelf/components/container",
       defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
@Exporter(name = "jackson", extensions = "json")
public class ContainerModel {

    @Self private SlingHttpServletRequest request;
    @SlingObject private Resource resource;
    @OSGiService private ModelFactory modelFactory;

    private final Map<String, Object> items = new LinkedHashMap<>();
    private final List<String> order = new ArrayList<>();

    @PostConstruct
    protected void init() {
        for (Resource child : resource.getChildren()) {
            items.put(child.getName(), ComponentExporter.export(request, child, modelFactory));
            order.add(child.getName());
        }
    }

    @JsonProperty(":items") public Map<String, Object> getItems() { return items; }
    @JsonProperty(":itemsOrder") public List<String> getItemsOrder() { return order; }
    @JsonProperty(":type") public String getType() { return resource.getResourceType(); }
}
