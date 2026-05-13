package com.example.ratelimiter.redis;

import com.example.ratelimiter.service.RateLimitResult;
import java.util.List;
import com.example.ratelimiter.api.RateLimitStrategy;

public interface RedisRateLimiterClient {
    RateLimitResult evaluateFixedWindow(String key, long limit, long windowSeconds, String scope);

    RateLimitResult evaluateTokenBucket(String key, long capacity, long refillSeconds, String scope);

    RateLimitResult evaluateSlidingWindow(String key, long limit, long windowSeconds, String scope);

    List<String> getFixedWindowState(String key, String scope);

    List<String> getTokenBucketState(String key, String scope);

    List<String> getSlidingWindowState(String key, long windowSeconds, String scope);

    boolean resetState(RateLimitStrategy strategy, String key, String scope);
}
