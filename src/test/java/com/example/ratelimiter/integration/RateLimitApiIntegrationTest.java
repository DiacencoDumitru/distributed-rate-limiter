package com.example.ratelimiter.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
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
                .andExpect(status().isBadRequest());
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
                .andExpect(status().isBadRequest());
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
}
