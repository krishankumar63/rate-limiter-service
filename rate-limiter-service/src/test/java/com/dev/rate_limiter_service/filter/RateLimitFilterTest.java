package com.dev.rate_limiter_service.filter;

import com.dev.rate_limiter_service.config.RateLimitProperties;
import com.dev.rate_limiter_service.logging.RateLimitLogger;
import com.dev.rate_limiter_service.service.BucketService;
import com.dev.rate_limiter_service.service.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration-style filter tests using full Spring Boot context + MockMvc.
 * These tests prove the filter intercepts correctly before controllers.
 */
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BucketService bucketService;

    @Autowired
    private MetricsService metricsService;

    @BeforeEach
    void resetState() {
        // Reset buckets and metrics before each test for isolation
        bucketService.resetUserBuckets("filterTestUser");
        bucketService.resetUserBuckets("userA");
        bucketService.resetUserBuckets("userB");
        metricsService.reset();
    }

    // ── Test 10 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should allow first request and return 200 when under rate limit")
    void shouldAllowRequestWhenUnderLimit() throws Exception {
        mockMvc.perform(post("/api/orders/place")
                        .header("X-User-Id", "filterTestUser")
                        .param("restaurantId", "R001")
                        .param("items", "Pizza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── Test 11 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return 429 after user exceeds 5 orders per minute limit")
    void shouldReturn429WhenUserLimitExceeded() throws Exception {
        // Send 5 allowed requests
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/orders/place")
                            .header("X-User-Id", "filterTestUser")
                            .param("restaurantId", "R001")
                            .param("items", "Pizza"))
                    .andExpect(status().isOk());
        }

        // 6th request must be blocked
        mockMvc.perform(post("/api/orders/place")
                        .header("X-User-Id", "filterTestUser")
                        .param("restaurantId", "R001")
                        .param("items", "Pizza"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
    }

    // ── Test 12 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should include X-Retry-After header in 429 response")
    void shouldReturn429WithRetryAfterHeader() throws Exception {
        // Exhaust the bucket
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/orders/place")
                            .header("X-User-Id", "filterTestUser")
                            .param("restaurantId", "R001")
                            .param("items", "Pizza"))
                    .andExpect(status().isOk());
        }

        // Verify retry header is present on blocked response
        mockMvc.perform(post("/api/orders/place")
                        .header("X-User-Id", "filterTestUser")
                        .param("restaurantId", "R001")
                        .param("items", "Pizza"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("X-Retry-After"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    // ── Test 13 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should add X-RateLimit-Remaining header on every successful response")
    void shouldAddRateLimitRemainingHeaderOnSuccess() throws Exception {
        mockMvc.perform(post("/api/orders/place")
                        .header("X-User-Id", "filterTestUser")
                        .param("restaurantId", "R001")
                        .param("items", "Pizza"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().string("X-RateLimit-Remaining", not(emptyString())));
    }

    // ── Test 14 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return 400 when X-User-Id header is missing on order endpoint")
    void shouldReturn400WhenUserIdHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/orders/place")
                        // No X-User-Id header
                        .param("restaurantId", "R001")
                        .param("items", "Pizza"))
                .andExpect(status().isBadRequest());
    }

    // ── Test 15 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Different users should be rate limited independently")
    void differentUsersShouldBeRateLimitedIndependently() throws Exception {
        // Exhaust userA's bucket completely
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/orders/place")
                            .header("X-User-Id", "userA")
                            .param("restaurantId", "R001")
                            .param("items", "Pizza"))
                    .andExpect(status().isOk());
        }

        // userA is now blocked
        mockMvc.perform(post("/api/orders/place")
                        .header("X-User-Id", "userA")
                        .param("restaurantId", "R001")
                        .param("items", "Pizza"))
                .andExpect(status().isTooManyRequests());

        // userB should still get 200 — completely independent bucket
        mockMvc.perform(post("/api/orders/place")
                        .header("X-User-Id", "userB")
                        .param("restaurantId", "R001")
                        .param("items", "Pizza"))
                .andExpect(status().isOk());
    }

    // ── Test 16 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Admin endpoints should never be rate limited")
    void shouldNotRateLimitAdminEndpoints() throws Exception {
        // Hit admin endpoint many times — should always be 200
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/admin/metrics"))
                    .andExpect(status().isOk());
        }
    }
}