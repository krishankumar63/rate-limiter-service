package com.dev.rate_limiter_service.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory metrics — demonstrates observability for the demo.
 *
 * Tracks:
 * - Total requests per endpoint
 * - Total blocked requests per endpoint
 * - Overall totals
 *
 * In production: replace with Micrometer + Prometheus.
 */
@Service
public class MetricsService {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalBlocked  = new AtomicLong(0);

    private final Map<String, AtomicLong> requestsPerEndpoint = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> blockedPerEndpoint  = new ConcurrentHashMap<>();

    public void recordRequest(String endpoint) {
        totalRequests.incrementAndGet();
        requestsPerEndpoint.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordBlocked(String endpoint) {
        totalBlocked.incrementAndGet();
        blockedPerEndpoint.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
    }

    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        summary.put("totalRequests", totalRequests.get());
        summary.put("totalBlocked", totalBlocked.get());
        summary.put("totalAllowed", totalRequests.get() - totalBlocked.get());
        summary.put("blockRate", totalRequests.get() == 0 ? "0%"
                : String.format("%.1f%%", (totalBlocked.get() * 100.0 / totalRequests.get())));
        summary.put("requestsPerEndpoint", requestsPerEndpoint);
        summary.put("blockedPerEndpoint", blockedPerEndpoint);
        return summary;
    }

    public void reset() {
        totalRequests.set(0);
        totalBlocked.set(0);
        requestsPerEndpoint.clear();
        blockedPerEndpoint.clear();
    }
}