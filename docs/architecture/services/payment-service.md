# Payment Service

Port `8086` · database `shopflow_payment` (PostgreSQL) · admin/internal only · **simulated, no real gateway** · [Swagger UI](http://localhost:8086/swagger-ui.html)

## Endpoints (all `ROLE_ADMIN`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/payments` | `{orderId, amount, currency}` → process. **Idempotent per orderId** (`payment.order_id` UNIQUE) — a replay returns the existing outcome, never a second charge. `201`. |
| `GET` | `/api/payments/{orderId}` | Payment status. |
| `POST` | `/api/payments/{orderId}/refund` | Refund a **captured** payment (`409` otherwise). Idempotent per orderId (`refund.order_id` UNIQUE). |

## Simulation (`PaymentSimulator`)

- Amount whose paise/cents == `13` (e.g. `120.13`) → **always `DECLINED`** — deterministic hook for tests/demos.
- Otherwise declines with probability `shopflow.payment.fail-rate` (default `0.15`; `0.0` = always succeed).
- Success sets a fake `provider_ref`.

## Model

`payment(id, order_id UNIQUE, amount, currency, status PENDING|CAPTURED|FAILED, provider_ref, failure_reason)`,
`refund(id, payment_id FK, order_id UNIQUE, amount, status)`, `processed_event` (Kafka de-dup, Phase 11).
Flyway `V1`; Hibernate `validate`.

## Saga role (Phase 11)

Consumes `PAYMENT_REQUESTED`, emits `PAYMENT_INITIATED` / `PAYMENT_SUCCESS` / `PAYMENT_FAILED`;
consumes `REFUND_INITIATED`, emits `REFUND_COMPLETED`. `PaymentEventPublisher` currently logs the envelopes.
