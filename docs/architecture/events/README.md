# Event contracts

- **[event-catalog.md](event-catalog.md)** — every business event: topic, partition key, producer,
  consumers, trigger, payload schema, and a JSON example.
- The envelope (the fields wrapping every payload) is specified in
  [`../phase-01-requirements-and-architecture.md`](../phase-01-requirements-and-architecture.md#6-kafka-event-architecture)
  and implemented as `com.shopflow.common.event.EventEnvelope`.
- Event type names are the `com.shopflow.common.event.EventType` enum.

All events ship at `eventVersion: 1`. Versioning policy is in
[`../conventions.md`](../conventions.md#8-events-summary---full-catalog-in-eventsevent-catalogmd).
