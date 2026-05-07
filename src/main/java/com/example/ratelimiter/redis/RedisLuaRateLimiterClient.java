package com.example.ratelimiter.redis;

import com.example.ratelimiter.api.RateLimitStrategy;
import com.example.ratelimiter.service.RateLimitResult;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisLuaRateLimiterClient implements RedisRateLimiterClient {
    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final DefaultRedisScript<List> fixedWindowScript;
    private final DefaultRedisScript<List> tokenBucketScript;
    private final DefaultRedisScript<List> fixedWindowStateScript;
    private final DefaultRedisScript<List> tokenBucketStateScript;

    public RedisLuaRateLimiterClient(StringRedisTemplate redisTemplate, RedisKeyFactory keyFactory, LuaScriptLoader scriptLoader) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.fixedWindowScript = new DefaultRedisScript<>();
        this.fixedWindowScript.setScriptText(scriptLoader.load("lua/fixed_window.lua"));
        this.fixedWindowScript.setResultType(List.class);
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptText(scriptLoader.load("lua/token_bucket.lua"));
        this.tokenBucketScript.setResultType(List.class);
        this.fixedWindowStateScript = new DefaultRedisScript<>();
        this.fixedWindowStateScript.setScriptText(scriptLoader.load("lua/fixed_window_state.lua"));
        this.fixedWindowStateScript.setResultType(List.class);
        this.tokenBucketStateScript = new DefaultRedisScript<>();
        this.tokenBucketStateScript.setScriptText(scriptLoader.load("lua/token_bucket_state.lua"));
        this.tokenBucketStateScript.setResultType(List.class);
    }

    @Override
    public RateLimitResult evaluateFixedWindow(String key, long limit, long windowSeconds) {
        String redisKey = keyFactory.build(RateLimitStrategy.FIXED_WINDOW, key);
        Object[] args = {String.valueOf(limit), String.valueOf(windowSeconds)};
        List result = redisTemplate.execute(fixedWindowScript, List.of(redisKey), args);
        return toResult(result);
    }

    @Override
    public RateLimitResult evaluateTokenBucket(String key, long capacity, long refillSeconds) {
        String redisKey = keyFactory.build(RateLimitStrategy.TOKEN_BUCKET, key);
        Object[] args = {String.valueOf(capacity), String.valueOf(refillSeconds)};
        List result = redisTemplate.execute(tokenBucketScript, List.of(redisKey), args);
        return toResult(result);
    }

    @Override
    public List<String> getFixedWindowState(String key) {
        String redisKey = keyFactory.build(RateLimitStrategy.FIXED_WINDOW, key);
        List result = redisTemplate.execute(fixedWindowStateScript, List.of(redisKey));
        return toStringList(result, 2);
    }

    @Override
    public List<String> getTokenBucketState(String key) {
        String redisKey = keyFactory.build(RateLimitStrategy.TOKEN_BUCKET, key);
        List result = redisTemplate.execute(tokenBucketStateScript, List.of(redisKey));
        return toStringList(result, 3);
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
