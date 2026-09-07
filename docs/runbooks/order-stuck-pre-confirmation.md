# Runbook: an order is stuck in PENDING / INVENTORY_PENDING / AWAITING_PAYMENT

## Symptoms
`GET /api/orders/{id}` keeps returning a non-terminal status well past a few seconds.

## Triage
1. `order_status_history` for the order — what was the last transition and `created_at`?
2. `outbox_event` (order DB) — is there a row whose `published_at` is NULL?
   - **NULL** → the relay is not publishing. Check `OrderRelay` logs / that `SPRING_KAFKA_BOOTSTRAP_SERVERS`
     is set / Kafka is reachable.
   - **set** → the event was published; the problem is downstream.
3. `processed_event` in the *next* service's DB — did it consume the event?
   - **no** → that consumer is down or lagging (`kafka_consumer_fetch_manager_records_lag` in Grafana),
     or the record is in `<topic>.DLT`.
   - **yes** but no state change → check that service's logs for a guard/skip or an exception.
4. Check `<topic>.DLT` (see the DLT runbook).

## Resolution
- Bring the down consumer back — at-least-once redelivery + `processed_event` make replay safe.
- Re-drive a DLT record onto its source topic once the cause is fixed.
- Otherwise `OrderWatchdog` cancels the order after `shopflow.order.saga-timeout` (default 5 min),
  emitting `ORDER_CANCELLED` so Inventory releases any reservation; the customer sees `CANCELLED`.

## Prevent
- Keep all consumers running (K8s handles restarts).
- The dual-write window on Inventory/Payment/Notification is the usual root cause of a *lost* event;
  the follow-up is an outbox in those services too (ADR-0009).
