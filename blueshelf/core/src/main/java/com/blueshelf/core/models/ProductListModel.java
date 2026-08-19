package com.blueshelf.core.models;

import com.blueshelf.core.services.CatalogService;
import com.blueshelf.core.services.catalog.Product;
import com.blueshelf.core.services.catalog.ProductPage;
import com.blueshelf.core.services.catalog.ProductQuery;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.RequestAttribute;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Product List component: authored config (category/limit/sort) + live data from {@link CatalogService}.
 *
 * <p>Pattern to remember: <b>content from JCR, data from services</b>. The author decides WHAT to show
 * (dialog → node properties); the service decides the actual products at render time (cached).</p>
 *
 * <p>Injectors used: {@code @ValueMapValue} (dialog props), {@code @OSGiService} (service), {@code @Self}
 * (the request, to read query params like ?q=). Adapted from the REQUEST because we read parameters.</p>
 */
@Model(adaptables = SlingHttpServletRequest.class,
       resourceType = ProductListModel.RESOURCE_TYPE,
       defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
@Exporter(name = "jackson", extensions = "json")
public class ProductListModel {

    public static final String RESOURCE_TYPE = "blueshelf/components/product-list";

    @Self
    private SlingHttpServletRequest request;

    @OSGiService
    private CatalogService catalogService;

    @ValueMapValue(name = "jcr:title")
    private String title;

    @ValueMapValue
    private String category;

    @ValueMapValue
    private Integer limit;

    @ValueMapValue
    private String sort;

    /** When true, the component reads ?q= from the URL (search results page mode). */
    @ValueMapValue
    private boolean useQueryParam;

    @ValueMapValue
    private String productPagePath;

    /** Test hook / composition hook: a parent component may pass a query via request attribute. */
    @RequestAttribute(name = "blueshelf.productQuery")
    private ProductQuery injectedQuery;

    private ProductPage page;
    private String effectiveText;

    @PostConstruct
    protected void init() {
        int size = limit == null || limit <= 0 ? 6 : limit;
        effectiveText = useQueryParam && request != null ? StringUtils.trimToEmpty(request.getParameter("q")) : "";
        ProductQuery q = injectedQuery != null ? injectedQuery : new ProductQuery(category, effectiveText, 0, size, sort);
        page = catalogService == null ? ProductPage.unavailable() : catalogService.search(q);
    }

    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getSearchText() { return effectiveText; }
    public List<Product> getProducts() { return page.getItems(); }
    public int getTotal() { return page.getTotal(); }
    public boolean isAvailable() { return page.isAvailable(); }
    public boolean isStale() { return page.isStale(); }
    public String getSource() { return page.getSource().name(); }
    public boolean isEmpty() { return page.getItems().isEmpty(); }

    /** Link target for a product card: the authored product page + SKU suffix, e.g. /content/blueshelf/us/en/product.html/BS1001 */
    public String getProductPagePath() {
        return StringUtils.isBlank(productPagePath) ? "/content/blueshelf/us/en/product" : productPagePath;
    }
    @JsonIgnore
    public String productUrl(String sku) { return getProductPagePath() + ".html/" + sku; }
}
