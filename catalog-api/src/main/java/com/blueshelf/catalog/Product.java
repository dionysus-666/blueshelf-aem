package com.blueshelf.catalog;

import java.util.List;

public record Product(
        String sku,
        String name,
        String brand,
        String category,
        double price,
        Double salePrice,
        double rating,
        int reviewCount,
        boolean inStock,
        String image,
        String shortDescription,
        List<String> highlights
) {
    public boolean onSale() { return salePrice != null && salePrice < price; }
}
