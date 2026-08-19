package com.blueshelf.core.models;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/** Teaser model — HTL has no string functions on purpose; link handling lives here (Core Components: LinkHandler). */
@Model(adaptables = {Resource.class, SlingHttpServletRequest.class}, resourceType = "blueshelf/components/base/teaser/v1/teaser",
       defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
@Exporter(name = "jackson", extensions = "json")
public class TeaserModel {
    @ValueMapValue(name = "jcr:title") private String title;
    @ValueMapValue(name = "jcr:description") private String description;
    @ValueMapValue private String image;
    @ValueMapValue private String linkURL;
    @ValueMapValue private String linkText;

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getImage() { return image; }
    public String getLinkText() { return StringUtils.defaultIfBlank(linkText, "Learn more"); }
    public String getLink() {
        if (StringUtils.isBlank(linkURL)) return null;
        return linkURL.startsWith("/content/") && !linkURL.endsWith(".html") ? linkURL + ".html" : linkURL;
    }
    public boolean isEmpty() { return StringUtils.isBlank(title) && StringUtils.isBlank(image); }
}
