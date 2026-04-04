package com.dev.rate_limiter_service.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Structured request logging for rate limiting events.
 *
 * Log format is consistent so it can be parsed by tools like Splunk/ELK.
 * Every log line has: timestamp | userId | endpoint | outcome | detail
 */
@Component
public class RateLimitLogger {

    private static final Logger log = LoggerFactory.getLogger(RateLimitLogger.class);

    public void logAllowed(String userId, String endpoint, long remainingTokens) {
        log.info("[ALLOWED]  user={} endpoint={} remaining={} ts={}",
                userId, endpoint, remainingTokens, Instant.now());
    }

    public void logBlocked(String userId, String endpoint, long retryAfterSeconds) {
        log.warn("[BLOCKED]  user={} endpoint={} retryAfter={}s ts={}",
                userId, endpoint, retryAfterSeconds, Instant.now());
    }

    public void logSystemOverload(String endpoint) {
        log.error("[OVERLOAD] endpoint={} ts={} — global bucket exhausted",
                endpoint, Instant.now());
    }
}