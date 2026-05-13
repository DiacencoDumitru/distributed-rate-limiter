package com.example.ratelimiter.api;

public record AdminRateLimitResetResponse(
        String key,
        RateLimitStrategy strategy,
        boolean deleted
) {
}
