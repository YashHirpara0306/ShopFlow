# Event Catalog (v1)

Every message on every topic is an `EventEnvelope<T>`:

```json
{
  "eventId": "b2c1d0e9-...-uuid",
  "eventType": "ORDER_CREATED",
  "eventVersion": 1,
  "timestamp": "2026-01-01T10:00:00Z",
  "correlationId": "9f1c2a4e-...-uuid",
  "source": "order-service",
  "payload": { }
}
```

Only the **`payload`** differs per event and is documented below. Field meanings for the envelope
itself are in Phase 1 §6.

## Topics at a glance

| Topic | Partitions | Key | Events carried |
|---|---|---|---|
| `order-events` | 3 | `orderId` | `ORDER_CREATED`, `ORDER_CONFIRMED`, `ORDER_CANCELLED` |
| `inventory-events` | 3 | `orderId` | `INVENTORY_RESERVED`, `INVENTORY_RESERVATION_FAILED`, `INVENTORY_RELEASED` |
| `payment-events` | 3 | `orderId` | `PAYMENT_REQUESTED`, `PAYMENT_INITIATED`, `PAYMENT_SUCCESS`, `PAYMENT_FAILED`, `REFUND_INITIATED`, `REFUND_COMPLETED` |
| `notification-events` | 3 | `userId` | `USER_REGISTERED`, `NOTIFICATION_REQUESTED` |

`PRODUCT_CREATED` is **not** on a Kafka topic (ADR-0005): product creation calls Inventory over REST.
The enum value exists for a possible future search/catalog consumer.

Each topic has a companion `<topic>.DLT` for messages that exhaust retries.

Money fields are decimal strings with 2 places. All timestamps are ISO-8601 UTC.

---

## order-events

### ORDER_CREATED
- **Producer:** order-service — after the pending order row is committed.
- **Consumers:** inventory-service (attempt reservation), notification-service (optional "order received").
- **Key:** `orderId`

```json
{
  "orderId": "3f0d8b2a-1c4e-4a9b-8e21-9c7d6b5a4f30",
  "userId": "a17c9e50-2b6f-4d18-9a3c-1e2d3f4a5b6c",
  "createdAt": "2026-01-01T10:00:00Z",
  "currency": "INR",
  "totalAmount": "1298.00",
  "lines": [
    { "productId": "8d1f...-uuid", "sku": "BOOK-DDD-01", "name": "Domain-Driven Design", "unitPrice": "649.00", "quantity": 2 }
  ]
}
```

### ORDER_CONFIRMED
- **Producer:** order-service — on `PAYMENT_SUCCESS`, after status -> `CONFIRMED`.
- **Consumers:** inventory-service (convert reservation to committed), notification-service (confirmation message).

```json
{ "orderId": "3f0d8b2a-...", "userId": "a17c9e50-...", "confirmedAt": "2026-01-01T10:00:07Z", "paymentId": "6a2b...-uuid", "totalAmount": "1298.00" }
```

### ORDER_CANCELLED
- **Producer:** order-service — on reservation failure, payment failure, customer cancel, or watchdog timeout.
- **Consumers:** inventory-service (release reservation if one was held — compensation), notification-service.

```json
{ "orderId": "3f0d8b2a-...", "userId": "a17c9e50-...", "cancelledAt": "2026-01-01T10:00:05Z", "reason": "PAYMENT_DECLINED", "inventoryWasReserved": true }
```
`reason` ∈ `OUT_OF_STOCK | PAYMENT_DECLINED | CUSTOMER_CANCELLED | TIMEOUT`.

---

## inventory-events

### INVENTORY_RESERVED
- **Producer:** inventory-service — after atomically decrementing available stock for every line.
- **Consumers:** order-service (advance Saga -> request payment).

```json
{
  "orderId": "3f0d8b2a-...",
  "reservationId": "c4e5...-uuid",
  "reservedAt": "2026-01-01T10:00:02Z",
  "lines": [ { "productId": "8d1f...-uuid", "quantity": 2 } ]
}
```

### INVENTORY_RESERVATION_FAILED
- **Producer:** inventory-service — at least one line could not be satisfied; nothing was reserved (all-or-nothing).
- **Consumers:** order-service (fail Saga -> cancel order, no charge).

```json
{
  "orderId": "3f0d8b2a-...",
  "failedAt": "2026-01-01T10:00:02Z",
  "shortages": [ { "productId": "8d1f...-uuid", "requested": 2, "available": 1 } ]
}
```

