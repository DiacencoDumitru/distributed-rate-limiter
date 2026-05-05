package com.example.ratelimiter.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FixedWindowRateLimitRequest(
        @NotBlank String key,
        @NotNull RateLimitStrategy strategy,
        @NotNull @Min(1) Long limit,
        @NotNull @Min(1) Long windowSeconds
) implements RateLimitRequest {
}
