package com.example.ratelimiter.redis;

import com.example.ratelimiter.service.RateLimitResult;
import java.util.List;

public interface RedisRateLimiterClient {
    RateLimitResult evaluateFixedWindow(String key, long limit, long windowSeconds);

    RateLimitResult evaluateTokenBucket(String key, long capacity, long refillSeconds);

    RateLimitResult evaluateSlidingWindow(String key, long limit, long windowSeconds);

    List<String> getFixedWindowState(String key);

    List<String> getTokenBucketState(String key);

    List<String> getSlidingWindowState(String key, long windowSeconds);
}
