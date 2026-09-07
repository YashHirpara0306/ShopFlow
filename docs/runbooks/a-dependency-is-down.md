# Runbook: a dependency is down (Postgres / Redis / Kafka / a downstream service)

| Down | What breaks | What the user sees | Recovery |
|---|---|---|---|
| **Postgres** (one service) | that service's writes/reads fail; Kafka handlers throw → retry → DLT (offset not committed) | that service's reads → `503`; in-flight orders sit in a non-terminal state | bring the DB back; un-committed offsets redeliver; DLT records can be re-driven; idempotent handlers make replay safe |
| **Redis** | Product reads fall back to Postgres (slower); **Cart is unavailable**; gateway rate-limiter errors | product pages slower; cart writes → `503`; existing carts lost only if Redis had no persistence | bring Redis back; product caches refill lazily; carts resume |
| **Kafka** | producers can't publish; the Order outbox fills; consumers stop | new orders sit in `PENDING`/`INVENTORY_PENDING` ("processing") | bring Kafka back; the outbox relay drains; consumers resume from last committed offset; the watchdog cancels anything stuck past 5 min |
| **A downstream service** (behind the gateway) | gateway routes fail | `503 DOWNSTREAM_UNAVAILABLE` with the standard error body | restart the service (K8s does this automatically); the Saga is event-driven so an order in progress resumes when the consumer is back |

## General checks
- `docker compose ps` — which containers are not `healthy`?
- `docker compose logs <service> --tail=100`
- Gateway `/actuator/health` aggregates its own; each service's `/actuator/health` shows DB/Redis/Kafka
  sub-checks.
- Nothing here corrupts data: every cross-service step is idempotent, and money is never captured for
  an order that is not `CONFIRMED`.
