package com.example.ratelimiter.service;

import com.example.ratelimiter.api.RateLimitRequest;
import com.example.ratelimiter.api.FixedWindowRateLimitRequest;
import com.example.ratelimiter.api.TokenBucketRateLimitRequest;
import com.example.ratelimiter.redis.RedisRateLimiterClient;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {
    private final RedisRateLimiterClient redisRateLimiterClient;

    public RateLimiterService(RedisRateLimiterClient redisRateLimiterClient) {
        this.redisRateLimiterClient = redisRateLimiterClient;
    }

    public RateLimitResult check(RateLimitRequest request) {
        return switch (request) {
            case FixedWindowRateLimitRequest fixedWindowRequest -> redisRateLimiterClient.evaluateFixedWindow(
                    fixedWindowRequest.key(),
                    fixedWindowRequest.limit(),
                    fixedWindowRequest.windowSeconds()
            );
            case TokenBucketRateLimitRequest tokenBucketRequest -> redisRateLimiterClient.evaluateTokenBucket(
                    tokenBucketRequest.key(),
                    tokenBucketRequest.capacity(),
                    tokenBucketRequest.refillSeconds()
            );
        };
    }
}
