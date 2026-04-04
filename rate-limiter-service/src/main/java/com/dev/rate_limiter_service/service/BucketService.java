package com.dev.rate_limiter_service.service;

import com.dev.rate_limiter_service.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single responsibility: resolve and manage Bucket4j buckets.
 *
 * - Global buckets injected as beans (order, forgotPassword)
 * - Per-user buckets created lazily and stored in ConcurrentHashMap
 *
 * BucketService does NOT make allow/deny decisions —
 * that is the filter's job. It only hands back buckets.
 */
@Service
public class BucketService {

    private final Bucket globalOrderBucket;
    private final Bucket forgotPasswordBucket;
    private final RateLimitProperties props;

    // userId → their personal bucket
    private final Map<String, Bucket> userOrderBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> userLoginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> userProductBuckets = new ConcurrentHashMap<>();

    public BucketService(
            @Qualifier("globalOrderBucket") Bucket globalOrderBucket,
            @Qualifier("forgotPasswordBucket") Bucket forgotPasswordBucket,
            RateLimitProperties props) {
        this.globalOrderBucket = globalOrderBucket;
        this.forgotPasswordBucket = forgotPasswordBucket;
        this.props = props;
    }

    // ── Global buckets ────────────────────────────────────────────────────────

    public Bucket getGlobalOrderBucket() {
        return globalOrderBucket;
    }

    public Bucket getForgotPasswordBucket() {
        return forgotPasswordBucket;
    }

    // ── Per-user buckets ──────────────────────────────────────────────────────

    /**
     * Returns (or lazily creates) the order bucket for this user.
     * 5 orders per minute, greedy refill.
     */
    public Bucket getUserOrderBucket(String userId) {
        return userOrderBuckets.computeIfAbsent(userId, id -> buildBucket(
                props.getOrder().getPerUserCapacity(),
                props.getOrder().getRefillDurationSeconds()));
    }

    /**
     * Returns (or lazily creates) the login bucket for this user.
     * 10 login attempts per minute.
     */
    public Bucket getUserLoginBucket(String userId) {
        return userLoginBuckets.computeIfAbsent(userId, id -> buildBucket(
                props.getAuth().getLoginCapacity(),
                props.getAuth().getRefillDurationSeconds()));
    }

    /**
     * Returns (or lazily creates) the product browse bucket for this user.
     * 100 requests per minute.
     */
    public Bucket getUserProductBucket(String userId) {
        return userProductBuckets.computeIfAbsent(userId, id -> buildBucket(
                props.getProduct().getCapacity(),
                props.getProduct().getRefillDurationSeconds()));
    }

    // ── Admin / monitoring ────────────────────────────────────────────────────

    public Map<String, Bucket> getAllUserOrderBuckets() {
        return Map.copyOf(userOrderBuckets);
    }

    public void resetUserBuckets(String userId) {
        userOrderBuckets.remove(userId);
        userLoginBuckets.remove(userId);
        userProductBuckets.remove(userId);
    }

    public int getActiveUserCount() {
        return userOrderBuckets.size();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Bucket buildBucket(long capacity, long refillSeconds) {
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.greedy(capacity, Duration.ofSeconds(refillSeconds)));
        return Bucket.builder().addLimit(limit).build();
    }
}