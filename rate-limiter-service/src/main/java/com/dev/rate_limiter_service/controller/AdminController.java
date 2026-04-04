package com.dev.rate_limiter_service.controller;

import com.dev.rate_limiter_service.dto.ApiResponse;
import com.dev.rate_limiter_service.dto.RateLimitMeta;
import com.dev.rate_limiter_service.service.BucketService;
import com.dev.rate_limiter_service.service.MetricsService;
import com.dev.rate_limiter_service.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoints — your DEMO SHOWPIECE for interviews.
 *
 * Show interviewers:
 * - Live bucket states per user
 * - Request metrics (total, blocked, block rate)
 * - Ability to reset a user's bucket
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final BucketService bucketService;
    private final MetricsService metricsService;
    private final OrderService orderService;

    public AdminController(BucketService bucketService,
                           MetricsService metricsService,
                           OrderService orderService) {
        this.bucketService = bucketService;
        this.metricsService = metricsService;
        this.orderService = orderService;
    }

    /**
     * GET /api/admin/metrics
     * Shows total requests, blocked count, block rate per endpoint.
     */
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics() {
        Map<String, Object> metrics = metricsService.getSummary();
        metrics.put("totalOrdersPlaced", orderService.getTotalOrdersPlaced());
        metrics.put("activeUsers", bucketService.getActiveUserCount());

        RateLimitMeta meta = RateLimitMeta.of(-1, "admin");
        return ResponseEntity.ok(ApiResponse.ok(metrics, meta));
    }

    /**
     * GET /api/admin/bucket-status/{userId}
     * Shows remaining tokens for all buckets of a specific user.
     */
    @GetMapping("/bucket-status/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBucketStatus(
            @PathVariable String userId) {

        Map<String, Object> status = Map.of(
                "userId", userId,
                "orderBucketRemaining",   bucketService.getUserOrderBucket(userId).getAvailableTokens(),
                "loginBucketRemaining",   bucketService.getUserLoginBucket(userId).getAvailableTokens(),
                "productBucketRemaining", bucketService.getUserProductBucket(userId).getAvailableTokens(),
                "globalOrderRemaining",   bucketService.getGlobalOrderBucket().getAvailableTokens(),
                "forgotPasswordRemaining",bucketService.getForgotPasswordBucket().getAvailableTokens()
        );

        RateLimitMeta meta = RateLimitMeta.of(-1, "admin");
        return ResponseEntity.ok(ApiResponse.ok(status, meta));
    }

    /**
     * DELETE /api/admin/reset/{userId}
     * Resets all buckets for a user (for testing/demo).
     */
    @DeleteMapping("/reset/{userId}")
    public ResponseEntity<ApiResponse<String>> resetUser(@PathVariable String userId) {
        bucketService.resetUserBuckets(userId);
        RateLimitMeta meta = RateLimitMeta.of(-1, "admin");
        return ResponseEntity.ok(ApiResponse.ok(
                "Buckets reset for user: " + userId, meta));
    }

    /**
     * DELETE /api/admin/metrics/reset
     * Resets all metrics counters.
     */
    @DeleteMapping("/metrics/reset")
    public ResponseEntity<ApiResponse<String>> resetMetrics() {
        metricsService.reset();
        RateLimitMeta meta = RateLimitMeta.of(-1, "admin");
        return ResponseEntity.ok(ApiResponse.ok("Metrics reset successfully.", meta));
    }
}