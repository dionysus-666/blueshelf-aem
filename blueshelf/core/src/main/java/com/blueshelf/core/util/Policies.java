package com.blueshelf.core.util;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the editable-template POLICY for a component resource (AEM: ContentPolicyManager / ContentPolicy).
 *
 * Lookup (mirrors AEM):
 *  1. walk up to the page's jcr:content, read cq:template
 *  2. mapping node = <template>/policies/jcr:content/<relative path of container>/<component resourceType>
 *     e.g. /conf/.../content-page/policies/jcr:content/root/blueshelf/components/hero  → cq:policy
 *  3. policy node = /conf/blueshelf/settings/wcm/policies/<cq:policy>
 */
public final class Policies {

    public static final String POLICIES_ROOT = "/conf/blueshelf/settings/wcm/policies/";

    private Policies() {}

    /** @return the policy resource or null */
    public static Resource policyFor(Resource component) {
        if (component == null) return null;
        Resource pageContent = pageContentOf(component);
        if (pageContent == null) return null;
        String template = pageContent.getValueMap().get("cq:template", String.class);
        if (template == null) return null;
        ResourceResolver rr = component.getResourceResolver();

        // relative path of the PARENT container under jcr:content, e.g. "root" (or "" for the page itself)
        String rel = component.getParent() != null && component.getParent().getPath().startsWith(pageContent.getPath())
                ? component.getParent().getPath().substring(pageContent.getPath().length()) : "";
        String rt = component.getResourceType();
        List<String> candidates = new ArrayList<>();
        if (component.getPath().equals(pageContent.getPath())) {
            candidates.add(template + "/policies/jcr:content");
        } else {
            candidates.add(template + "/policies/jcr:content" + rel + "/" + rt);    // container child by resource type
            candidates.add(template + "/policies/jcr:content" + rel + "/" + component.getName()); // explicit node (e.g. root container itself)
            candidates.add(template + "/policies/jcr:content/" + rt);               // fallback: anywhere in template
        }
        for (String c : candidates) {
            Resource mapping = rr.getResource(c);
            String policy = mapping == null ? null : mapping.getValueMap().get("cq:policy", String.class);
            if (policy != null) {
                Resource p = rr.getResource(POLICIES_ROOT + policy);
                if (p != null) return p;
            }
        }
        return null;
    }

    public static ValueMap policyProperties(Resource component) {
        Resource p = policyFor(component);
        return p == null ? ValueMap.EMPTY : p.getValueMap();
    }

    public static Resource pageContentOf(Resource r) {
        for (Resource cur = r; cur != null; cur = cur.getParent()) {
            if ("jcr:content".equals(cur.getName()) && cur.getParent() != null
                    && "cq:Page".equals(cur.getParent().getValueMap().get("jcr:primaryType", String.class))) {
                return cur;
            }
        }
        return null;
    }
}
