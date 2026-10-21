package com.Sambhav.ecommerce.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class UpdateProductRequest {
    @Size(max = 200, message = "Product name cannot exceed 200 characters")
    private String name;

    @Positive(message = "Price must be greater than zero")
    private Double price;

    @Positive(message = "Category ID must be greater than zero")
    private Integer categoryId;

    @PositiveOrZero(message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    @Size(max = 1000, message = "Image URL cannot exceed 1000 characters")
    private String imageUrl;

    public UpdateProductRequest() { }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
