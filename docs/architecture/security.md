# Security posture

## Authentication

- **Access token**: JWT, HS256, 15 min. Claims `sub` (userId), `roles` (`["ROLE_CUSTOMER", ...]`),
  `iss=shopflow`, `iat`, `exp`, `jti`. Issued by User Service (`JwtIssuer`), verified everywhere by
  `JwtValidator` (both in `shopflow-common`, from one secret so issuer/validator can't drift).
- **Refresh token**: opaque 256-bit random, 7 days, **rotated on every use**, **SHA-256-hashed at
  rest**, grouped by `family_id`; presenting a revoked token revokes the whole family
  ([ADR-0007](adr/0007-refresh-token-rotation.md)). Logout revokes it. `RefreshTokenCleanupJob`
  prunes revoked/expired rows nightly.
- **Password**: BCrypt cost 12. Login runs `matches()` against a dummy hash for unknown emails so
  timing does not reveal account existence.

## Authorization

- **Two roles**: `ROLE_CUSTOMER`, `ROLE_ADMIN` (every user has `CUSTOMER`; bootstrap admins via
  `shopflow.security.bootstrap-admin-emails`).
- **Edge** (gateway): `GatewayAuthFilter` verifies the token, **strips inbound `X-User-*`**, injects
  `X-User-Id` / `X-User-Roles`. Public paths are an explicit allow-list (`shopflow.gateway.public-paths`).
- **Service** (defence in depth): each service re-validates the JWT (`JwtAuthenticationFilter` from
  common) - it never blindly trusts gateway headers. Request-matcher rules + `@PreAuthorize` for
  method-level checks + ownership checks (`order.userId == caller`).
- **Admin-only services**: Inventory, Payment, Notification - every route `hasRole('ADMIN')`.
- **Admin-only endpoints elsewhere**: product/category writes, `GET /api/users`, `PUT /api/orders/*/status`.

## Transport & headers

- Gateway: `SecureHeaders` default filter (`X-Content-Type-Options`, `X-Frame-Options`,
  `Referrer-Policy`, `Strict-Transport-Security` when TLS, ...). CORS allow-list from
  `CORS_ALLOWED_ORIGINS`.
- Services: Spring Security's default headers (`X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`, `Cache-Control: no-store` on secured responses).
- TLS terminates at the gateway / load balancer (documented for AWS/K8s; local dev is HTTP).

## Rate limiting

Gateway `RequestRateLimiter` (Redis token bucket): key = user id after auth, else client IP.
`RATE_LIMIT_RPS` / `RATE_LIMIT_BURST` (default 20 / 40). Over limit -> `429 RATE_LIMITED`.

## Secrets

`JWT_SECRET` (and DB passwords) come from environment variables / K8s Secrets / AWS Secrets Manager.
`application.yml` only references `${JWT_SECRET:<dev fallback>}`; the fallback is clearly labelled and
must be overridden in every non-local environment. Nothing secret is committed.

## Input handling

- Bean Validation on every request DTO; failures -> `400 VALIDATION_ERROR` (central handler, no stack
  trace, no internals).
- JPA / parameterised queries only (no string-concatenated SQL). `@Size` caps on free-text fields.
- Errors return the standard body with a `correlationId`; 5xx messages are generic.

## Logging

- Structured (JSON under the `json`/`docker`/`prod` profile) with `correlationId` + `userId` in the MDC.
- **Never logged**: passwords, tokens, `Authorization` headers, request bodies containing credentials.
  DTOs logged are ids and business fields, not raw payloads.

## Known limitations (documented, not fixed)

- Access tokens cannot be revoked before their 15-min expiry (upgrade path: a `jti` denylist in Redis).
- Email verification is off (demo). Real deployments would require it before issuing tokens.
- Inventory/Payment/Notification have a small dual-write window on event publish (Order Service uses
  an outbox; see [ADR-0009](adr/0009-kafka-support-and-outbox.md)).
- No WAF / bot protection / account-lockout beyond the gateway rate limit.
