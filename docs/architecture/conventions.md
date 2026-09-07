# ShopFlow Conventions

Rules every service follows so the system feels like one product. Enforced by `shopflow-common`
where possible (error body, correlation id, pagination envelope) and by review otherwise.

---

## 1. REST API design

### URLs
- Lower-case, kebab-case, **plural nouns** for collections: `/api/products`, `/api/orders`.
- Hierarchy shows ownership: `/api/orders/{orderId}` , `/api/orders/{orderId}/items`.
- No verbs in paths. An action that is not a plain CRUD maps to a sub-resource or a state change:
  `POST /api/orders/{id}/cancellation` (preferred) or `POST /api/orders/{id}:cancel`.
- Every service is reached through the gateway under `/api/**`. Internal-only endpoints live under
  `/internal/**` and are never routed by the gateway.

### API versioning
- **No URI version segment** (`/api/v1/...`) in this project. The API surface is small and evolves
  additively. Breaking changes would be introduced behind a new path at that time.
- **Events are versioned** (`eventVersion` in the envelope) - that is where compatibility actually bites.

### HTTP methods
| Method | Use | Idempotent | Body |
|---|---|---|---|
| `GET` | read | yes | no |
| `POST` | create, or trigger a process | no (use `Idempotency-Key` where a retry could double-charge) | yes |
| `PUT` | full replace of a known resource | yes | yes |
| `PATCH` | partial update | no (semantically) | yes (JSON merge) |
| `DELETE` | remove / deactivate | yes | no |

### Status codes
| Code | When |
|---|---|
| `200 OK` | successful `GET` / `PUT` / `PATCH`, or a `POST` that returns an existing resource (idempotent replay) |
| `201 Created` | resource created; `Location` header points to it |
| `202 Accepted` | work accepted but not finished - **order creation** returns this (Saga runs async) |
| `204 No Content` | success with no body (e.g. `DELETE`, cart cleared) |
| `400 Bad Request` | validation failure, malformed body/params (`VALIDATION_ERROR`) |
| `401 Unauthorized` | missing/invalid/expired token (`AUTHENTICATION_FAILED`) |
| `403 Forbidden` | authenticated but not allowed (`ACCESS_DENIED`) |
| `404 Not Found` | resource absent or hidden from this caller (`RESOURCE_NOT_FOUND`) |
| `409 Conflict` | duplicate unique value, illegal state transition, idempotency-key clash |
| `422` | **not used** - we return `400` for semantic validation too, to keep clients simple |
| `429 Too Many Requests` | rate limit tripped at the gateway (`RATE_LIMITED`) |
| `503 Service Unavailable` | a required downstream/dependency is down (`DOWNSTREAM_UNAVAILABLE`) |
| `500 Internal Server Error` | unexpected; generic message only (`INTERNAL_ERROR`) |

### Error body
Always this shape (produced by `com.shopflow.common.web.GlobalExceptionHandler`):

```json
{
  "timestamp": "2026-01-01T10:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "email must be a well-formed email address",
  "path": "/api/users/register",
  "correlationId": "9f1c2a4e-6b7d-4c31-9a0e-1d2f3b4c5d6e"
}
```
- `error` is a stable machine code from `com.shopflow.common.error.ErrorCode`. Clients branch on it.
- `message` is human-readable and may change; never parse it.
- Never return a stack trace, SQL, class name, or internal id in `message`.

---

## 2. Request / response bodies

- **DTOs only.** Entities are never serialized to or deserialized from JSON. Each service maps
  entity <-> DTO explicitly (a mapper class or method; no field-by-field reflection magic needed).
- DTOs are Java `record`s. Request DTOs carry Bean Validation annotations.
- JSON is `camelCase`. Unknown properties on input are ignored (`FAIL_ON_UNKNOWN_PROPERTIES=false`,
  Boot default) but documented fields are validated.
- Timestamps: **ISO-8601 UTC** (`2026-01-01T10:00:00Z`), serialized from `java.time.Instant`.
  Never epoch millis in a body.
- Money: string or number with exactly 2 decimals, backed by `BigDecimal` / `NUMERIC(12,2)`.
  Never `double`. Currency is implied (single currency project) but a `currency` field may be added.
- IDs in URLs and bodies: `UUID` for anything externally referenced (`orderId`, `paymentId`);
  numeric ids stay internal.

---

## 3. Validation

- Field constraints: Bean Validation annotations on the request record
  (`@NotBlank`, `@Email`, `@Size`, `@Positive`, `@PositiveOrZero`, `@Min`, `@Max`).
- `@Valid` on the controller parameter triggers it; failures become `400 VALIDATION_ERROR` with a
  `field message; field message` summary - handled centrally, no per-controller try/catch.
- Cross-field or domain rules that annotations cannot express: throw
  `com.shopflow.common.error.ValidationException` from the service layer.
