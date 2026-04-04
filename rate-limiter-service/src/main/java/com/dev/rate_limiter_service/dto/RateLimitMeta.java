package com.dev.rate_limiter_service.dto;

/**
 * Appended to every API response so clients can see their quota.
 * This is what Stripe, GitHub, and other production APIs return.
 */
public class RateLimitMeta {

    private long remainingTokens;
    private String limitType;
    private long timestamp;

    public static RateLimitMeta of(long remainingTokens, String limitType) {
        RateLimitMeta m = new RateLimitMeta();
        m.remainingTokens = remainingTokens;
        m.limitType = limitType;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public long getRemainingTokens() { return remainingTokens; }
    public String getLimitType() { return limitType; }
    public long getTimestamp() { return timestamp; }
}