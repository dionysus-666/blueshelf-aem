package com.blueshelf.core.services.catalog;

import java.util.Objects;

/** Immutable query object => stable cache key. */
public final class ProductQuery {
    private final String category;
    private final String text;
    private final int page;
    private final int size;
    private final String sort;

    public ProductQuery(String category, String text, int page, int size, String sort) {
        this.category = category == null ? "" : category.trim();
        this.text = text == null ? "" : text.trim();
        this.page = Math.max(0, page);
        this.size = Math.min(Math.max(1, size), 48);
        this.sort = sort == null || sort.isBlank() ? "relevance" : sort;
    }
    public static ProductQuery category(String category, int size) { return new ProductQuery(category, null, 0, size, null); }

    public String getCategory() { return category; }
    public String getText() { return text; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public String getSort() { return sort; }

    public String cacheKey() { return "q|" + category + "|" + text + "|" + page + "|" + size + "|" + sort; }

    @Override public boolean equals(Object o) { return o instanceof ProductQuery q && cacheKey().equals(q.cacheKey()); }
    @Override public int hashCode() { return Objects.hash(cacheKey()); }
    @Override public String toString() { return cacheKey(); }
}
