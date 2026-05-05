package com.example.ratelimiter.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TokenBucketRateLimitRequest(
        @NotBlank String key,
        @NotNull RateLimitStrategy strategy,
        @NotNull @Min(1) Long capacity,
        @NotNull @Min(1) Long refillSeconds
) implements RateLimitRequest {
}
