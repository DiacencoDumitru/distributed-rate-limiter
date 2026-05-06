package com.example.ratelimiter.service;

import com.example.ratelimiter.api.AdminRateLimitStateRequest;
import com.example.ratelimiter.api.RateLimitRequest;
import com.example.ratelimiter.api.FixedWindowRateLimitRequest;
import com.example.ratelimiter.api.TokenBucketRateLimitRequest;
import com.example.ratelimiter.redis.RedisKeyFactory;
import com.example.ratelimiter.redis.RedisRateLimiterClient;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {
    private final RedisRateLimiterClient redisRateLimiterClient;
    private final RedisKeyFactory redisKeyFactory;

    public RateLimiterService(RedisRateLimiterClient redisRateLimiterClient, RedisKeyFactory redisKeyFactory) {
        this.redisRateLimiterClient = redisRateLimiterClient;
        this.redisKeyFactory = redisKeyFactory;
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

    public RateLimitStateResult getState(AdminRateLimitStateRequest request) {
        String redisKey = redisKeyFactory.build(request.strategy(), request.key());
        long ttlSeconds = redisRateLimiterClient.getTtlSeconds(redisKey);
        return switch (request.strategy()) {
            case FIXED_WINDOW -> {
                String currentCountValue = redisRateLimiterClient.getFixedWindowCurrentCount(request.key());
                if (currentCountValue == null) {
                    yield new RateLimitStateResult(false, ttlSeconds, null, null, null);
                }
                yield new RateLimitStateResult(true, ttlSeconds, Long.parseLong(currentCountValue), null, null);
            }
            case TOKEN_BUCKET -> {
                var tokenBucketData = redisRateLimiterClient.getTokenBucketData(request.key());
                String tokensValue = tokenBucketData.get(0);
                String lastRefillTimestampValue = tokenBucketData.get(1);
                if (tokensValue == null || lastRefillTimestampValue == null) {
                    yield new RateLimitStateResult(false, ttlSeconds, null, null, null);
                }
                yield new RateLimitStateResult(
                        true,
                        ttlSeconds,
                        null,
                        Double.parseDouble(tokensValue),
                        Double.parseDouble(lastRefillTimestampValue)
                );
            }
        };
    }
}