- Validate at the edge of the service (controller/DTO), not deep in the domain.

---

## 4. Pagination, sorting, filtering

- Query params: `page` (0-based, default `0`), `size` (default `20`, **max `100`** - clamp, don't error),
  `sort` (`field,asc|desc`; repeatable, e.g. `?sort=price,asc&sort=name,desc`).
- Response body is always `com.shopflow.common.web.PageResponse<T>`:
  ```json
  { "content": [ ... ], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7, "first": true, "last": false }
  ```
  Never serialize Spring Data's `Page` directly.
- Filtering: explicit named params only (`?category=books&minPrice=10&maxPrice=50&active=true`).
  No generic query-language param. Unknown filter params are ignored.
- Every field exposed for `sort` or filter must be backed by a database index (see per-service schema).

---

## 5. Standard headers

| Header | Direction | Meaning |
|---|---|---|
| `Authorization: Bearer <jwt>` | request | access token; required on protected routes |
| `X-Correlation-Id` | both | generated by the gateway if absent; echoed on the response; on every log line and event |
| `X-User-Id`, `X-User-Roles` | gateway -> service | set by the gateway **after** verifying the JWT; services strip any client-supplied copies |
| `Idempotency-Key` | request | client-generated UUID on non-idempotent `POST`s (order creation, payment retry) |
| `Location` | response | absolute URL of a newly created resource (with `201`) |
| `Retry-After` | response | seconds, on `429` and sometimes `503` |

Constants: `com.shopflow.common.web.ShopFlowHeaders`, `com.shopflow.common.observability.CorrelationId.HEADER`.

---

## 6. Idempotency

- **Client-driven** (HTTP): client sends `Idempotency-Key: <uuid>`. The service stores
  `(idempotency_key UNIQUE, request_hash, response_snapshot, created_at)`. Replay with the same key +
  same body -> return the stored response (`200`). Same key + different body -> `409 IDEMPOTENCY_CONFLICT`.
  Keys expire after 24h.
- **Event-driven** (Kafka consumers): every consumer keeps `processed_event(event_id PRIMARY KEY, ...)`.
  Insert-then-process (or process-then-insert) in the same local transaction; a duplicate `event_id`
  is a no-op. State-machine guards (e.g. "ignore `PAYMENT_SUCCESS` unless status is `AWAITING_PAYMENT`")
  are a second layer.
- Full rules and rationale: Phase 1 doc §6, and Phase 6/11 implementations.

---

## 7. Logging

- SLF4J via Logback. Config: `shopflow-common/logback-common.xml`, included by each service's
  `logback-spring.xml`. Text format locally; JSON (`LogstashEncoder`) under the `json` / `docker` /
  `prod` profile.
- Every line carries MDC `correlationId` and (when authenticated) `userId`.
- Levels: `ERROR` = needs attention (unexpected 500, exhausted retries, DLT publish). `WARN` = handled
  but notable (4xx from a client, compensation triggered, cache miss storm). `INFO` = business
  milestones (order created, payment succeeded, event consumed). `DEBUG` = developer detail, off in prod.
- **Never log**: passwords, tokens, `Authorization` headers, full card-like numbers, request bodies
  containing credentials. Log ids, not payloads.
- One event = one log line where possible; use structured key-values, not string concatenation of maps.

---

## 8. Events (summary - full catalog in `events/event-catalog.md`)

- Envelope: `com.shopflow.common.event.EventEnvelope<T>` (fields explained in Phase 1 §6).
- Type: `com.shopflow.common.event.EventType` enum. `eventVersion` starts at `1`.
- Additive payload changes do not bump the version (tolerant reader). Breaking changes -> `eventVersion: 2`
  and, if consumers cannot all migrate at once, a new topic suffix.
- Partition key: `orderId` for order/payment/inventory events (per-order ordering); `userId` for
  notification events.

---

## 9. Package layout inside a service

```
com.shopflow.<service>
├── api          controllers + request/response DTOs + mappers
├── domain       entities, value objects, enums, domain services
├── repository    Spring Data repositories
├── service      application/use-case layer (transaction boundaries live here)
├── event        Kafka producers, consumers, and payload records
├── security     filters, method-security config, JWT wiring
├── config       @Configuration classes, properties
└── exception    service-specific exceptions + optional @RestControllerAdvice
```
- **Constructor injection only.** No `@Autowired` on fields.
- Transaction boundary = the `service` method. Controllers and repositories are not `@Transactional`.
- No business logic in controllers; no HTTP concerns in the domain.

---

## 10. OpenAPI / Swagger

- Every service exposes `/swagger-ui.html` and `/v3/api-docs` (springdoc).
- Document: summary, parameters, request example, success example, the error codes it can return,
  and whether auth is required. Group admin vs public endpoints with tags.
- The gateway aggregates the per-service docs in Phase 13.
