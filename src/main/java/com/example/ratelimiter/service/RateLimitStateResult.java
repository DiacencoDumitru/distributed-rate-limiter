package com.example.ratelimiter.service;

public record RateLimitStateResult(
        boolean exists,
        long ttlSeconds,
        Long currentCount,
        Double tokens,
        Double lastRefillTimestampSeconds
) {
}
