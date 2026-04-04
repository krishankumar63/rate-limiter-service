package com.dev.rate_limiter_service.dto;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic API wrapper — every endpoint returns this shape.
 *
 * Success: { success: true,  data: {...},  meta: {...} }
 * Error:   { success: false, error: "...", meta: {...} }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String error;
    private String message;
    private RateLimitMeta meta;

    // ── Static factory methods ────────────────────────────────────────────────

    public static <T> ApiResponse<T> ok(T data, RateLimitMeta meta) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        r.meta = meta;
        return r;
    }

    public static <T> ApiResponse<T> ok(T data, String message, RateLimitMeta meta) {
        ApiResponse<T> r = ok(data, meta);
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> error(String error, RateLimitMeta meta) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.error = error;
        r.meta = meta;
        return r;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public RateLimitMeta getMeta() { return meta; }
}