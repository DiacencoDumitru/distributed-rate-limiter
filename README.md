# Distributed Rate Limiter

Distributed rate limiter service built with Spring Boot, Redis, and Lua scripts.

## Implemented In This Feature PR

- API endpoint: `POST /api/v1/rate-limit/check`
- Admin API endpoint: `POST /api/v1/admin/rate-limit/state`
- Three rate limiting strategies:
  - `FIXED_WINDOW`
  - `TOKEN_BUCKET`
  - `SLIDING_WINDOW`
- Atomic check-and-update logic using Redis Lua
- Integration tests with Testcontainers (Redis)

## Tech Stack

- Java 21
- Spring Boot 3
- Spring Data Redis
- Redis Lua scripts
- Spring Boot Actuator
- Micrometer Prometheus
- OpenAPI UI
- JUnit 5 + Testcontainers
- Docker Compose

## Run Locally

1. Start Redis:

```bash
docker run --name drl-redis -p 6379:6379 -d redis:7.4-alpine
```

2. Run the application:

```bash
mvn spring-boot:run
```

## Run With Docker Compose

```bash
docker compose up --build
```

The application starts on `http://localhost:8080` and Redis starts on `localhost:6379`.

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

```http
POST /api/v1/rate-limit/check
Content-Type: application/json

{
  "key": "user-123",
  "strategy": "SLIDING_WINDOW",
  "limit": 5,
  "windowSeconds": 10
}
```

```http
POST /api/v1/admin/rate-limit/state
Content-Type: application/json

{
  "key": "user-123",
  "strategy": "SLIDING_WINDOW",
  "windowSeconds": 10
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

## Admin State Response Example (sliding window)

```json
{
  "key": "user-123",
  "strategy": "SLIDING_WINDOW",
  "exists": true,
  "ttlSeconds": 8,
  "currentCount": 2,
  "tokens": null,
  "lastRefillTimestampSeconds": null
}
```

Admin state endpoint is read-only and does not consume tokens or increment counters.
For `SLIDING_WINDOW` the admin state request must include `windowSeconds` so the server can
prune expired entries before reporting `currentCount`.

## Observability

- Health: `GET /actuator/health`
- Prometheus metrics: `GET /actuator/prometheus`
- OpenAPI JSON: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui`

## Configuration

| Environment variable | Default |
| --- | --- |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `REDIS_TIMEOUT` | `2s` |
| `REDIS_POOL_MAX_ACTIVE` | `16` |
| `REDIS_POOL_MAX_IDLE` | `8` |
| `REDIS_POOL_MIN_IDLE` | `0` |
| `REDIS_POOL_MAX_WAIT` | `2s` |

## Error Response Example

```json
{
  "timestamp": "2026-05-10T08:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/rate-limit/check",
  "fieldErrors": {
    "limit": "must not be null"
  }
}
```

## Tests

```bash
mvn test
```

## Implementation Notes

The API now uses strategy-specific request models:

- `FIXED_WINDOW` requires `limit` and `windowSeconds`.
- `TOKEN_BUCKET` requires `capacity` and `refillSeconds`.
- `SLIDING_WINDOW` requires `limit` and `windowSeconds`. Implemented as a sliding window log
  on a Redis sorted set: each request is recorded with a microsecond timestamp, expired
  entries are pruned in the same Lua call, and `retryAfterSeconds` is computed from the
  oldest entry still in the window.

At runtime, Jackson resolves the incoming request model by `strategy`.
Then `RateLimiterService` maps each request type to a dedicated Redis client method.
Each method executes its own Lua script path with strategy-specific parameters and returns a unified `RateLimitResponse`.
