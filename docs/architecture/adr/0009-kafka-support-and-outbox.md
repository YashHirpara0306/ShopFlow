# ADR-0009: Shared Kafka support in shopflow-common; transactional outbox in Order Service

**Status:** Accepted (Phase 11)

## Context

Six services now produce and/or consume events. They need a consistent producer/consumer setup,
consumer idempotency, dead-lettering, and a story for the "DB committed but event not published"
dual-write gap.

## Decision

**`shopflow-common` (`com.shopflow.common.kafka` + `KafkaSupportAutoConfiguration`)**, active only
when `spring.kafka.bootstrap-servers` is set:

- `EventJson` (envelope <-> JSON), `DomainEventPublisher` (send with correlation header),
  `InboundEvent` (parsed, tolerant-reader view), `ProcessedEventStore` (interface),
  `Topics` (the 4 names + `.DLT`).
- A `KafkaTemplate<String,String>` from an **idempotent, `acks=all`** producer.
- A listener factory: **RECORD ack** (commit after each handler), retry with exponential backoff
  (1s/2s/4s, 15s cap), then `DeadLetterPublishingRecoverer` -> `<topic>.DLT`; a malformed envelope
  (`IllegalArgumentException`) is non-retryable and goes straight to the DLT.
- `NewTopic` beans create the 4 topics (3 partitions) + 4 DLTs.

**Consumer idempotency:** each service has a `processed_event(event_id PK)` table and a
`JpaProcessedEventStore` that does `existsById` then `save` (no flush) **inside the handler's
transaction** - the marker and the side effects commit together.

**Transactional outbox - Order Service only:** an `outbox_event` table; `OutboxWriter.enqueue`
writes the envelope in the same transaction as the order state change; `OutboxRelay` (`@Scheduled`,
1s) publishes unpublished rows. All four Order events (`ORDER_CREATED`, `PAYMENT_REQUESTED`,
`ORDER_CONFIRMED`, `ORDER_CANCELLED`) go through it.

Inventory / Payment / Notification publish **directly** after their DB commit.

**Watchdog:** `OrderWatchdog` (`@Scheduled`) cancels orders stuck pre-confirmation past
`shopflow.order.saga-timeout` (5m), which triggers the normal compensation path.

## Consequences

**Positive**
- One Kafka setup; DLT + backoff + idempotency come for free per service.
- Order Service - the orchestrator, where correctness matters most and where the synchronous HTTP
  path emits the first event - has no dual-write gap.
- The whole Saga is inspectable: `order_status_history`, `outbox_event`, `processed_event`, the DLTs.

**Negative / accepted trade-offs**
- Inventory / Payment / Notification retain a small dual-write window: DB commits, then the publish
  fails -> the event is lost. Recovery: consumers are idempotent, and the Order watchdog cancels a
  Saga that stalls because a hop's event went missing. Full outbox everywhere is the follow-up.
- The outbox relay can double-publish a row if it crashes between `send` and `markPublished`;
  consumers de-duplicate on `eventId`, so this is harmless.
- `RECORD` ack + at-least-once means every handler must be idempotent - enforced by `processed_event`
  and the order state-machine guards (a late/duplicate outcome for the wrong state is ignored).
