# Cart Service

Port `8083` · **Redis only, no database** (ADR-0004) · [Swagger UI](http://localhost:8083/swagger-ui.html)

Every endpoint requires an authenticated user; the cart is keyed by the caller's id.

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/cart` | The caller's priced cart (`CartResponse`). Stale/removed products are pruned on read. |
| `POST` | `/api/cart/items` | `{productId, quantity}` — validates the product via Product Service, then `HINCRBY` (clamped to 99). |
| `PUT` | `/api/cart/items/{productId}` | `{quantity}` — set exact quantity; `0` removes; `404` if the line isn't in the cart. |
| `DELETE` | `/api/cart/items/{productId}` | Remove a line. |
| `DELETE` | `/api/cart` | Clear the cart. `204`. |

## Storage (`RedisCartStore`)

- Key `cart:{userId}` — a Redis **hash**, field = productId, value = quantity.
- Sliding TTL `shopflow.cart.ttl` (default `14d`), refreshed on every mutation.
- Redis unreachable → `503 DOWNSTREAM_UNAVAILABLE` (there is no DB to fall back to).
- `CartService` depends on the `CartStore` interface (testable without a broker); `RedisCartStore` is the impl.

## Pricing

`getCart` calls Product Service `GET /api/products/{id}` per line (few items; Product Service caches).
Products that 404 / are inactive are dropped and pruned from the hash.

## Config

`shopflow.jwt.secret`, `shopflow.cart.ttl`, `shopflow.cart.max-quantity-per-line`,
`shopflow.clients.product-service.base-url`, `spring.data.redis.*`.
