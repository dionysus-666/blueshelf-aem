package com.blueshelf.core.services.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Product DTO as returned by the Catalog API. Plain immutable value object (no Sling/JCR here):
 * the service layer must stay testable without a repository.
 * Jackson-friendly: public no-arg ctor + setters, unknown fields ignored (upstream can add fields safely).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Product {
    private String sku;
    private String name;
    private String brand;
    private String category;
    private double price;
    private Double salePrice;
    private double rating;
    private int reviewCount;
    private boolean inStock;
    private String image;
    private String shortDescription;
    private List<String> highlights = List.of();

    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public Double getSalePrice() { return salePrice; }
    public double getRating() { return rating; }
    public int getReviewCount() { return reviewCount; }
    public boolean isInStock() { return inStock; }
    public String getImage() { return image; }
    public String getShortDescription() { return shortDescription; }
    public List<String> getHighlights() { return highlights; }

    public boolean isOnSale() { return salePrice != null && salePrice < price; }
    /** Effective price for display/sorting. */
    public double getCurrentPrice() { return isOnSale() ? salePrice : price; }
    public int getSavingsPercent() { return isOnSale() ? (int) Math.round((1 - salePrice / price) * 100) : 0; }

    public void setSku(String sku) { this.sku = sku; }
    public void setName(String name) { this.name = name; }
    public void setBrand(String brand) { this.brand = brand; }
    public void setCategory(String category) { this.category = category; }
    public void setPrice(double price) { this.price = price; }
    public void setSalePrice(Double salePrice) { this.salePrice = salePrice; }
    public void setRating(double rating) { this.rating = rating; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
    public void setInStock(boolean inStock) { this.inStock = inStock; }
    public void setImage(String image) { this.image = image; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public void setHighlights(List<String> highlights) { this.highlights = highlights == null ? List.of() : highlights; }
}
