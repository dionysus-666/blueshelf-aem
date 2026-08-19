package com.blueshelf.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.SlingHttpServletRequestWrapper;
import org.apache.sling.models.factory.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a component resource into the JSON shape AEM's SPA Editor / Core Components produce:
 * <pre>{ ":type": "blueshelf/components/hero", "title": "...", ... }</pre>
 * and for containers <pre>{ ":type": ".../container", ":items": {name: {...}}, ":itemsOrder": [names] }</pre>
 *
 * How: for each child we ask Sling Models for the model registered for that resource type (request-adaptable
 * first, via a request wrapper that swaps the resource — that is what {@code data-sly-resource} does too),
 * serialize it with Jackson to a Map, and add {@code :type}. Components without a model export their
 * raw properties (good enough for text-like components).
 *
 * AEM correlation: {@code com.adobe.cq.export.json.ComponentExporter} / {@code ContainerExporter} and
 * {@code @Exporter(name="jackson", ... options=...)}; the {@code :type} key is what {@code MapTo('...')} matches.
 */
public final class ComponentExporter {

    private static final Logger LOG = LoggerFactory.getLogger(ComponentExporter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private ComponentExporter() {}

    public static Map<String, Object> export(SlingHttpServletRequest request, Resource resource, ModelFactory modelFactory) {
        Map<String, Object> out = new LinkedHashMap<>();
        Object model = null;
        try {
            SlingHttpServletRequest wrapped = new ResourceRequestWrapper(request, resource);
            if (modelFactory.isModelAvailableForRequest(wrapped)) {
                model = modelFactory.getModelFromRequest(wrapped);
            } else if (modelFactory.isModelAvailableForResource(resource)) {
                model = modelFactory.getModelFromResource(resource);
            }
        } catch (RuntimeException e) {
            LOG.warn("Could not build model for {} ({}): {}", resource.getPath(), resource.getResourceType(), e.toString());
        }
        if (model != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = MAPPER.convertValue(model, Map.class);
            out.putAll(props);
        } else {
            resource.getValueMap().forEach((k, v) -> {
                if (!k.startsWith("jcr:") && !k.startsWith("sling:") && !k.startsWith("cq:")) out.put(k, v);
                if ("jcr:title".equals(k)) out.put("title", v);
                if ("jcr:description".equals(k)) out.put("description", v);
            });
        }
        out.put(":type", resource.getResourceType());
        out.put(":path", resource.getPath());
        return out;
    }

    /** Request wrapper whose getResource() is the given resource (like Sling's include does). */
    public static final class ResourceRequestWrapper extends SlingHttpServletRequestWrapper {
        private final Resource resource;
        public ResourceRequestWrapper(SlingHttpServletRequest req, Resource resource) { super(req); this.resource = resource; }
        @Override public Resource getResource() { return resource; }
    }
}
