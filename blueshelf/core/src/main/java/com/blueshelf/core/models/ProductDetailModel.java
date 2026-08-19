package com.blueshelf.core.models;

import com.blueshelf.core.services.CatalogService;
import com.blueshelf.core.services.catalog.Product;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.Optional;

/**
 * Product Detail component. SKU comes from (1) the dialog (fixed product on a campaign page) or
 * (2) the URL suffix: /content/blueshelf/us/en/product.html/BS1001
 *
 * <p>The suffix pattern is how many AEM catalog sites render thousands of PDPs from ONE authored page
 * (no page per product in the JCR). Gotcha: the Dispatcher caches by full URL incl. suffix, so each
 * product gets its own cache file — fine — but cache invalidation must be by content path /…/product.html
 * prefix (statfile) or per-suffix flush.</p>
 */
@Model(adaptables = SlingHttpServletRequest.class,
       resourceType = ProductDetailModel.RESOURCE_TYPE,
       defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
@Exporter(name = "jackson", extensions = "json")
public class ProductDetailModel {

    public static final String RESOURCE_TYPE = "blueshelf/components/product-detail";

    @Self
    private SlingHttpServletRequest request;

    @OSGiService
    private CatalogService catalogService;

    @ValueMapValue
    private String sku;

    private String resolvedSku;
    private Product product;
    private boolean lookedUp;

    @PostConstruct
    protected void init() {
        resolvedSku = StringUtils.trimToNull(sku);
        if (resolvedSku == null && request != null) {
            String suffix = request.getRequestPathInfo().getSuffix(); // "/BS1001" or null
            if (suffix != null) {
                resolvedSku = StringUtils.trimToNull(suffix.replaceFirst("^/+", "").split("/")[0]);
            }
        }
        if (resolvedSku != null && catalogService != null) {
            Optional<Product> p = catalogService.getProduct(resolvedSku);
            product = p.orElse(null);
            lookedUp = true;
        }
    }

    public String getSku() { return resolvedSku; }
    public Product getProduct() { return product; }
    public boolean isFound() { return product != null; }
    /** True when we had a SKU but nothing came back (unknown SKU or backend down). */
    public boolean isNotFound() { return lookedUp && product == null; }
    public boolean isNoSku() { return resolvedSku == null; }
}
