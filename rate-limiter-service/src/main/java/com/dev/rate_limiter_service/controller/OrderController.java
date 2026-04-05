package com.dev.rate_limiter_service.controller;

import com.dev.rate_limiter_service.dto.ApiResponse;
import com.dev.rate_limiter_service.dto.Order;
import com.dev.rate_limiter_service.dto.RateLimitMeta;
import com.dev.rate_limiter_service.service.BucketService;
import com.dev.rate_limiter_service.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order endpoints — ZERO rate limiting code here.
 * The filter has already done its job by the time we reach this class.
 */
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;
    private final BucketService bucketService;

    public OrderController(OrderService orderService, BucketService bucketService) {
        this.orderService = orderService;
        this.bucketService = bucketService;
    }

    /**
     * POST /api/orders/place
     * Header: X-User-Id: user123
     */
    @PostMapping("/place")
    public ResponseEntity<ApiResponse<Order>> placeOrder(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String restaurantId,
            @RequestParam(defaultValue = "Burger King") String restaurantName,
            @RequestParam String items,
            @RequestParam(defaultValue = "299.00") BigDecimal amount) {

        Order order = orderService.placeOrder(userId, restaurantId, restaurantName, items, amount);

        RateLimitMeta meta = RateLimitMeta.of(
                bucketService.getUserOrderBucket(userId).getAvailableTokens(),
                "user_order");

        return ResponseEntity.ok(ApiResponse.ok(order, "Order placed successfully!", meta));
    }

    /**
     * GET /api/orders/history
     * Header: X-User-Id: user123
     */
    //availabe tokens in the response is just for demo purposes, in real world we might not want to expose this info
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<Order>>> getOrderHistory(
            @RequestHeader("X-User-Id") String userId) {

        List<Order> orders = orderService.getOrderHistory(userId);

        RateLimitMeta meta = RateLimitMeta.of(
                bucketService.getUserOrderBucket(userId).getAvailableTokens(),
                "user_order");

        return ResponseEntity.ok(ApiResponse.ok(orders, meta));
    }
}