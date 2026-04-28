package com.example.ratelimiter.api;

public record RateLimitResponse(
        boolean allowed,
        long remaining,
        long retryAfterSeconds
) {
}
