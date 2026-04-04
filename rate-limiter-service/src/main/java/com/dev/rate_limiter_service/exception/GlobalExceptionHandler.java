package com.dev.rate_limiter_service.exception;

import com.dev.rate_limiter_service.dto.ApiResponse;
import com.dev.rate_limiter_service.dto.RateLimitMeta;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Catches all unhandled exceptions and returns consistent JSON.
 * Without this, Spring returns HTML error pages — very unprofessional.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        String msg = "Required header '" + ex.getHeaderName() + "' is missing. "
                + "Add 'X-User-Id: yourUserId' to your request headers.";
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(msg, RateLimitMeta.of(-1, "none")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Missing parameter: " + ex.getParameterName(),
                        RateLimitMeta.of(-1, "none")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal error: " + ex.getMessage(),
                        RateLimitMeta.of(-1, "none")));
    }
}