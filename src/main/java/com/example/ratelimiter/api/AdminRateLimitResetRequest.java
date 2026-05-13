package com.example.ratelimiter.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminRateLimitResetRequest(
        @NotBlank String key,
        @NotNull RateLimitStrategy strategy
) {
}
