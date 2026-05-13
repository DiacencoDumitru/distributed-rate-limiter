package com.example.ratelimiter.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class RedisFailModeIntegrationTest {

    private static final String CHECK_BODY = """
            {"key":"offline","strategy":"FIXED_WINDOW","limit":3,"windowSeconds":10}
            """;

    private static final String STATE_BODY = """
            {"key":"offline","strategy":"FIXED_WINDOW"}
            """;

    private static final String RESET_BODY = """
            {"key":"offline","strategy":"FIXED_WINDOW"}
            """;

    @Nested
    @SpringBootTest(
            properties = {
                    "spring.data.redis.host=127.0.0.1",
                    "spring.data.redis.port=63979",
                    "rate-limit.redis-fail-mode=FAIL_OPEN"
            })
    @AutoConfigureMockMvc
    class FailOpen {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void checkAllowsWhenRedisUnreachable() throws Exception {
            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CHECK_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowed").value(true))
                    .andExpect(jsonPath("$.remaining").value(3));
        }

        @Test
        void stateReturnsNotExistsWhenRedisUnreachable() throws Exception {
            mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(STATE_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(false));
        }

        @Test
        void resetReturnsNotDeletedWhenRedisUnreachable() throws Exception {
            mockMvc.perform(post("/api/v1/admin/rate-limit/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(RESET_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deleted").value(false));
        }
    }

    @Nested
    @SpringBootTest(
            properties = {
                    "spring.data.redis.host=127.0.0.1",
                    "spring.data.redis.port=63979",
                    "rate-limit.redis-fail-mode=FAIL_CLOSED"
            })
    @AutoConfigureMockMvc
    class FailClosed {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void checkReturns503WhenRedisUnreachable() throws Exception {
            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CHECK_BODY))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        void stateReturns503WhenRedisUnreachable() throws Exception {
            mockMvc.perform(post("/api/v1/admin/rate-limit/state")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(STATE_BODY))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        void resetReturns503WhenRedisUnreachable() throws Exception {
            mockMvc.perform(post("/api/v1/admin/rate-limit/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(RESET_BODY))
                    .andExpect(status().isServiceUnavailable());
        }
    }
}
