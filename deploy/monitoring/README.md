# Monitoring

Populated in **Phase 17**: `prometheus/prometheus.yml` scrape config and `grafana/dashboards/*.json`
(HTTP rate/latency/errors, order count, payment success/failure, inventory reservation failures,
Kafka consumer lag/errors). Prometheus + Grafana are added to `docker-compose.yml` in the same phase.
