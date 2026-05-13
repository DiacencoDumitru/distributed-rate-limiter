package com.example.ratelimiter.api;

import com.example.ratelimiter.service.RateLimitCheckOutcome;
import com.example.ratelimiter.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rate-limit")
public class RateLimitController {
    private final RateLimiterService rateLimiterService;

    public RateLimitController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> check(@Valid @RequestBody RateLimitRequest request) {
        RateLimitCheckOutcome outcome = rateLimiterService.check(request);
        RateLimitResponse response = new RateLimitResponse(
                outcome.result().allowed(),
                outcome.result().remaining(),
                outcome.result().retryAfterSeconds()
        );
        HttpStatus status = outcome.result().allowed() ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status)
                .header("X-RateLimit-Limit", String.valueOf(outcome.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(outcome.result().remaining()))
                .header(
                        HttpHeaders.RETRY_AFTER,
                        outcome.result().allowed() ? "0" : String.valueOf(Math.max(1, outcome.result().retryAfterSeconds())))
                .body(response);
    }

    @PostMapping("/check-batch")
    public ResponseEntity<List<RateLimitResponse>> checkBatch(@Valid @RequestBody RateLimitBatchRequest batch) {
        List<RateLimitCheckOutcome> outcomes = rateLimiterService.checkBatch(batch.requests());
        List<RateLimitResponse> body = outcomes.stream()
                .map(outcome -> new RateLimitResponse(
                        outcome.result().allowed(),
                        outcome.result().remaining(),
                        outcome.result().retryAfterSeconds()
                ))
                .toList();
        return ResponseEntity.ok(body);
    }
}
