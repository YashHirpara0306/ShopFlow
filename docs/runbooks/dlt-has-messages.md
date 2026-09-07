# Runbook: a dead-letter topic has messages

## Detect
- Grafana: consumer lag on a `*.DLT` topic, or an `ERROR` log line "publishing to DLT".
- `docker compose exec kafka /opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell \
   --broker-list localhost:9092 --topic order-events.DLT` — non-zero end offsets.

## Inspect
```
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic order-events.DLT \
  --from-beginning --property print.headers=true
```
Headers include `kafka_dlt-original-topic`, `-partition`, `-offset`, and
`kafka_dlt-exception-message` / `-stacktrace`.

## Classify
- **Malformed envelope** (`IllegalArgumentException: Malformed event envelope`) → a producer bug or a
  schema mismatch. Fix the producer; the DLT record is usually not worth replaying.
- **Transient** (DB deadlock, downstream timeout) → the dependency was down; safe to replay.
- **Poison business case** (e.g. an event referencing a deleted aggregate) → decide per case.

## Re-drive
There is no automated re-drive in this project. Manually:
```
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic order-events.DLT --from-beginning --max-messages N \
| docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic order-events
```
Consumers de-duplicate on `eventId`, so replaying an already-processed event is a no-op.

## Prevent
- Alert on any `*.DLT` end offset > 0.
- Validate event payloads at the producer.
