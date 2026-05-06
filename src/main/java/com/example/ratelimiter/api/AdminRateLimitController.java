package com.example.ratelimiter.api;

import com.example.ratelimiter.service.RateLimitStateResult;
import com.example.ratelimiter.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/rate-limit")
public class AdminRateLimitController {
    private final RateLimiterService rateLimiterService;

    public AdminRateLimitController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/state")
    public ResponseEntity<AdminRateLimitStateResponse> state(@Valid @RequestBody AdminRateLimitStateRequest request) {
        RateLimitStateResult result = rateLimiterService.getState(request);
        AdminRateLimitStateResponse response = new AdminRateLimitStateResponse(
                request.key(),
                request.strategy(),
                result.exists(),
                result.ttlSeconds(),
                result.currentCount(),
                result.tokens(),
                result.lastRefillTimestampSeconds()
        );
        return ResponseEntity.ok(response);
    }
}
