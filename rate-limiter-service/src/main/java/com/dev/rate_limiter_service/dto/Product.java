package com.dev.rate_limiter_service.dto;

import java.math.BigDecimal;

public class Product {

    private String productId;
    private String name;
    private String category;
    private BigDecimal price;
    private boolean available;

    public static Product of(String productId, String name,
                             String category, BigDecimal price, boolean available) {
        Product p = new Product();
        p.productId = productId;
        p.name = name;
        p.category = category;
        p.price = price;
        p.available = available;
        return p;
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public boolean isAvailable() { return available; }
}