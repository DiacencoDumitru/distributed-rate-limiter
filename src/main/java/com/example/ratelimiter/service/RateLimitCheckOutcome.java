package com.example.ratelimiter.service;

public record RateLimitCheckOutcome(RateLimitResult result, long limit) {}
