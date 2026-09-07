# Observability

## Logs

- SLF4J → Logback. `shopflow-common/logback-common.xml` (included by each service's
  `logback-spring.xml`). Human-readable locally; **JSON** (`LogstashEncoder`) under the
  `json` / `docker` / `prod` profile - `docker compose` runs services with `SPRING_PROFILES_ACTIVE=docker`.
- Every line carries MDC `correlationId` and (when authenticated) `userId`.
- Levels: `ERROR` = needs attention (unexpected 500, exhausted retries, DLT publish);
  `WARN` = handled but notable (4xx, compensation triggered, reuse detected);
  `INFO` = business milestones (order created, payment captured, event consumed).

## Metrics

- Micrometer → `/actuator/prometheus` on every service (only `health`, `info`, `prometheus` are exposed).
- Prometheus (`deploy/monitoring/prometheus/prometheus.yml`) scrapes all 8 services every 10s,
  tagged `service=<name>`.
- Grafana (`http://localhost:3001`, anon access on) auto-provisions the datasource and the
  **ShopFlow Overview** dashboard: HTTP request rate, 5xx rate, average latency, JVM heap,
  Kafka consumer lag, product cache hit/miss.
- Useful series out of the box: `http_server_requests_seconds_{count,sum}` (add
  `management.metrics.distribution.percentiles-histogram.http.server.requests=true` for true p95),
  `kafka_consumer_fetch_manager_records_lag`, `jvm_*`, `hikaricp_connections_*`,
  `shopflow_product_cache_total{result}`.

## Tracing

Correlation IDs (not full distributed tracing yet). The gateway sets `X-Correlation-Id`; it flows
through every HTTP hop (header) and every Kafka event (envelope field + header) and appears on every
log line. OpenTelemetry export is a Phase-17-follow-up (Micrometer Tracing + an OTLP collector).

## Debugging a failed order (the walkthrough)

1. **Get the id.** The client's `X-Correlation-Id` response header, or
   `GET /api/orders/{id}` → the `history` array (each transition has a `reason`), or query
   `order_status_history` directly.
2. **One query, all services.** `docker compose logs | grep <correlationId>` (or one Loki/Grafana
   query). Because the id is on every line and every event, this reconstructs the whole timeline.
3. **Where did it stop?** `order_status_history` shows the last transition and why. If it is stuck in
   `INVENTORY_PENDING` / `AWAITING_PAYMENT`, an event was lost or a consumer is down.
4. **Per-service state:**
   - `processed_event` (each DB) - did the service consume the event?
   - `outbox_event` (order DB) - did Order emit it, and is `published_at` set?
   - `inventory_reservation` / `payment` / `notification` rows - what does each service think happened?
5. **Dead letters.** `docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh
   --bootstrap-server localhost:9092 --topic order-events.DLT --from-beginning` (and the other
   `*.DLT` topics). Headers carry the original topic/partition/offset and the exception.
6. **Recovery.** Fix the cause, re-drive the DLT record, or wait for `OrderWatchdog` (5 min) to
   cancel the stuck order and run compensations.

See [`../runbooks/`](../runbooks/) for scenario-specific steps.
