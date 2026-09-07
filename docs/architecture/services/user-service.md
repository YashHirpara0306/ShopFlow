# User Service

Port `8081` · database `shopflow_user` (PostgreSQL) · [Swagger UI](http://localhost:8081/swagger-ui.html)

Owns identity: registration, authentication, JWT issuance, refresh-token lifecycle, profile, roles.

## Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | public | Create a customer account. `201` + `UserResponse`. |
| `POST` | `/api/auth/login` | public | Email + password -> `200` + `TokenResponse` (access + refresh). |
| `POST` | `/api/auth/refresh` | public | Rotate: valid refresh token -> new access + refresh. Reuse -> `401` + family revoked. |
| `POST` | `/api/auth/logout` | public | Revoke a refresh token. `204` (idempotent). |
| `GET` | `/api/users/me` | bearer | The caller's own profile. |
| `PUT` | `/api/users/me` | bearer | Update own `displayName` / `phone`. |
| `GET` | `/api/users/{id}` | bearer (self or `ADMIN`) | One user. |
| `GET` | `/api/users?page=&size=&sort=` | `ADMIN` | Paged list (`PageResponse<UserResponse>`). |

All errors use the shared body (`{ timestamp, status, error, message, path, correlationId }`).

## Model

- `app_user` — `id (uuid)`, `email (unique, ci)`, `password_hash (BCrypt, strength 12)`, `display_name`,
  `phone`, `status (ACTIVE|DISABLED)`, `created_at`, `updated_at`, `version`.
- `user_role` — `(user_id, role)`; role ∈ `CUSTOMER | ADMIN`. Every user has `CUSTOMER`.
- `refresh_token` — `id`, `user_id`, `token_hash (sha256, unique)`, `family_id`, `issued_at`,
  `expires_at`, `revoked`, `replaced_by_id`. See [ADR-0007](../adr/0007-refresh-token-rotation.md).

Schema is created by Flyway (`db/migration/V1__init_user_schema.sql`); Hibernate runs in `validate`.

## Tokens

- **Access**: JWT, HS256, 15 min. Claims `sub` (userId), `roles` (`["ROLE_CUSTOMER", ...]`), `iss`,
  `iat`, `exp`, `jti`. Issued by `JwtIssuer` (shopflow-common).
- **Refresh**: opaque 256-bit random, 7 days, rotated on use, hashed at rest, family-revoked on reuse.
- Secret: `JWT_SECRET` env var (dev fallback in `application.yml`, prod MUST override).

## Config (`shopflow.*`)

| Key | Default | Meaning |
|---|---|---|
| `jwt.secret` | dev fallback | HS256 secret, >= 32 bytes |
| `jwt.access-token-ttl` | `15m` | access token lifetime |
| `jwt.refresh-token-ttl` | `7d` | refresh token lifetime |
| `security.bootstrap-admin-emails` | empty | emails granted `ADMIN` at registration |
| `bcrypt-strength` | `12` | BCrypt cost factor |

## Run locally

```bash
docker compose up -d postgres          # shopflow_user db is created by the init script
mvn -pl services/user-service -am spring-boot:run
curl -s localhost:8081/actuator/health
```

## Events

`USER_REGISTERED` is built as a real `EventEnvelope` and **logged** (Phase 4). Phase 11 swaps
`LoggingUserEventPublisher` for a Kafka producer to `notification-events`; nothing else changes.
