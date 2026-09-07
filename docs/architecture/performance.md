# Performance

> Measure before claiming. This document lists the deliberate choices; numbers must come from a
> load test on real infrastructure, not from a laptop.

## Database

- **Indexes** (in the Flyway migrations), each backing a real query:
  - `product`: `lower(name)` (search), `(category_id)`, `(active)`, `(price)` (filters/sort)
  - `orders`: `(user_id, created_at DESC)` (a customer's order list), `(status)` (admin + watchdog)
  - `refresh_token`: `(token_hash)` UNIQUE, `(user_id)`, `(family_id)`
  - `order_status_history`, `reservation_item`, `notification_attempt`: `(<parent>_id)`
  - `outbox_event`: partial index `WHERE published_at IS NULL` (the relay only reads unpublished)
- **N+1 avoidance**: `@ManyToOne(LAZY)` + `@EntityGraph(attributePaths = ...)` on the read methods
  that map to a DTO (`product.findWithCategoryById`, `product.findAll(spec, pageable)`,
  `order.findWithItemsById`, `reservation.findByOrderId`). `open-in-view=false` everywhere so a
  missed fetch fails loudly in a test instead of silently issuing queries in the view layer.
- **Pagination is mandatory** on list endpoints; `size` is clamped to 100.
- **Connection pool**: HikariCP (Boot default). Size for `~ (core_count * 2) + effective_spindle`;
  in K8s set `spring.datasource.hikari.maximum-pool-size` per replica so
  `replicas * pool_size < max_connections` on the DB.
- **Money** is `NUMERIC(12,2)` / `BigDecimal` — never `double`.

## Caching

- Product detail: cache-aside, 10 min TTL **± 10 % jitter** (stampede), delete-on-write.
- Listings are **not** cached (filter-combination cardinality). Cart is Redis-native, not a cache.
- Hit/miss are metered (`shopflow_product_cache_total{result}`) so the hit ratio is observable.

## Kafka

- 3 partitions per topic → up to 3 concurrent consumers per group; key = `orderId` keeps a single
  order's events ordered while different orders parallelise.
- `RECORD` ack (commit per record) trades a little throughput for simple, precise redelivery.
- Idempotent producer (`enable.idempotence=true`, `acks=all`) — no duplicate-from-retry, no silent loss.

## HTTP

- The gateway is non-blocking (Netty); services are blocking MVC (Tomcat) — fine at this scale,
  thread-per-request is the ceiling.
- `202 Accepted` on order creation: the HTTP thread is freed immediately; the Saga runs async.

## Load test (skeleton)

`scripts/load/` has k6 scripts for the two hot paths. Run against a deployed stack and record the
numbers here:

```bash
k6 run -e GATEWAY=https://<host> scripts/load/browse-products.js
k6 run -e GATEWAY=https://<host> scripts/load/place-order.js
```

| Scenario | VUs | p50 | p95 | error rate | notes |
|---|---|---|---|---|---|
| GET /api/products (cached) | _tbd_ | _tbd_ | _tbd_ | _tbd_ | fill in after a real run |
| POST /api/orders (202) → CONFIRMED | _tbd_ | _tbd_ | _tbd_ | _tbd_ | measure accept latency and Saga completion time separately |

## Not done (premature)

No second-level cache, no read replicas, no CQRS, no Kafka Streams. Add only with a measurement
that justifies it.
