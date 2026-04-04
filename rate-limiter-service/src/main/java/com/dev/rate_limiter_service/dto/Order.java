package com.dev.rate_limiter_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class Order {

    private String orderId;
    private String userId;
    private String restaurantId;
    private String restaurantName;
    private String items;
    private BigDecimal amount;
    private String status;
    private Instant placedAt;

    // ── Static factory ────────────────────────────────────────────────────────

    public static Order create(String userId, String restaurantId,
                               String restaurantName, String items, BigDecimal amount) {
        Order o = new Order();
        o.orderId     = "ORD_" + System.currentTimeMillis();
        o.userId      = userId;
        o.restaurantId = restaurantId;
        o.restaurantName = restaurantName;
        o.items       = items;
        o.amount      = amount;
        o.status      = "CONFIRMED";
        o.placedAt    = Instant.now();
        return o;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getRestaurantId() { return restaurantId; }
    public String getRestaurantName() { return restaurantName; }
    public String getItems() { return items; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public Instant getPlacedAt() { return placedAt; }
}