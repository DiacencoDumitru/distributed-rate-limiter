package com.example.ratelimiter.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "strategy", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = FixedWindowRateLimitRequest.class, name = "FIXED_WINDOW"),
        @JsonSubTypes.Type(value = TokenBucketRateLimitRequest.class, name = "TOKEN_BUCKET"),
        @JsonSubTypes.Type(value = SlidingWindowRateLimitRequest.class, name = "SLIDING_WINDOW")
})
public sealed interface RateLimitRequest permits FixedWindowRateLimitRequest, TokenBucketRateLimitRequest, SlidingWindowRateLimitRequest {
    @NotBlank
    String key();

    @NotNull
    RateLimitStrategy strategy();

    String scope();
}
