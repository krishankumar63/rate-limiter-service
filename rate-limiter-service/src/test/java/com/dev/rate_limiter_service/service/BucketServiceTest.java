package com.dev.rate_limiter_service.service;

import com.dev.rate_limiter_service.config.RateLimitProperties;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BucketService.
 * No Spring context — pure Java, runs in milliseconds.
 */
class BucketServiceTest {

    private BucketService bucketService;

    @BeforeEach
    void setUp() {
        // Build real props with small limits for fast testing
        RateLimitProperties props = new RateLimitProperties();
        props.getOrder().setPerUserCapacity(5);
        props.getOrder().setRefillDurationSeconds(60);
        props.getAuth().setLoginCapacity(10);
        props.getAuth().setRefillDurationSeconds(60);
        props.getProduct().setCapacity(100);
        props.getProduct().setRefillDurationSeconds(60);

        // Mock the two global buckets (injected via @Qualifier)
        Bucket mockGlobalOrder     = mock(Bucket.class);
        Bucket mockForgotPassword  = mock(Bucket.class);

        bucketService = new BucketService(mockGlobalOrder, mockForgotPassword, props);
    }

    // ── Test 1 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should create new bucket with full tokens for a new user")
    void shouldCreateNewBucketForNewUser() {
        Bucket bucket = bucketService.getUserOrderBucket("newUser");

        assertThat(bucket).isNotNull();
        assertThat(bucket.getAvailableTokens()).isEqualTo(5);
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return the same bucket instance for the same userId")
    void shouldReturnSameBucketForSameUser() {
        Bucket first  = bucketService.getUserOrderBucket("user123");
        Bucket second = bucketService.getUserOrderBucket("user123");

        // Must be the exact same object — not two separate buckets
        assertThat(first).isSameAs(second);
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Different users should have completely independent buckets")
    void differentUsersShouldHaveIndependentBuckets() {
        Bucket user1Bucket = bucketService.getUserOrderBucket("user1");
        Bucket user2Bucket = bucketService.getUserOrderBucket("user2");

        // Exhaust user1's bucket entirely
        for (int i = 0; i < 5; i++) {
            user1Bucket.tryConsume(1);
        }

        // user1 should be empty
        assertThat(user1Bucket.getAvailableTokens()).isEqualTo(0);

        // user2 should still be completely full — total isolation
        assertThat(user2Bucket.getAvailableTokens()).isEqualTo(5);
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should reset all buckets for a user successfully")
    void shouldResetUserBucketsSuccessfully() {
        // Exhaust all three bucket types for user123
        Bucket orderBucket   = bucketService.getUserOrderBucket("user123");
        Bucket loginBucket   = bucketService.getUserLoginBucket("user123");
        Bucket productBucket = bucketService.getUserProductBucket("user123");

        for (int i = 0; i < 5;   i++) orderBucket.tryConsume(1);
        for (int i = 0; i < 10;  i++) loginBucket.tryConsume(1);
        for (int i = 0; i < 100; i++) productBucket.tryConsume(1);

        assertThat(orderBucket.getAvailableTokens()).isEqualTo(0);

        // Reset
        bucketService.resetUserBuckets("user123");

        // After reset, a fresh bucket is created with full tokens
        Bucket freshBucket = bucketService.getUserOrderBucket("user123");
        assertThat(freshBucket.getAvailableTokens()).isEqualTo(5);
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should create separate independent buckets for order, login and product")
    void shouldCreateSeparateBucketsForOrderLoginAndProduct() {
        Bucket orderBucket   = bucketService.getUserOrderBucket("user123");
        Bucket loginBucket   = bucketService.getUserLoginBucket("user123");
        Bucket productBucket = bucketService.getUserProductBucket("user123");

        // All three must be different objects
        assertThat(orderBucket).isNotSameAs(loginBucket);
        assertThat(loginBucket).isNotSameAs(productBucket);
        assertThat(orderBucket).isNotSameAs(productBucket);

        // Each has its own capacity
        assertThat(orderBucket.getAvailableTokens()).isEqualTo(5);
        assertThat(loginBucket.getAvailableTokens()).isEqualTo(10);
        assertThat(productBucket.getAvailableTokens()).isEqualTo(100);
    }

    // ── Test 6 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Global order bucket should be the same instance every time")
    void globalOrderBucketShouldBeSharedAcrossAllUsers() {
        Bucket first  = bucketService.getGlobalOrderBucket();
        Bucket second = bucketService.getGlobalOrderBucket();

        // Same singleton bean — not a new bucket per call
        assertThat(first).isSameAs(second);
    }
}