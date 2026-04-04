package com.dev.rate_limiter_service.service;

import com.dev.rate_limiter_service.dto.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Static product catalogue — simulates a real product DB.
 */
@Service
public class ProductService {

    private static final List<Product> CATALOGUE = List.of(
            Product.of("P001", "Margherita Pizza",  "Pizza",  new BigDecimal("299.00"),  true),
            Product.of("P002", "Chicken Biryani",   "Biryani",new BigDecimal("349.00"),  true),
            Product.of("P003", "Butter Paneer",     "Curry",  new BigDecimal("249.00"),  true),
            Product.of("P004", "Masala Dosa",       "South",  new BigDecimal("149.00"),  true),
            Product.of("P005", "Cold Coffee",       "Drinks", new BigDecimal("129.00"),  true),
            Product.of("P006", "Veg Burger",        "Fast Food",new BigDecimal("199.00"), false),
            Product.of("P007", "Pasta Arrabiata",   "Italian",new BigDecimal("279.00"),  true),
            Product.of("P008", "Gulab Jamun",       "Dessert",new BigDecimal("89.00"),   true)
    );

    public List<Product> getAllProducts() {
        return CATALOGUE;
    }

    public List<Product> getByCategory(String category) {
        return CATALOGUE.stream()
                .filter(p -> p.getCategory().equalsIgnoreCase(category))
                .toList();
    }

    public Product getById(String productId) {
        return CATALOGUE.stream()
                .filter(p -> p.getProductId().equalsIgnoreCase(productId))
                .findFirst()
                .orElse(null);
    }
}