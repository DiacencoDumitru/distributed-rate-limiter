package com.example.ratelimiter.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TokenBucketRateLimitRequest(
        @NotBlank String key,
        @NotNull RateLimitStrategy strategy,
        String policyId,
        @Min(1) Long capacity,
        @Min(1) Long refillSeconds
) implements RateLimitRequest {
}
