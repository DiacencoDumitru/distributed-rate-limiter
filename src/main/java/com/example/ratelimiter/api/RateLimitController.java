package com.example.ratelimiter.api;

import com.example.ratelimiter.service.RateLimitResult;
import com.example.ratelimiter.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rate-limit")
public class RateLimitController {
    private final RateLimiterService rateLimiterService;

    public RateLimitController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> check(@Valid @RequestBody RateLimitRequest request) {
        RateLimitResult result = rateLimiterService.check(request);
        RateLimitResponse response = new RateLimitResponse(
                result.allowed(),
                result.remaining(),
                result.retryAfterSeconds()
        );
        HttpStatus status = result.allowed() ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status).body(response);
    }
}
