package com.example.ratelimiter.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FixedWindowRateLimitRequest(
        @NotBlank String key,
        @NotNull RateLimitStrategy strategy,
        String policyId,
        @Min(1) Long limit,
        @Min(1) Long windowSeconds
) implements RateLimitRequest {
}
