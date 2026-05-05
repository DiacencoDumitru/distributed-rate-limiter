package com.example.ratelimiter.redis;

import com.example.ratelimiter.service.RateLimitResult;

public interface RedisRateLimiterClient {
    RateLimitResult evaluateFixedWindow(String key, long limit, long windowSeconds);

    RateLimitResult evaluateTokenBucket(String key, long capacity, long refillSeconds);
}
