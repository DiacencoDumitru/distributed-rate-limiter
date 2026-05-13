package com.example.ratelimiter.redis;

import com.example.ratelimiter.api.RateLimitStrategy;
import org.springframework.stereotype.Component;

@Component
public class RedisKeyFactory {
    public String build(RateLimitStrategy strategy, String key, String scope) {
        String segment = scope == null || scope.isBlank() ? "default" : scope.trim();
        return "ratelimiter:" + strategy.name().toLowerCase() + ":" + segment + ":" + key;
    }
}
