package com.example.ratelimiter.service;

import com.example.ratelimiter.api.AdminRateLimitResetRequest;
import com.example.ratelimiter.api.AdminRateLimitStateRequest;
import com.example.ratelimiter.api.FixedWindowRateLimitRequest;
import com.example.ratelimiter.api.RateLimitRequest;
import com.example.ratelimiter.api.SlidingWindowRateLimitRequest;
import com.example.ratelimiter.api.TokenBucketRateLimitRequest;
import com.example.ratelimiter.config.RateLimitPoliciesProperties;
import com.example.ratelimiter.config.RateLimitPoliciesProperties.PolicyDefinition;
import com.example.ratelimiter.config.RedisFailMode;
import com.example.ratelimiter.redis.RedisRateLimiterClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RateLimiterService {
    private final RedisRateLimiterClient redisRateLimiterClient;
    private final MeterRegistry meterRegistry;
    private final RateLimitPoliciesProperties policiesProperties;

    public RateLimiterService(
            RedisRateLimiterClient redisRateLimiterClient,
            MeterRegistry meterRegistry,
            RateLimitPoliciesProperties policiesProperties) {
        this.redisRateLimiterClient = redisRateLimiterClient;
        this.meterRegistry = meterRegistry;
        this.policiesProperties = policiesProperties;
    }

    public RateLimitCheckOutcome check(RateLimitRequest request) {
        RateLimitRequest resolved = resolvePolicies(request);
        String strategy = resolveStrategy(resolved);
        String scope = redisScope(resolved);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            RateLimitResult result = evaluateRedis(resolved, scope);
            String outcome = result.allowed() ? "allowed" : "rejected";
            meterRegistry.counter(
                    "ratelimiter.requests.total",
                    "strategy", strategy,
                    "outcome", outcome
            ).increment();
            sample.stop(Timer.builder("ratelimiter.request.duration")
                    .tag("strategy", strategy)
                    .tag("outcome", outcome)
                    .register(meterRegistry));
            return new RateLimitCheckOutcome(result, limitOf(resolved));
        } catch (DataAccessException ex) {
            return handleRedisFailureOnCheck(resolved, strategy, sample);
        }
    }

    public List<RateLimitCheckOutcome> checkBatch(List<RateLimitRequest> requests) {
        List<RateLimitCheckOutcome> outcomes = new ArrayList<>(requests.size());
        for (RateLimitRequest request : requests) {
            outcomes.add(check(request));
        }
        return outcomes;
    }

    public RateLimitStateResult getState(AdminRateLimitStateRequest request) {
        String scope = emptyToNull(request.scope());
        try {
            return switch (request.strategy()) {
                case FIXED_WINDOW -> {
                    var fixedWindowState = redisRateLimiterClient.getFixedWindowState(request.key(), scope);
                    String currentCountValue = fixedWindowState.get(0);
                    long ttlSeconds = Long.parseLong(fixedWindowState.get(1));
                    if (isMissingState(currentCountValue)) {
                        yield new RateLimitStateResult(false, 0, null, null, null);
                    }
                    yield new RateLimitStateResult(true, ttlSeconds, Long.parseLong(currentCountValue), null, null);
                }
                case TOKEN_BUCKET -> {
                    var tokenBucketState = redisRateLimiterClient.getTokenBucketState(request.key(), scope);
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
                case SLIDING_WINDOW -> {
                    if (request.windowSeconds() == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "windowSeconds is required for SLIDING_WINDOW");
                    }
                    var slidingWindowState = redisRateLimiterClient.getSlidingWindowState(request.key(), request.windowSeconds(), scope);
                    String currentCountValue = slidingWindowState.get(0);
                    long ttlSeconds = Long.parseLong(slidingWindowState.get(1));
                    if (isMissingState(currentCountValue)) {
                        yield new RateLimitStateResult(false, 0, null, null, null);
                    }
                    yield new RateLimitStateResult(true, ttlSeconds, Long.parseLong(currentCountValue), null, null);
                }
            };
        } catch (DataAccessException ex) {
            if (policiesProperties.getRedisFailMode() == RedisFailMode.FAIL_OPEN) {
                return new RateLimitStateResult(false, 0, null, null, null);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "redis_unavailable");
        }
    }

    public boolean resetState(AdminRateLimitResetRequest request) {
        try {
            return redisRateLimiterClient.resetState(request.strategy(), request.key(), emptyToNull(request.scope()));
        } catch (DataAccessException ex) {
            if (policiesProperties.getRedisFailMode() == RedisFailMode.FAIL_OPEN) {
                return false;
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "redis_unavailable");
        }
    }

    private RateLimitResult evaluateRedis(RateLimitRequest resolved, String scope) {
        return switch (resolved) {
            case FixedWindowRateLimitRequest fixedWindowRequest -> redisRateLimiterClient.evaluateFixedWindow(
                    fixedWindowRequest.key(),
                    fixedWindowRequest.limit(),
                    fixedWindowRequest.windowSeconds(),
                    scope
            );
            case TokenBucketRateLimitRequest tokenBucketRequest -> redisRateLimiterClient.evaluateTokenBucket(
                    tokenBucketRequest.key(),
                    tokenBucketRequest.capacity(),
                    tokenBucketRequest.refillSeconds(),
                    scope
            );
            case SlidingWindowRateLimitRequest slidingWindowRequest -> redisRateLimiterClient.evaluateSlidingWindow(
                    slidingWindowRequest.key(),
                    slidingWindowRequest.limit(),
                    slidingWindowRequest.windowSeconds(),
                    scope
            );
        };
    }

    private RateLimitCheckOutcome handleRedisFailureOnCheck(RateLimitRequest resolved, String strategy, Timer.Sample sample) {
        if (policiesProperties.getRedisFailMode() == RedisFailMode.FAIL_OPEN) {
            String outcome = "redis_fail_open";
            long lim = limitOf(resolved);
            RateLimitResult result = new RateLimitResult(true, lim, 0L);
            meterRegistry.counter(
                    "ratelimiter.requests.total",
                    "strategy", strategy,
                    "outcome", outcome
            ).increment();
            sample.stop(Timer.builder("ratelimiter.request.duration")
                    .tag("strategy", strategy)
                    .tag("outcome", outcome)
                    .register(meterRegistry));
            return new RateLimitCheckOutcome(result, lim);
        }
        String outcome = "redis_unavailable";
        meterRegistry.counter(
                "ratelimiter.requests.total",
                "strategy", strategy,
                "outcome", outcome
        ).increment();
        sample.stop(Timer.builder("ratelimiter.request.duration")
                .tag("strategy", strategy)
                .tag("outcome", outcome)
                .register(meterRegistry));
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "redis_unavailable");
    }

    private RateLimitRequest resolvePolicies(RateLimitRequest request) {
        String policyId = switch (request) {
            case FixedWindowRateLimitRequest r -> emptyToNull(r.policyId());
            case TokenBucketRateLimitRequest r -> emptyToNull(r.policyId());
            case SlidingWindowRateLimitRequest r -> emptyToNull(r.policyId());
        };
        if (policyId == null) {
            validateExplicitOnly(request);
            return request;
        }
        PolicyDefinition def = policiesProperties.getPolicies().get(policyId);
        if (def == null || def.getStrategy() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown policyId");
        }
        if (def.getStrategy() != request.strategy()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "policyId strategy mismatch");
        }
        return switch (request) {
            case FixedWindowRateLimitRequest r -> {
                long limit = requirePositive(firstNonNull(r.limit(), def.getLimit()), "limit");
                long windowSeconds = requirePositive(firstNonNull(r.windowSeconds(), def.getWindowSeconds()), "windowSeconds");
                yield new FixedWindowRateLimitRequest(r.key(), r.strategy(), r.policyId(), r.scope(), limit, windowSeconds);
            }
            case TokenBucketRateLimitRequest r -> {
                long capacity = requirePositive(firstNonNull(r.capacity(), def.getCapacity()), "capacity");
                long refillSeconds = requirePositive(firstNonNull(r.refillSeconds(), def.getRefillSeconds()), "refillSeconds");
                yield new TokenBucketRateLimitRequest(r.key(), r.strategy(), r.policyId(), r.scope(), capacity, refillSeconds);
            }
            case SlidingWindowRateLimitRequest r -> {
                long limit = requirePositive(firstNonNull(r.limit(), def.getLimit()), "limit");
                long windowSeconds = requirePositive(firstNonNull(r.windowSeconds(), def.getWindowSeconds()), "windowSeconds");
                yield new SlidingWindowRateLimitRequest(r.key(), r.strategy(), r.policyId(), r.scope(), limit, windowSeconds);
            }
        };
    }

    private static String redisScope(RateLimitRequest request) {
        return switch (request) {
            case FixedWindowRateLimitRequest r -> emptyToNull(r.scope());
            case TokenBucketRateLimitRequest r -> emptyToNull(r.scope());
            case SlidingWindowRateLimitRequest r -> emptyToNull(r.scope());
        };
    }

    private static void validateExplicitOnly(RateLimitRequest request) {
        switch (request) {
            case FixedWindowRateLimitRequest r -> {
                requirePresent(r.limit(), "limit");
                requirePresent(r.windowSeconds(), "windowSeconds");
            }
            case TokenBucketRateLimitRequest r -> {
                requirePresent(r.capacity(), "capacity");
                requirePresent(r.refillSeconds(), "refillSeconds");
            }
            case SlidingWindowRateLimitRequest r -> {
                requirePresent(r.limit(), "limit");
                requirePresent(r.windowSeconds(), "windowSeconds");
            }
        }
    }

    private static void requirePresent(Long value, String name) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required");
        }
    }

    private static long requirePositive(Long value, String name) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required");
        }
        if (value < 1L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must be at least 1");
        }
        return value;
    }

    private static Long firstNonNull(Long a, Long b) {
        return a != null ? a : b;
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long limitOf(RateLimitRequest request) {
        return switch (request) {
            case FixedWindowRateLimitRequest fixedWindowRateLimitRequest -> fixedWindowRateLimitRequest.limit();
            case TokenBucketRateLimitRequest tokenBucketRateLimitRequest -> tokenBucketRateLimitRequest.capacity();
            case SlidingWindowRateLimitRequest slidingWindowRateLimitRequest -> slidingWindowRateLimitRequest.limit();
        };
    }

    private boolean isMissingState(String value) {
        return value == null || "-1".equals(value);
    }

    private String resolveStrategy(RateLimitRequest request) {
        return switch (request) {
            case FixedWindowRateLimitRequest ignored -> "fixed_window";
            case TokenBucketRateLimitRequest ignored -> "token_bucket";
            case SlidingWindowRateLimitRequest ignored -> "sliding_window";
        };
    }
}
