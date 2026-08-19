package com.blueshelf.core.models;

import com.blueshelf.core.util.Policies;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.Set;

/**
 * Title (like Core Components' Title): text defaults to the PAGE title when the author leaves it empty,
 * heading level defaults from the POLICY (design-time) then the dialog (author-time).
 * Policy-over-dialog layering is a very common AEM question: "where can a default come from?"
 * → dialog value → policy/design → component default.
 */
@Model(adaptables = {Resource.class, SlingHttpServletRequest.class}, resourceType = "blueshelf/components/base/title/v1/title",
       defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
@Exporter(name = "jackson", extensions = "json")
public class TitleModel {

    private static final Set<String> ALLOWED = Set.of("h1", "h2", "h3", "h4", "h5", "h6");

    @SlingObject
    private Resource resource;

    @ValueMapValue(name = "jcr:title")
    private String text;

    @ValueMapValue
    private String type;

    @PostConstruct
    protected void init() {
        if (StringUtils.isBlank(text)) {
            Resource pageContent = Policies.pageContentOf(resource);
            if (pageContent != null) text = pageContent.getValueMap().get("jcr:title", String.class);
        }
        if (StringUtils.isBlank(type)) {
            type = Policies.policyProperties(resource).get("type", String.class);
        }
        if (type == null || !ALLOWED.contains(type)) type = "h2";
    }

    public String getText() { return text; }
    public String getType() { return type; }
}
