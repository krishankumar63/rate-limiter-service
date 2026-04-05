package com.dev.rate_limiter_service.controller;

import com.dev.rate_limiter_service.dto.ApiResponse;
import com.dev.rate_limiter_service.dto.Product;
import com.dev.rate_limiter_service.dto.RateLimitMeta;
import com.dev.rate_limiter_service.service.BucketService;
import com.dev.rate_limiter_service.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;
    private final BucketService bucketService;

    public ProductController(ProductService productService, BucketService bucketService) {
        this.productService = productService;
        this.bucketService = bucketService;
    }

    /**
     * GET /api/products
     * Optional: ?category=Pizza
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> getProducts(
            @RequestHeader(value = "X-User-Id", required = false,
                    defaultValue = "anonymous") String userId,
            @RequestParam(required = false) String category) {

        List<Product> products = (category != null && !category.isBlank())
                ? productService.getByCategory(category)
                : productService.getAllProducts();

        RateLimitMeta meta = RateLimitMeta.of(
                bucketService.getUserProductBucket(userId).getAvailableTokens(),
                "product");

        return ResponseEntity.ok(ApiResponse.ok(products, meta));
    }

    /**
     * GET /api/products/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getProduct(
            @RequestHeader(value = "X-User-Id", required = false,
                    defaultValue = "anonymous") String userId,
            @PathVariable String id) {

        Product product = productService.getById(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }

        RateLimitMeta meta = RateLimitMeta.of(
                bucketService.getUserProductBucket(userId).getAvailableTokens(),
                "product");

        return ResponseEntity.ok(ApiResponse.ok(product, meta));
    }
}