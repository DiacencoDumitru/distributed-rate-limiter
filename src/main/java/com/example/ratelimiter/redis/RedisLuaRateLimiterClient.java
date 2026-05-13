package com.example.ratelimiter.redis;

import com.example.ratelimiter.api.RateLimitStrategy;
import com.example.ratelimiter.service.RateLimitResult;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class RedisLuaRateLimiterClient implements RedisRateLimiterClient {
    private final RedisKeyFactory keyFactory;
    private final RedisLuaScriptExecutor scriptExecutor;
    private final String fixedWindowScript;
    private final String tokenBucketScript;
    private final String slidingWindowScript;
    private final String fixedWindowStateScript;
    private final String tokenBucketStateScript;
    private final String slidingWindowStateScript;

    public RedisLuaRateLimiterClient(RedisKeyFactory keyFactory, LuaScriptLoader scriptLoader, RedisLuaScriptExecutor scriptExecutor) {
        this.keyFactory = keyFactory;
        this.scriptExecutor = scriptExecutor;
        this.fixedWindowScript = scriptLoader.load("lua/fixed_window.lua");
        this.tokenBucketScript = scriptLoader.load("lua/token_bucket.lua");
        this.slidingWindowScript = scriptLoader.load("lua/sliding_window.lua");
        this.fixedWindowStateScript = scriptLoader.load("lua/fixed_window_state.lua");
        this.tokenBucketStateScript = scriptLoader.load("lua/token_bucket_state.lua");
        this.slidingWindowStateScript = scriptLoader.load("lua/sliding_window_state.lua");
    }

    @Override
    public RateLimitResult evaluateFixedWindow(String key, long limit, long windowSeconds) {
        String redisKey = keyFactory.build(RateLimitStrategy.FIXED_WINDOW, key);
        List result = scriptExecutor.execute(
                "fixed_window",
                fixedWindowScript,
                List.of(redisKey),
                String.valueOf(limit),
                String.valueOf(windowSeconds));
        return toResult(result);
    }

    @Override
    public RateLimitResult evaluateTokenBucket(String key, long capacity, long refillSeconds) {
        String redisKey = keyFactory.build(RateLimitStrategy.TOKEN_BUCKET, key);
        List result = scriptExecutor.execute(
                "token_bucket",
                tokenBucketScript,
                List.of(redisKey),
                String.valueOf(capacity),
                String.valueOf(refillSeconds));
        return toResult(result);
    }

    @Override
    public RateLimitResult evaluateSlidingWindow(String key, long limit, long windowSeconds) {
        String redisKey = keyFactory.build(RateLimitStrategy.SLIDING_WINDOW, key);
        List result = scriptExecutor.execute(
                "sliding_window",
                slidingWindowScript,
                List.of(redisKey),
                String.valueOf(limit),
                String.valueOf(windowSeconds));
        return toResult(result);
    }

    @Override
    public List<String> getFixedWindowState(String key) {
        String redisKey = keyFactory.build(RateLimitStrategy.FIXED_WINDOW, key);
        List result = scriptExecutor.execute("fixed_window_state", fixedWindowStateScript, List.of(redisKey));
        return toStringList(result, 2);
    }

    @Override
    public List<String> getTokenBucketState(String key) {
        String redisKey = keyFactory.build(RateLimitStrategy.TOKEN_BUCKET, key);
        List result = scriptExecutor.execute("token_bucket_state", tokenBucketStateScript, List.of(redisKey));
        return toStringList(result, 3);
    }

    @Override
    public List<String> getSlidingWindowState(String key, long windowSeconds) {
        String redisKey = keyFactory.build(RateLimitStrategy.SLIDING_WINDOW, key);
        List result = scriptExecutor.execute(
                "sliding_window_state",
                slidingWindowStateScript,
                List.of(redisKey),
                String.valueOf(windowSeconds));
        return toStringList(result, 2);
    }

    @Override
    public boolean resetState(RateLimitStrategy strategy, String key) {
        String redisKey = keyFactory.build(strategy, key);
        List result = scriptExecutor.execute(
                "reset_state",
                "return {redis.call('DEL', KEYS[1])}",
                List.of(redisKey));
        return parseLong(result.getFirst()) > 0;
    }

    private RateLimitResult toResult(List result) {
        if (result == null || result.size() < 3) {
            throw new IllegalStateException("Unexpected Redis Lua result");
        }
        boolean allowed = parseLong(result.get(0)) == 1L;
        long remaining = parseLong(result.get(1));
        long retryAfterSeconds = parseLong(result.get(2));
        return new RateLimitResult(allowed, remaining, retryAfterSeconds);
    }

    private long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private List<String> toStringList(List result, int expectedSize) {
        if (result == null || result.size() < expectedSize) {
            throw new IllegalStateException("Unexpected Redis Lua state result");
        }
        return result.stream()
                .map(value -> Objects.toString(value, null))
                .toList();
    }
}
