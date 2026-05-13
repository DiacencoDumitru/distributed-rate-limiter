package com.example.ratelimiter.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SlidingWindowRateLimitRequest(
        @NotBlank String key,
        @NotNull RateLimitStrategy strategy,
        String policyId,
        @Pattern(regexp = "^[a-zA-Z0-9._-]{0,128}$") String scope,
        @Min(1) Long limit,
        @Min(1) Long windowSeconds
) implements RateLimitRequest {
}
