package com.dev.rate_limiter_service.controller;

import com.dev.rate_limiter_service.dto.ApiResponse;
import com.dev.rate_limiter_service.dto.RateLimitMeta;
import com.dev.rate_limiter_service.service.BucketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final BucketService bucketService;

    public AuthController(BucketService bucketService) {
        this.bucketService = bucketService;
    }

    /**
     * POST /api/auth/login
     * Simulates login — rate limited to 10 attempts/min per user.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, String>>> login(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String password) {

        // Simulate auth (replace with real auth logic later)
        boolean valid = !password.isBlank();

        RateLimitMeta meta = RateLimitMeta.of(
                bucketService.getUserLoginBucket(userId).getAvailableTokens(),
                "login");

        if (valid) {
            Map<String, String> data = Map.of(
                    "userId", userId,
                    "token", "mock-jwt-token-" + userId,
                    "message", "Login successful"
            );
            return ResponseEntity.ok(ApiResponse.ok(data, meta));
        }

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid credentials", meta));
    }

    /**
     * POST /api/auth/forgot-password
     * Shared bucket — 3 requests per minute across ALL users.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @RequestParam String email) {

        RateLimitMeta meta = RateLimitMeta.of(
                bucketService.getForgotPasswordBucket().getAvailableTokens(),
                "forgot_password");

        Map<String, String> data = Map.of(
                "email", email,
                "message", "Password reset link sent to " + email
        );

        return ResponseEntity.ok(ApiResponse.ok(data, "Check your inbox.", meta));
    }
}