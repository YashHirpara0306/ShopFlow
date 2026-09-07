# Kafka & the Order Saga (as implemented, Phase 11)

## Topics

4 business topics (3 partitions each) + a `.DLT` per topic. Key = `orderId` for
order/inventory/payment events, `userId` for notification events. Created on startup by
`NewTopic` beans in `shopflow-common`.

| Topic | Produced by | Consumed by (group) |
|---|---|---|
| `order-events` | order-service (outbox) | inventory-service (`inventory-svc`), notification-service (`notification-svc`) |
| `inventory-events` | inventory-service | order-service (`order-svc`) |
| `payment-events` | order-service (outbox, `PAYMENT_REQUESTED`), payment-service | payment-service (`payment-svc`), order-service (`order-svc`), notification-service (`notification-svc`) |
| `notification-events` | user-service, (ad-hoc) | notification-service (`notification-svc`) |

## The Saga (orchestration; Order Service is the orchestrator)

```
POST /api/orders
  └─ tx: insert order PENDING + history + idempotency_key + outbox(ORDER_CREATED); advance to INVENTORY_PENDING
  └─ 202 Accepted
OutboxRelay ──▶ order-events: ORDER_CREATED

inventory-svc  consumes ORDER_CREATED
  ├─ reserve (atomic, all-or-nothing, idempotent per orderId)
  ├─ ok   ──▶ inventory-events: INVENTORY_RESERVED
  └─ short──▶ inventory-events: INVENTORY_RESERVATION_FAILED

order-svc      consumes INVENTORY_RESERVED         (guard: status == INVENTORY_PENDING)
  └─ tx: status → AWAITING_PAYMENT; outbox(PAYMENT_REQUESTED)  ──▶ payment-events
order-svc      consumes INVENTORY_RESERVATION_FAILED
  └─ tx: status → CANCELLED (OUT_OF_STOCK); outbox(ORDER_CANCELLED)   [no charge was made]

payment-svc    consumes PAYMENT_REQUESTED          (idempotent per orderId + processed_event)
  ├─ captured ──▶ payment-events: PAYMENT_SUCCESS
  └─ declined ──▶ payment-events: PAYMENT_FAILED

order-svc      consumes PAYMENT_SUCCESS             (guard: status == AWAITING_PAYMENT)
  └─ tx: status → CONFIRMED; outbox(ORDER_CONFIRMED)  ──▶ order-events
order-svc      consumes PAYMENT_FAILED
  └─ tx: status → CANCELLED (PAYMENT_DECLINED); outbox(ORDER_CANCELLED)

inventory-svc  consumes ORDER_CONFIRMED  → confirm reservation (deduct on-hand)
inventory-svc  consumes ORDER_CANCELLED  → release reservation (compensation) ──▶ INVENTORY_RELEASED
notification-svc consumes ORDER_CONFIRMED / ORDER_CANCELLED / PAYMENT_FAILED / REFUND_COMPLETED / USER_REGISTERED
```

Inventory is reserved **before** payment, so "charged but no stock" cannot happen; a payment
failure only needs the reservation released.

## Idempotency

- **HTTP:** `Idempotency-Key` header → `idempotency_key` table (Order Service).
- **Consumers:** `processed_event(event_id PK)` per service, written in the handler transaction
  (`existsById` + `save`, no flush). A duplicate delivery is a clean no-op.
- **State guards:** an outcome event for the wrong current status is logged and ignored, so late or
  re-ordered events (across partitions) are harmless.
- **Domain:** `payment.order_id`, `inventory_reservation.order_id`, `notification(event_id, channel)`
  are UNIQUE - a second attempt returns the first result.

## Delivery, retry, DLT

At-least-once. `enable.auto.commit=false`, offset committed after each successful handler. On a
handler exception: retry 1s/2s/4s (15s cap), then the record is published to `<topic>.DLT` (same
partition) with the exception in the headers; the main partition is not blocked. A malformed
envelope is non-retryable → DLT immediately.

## Dual-write handling

- **Order Service:** transactional **outbox** (`outbox_event` + `OutboxRelay`) — the state change
  and the event enqueue commit together; the relay publishes. No gap.
- **Inventory / Payment / Notification:** publish directly after commit — a small window where a
  crash loses the event. Recovery: idempotent consumers + the **watchdog** (`OrderWatchdog`) cancels
  an order stuck pre-confirmation past `shopflow.order.saga-timeout` (5 min), running the normal
  compensation.

## Running with Kafka

Kafka wiring activates only when `SPRING_KAFKA_BOOTSTRAP_SERVERS` is set (env var → relaxed binding
→ `spring.kafka.bootstrap-servers`). Unset (local dev, most integration tests) → the services fall
back to `Logging*EventPublisher` and register no listeners. `docker compose` (Phase 16) sets it to
`kafka:9092`. The Saga integration test (`OrderSagaOrchestratorIT`) sets it via a Testcontainers
Kafka container.

## Debugging a failed order

1. Get the `correlationId` from the client's `X-Correlation-Id` response header (or the order's
   `order_status_history`).
2. `grep` that id across all services' logs — it is on every line and every event envelope.
3. `order_status_history` shows every transition + reason. `processed_event` shows what each service
   consumed. `outbox_event` shows what Order emitted and whether it was published.
4. Check `<topic>.DLT` for stuck messages.
