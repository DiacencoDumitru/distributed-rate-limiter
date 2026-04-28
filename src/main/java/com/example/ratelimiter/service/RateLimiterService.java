package com.example.ratelimiter.service;

import com.example.ratelimiter.api.RateLimitRequest;
import com.example.ratelimiter.redis.RedisRateLimiterClient;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {
    private final RedisRateLimiterClient redisRateLimiterClient;

    public RateLimiterService(RedisRateLimiterClient redisRateLimiterClient) {
        this.redisRateLimiterClient = redisRateLimiterClient;
    }

    public RateLimitResult check(RateLimitRequest request) {
        return redisRateLimiterClient.evaluate(
                request.strategy(),
                request.key(),
                request.limit(),
                request.windowSeconds()
        );
    }
}
