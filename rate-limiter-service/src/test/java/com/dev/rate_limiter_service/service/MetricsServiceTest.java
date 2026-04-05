package com.dev.rate_limiter_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MetricsService.
 * No Spring context — pure Java.
 */
class MetricsServiceTest {

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new MetricsService();
    }

    // ── Test 7 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should correctly track total requests per endpoint")
    void shouldTrackTotalRequests() {
        metricsService.recordRequest("/api/orders/place");
        metricsService.recordRequest("/api/orders/place");
        metricsService.recordRequest("/api/products");

        Map<String, Object> summary = metricsService.getSummary();

        assertThat(summary.get("totalRequests")).isEqualTo(3L);
    }

    // ── Test 8 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should track blocked requests separately from total requests")
    void shouldTrackBlockedRequests() {
        metricsService.recordRequest("/api/orders/place");
        metricsService.recordRequest("/api/orders/place");
        metricsService.recordBlocked("/api/orders/place");

        Map<String, Object> summary = metricsService.getSummary();

        assertThat(summary.get("totalRequests")).isEqualTo(2L);
        assertThat(summary.get("totalBlocked")).isEqualTo(1L);
        assertThat(summary.get("totalAllowed")).isEqualTo(1L);
    }

    // ── Test 9 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should reset all metrics counters to zero")
    void shouldResetMetricsSuccessfully() {
        metricsService.recordRequest("/api/orders/place");
        metricsService.recordBlocked("/api/orders/place");

        metricsService.reset();

        Map<String, Object> summary = metricsService.getSummary();

        assertThat(summary.get("totalRequests")).isEqualTo(0L);
        assertThat(summary.get("totalBlocked")).isEqualTo(0L);
        assertThat(summary.get("totalAllowed")).isEqualTo(0L);
        assertThat(summary.get("blockRate")).isEqualTo("0%");
    }
}