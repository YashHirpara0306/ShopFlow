# Inventory Service

Port `8085` · database `shopflow_inventory` (PostgreSQL) · admin/internal only · [Swagger UI](http://localhost:8085/swagger-ui.html)

From Phase 11 the Saga drives reserve/release/confirm over Kafka; the REST endpoints stay for admin use and tests.

## Endpoints (all `ROLE_ADMIN`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/inventory/stock` | Create/set a product's on-hand quantity. **Product Service calls this on product creation** (ADR-0005), forwarding the admin token. `201`. |
| `PUT` | `/api/inventory/{productId}/adjustment` | Adjust by a signed `delta` (audited). |
| `GET` | `/api/inventory/{productId}` | Stock: `{total, available, reserved}`. |
| `POST` | `/api/inventory/reservations` | `{orderId, lines}` — **all-or-nothing**, **idempotent per orderId**. Shortage → `409` (rolls back). |
| `POST` | `/api/inventory/reservations/{orderId}/release` | Compensation: `available += qty`, `reserved -= qty`. Idempotent. |
| `POST` | `/api/inventory/reservations/{orderId}/confirm` | `total -= qty`, `reserved -= qty`. Idempotent. |

## Concurrency — oversell prevention

Reserve/release/confirm are **atomic conditional SQL UPDATEs**, e.g.

```sql
UPDATE stock SET available = available - :q, reserved = reserved + :q
 WHERE product_id = :id AND available >= :q
```

Concurrent transactions serialize on the row lock and re-check `available >= :q`; the losers update
0 rows and their reservation fails. `InventoryConcurrencyIT` fires 16 threads at the last unit and
asserts exactly one wins, `available` ends at 0. `@Version` on `Stock` covers the admin set/adjust path.

## Model

- `stock(product_id PK, total, available, reserved, version)` — CHECK constraints keep all `>= 0`.
- `inventory_reservation(id, order_id UNIQUE, status RESERVED|RELEASED|CONFIRMED)` + `reservation_item(reservation_id, product_id, quantity)`.
- `stock_adjustment_audit`, `processed_event` (Kafka de-dup, Phase 11).

Flyway `V1`; Hibernate `validate`.
