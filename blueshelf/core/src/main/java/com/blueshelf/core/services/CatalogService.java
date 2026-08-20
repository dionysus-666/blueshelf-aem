package com.blueshelf.core.services;

import com.blueshelf.core.services.catalog.Product;
import com.blueshelf.core.services.catalog.ProductPage;
import com.blueshelf.core.services.catalog.ProductQuery;
import org.osgi.annotation.versioning.ProviderType;

import java.util.Optional;
import java.util.List;
import com.blueshelf.core.services.catalog.Store;

/**
 * Read access to the product catalog (an external system). Components depend on THIS interface,
 * never on HTTP details => swappable, mockable in unit tests (`context.registerService(CatalogService.class, mock)`).
 *
 * Correlation: a Spring `@Service` interface; in AEM it's an OSGi service injected with `@OSGiService` (models)
 * or `@Reference` (other services/servlets).
 */
@ProviderType
public interface CatalogService {

    /** Never throws: on upstream failure returns a cached/stale/unavailable page so pages still render. */
    ProductPage search(ProductQuery query);

    /** Empty if unknown OR upstream down (callers render a friendly message either way). */
    Optional<Product> getProduct(String sku);

    List<Store> storesNear(String zip);
    /** Health snapshot for debugging/monitoring (exposed in the OSGi console + servlet). */
    String status();
}
