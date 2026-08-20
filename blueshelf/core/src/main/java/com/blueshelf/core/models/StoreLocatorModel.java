package com.blueshelf.core.models;

import com.blueshelf.core.services.CatalogService;
import com.blueshelf.core.services.catalog.Store;
import org.apache.commons.lang3.StringUtils;
import com.blueshelf.core.util.Policies;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.List;

@Model(adaptables = SlingHttpServletRequest.class,
        resourceType = "blueshelf/components/store-locator",
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
@Exporter(name = "jackson", extensions = "json")
public class StoreLocatorModel {

    @Self
    private SlingHttpServletRequest request;

    @OSGiService
    private CatalogService catalogService;

    /** The component's resource — used to find the containing page (cq:Page ancestor). */
    @SlingObject
    private Resource resource;

    @ValueMapValue(name = "jcr:title")
    private String title;

    @ValueMapValue
    private String defaultZip;

    private String zip;
    private String pageUrl;
    private List<Store> stores = List.of();

    @PostConstruct
    protected void init() {
        String param = request == null ? null : StringUtils.trimToNull(request.getParameter("zip"));
        zip = param != null ? param : StringUtils.trimToNull(defaultZip);
        // The form must submit to the PAGE, not the component (a component path renders a naked fragment).
        // AEM equivalent: currentPage.getPath() — Core Components expose it from the model just like this.
        Resource pageContent = Policies.pageContentOf(resource);
        pageUrl = pageContent != null && pageContent.getParent() != null ? pageContent.getParent().getPath() + ".html" : null;
        if (zip != null && catalogService != null) {
            stores = catalogService.storesNear(zip);
        }
    }

    public String getTitle() { return title; }
    public String getZip() { return zip; }
    public String getPageUrl() { return pageUrl; }
    public List<Store> getStores() { return stores; }
    public boolean isEmpty() { return stores.isEmpty(); }
}