# Failure scenarios (Phase 1 §25) — as implemented

For each: **what happens · what is retried · what is NOT retried · idempotent? · recovery · what the
user sees**. Runbooks in [`../runbooks/`](../runbooks/).

| Scenario | Behaviour |
|---|---|
| **Postgres down (a service)** | local step fails; Kafka handler throws → retry (1/2/4s) → `<topic>.DLT`, offset **not** committed. Not retried: the client's original HTTP call. Idempotent: yes (`processed_event` + state guards + unique keys). Recovery: DB back → un-committed offsets redeliver / re-drive DLT. User: reads → `503`; in-flight order stays non-terminal, then resolves or the watchdog cancels it. |
| **Kafka down** | producers can't publish; the Order **outbox** fills (rows with `published_at = NULL`); consumers stop. Retried: producer + outbox relay (every 1s). Idempotent: yes. Recovery: Kafka back → relay drains, consumers resume from last offset. User: order sits in `PENDING`/`INVENTORY_PENDING` ("processing"); resolves or watchdog-cancels. |
| **Redis down** | Product reads → DB fallback (logged, slower); **Cart unavailable** → `503`; gateway rate-limiter errors on that request. Not retried by the app for product reads (just uses the DB). Idempotent: n/a. Recovery: Redis back → caches refill lazily; carts resume. User: slower catalogue; cart write `503` "try again"; a cart may look empty if Redis had no persistence. |
| **Payment declined (simulated)** | `PAYMENT_FAILED` emitted. **Not retried** — a decline is a business outcome. Idempotent: yes (`payment.order_id` UNIQUE). Recovery: Order → `CANCELLED`, Inventory releases the reservation. User: "payment failed, order cancelled". |
| **Payment transient error** (not a decline) | handler throws → retry → DLT. Idempotent: yes. Recovery: fix cause → re-drive DLT → SUCCESS/FAILED flows on. User: order briefly `AWAITING_PAYMENT`. |
| **Inventory insufficient** | `INVENTORY_RESERVATION_FAILED` emitted; the partial reservation is rolled back (`REQUIRES_NEW` tx + `setRollbackOnly`). Not retried (business outcome). Idempotent: per `orderId`. Recovery: Order → `CANCELLED`, **no charge**. User: "out of stock, order cancelled". |
| **Duplicate Kafka event** (same `eventId`) | consumer's `processed_event` `existsById` check (same tx) → skip. Also state-machine guards ignore a valid event for the wrong status. Invisible to the user. |
| **Duplicate `POST /api/orders`** (same `Idempotency-Key`) | `idempotency_key` UNIQUE (id reserved before the order is created) → the original order is returned. Same `orderId` both times. |
| **Service timeout on a REST call** (Order→Cart, Cart→Product, Product→Inventory) | caller gets a timeout; `DownstreamUnavailableException` → `503`. Bounded retry is the client's job. The GET is naturally idempotent. Recovery: downstream heals. User: `POST /api/orders` → `503` "please retry"; **no order created** (the failure is before the DB write). |
| **Network partition (orchestrator ↔ Kafka)** | same as "Kafka down" from the orchestrator's side; outbox + relay + at-least-once cover it. |
| **Partial transaction (DB committed, event not published)** | **Order Service**: eliminated by the transactional outbox. **Inventory/Payment/Notification**: a small window; recovery = idempotent consumers + the watchdog cancelling a stalled order after 5 min. |
| **Consumer crashes mid-handler** | offset not committed (committed only after the handler returns) → Kafka redelivers to another instance after rebalance. Idempotent handler re-runs safely. Invisible. |
| **Poison message** (bad JSON / unknown type) | `EventJson.parse` throws `IllegalArgumentException` → classified non-retryable → straight to `<topic>.DLT`; the partition is not blocked. Recovery: inspect DLT, fix the producer, replay. |
| **Two requests reserve the last unit concurrently** | atomic `UPDATE stock SET available = available - :q WHERE available >= :q` — exactly one row updated. Loser → shortage → order `CANCELLED` cleanly. Verified by `InventoryConcurrencyIT` (16 threads → 1 winner, `available` ends at 0). |
| **Watchdog finds a stuck Saga** | `OrderWatchdog` scans non-terminal orders older than `shopflow.order.saga-timeout` (5 min) → `ORDER_CANCELLED` (reason `TIMEOUT`) → Inventory releases. Not retried forward. User: "order could not be completed". |

## Injecting each (for a demo)

- **Kafka / Redis / a service down**: `docker compose stop kafka` / `redis` / `payment-service`, exercise the flow, `docker compose start ...`.
- **Payment decline**: order a cart whose total ends in `.13`, or set `PAYMENT_FAIL_RATE=1.0`.
- **Inventory shortage**: order more than the stocked quantity.
- **Duplicate event**: `kafka-console-producer` the same envelope JSON twice.
- **Poison message**: `kafka-console-producer` invalid JSON to a topic; watch `<topic>.DLT`.
- **Stuck Saga**: `docker compose stop inventory-service`, place an order, wait 5 min for the watchdog.
