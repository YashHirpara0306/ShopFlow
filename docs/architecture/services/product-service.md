# Product Service

Port `8082` · database `shopflow_product` (PostgreSQL) · Redis cache · [Swagger UI](http://localhost:8082/swagger-ui.html)

## Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/products?page=&size=&sort=&category=&minPrice=&maxPrice=&q=` | public | Active products, paged/sorted/filtered. `PageResponse<ProductResponse>`. |
| `GET` | `/api/products/{id}` | public | One active product (cache-aside). `404` if inactive/missing. |
| `POST` | `/api/products` | `ADMIN` | Create. Validates category, unique SKU (`409`), calls Inventory (REST, ADR-0005). `201`. |
| `PUT` | `/api/products/{id}` | `ADMIN` | Partial update; **evicts the cache**. |
| `DELETE` | `/api/products/{id}` | `ADMIN` | Soft delete (`active=false`); evicts the cache. `204`. |
| `GET` | `/api/categories` | public | All categories. |
| `POST` | `/api/categories` | `ADMIN` | Create (slug derived from name; unique `409`). `201`. |

## Caching (`ProductCache`)

- Key `product:{id}`, JSON value, TTL `shopflow.cache.product-ttl` (default `10m`).
- **Cache-aside**: read → miss → DB → `put`. Writes **`evict`** (delete, not update).
- Hit/miss exposed as `shopflow_product_cache_total{result="hit|miss"}` counters.
- Redis down → degrades to DB-only reads (logged), never an error. Only *active* products are cached.
- Listings are **not** cached (too many filter combinations - see conventions §4).

## Model

- `category(id, name, slug UNIQUE, created_at)`
- `product(id, sku UNIQUE, name, description, price NUMERIC(12,2), currency, category_id FK, image_url, active, created_at, updated_at, version)` — indexes on `category_id`, `active`, `lower(name)`, `price`.

Flyway `V1__init_product_schema.sql`; Hibernate `validate`. `@ManyToOne(LAZY)` category + `@EntityGraph` on reads (no N+1).

## Config

`shopflow.jwt.secret`, `shopflow.cache.product-ttl`, `spring.data.redis.*`.
