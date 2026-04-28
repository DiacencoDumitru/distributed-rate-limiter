# Distributed Rate Limiter

Spring Boot сервис распределенного rate limiting на Redis Lua.

## Что реализовано в этом feature PR

- API endpoint: `POST /api/v1/rate-limit/check`
- 2 стратегии лимитирования:
  - `FIXED_WINDOW`
  - `TOKEN_BUCKET`
- Атомарная проверка и обновление состояния через Lua в Redis
- Интеграционные тесты на Testcontainers (Redis)

## Технологии

- Java 21
- Spring Boot 3
- Spring Data Redis
- Redis Lua scripts
- JUnit 5 + Testcontainers

## Запуск локально

1. Поднять Redis:

```bash
docker run --name drl-redis -p 6379:6379 -d redis:7.4-alpine
```

2. Запустить приложение:

```bash
./mvnw spring-boot:run
```

Если `mvnw` отсутствует:

```bash
mvn spring-boot:run
```

## Пример запроса

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

## Пример ответа (allow)

```json
{
  "allowed": true,
  "remaining": 4,
  "retryAfterSeconds": 0
}
```

## Пример ответа (reject)

HTTP `429 Too Many Requests`

```json
{
  "allowed": false,
  "remaining": 0,
  "retryAfterSeconds": 3
}
```

## Тесты

```bash
./mvnw test
```

или

```bash
mvn test
```
