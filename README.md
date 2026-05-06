# Distributed Rate Limiter

Distributed rate limiter service built with Spring Boot, Redis, and Lua scripts.

## Implemented In This Feature PR

- API endpoint: `POST /api/v1/rate-limit/check`
- Admin API endpoint: `POST /api/v1/admin/rate-limit/state`
- Two rate limiting strategies:
  - `FIXED_WINDOW`
  - `TOKEN_BUCKET`
- Atomic check-and-update logic using Redis Lua
- Integration tests with Testcontainers (Redis)

## Tech Stack

- Java 21
- Spring Boot 3
- Spring Data Redis
- Redis Lua scripts
- JUnit 5 + Testcontainers

## Run Locally

1. Start Redis:

```bash
docker run --name drl-redis -p 6379:6379 -d redis:7.4-alpine
```

2. Run the application:

```bash
./mvnw spring-boot:run
```

If `mvnw` is not available:

```bash
mvn spring-boot:run
```

## Request Examples

```http
POST /api/v1/rate-limit/check
Content-Type: application/json

{
  "key": "user-123",
  "strategy": "FIXED_WINDOW",
  "limit": 5,
  "windowSeconds": 10
}
```

```http
POST /api/v1/admin/rate-limit/state
Content-Type: application/json

{
  "key": "user-123",
  "strategy": "FIXED_WINDOW"
}
```

```http
POST /api/v1/admin/rate-limit/state
Content-Type: application/json

{
  "key": "user-123",
  "strategy": "TOKEN_BUCKET"
}
```

```http
POST /api/v1/rate-limit/check
Content-Type: application/json

{
  "key": "user-123",
  "strategy": "TOKEN_BUCKET",
  "capacity": 5,
  "refillSeconds": 10
}
```

## Response Example (allow)

```json
{
  "allowed": true,
  "remaining": 4,
  "retryAfterSeconds": 0
}
```

## Response Example (reject)

HTTP `429 Too Many Requests`

```json
{
  "allowed": false,
  "remaining": 0,
  "retryAfterSeconds": 3
}
```

## Admin State Response Example (fixed window)

```json
{
  "key": "user-123",
  "strategy": "FIXED_WINDOW",
  "exists": true,
  "ttlSeconds": 8,
  "currentCount": 2,
  "tokens": null,
  "lastRefillTimestampSeconds": null
}
```

## Admin State Response Example (token bucket)

```json
{
  "key": "user-123",
  "strategy": "TOKEN_BUCKET",
  "exists": true,
  "ttlSeconds": 9,
  "currentCount": null,
  "tokens": 1.25,
  "lastRefillTimestampSeconds": 1714999999.12
}
```

Admin state endpoint is read-only and does not consume tokens or increment counters.

## Tests

```bash
./mvnw test
```

or

```bash
mvn test
```

## Implementation Notes

The API now uses strategy-specific request models:

- `FIXED_WINDOW` requires `limit` and `windowSeconds`.
- `TOKEN_BUCKET` requires `capacity` and `refillSeconds`.

At runtime, Jackson resolves the incoming request model by `strategy`.
Then `RateLimiterService` maps each request type to a dedicated Redis client method.
Each method executes its own Lua script path with strategy-specific parameters and returns a unified `RateLimitResponse`.
