package com.dev.rate_limiter_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Creates shared (global) buckets only.
 * Per-user buckets are created dynamically in BucketService.
 */
@Configuration
public class RateLimiterConfig {

    private final RateLimitProperties props;

    public RateLimiterConfig(RateLimitProperties props) {
        this.props = props;
    }

    /**
     * Global food order bucket — shared across ALL users.
     * Greedy refill = smooth token recovery (1 token every ~6ms for 10k/min).
     */
    @Bean("globalOrderBucket")
    public Bucket globalOrderBucket() {
        long capacity = props.getOrder().getGlobalCapacity();
        long seconds = props.getOrder().getRefillDurationSeconds();
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.greedy(capacity, Duration.ofSeconds(seconds)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Forgot password bucket — shared (not per-user) for security.
     * Stricter limit, greedy refill.
     */
    @Bean("forgotPasswordBucket")
    public Bucket forgotPasswordBucket() {
        long capacity = props.getAuth().getForgotPasswordCapacity();
        long seconds = props.getAuth().getRefillDurationSeconds();
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.greedy(capacity, Duration.ofSeconds(seconds)));
        return Bucket.builder().addLimit(limit).build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}