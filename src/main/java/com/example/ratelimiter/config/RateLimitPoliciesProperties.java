package com.example.ratelimiter.config;

import com.example.ratelimiter.api.RateLimitStrategy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitPoliciesProperties {

    private Map<String, PolicyDefinition> policies = new LinkedHashMap<>();

    public Map<String, PolicyDefinition> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, PolicyDefinition> policies) {
        this.policies = policies != null ? policies : new LinkedHashMap<>();
    }

    public static class PolicyDefinition {

        private RateLimitStrategy strategy;
        private Long limit;
        private Long windowSeconds;
        private Long capacity;
        private Long refillSeconds;

        public RateLimitStrategy getStrategy() {
            return strategy;
        }

        public void setStrategy(RateLimitStrategy strategy) {
            this.strategy = strategy;
        }

        public Long getLimit() {
            return limit;
        }

        public void setLimit(Long limit) {
            this.limit = limit;
        }

        public Long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(Long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public Long getCapacity() {
            return capacity;
        }

        public void setCapacity(Long capacity) {
            this.capacity = capacity;
        }

        public Long getRefillSeconds() {
            return refillSeconds;
        }

        public void setRefillSeconds(Long refillSeconds) {
            this.refillSeconds = refillSeconds;
        }
    }
}
