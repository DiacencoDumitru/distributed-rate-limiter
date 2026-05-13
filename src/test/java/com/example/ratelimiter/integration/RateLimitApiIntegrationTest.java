package com.example.ratelimiter.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RateLimitApiIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("rate-limit.policies.it-fixed-policy.strategy", () -> "FIXED_WINDOW");
        registry.add("rate-limit.policies.it-fixed-policy.limit", () -> "2");
        registry.add("rate-limit.policies.it-fixed-policy.window-seconds", () -> "10");
    }

    @Test
    void actuatorHealthShouldReturnUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheusEndpointShouldExposeMetrics() throws Exception {
        String request = """
                {
                  "key":"metrics-user",
                  "strategy":"FIXED_WINDOW",
                  "limit":1,
                  "windowSeconds":5
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("jvm_info")))
                .andExpect(content().string(Matchers.containsString("ratelimiter_requests_total")))
                .andExpect(content().string(Matchers.containsString("strategy=\"fixed_window\"")))
                .andExpect(content().string(Matchers.containsString("outcome=\"allowed\"")))
                .andExpect(content().string(Matchers.containsString("outcome=\"rejected\"")));
    }

    @Test
    void openApiEndpointShouldDescribeRateLimitApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/v1/rate-limit/check']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/rate-limit/state']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/rate-limit/reset']").exists());
    }

    @Test
    void fixedWindowShouldRejectAfterLimitReached() throws Exception {
        String request = """
                {
                  "key":"user-a",
                  "strategy":"FIXED_WINDOW",
                  "limit":2,
                  "windowSeconds":3
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remaining").value(1))
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"))
                .andExpect(header().string("Retry-After", "0"));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remaining").value(0));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.retryAfterSeconds").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("Retry-After", Matchers.not("0")));
    }

    @Test
    void fixedWindowShouldAllowAgainAfterWindowElapsed() throws Exception {
        String request = """
                {
                  "key":"user-b",
                  "strategy":"FIXED_WINDOW",
                  "limit":1,
                  "windowSeconds":1
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false));

        Thread.sleep(1200);

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void tokenBucketShouldRefillOverTime() throws Exception {
        String request = """
                {
                  "key":"user-c",
                  "strategy":"TOKEN_BUCKET",
                  "capacity":2,
                  "refillSeconds":2
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.retryAfterSeconds").value(Matchers.greaterThanOrEqualTo(1)));

        Thread.sleep(1100);

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void adminStateShouldReturnFixedWindowStateWithoutMutatingCounter() throws Exception {
        String checkRequest = """
                {
                  "key":"admin-fixed-user",
                  "strategy":"FIXED_WINDOW",
                  "limit":3,
                  "windowSeconds":10
                }
                """;
        String stateRequest = """
                {
                  "key":"admin-fixed-user",
                  "strategy":"FIXED_WINDOW"
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(2));

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("FIXED_WINDOW"))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.currentCount").value(1))
                .andExpect(jsonPath("$.tokens").doesNotExist())
                .andExpect(jsonPath("$.lastRefillTimestampSeconds").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCount").value(1));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(1));
    }

    @Test
    void adminStateShouldReturnTokenBucketStateWithoutMutatingTokens() throws Exception {
        String checkRequest = """
                {
                  "key":"admin-token-user",
                  "strategy":"TOKEN_BUCKET",
                  "capacity":2,
                  "refillSeconds":20
                }
                """;
        String stateRequest = """
                {
                  "key":"admin-token-user",
                  "strategy":"TOKEN_BUCKET"
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkRequest))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("TOKEN_BUCKET"))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.currentCount").doesNotExist())
                .andExpect(jsonPath("$.tokens").isNumber())
                .andExpect(jsonPath("$.lastRefillTimestampSeconds").isNumber());

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokens").isNumber());

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void adminStateShouldReturnPositiveTtlForExistingKeys() throws Exception {
        String fixedWindowCheckRequest = """
                {
                  "key":"admin-ttl-fixed-user",
                  "strategy":"FIXED_WINDOW",
                  "limit":2,
                  "windowSeconds":15
                }
                """;
        String fixedWindowStateRequest = """
                {
                  "key":"admin-ttl-fixed-user",
                  "strategy":"FIXED_WINDOW"
                }
                """;
        String tokenBucketCheckRequest = """
                {
                  "key":"admin-ttl-token-user",
                  "strategy":"TOKEN_BUCKET",
                  "capacity":2,
                  "refillSeconds":15
                }
                """;
        String tokenBucketStateRequest = """
                {
                  "key":"admin-ttl-token-user",
                  "strategy":"TOKEN_BUCKET"
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedWindowCheckRequest))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedWindowStateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.ttlSeconds").value(Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBucketCheckRequest))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBucketStateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.ttlSeconds").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void rateLimitChecksShouldWorkAfterScriptFlush() throws Exception {
        String fixedWindowRequest = """
                {
                  "key":"evalsha-fixed-user",
                  "strategy":"FIXED_WINDOW",
                  "limit":2,
                  "windowSeconds":5
                }
                """;
        String tokenBucketRequest = """
                {
                  "key":"evalsha-token-user",
                  "strategy":"TOKEN_BUCKET",
                  "capacity":2,
                  "refillSeconds":5
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedWindowRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBucketRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.scriptingCommands().scriptFlush();
            return null;
        });

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedWindowRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBucketRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void adminStateShouldReturnNotExistingForMissingKey() throws Exception {
        String stateRequest = """
                {
                  "key":"missing-user",
                  "strategy":"FIXED_WINDOW"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.currentCount").doesNotExist())
                .andExpect(jsonPath("$.tokens").doesNotExist())
                .andExpect(jsonPath("$.lastRefillTimestampSeconds").doesNotExist());
    }

    @Test
    void adminResetShouldDeleteStateAndAllowFreshChecks() throws Exception {
        String checkRequest = """
                {
                  "key":"admin-reset-user",
                  "strategy":"FIXED_WINDOW",
                  "limit":1,
                  "windowSeconds":20
                }
                """;
        String stateRequest = """
                {
                  "key":"admin-reset-user",
                  "strategy":"FIXED_WINDOW"
                }
                """;
        String resetRequest = """
                {
                  "key":"admin-reset-user",
                  "strategy":"FIXED_WINDOW"
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));

        mockMvc.perform(post("/api/v1/admin/rate-limit/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void checkShouldApplyPolicyPresetWithoutExplicitLimits() throws Exception {
        String request = """
                {
                  "key":"policy-preset-user",
                  "strategy":"FIXED_WINDOW",
                  "policyId":"it-fixed-policy"
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(header().string("X-RateLimit-Limit", "2"));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void checkShouldReturnBadRequestForUnknownPolicyId() throws Exception {
        String request = """
                {
                  "key":"policy-unknown-user",
                  "strategy":"FIXED_WINDOW",
                  "policyId":"does-not-exist"
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown policyId"));
    }

    @Test
    void fixedWindowShouldReturnBadRequestWhenRequiredFieldIsMissing() throws Exception {
        String request = """
                {
                  "key":"user-invalid-fixed",
                  "strategy":"FIXED_WINDOW",
                  "windowSeconds":2
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("limit is required"))
                .andExpect(jsonPath("$.path").value("/api/v1/rate-limit/check"));
    }

    @Test
    void tokenBucketShouldReturnBadRequestWhenRequiredFieldIsMissing() throws Exception {
        String request = """
                {
                  "key":"user-invalid-token",
                  "strategy":"TOKEN_BUCKET",
                  "refillSeconds":2
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("capacity is required"));
    }

    @Test
    void adminStateShouldReturnBadRequestWhenRequiredFieldIsMissing() throws Exception {
        String request = """
                {
                  "key":"admin-invalid"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void slidingWindowShouldRejectAfterLimitReached() throws Exception {
        String request = """
                {
                  "key":"sliding-user-a",
                  "strategy":"SLIDING_WINDOW",
                  "limit":2,
                  "windowSeconds":3
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remaining").value(1));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remaining").value(0));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.retryAfterSeconds").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void slidingWindowShouldAllowAgainAfterWindowSlides() throws Exception {
        String request = """
                {
                  "key":"sliding-user-b",
                  "strategy":"SLIDING_WINDOW",
                  "limit":1,
                  "windowSeconds":1
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false));

        Thread.sleep(1200);

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void slidingWindowShouldDropOldestEntryAsItLeavesWindow() throws Exception {
        String request = """
                {
                  "key":"sliding-user-c",
                  "strategy":"SLIDING_WINDOW",
                  "limit":2,
                  "windowSeconds":2
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        Thread.sleep(1100);

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false));

        Thread.sleep(1000);

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void adminStateShouldReturnSlidingWindowStateWithoutMutatingCounter() throws Exception {
        String checkRequest = """
                {
                  "key":"admin-sliding-user",
                  "strategy":"SLIDING_WINDOW",
                  "limit":3,
                  "windowSeconds":10
                }
                """;
        String stateRequest = """
                {
                  "key":"admin-sliding-user",
                  "strategy":"SLIDING_WINDOW",
                  "windowSeconds":10
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(2));

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("SLIDING_WINDOW"))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.currentCount").value(1))
                .andExpect(jsonPath("$.tokens").doesNotExist())
                .andExpect(jsonPath("$.lastRefillTimestampSeconds").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCount").value(1));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(1));
    }

    @Test
    void adminStateShouldReturnPositiveTtlForSlidingWindow() throws Exception {
        String checkRequest = """
                {
                  "key":"admin-ttl-sliding-user",
                  "strategy":"SLIDING_WINDOW",
                  "limit":2,
                  "windowSeconds":15
                }
                """;
        String stateRequest = """
                {
                  "key":"admin-ttl-sliding-user",
                  "strategy":"SLIDING_WINDOW",
                  "windowSeconds":15
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkRequest))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.ttlSeconds").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void slidingWindowShouldReturnBadRequestWhenRequiredFieldIsMissing() throws Exception {
        String request = """
                {
                  "key":"user-invalid-sliding",
                  "strategy":"SLIDING_WINDOW",
                  "limit":2
                }
                """;

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("windowSeconds is required"));
    }

    @Test
    void adminStateShouldReturnBadRequestWhenSlidingWindowSecondsMissing() throws Exception {
        String stateRequest = """
                {
                  "key":"admin-sliding-missing-window",
                  "strategy":"SLIDING_WINDOW"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest))
                .andExpect(status().isBadRequest());
    }
}