### INVENTORY_RELEASED
- **Producer:** inventory-service — on `ORDER_CANCELLED` after a reservation existed (compensation), or on TTL expiry of a stale reservation.
- **Consumers:** order-service (audit / Saga completion).

```json
{ "orderId": "3f0d8b2a-...", "reservationId": "c4e5...-uuid", "releasedAt": "2026-01-01T10:00:06Z", "reason": "ORDER_CANCELLED" }
```

---

## payment-events

### PAYMENT_REQUESTED
- **Producer:** order-service (the orchestrator) — on `INVENTORY_RESERVED`. A command-style event: it
  tells Payment to act. Kept on `payment-events` to avoid a separate command topic.
- **Consumers:** payment-service.

```json
{ "orderId": "3f0d8b2a-...", "userId": "a17c9e50-...", "amount": "1298.00", "currency": "INR", "requestedAt": "2026-01-01T10:00:03Z" }
```

### PAYMENT_INITIATED
- **Producer:** payment-service — accepted the request, created a `PENDING` payment row.
- **Consumers:** (none required) — audit / metrics.

```json
{ "orderId": "3f0d8b2a-...", "paymentId": "6a2b...-uuid", "amount": "1298.00", "initiatedAt": "2026-01-01T10:00:04Z" }
```

### PAYMENT_SUCCESS
- **Producer:** payment-service — simulated authorisation succeeded; payment row -> `CAPTURED`.
- **Consumers:** order-service (advance Saga -> confirm), notification-service.

```json
{ "orderId": "3f0d8b2a-...", "paymentId": "6a2b...-uuid", "amount": "1298.00", "capturedAt": "2026-01-01T10:00:05Z" }
```

### PAYMENT_FAILED
- **Producer:** payment-service — simulated decline or non-retryable error; payment row -> `FAILED`.
- **Consumers:** order-service (compensate -> release inventory, cancel order), notification-service.

```json
{ "orderId": "3f0d8b2a-...", "paymentId": "6a2b...-uuid", "amount": "1298.00", "failedAt": "2026-01-01T10:00:05Z", "reason": "DECLINED" }
```
`reason` ∈ `DECLINED | INSUFFICIENT_FUNDS | SIMULATED_ERROR`.

### REFUND_INITIATED
- **Producer:** order-service — admin-triggered refund of a `CONFIRMED` order.
- **Consumers:** payment-service.

```json
{ "orderId": "3f0d8b2a-...", "paymentId": "6a2b...-uuid", "amount": "1298.00", "initiatedAt": "2026-01-02T09:00:00Z", "reason": "CUSTOMER_REQUEST" }
```

### REFUND_COMPLETED
- **Producer:** payment-service — simulated refund done; refund row -> `COMPLETED`.
- **Consumers:** order-service (order -> `REFUNDED`), notification-service.

```json
{ "orderId": "3f0d8b2a-...", "paymentId": "6a2b...-uuid", "refundId": "9f7e...-uuid", "amount": "1298.00", "completedAt": "2026-01-02T09:00:03Z" }
```

---

## notification-events

### USER_REGISTERED
- **Producer:** user-service — after a user row is committed.
- **Consumers:** notification-service (welcome message).
- **Key:** `userId`

```json
{ "userId": "a17c9e50-...", "email": "jordan@example.com", "displayName": "Jordan", "registeredAt": "2026-01-01T09:55:00Z" }
```

### NOTIFICATION_REQUESTED
- **Producer:** any service needing an ad-hoc message not covered by a domain event.
- **Consumers:** notification-service.
- **Key:** `userId`

```json
{
  "userId": "a17c9e50-...",
  "channel": "EMAIL",
  "template": "PASSWORD_CHANGED",
  "data": { "changedAt": "2026-01-03T12:00:00Z" }
}
```
`channel` ∈ `EMAIL | SMS`.

---

## Consumer expectations (all events)

1. **Idempotent:** de-duplicate on `eventId` via `processed_event`; also guard on aggregate state.
2. **At-least-once:** you will occasionally see an event twice or slightly out of order across
   partitions (never out of order *within* one `orderId`).
3. **Retry then DLT:** transient failures retry with backoff; exhausted or non-retryable -> `<topic>.DLT`
   with headers `x-original-topic`, `x-exception-message`, `x-attempts`.
4. **Correlation:** copy `correlationId` from the envelope into the MDC before processing so logs join up.
