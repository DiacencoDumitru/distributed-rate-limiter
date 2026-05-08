package com.example.ratelimiter.api;

public enum RateLimitStrategy {
    FIXED_WINDOW,
    TOKEN_BUCKET,
    SLIDING_WINDOW
}
