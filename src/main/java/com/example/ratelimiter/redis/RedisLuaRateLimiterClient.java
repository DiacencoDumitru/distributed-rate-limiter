package com.example.ratelimiter.redis;

import com.example.ratelimiter.api.RateLimitStrategy;
import com.example.ratelimiter.service.RateLimitResult;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisLuaRateLimiterClient implements RedisRateLimiterClient {
    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final DefaultRedisScript<List> fixedWindowScript;
    private final DefaultRedisScript<List> tokenBucketScript;

    public RedisLuaRateLimiterClient(StringRedisTemplate redisTemplate, RedisKeyFactory keyFactory, LuaScriptLoader scriptLoader) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.fixedWindowScript = new DefaultRedisScript<>();
        this.fixedWindowScript.setScriptText(scriptLoader.load("lua/fixed_window.lua"));
        this.fixedWindowScript.setResultType(List.class);
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptText(scriptLoader.load("lua/token_bucket.lua"));
        this.tokenBucketScript.setResultType(List.class);
    }

    @Override
    public RateLimitResult evaluate(RateLimitStrategy strategy, String key, long limit, long windowSeconds) {
        String redisKey = keyFactory.build(strategy, key);
        Object[] args = {String.valueOf(limit), String.valueOf(windowSeconds)};
        List result = switch (strategy) {
            case FIXED_WINDOW -> redisTemplate.execute(fixedWindowScript, List.of(redisKey), args);
            case TOKEN_BUCKET -> redisTemplate.execute(tokenBucketScript, List.of(redisKey), args);
        };
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
}
