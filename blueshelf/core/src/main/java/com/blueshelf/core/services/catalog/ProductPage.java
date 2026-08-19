package com.blueshelf.core.services.catalog;

import java.util.List;

/** A page of results + a flag telling the UI whether data is live, stale (served from cache after an upstream error) or unavailable. */
public final class ProductPage {
    public enum Source { LIVE, CACHE, STALE, UNAVAILABLE }

    private final List<Product> items;
    private final int total;
    private final Source source;

    public ProductPage(List<Product> items, int total, Source source) {
        this.items = items == null ? List.of() : List.copyOf(items);
        this.total = total;
        this.source = source;
    }
    public static ProductPage unavailable() { return new ProductPage(List.of(), 0, Source.UNAVAILABLE); }
    public ProductPage withSource(Source s) { return new ProductPage(items, total, s); }

    public List<Product> getItems() { return items; }
    public int getTotal() { return total; }
    public Source getSource() { return source; }
    public boolean isAvailable() { return source != Source.UNAVAILABLE; }
    public boolean isStale() { return source == Source.STALE; }
}
