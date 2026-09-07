# Order Service

Port `8084` · database `shopflow_order` (PostgreSQL) · [Swagger UI](http://localhost:8084/swagger-ui.html)

Owns the order aggregate, the lifecycle state machine, and (from Phase 11) the Saga orchestrator.

## Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/orders` | customer | Create from the caller's cart. **`Idempotency-Key` header required.** Returns **`202`** + `PENDING` order; poll `GET` for the final status. |
| `GET` | `/api/orders/{id}` | owner or `ADMIN` | Order + full status history. `404` (not `403`) for a non-owner. |
| `GET` | `/api/orders?status=&userId=&page=&size=&sort=` | customer / `ADMIN` | Customer sees only their own; admin may filter by `userId` / `status`. |
| `POST` | `/api/orders/{id}/cancellation` | owner or `ADMIN` | Cancel while `PENDING` / `INVENTORY_PENDING` / `AWAITING_PAYMENT`; else `409`. |
| `PUT` | `/api/orders/{id}/status` | `ADMIN` | Force a transition (drives the state machine). **Phase 11 replaces this with the Kafka Saga.** |

## Lifecycle ([ADR-0002](../adr/0002-saga-orchestration.md), Phase 1 §9)

```
PENDING -> INVENTORY_PENDING -> AWAITING_PAYMENT -> CONFIRMED -> REFUNDED
   \_____________ \______________ \______________> CANCELLED
any state ------------------------------------------> FAILED
```

`OrderStateMachine` (static, no Spring) is the single source of legal transitions; `Order.transitionTo`
guards every change so the aggregate can't reach an illegal state. Every transition writes an
`order_status_history` row (from, to, reason, correlationId, timestamp) — the debugging tool.

## Idempotent creation

Client sends `Idempotency-Key`. Order Service reserves the order id, then `saveAndFlush` an
`idempotency_key` row (`UNIQUE`) *before* creating the order. Replay with the same key + same request
→ the original order (`200`); same key + different request → `409 IDEMPOTENCY_CONFLICT`; a lost race
on the unique constraint → replay path.

## Model

`orders`, `order_item` (price/name snapshot), `order_status_history` (identity PK, append-only),
`idempotency_key` (`idem_key` UNIQUE), `processed_event` (Kafka de-dup, from Phase 11).
Flyway `V1`; Hibernate `validate`.

## Not yet wired (Phase 11)

Kafka producers/consumers, the orchestrated Saga (reserve → pay → confirm/compensate), the stuck-Saga
watchdog, and the transactional outbox. `OrderEventPublisher` currently logs the envelopes.
