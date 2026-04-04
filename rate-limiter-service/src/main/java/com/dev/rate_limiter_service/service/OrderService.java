package com.dev.rate_limiter_service.service;

import com.dev.rate_limiter_service.dto.Order;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure order business logic — no rate limiting here.
 * In a real system this would talk to a database.
 */
@Service
public class OrderService {

    // In-memory order store (simulates DB for this demo)
    private final Map<String, List<Order>> ordersByUser = new ConcurrentHashMap<>();

    public Order placeOrder(String userId, String restaurantId,
                            String restaurantName, String items, BigDecimal amount) {
        Order order = Order.create(userId, restaurantId, restaurantName, items, amount);
        ordersByUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(order);
        return order;
    }

    public List<Order> getOrderHistory(String userId) {
        return ordersByUser.getOrDefault(userId, List.of());
    }

    public long getTotalOrdersPlaced() {
        return ordersByUser.values().stream().mapToLong(List::size).sum();
    }
}