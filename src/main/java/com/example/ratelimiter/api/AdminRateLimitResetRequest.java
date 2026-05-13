package com.example.ratelimiter.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AdminRateLimitResetRequest(
        @NotBlank String key,
        @NotNull RateLimitStrategy strategy,
        @Pattern(regexp = "^[a-zA-Z0-9._-]{0,128}$") String scope
) {
}
