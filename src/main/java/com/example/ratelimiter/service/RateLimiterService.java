package com.example.ratelimiter.service;

import com.example.ratelimiter.api.AdminRateLimitStateRequest;
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

    public RateLimitStateResult getState(AdminRateLimitStateRequest request) {
        return switch (request.strategy()) {
            case FIXED_WINDOW -> {
                var fixedWindowState = redisRateLimiterClient.getFixedWindowState(request.key());
                String currentCountValue = fixedWindowState.get(0);
                long ttlSeconds = Long.parseLong(fixedWindowState.get(1));
                if (isMissingState(currentCountValue)) {
                    yield new RateLimitStateResult(false, 0, null, null, null);
                }
                yield new RateLimitStateResult(true, ttlSeconds, Long.parseLong(currentCountValue), null, null);
            }
            case TOKEN_BUCKET -> {
                var tokenBucketState = redisRateLimiterClient.getTokenBucketState(request.key());
                String tokensValue = tokenBucketState.get(0);
                String lastRefillTimestampValue = tokenBucketState.get(1);
                long ttlSeconds = Long.parseLong(tokenBucketState.get(2));
                if (isMissingState(tokensValue) || isMissingState(lastRefillTimestampValue)) {
                    yield new RateLimitStateResult(false, 0, null, null, null);
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

    private boolean isMissingState(String value) {
        return value == null || "-1".equals(value);
    }
}
