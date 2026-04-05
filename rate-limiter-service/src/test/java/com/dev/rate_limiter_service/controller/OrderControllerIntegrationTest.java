package com.dev.rate_limiter_service.controller;

import com.dev.rate_limiter_service.service.BucketService;
import com.dev.rate_limiter_service.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full end-to-end integration tests.
 * Real HTTP → Filter → Controller → Service → Response.
 * Nothing is mocked — this is the whole stack.
 */
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BucketService bucketService;

    @Autowired
    private MetricsService metricsService;

    @BeforeEach
    void resetState() {
        bucketService.resetUserBuckets("integrationUser");
        bucketService.resetUserBuckets("user1");
        bucketService.resetUserBuckets("user2");
        metricsService.reset();
    }

    // ── Test 17 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should place order successfully and return correct response shape")
    void shouldPlaceOrderSuccessfully() throws Exception {
        mockMvc.perform(post("/api/orders/place")
                        .header("X-User-Id", "integrationUser")
                        .param("restaurantId", "R001")
                        .param("restaurantName", "Burger King")
                        .param("items", "Pizza, Coke")
                        .param("amount", "428.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order placed successfully!"))
                .andExpect(jsonPath("$.data.orderId").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").value("integrationUser"))
                .andExpect(jsonPath("$.data.restaurantId").value("R001"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.meta.remainingTokens").isNumber())
                .andExpect(jsonPath("$.meta.limitType").value("user_order"));
    }

    // ── Test 18 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return 429 after 5 orders from same user through full stack")
    void shouldReturn429AfterFiveOrdersFromSameUser() throws Exception {
        // Place 5 valid orders — all should succeed
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/orders/place")
                            .header("X-User-Id", "integrationUser")
                            .param("restaurantId", "R001")
                            .param("items", "Pizza"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        // 6th order — rate limit kicks in at filter level
        mockMvc.perform(post("/api/orders/place")
                        .header("X-User-Id", "integrationUser")
                        .param("restaurantId", "R001")
                        .param("items", "Pizza"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andExpect(jsonPath("$.limitType").value("user_order"));
    }

    // ── Test 19 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should allow different users to place orders independently after one is blocked")
    void shouldAllowDifferentUsersToPlaceOrdersIndependently() throws Exception {
        // Exhaust user1 completely
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/orders/place")
                            .header("X-User-Id", "user1")
                            .param("restaurantId", "R001")
                            .param("items", "Biryani"))
                    .andExpect(status().isOk());
        }

        // user1 is blocked
        mockMvc.perform(post("/api/orders/place")
                        .header("X-User-Id", "user1")
                        .param("restaurantId", "R001")
                        .param("items", "Biryani"))
                .andExpect(status().isTooManyRequests());

        // user2 is completely unaffected — independent bucket
        mockMvc.perform(post("/api/orders/place")
                        .header("X-User-Id", "user2")
                        .param("restaurantId", "R001")
                        .param("items", "Biryani"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("user2"));
    }
}