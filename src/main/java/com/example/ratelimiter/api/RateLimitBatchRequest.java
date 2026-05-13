package com.example.ratelimiter.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RateLimitBatchRequest(
        @NotNull
        @Size(min = 1, max = 32)
        @Valid
        List<RateLimitRequest> requests
) {
}
