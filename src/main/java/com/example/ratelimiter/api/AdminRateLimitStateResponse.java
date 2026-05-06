package com.example.ratelimiter.api;

public record AdminRateLimitStateResponse(
        String key,
        RateLimitStrategy strategy,
        boolean exists,
        long ttlSeconds,
        Long currentCount,
        Double tokens,
        Double lastRefillTimestampSeconds
) {
}
