package com.dev.rate_limiter_service.filter;

import com.dev.rate_limiter_service.logging.RateLimitLogger;
import com.dev.rate_limiter_service.service.BucketService;
import com.dev.rate_limiter_service.service.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * THE core of the project.
 *
 * Intercepts every request BEFORE it reaches any controller.
 * Applies the correct rate limit bucket based on the endpoint.
 * Controllers have zero knowledge that rate limiting exists.
 *
 * Header used for user identity: X-User-Id
 * (In a real system this would be extracted from a JWT token)
 *
 * Flow:
 *   1. Extract userId from X-User-Id header
 *   2. Pick the right bucket(s) for this endpoint
 *   3. tryConsume(1) — atomic, thread-safe
 *   4a. Allowed  → add rate limit headers, pass to controller
 *   4b. Blocked  → write 429 JSON directly, stop the chain
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final BucketService bucketService;
    private final MetricsService metricsService;
    private final RateLimitLogger rateLimitLogger;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(BucketService bucketService,
                           MetricsService metricsService,
                           RateLimitLogger rateLimitLogger,
                           ObjectMapper objectMapper) {
        this.bucketService = bucketService;
        this.metricsService = metricsService;
        this.rateLimitLogger = rateLimitLogger;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String userId = extractUserId(request);

        metricsService.recordRequest(path);

        // ── Route to correct rate limiting strategy ───────────────────────────

        RateLimitResult result = applyRateLimit(path, method, userId);

        // ── Add standard rate limit headers to EVERY response ─────────────────
        // (even 429s — so clients know when to retry)
        addRateLimitHeaders(response, result);

        if (result.isAllowed()) {
            rateLimitLogger.logAllowed(userId, path, result.remainingTokens());
            filterChain.doFilter(request, response);
        } else {
            metricsService.recordBlocked(path);
            rateLimitLogger.logBlocked(userId, path, result.retryAfterSeconds());
            write429Response(response, result);
        }
    }

    // ── Rate limit strategy per endpoint ──────────────────────────────────────

    private RateLimitResult applyRateLimit(String path, String method, String userId) {

        if (path.startsWith("/api/orders")) {
            // Dual check: global system capacity + per-user limit
            return dualCheck(
                    bucketService.getGlobalOrderBucket(),
                    bucketService.getUserOrderBucket(userId),
                    "global_order", "user_order", userId);
        }

        if (path.startsWith("/api/auth/forgot-password")) {
            // Single shared bucket — no per-user tracking for forgot-password
            return singleCheck(bucketService.getForgotPasswordBucket(), "forgot_password");
        }

        if (path.startsWith("/api/auth/login")) {
            return singleCheck(bucketService.getUserLoginBucket(userId), "login");
        }

        if (path.startsWith("/api/products")) {
            return singleCheck(bucketService.getUserProductBucket(userId), "product");
        }

        // Admin and health endpoints — no rate limiting
        return RateLimitResult.allowed(Long.MAX_VALUE, "none");
    }

    // ── Bucket check helpers ──────────────────────────────────────────────────

    /**
     * Single bucket check — one probe, one decision.
     */
    private RateLimitResult singleCheck(Bucket bucket, String limitType) {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitResult.allowed(probe.getRemainingTokens(), limitType);
        }
        long retryAfter = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        return RateLimitResult.blocked(retryAfter, limitType);
    }

    /**
     * Dual bucket check for orders:
     * Global must pass first, then per-user.
     * If global fails → system overloaded message.
     * If per-user fails → user spam message.
     */
    private RateLimitResult dualCheck(Bucket globalBucket, Bucket userBucket,
                                      String globalType, String userType, String userId) {
        ConsumptionProbe globalProbe = globalBucket.tryConsumeAndReturnRemaining(1);
        if (!globalProbe.isConsumed()) {
            long retryAfter = TimeUnit.NANOSECONDS.toSeconds(globalProbe.getNanosToWaitForRefill());
            return RateLimitResult.blocked(retryAfter, globalType)
                    .withReason("System is at capacity. Please try again shortly.");
        }

        ConsumptionProbe userProbe = userBucket.tryConsumeAndReturnRemaining(1);
        if (!userProbe.isConsumed()) {
            // Global token was consumed — give it back to be fair
            // (Bucket4j doesn't support rollback, so we note this as a known trade-off)
            long retryAfter = TimeUnit.NANOSECONDS.toSeconds(userProbe.getNanosToWaitForRefill());
            return RateLimitResult.blocked(retryAfter, userType)
                    .withReason("You are placing orders too quickly. Slow down.");
        }

        return RateLimitResult.allowed(userProbe.getRemainingTokens(), userType);
    }

    // ── Response writing ──────────────────────────────────────────────────────

    private void addRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
        response.setHeader("X-RateLimit-LimitType", result.limitType());
        if (!result.isAllowed()) {
            response.setHeader("X-Retry-After", String.valueOf(result.retryAfterSeconds()));
        }
    }

    private void write429Response(HttpServletResponse response, RateLimitResult result)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("status", 429);
        body.put("error", "TOO_MANY_REQUESTS");
        body.put("message", result.reason());
        body.put("retryAfterSeconds", result.retryAfterSeconds());
        body.put("limitType", result.limitType());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Extract user identity from header.
     * Falls back to IP address if header is missing (for unauthenticated endpoints).
     */
    private String extractUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        // Fallback: use IP address (handles proxies)
        String ip = request.getHeader("X-Forwarded-For");
        return (ip != null && !ip.isBlank()) ? ip.split(",")[0].trim() : request.getRemoteAddr();
    }

    /**
     * Skip rate limiting entirely for actuator/health endpoints.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }

    // ── Inner result record ───────────────────────────────────────────────────

    /**
     * Immutable result object carrying the outcome of a rate limit check.
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final long remainingTokens;
        private final long retryAfterSeconds;
        private final String limitType;
        private String reason;

        private RateLimitResult(boolean allowed, long remainingTokens,
                                long retryAfterSeconds, String limitType) {
            this.allowed = allowed;
            this.remainingTokens = remainingTokens;
            this.retryAfterSeconds = retryAfterSeconds;
            this.limitType = limitType;
            this.reason = allowed
                    ? "Request allowed"
                    : "Rate limit exceeded. Please wait before retrying.";
        }

        public static RateLimitResult allowed(long remaining, String limitType) {
            return new RateLimitResult(true, remaining, 0, limitType);
        }

        public static RateLimitResult blocked(long retryAfter, String limitType) {
            return new RateLimitResult(false, 0, retryAfter, limitType);
        }

        public RateLimitResult withReason(String reason) {
            this.reason = reason;
            return this;
        }

        public boolean isAllowed() { return allowed; }
        public long remainingTokens() { return remainingTokens; }
        public long retryAfterSeconds() { return retryAfterSeconds; }
        public String limitType() { return limitType; }
        public String reason() { return reason; }
    }
}