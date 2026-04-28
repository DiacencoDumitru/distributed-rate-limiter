package com.example.ratelimiter.redis;

import com.example.ratelimiter.api.RateLimitStrategy;
import com.example.ratelimiter.service.RateLimitResult;

public interface RedisRateLimiterClient {
    RateLimitResult evaluate(RateLimitStrategy strategy, String key, long limit, long windowSeconds);
}
