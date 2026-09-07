# API Gateway

Port `8080` · Spring Cloud Gateway (reactive / Netty) · the single entry point.

## Routing

| Path | → service |
|---|---|
| `/api/users/**`, `/api/auth/**` | user-service :8081 |
| `/api/products/**`, `/api/categories/**` | product-service :8082 |
| `/api/cart/**` | cart-service :8083 |
| `/api/orders/**` | order-service :8084 |
| `/api/inventory/**` | inventory-service :8085 (admin) |
| `/api/payments/**` | payment-service :8086 (admin) |
| `/api/notifications/**` | notification-service :8087 (admin) |

Downstream URLs come from `*_SERVICE_URI` env vars (Compose / K8s DNS).

## Global filters (in order)

1. **`CorrelationIdGatewayFilter`** — read/generate `X-Correlation-Id`, forward it, echo on the response.
2. **`GatewayAuthFilter`** — always strips inbound `X-User-*`. Public paths
   (`shopflow.gateway.public-paths`: register/login/refresh/logout, `GET` products & categories,
   actuator, swagger) pass through. Otherwise verifies the bearer token with `JwtValidator`
   (shopflow-common) and injects `X-User-Id` / `X-User-Roles`. No/invalid token → `401` with the
   standard `ErrorResponse` body. **Services still re-validate the JWT themselves.**
3. **`SecureHeaders`** (built-in) — `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, …
4. **`RequestRateLimiter`** — `RedisRateLimiter`, key = user id (post-auth) else client IP,
   `RATE_LIMIT_RPS` / `RATE_LIMIT_BURST` (default 20 / 40). Over limit → `429 RATE_LIMITED`.

## Errors

`GatewayErrorHandler` (`@Order(-2)`) renders the standard `ErrorResponse` for gateway-level failures:
downed downstream → `503 DOWNSTREAM_UNAVAILABLE`, rate limit → `429 RATE_LIMITED`, else `500`.

## CORS

`spring.cloud.gateway.globalcors` — origins from `CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`).

## Config

`JWT_SECRET`, `REDIS_HOST`/`REDIS_PORT`, `*_SERVICE_URI`, `RATE_LIMIT_RPS`/`RATE_LIMIT_BURST`,
`CORS_ALLOWED_ORIGINS`, `shopflow.gateway.public-paths`.
