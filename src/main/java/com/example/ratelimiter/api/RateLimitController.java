package com.example.ratelimiter.api;

import com.example.ratelimiter.service.RateLimitResult;
import com.example.ratelimiter.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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
        long limit = resolveLimit(request);
        return ResponseEntity.status(status)
                .header("X-RateLimit-Limit", String.valueOf(limit))
                .header("X-RateLimit-Remaining", String.valueOf(result.remaining()))
                .header(HttpHeaders.RETRY_AFTER, result.allowed() ? "0" : String.valueOf(Math.max(1, result.retryAfterSeconds())))
                .body(response);
    }

    private long resolveLimit(RateLimitRequest request) {
        return switch (request) {
            case FixedWindowRateLimitRequest fixedWindowRateLimitRequest -> fixedWindowRateLimitRequest.limit();
            case TokenBucketRateLimitRequest tokenBucketRateLimitRequest -> tokenBucketRateLimitRequest.capacity();
            case SlidingWindowRateLimitRequest slidingWindowRateLimitRequest -> slidingWindowRateLimitRequest.limit();
        };
    }
}
