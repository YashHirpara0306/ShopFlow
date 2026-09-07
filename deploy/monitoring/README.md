# Monitoring

Brought up by `docker compose up` alongside the services.

- **Prometheus** — `http://localhost:9090`. Config: `prometheus/prometheus.yml` (scrapes every
  service's `/actuator/prometheus` every 10s, tagged `service`).
- **Grafana** — `http://localhost:3001` (anonymous viewer enabled; admin `admin`/`admin`).
  Auto-provisions the Prometheus datasource (`grafana/provisioning/datasources`) and the
  **ShopFlow Overview** dashboard (`grafana/dashboards/shopflow-overview.json`,
  loaded via `grafana/provisioning/dashboards`).

Dashboard panels: HTTP request rate, 5xx rate, average latency, JVM heap, Kafka consumer lag,
product cache hit/miss. See [`../../docs/architecture/observability.md`](../../docs/architecture/observability.md).
