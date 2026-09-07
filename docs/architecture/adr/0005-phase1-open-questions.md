# ADR-0005: Resolutions to the six Phase 1 open questions

**Status:** Accepted (Phase 2)

The Phase 1 document left six decisions open. They are resolved as follows.

## 1. `PRODUCT_CREATED` transport

**Decision:** No dedicated Kafka topic. When an admin creates a product, Product Service makes a
synchronous REST call to Inventory Service to create the stock row (quantity 0). Rationale: an
immediate, one-to-one, request/response interaction — REST fits; a topic would be overkill.
A `PRODUCT_CREATED` event may still be emitted later for other consumers (e.g. search), but it is
not on the critical path for inventory.

## 2. Transactional outbox

**Decision:** Adopt the outbox pattern **only in Order Service**, introduced in **Phase 11**. Order
Service writes the domain change and an `outbox` row in the same local transaction; a relay
publishes outbox rows to Kafka. Other services rely on producer retries + the Saga watchdog, and
this limitation is documented. Rationale: the dual-write risk matters most where money and the Saga
start; adding outbox everywhere is disproportionate machinery for a portfolio project.

## 3. Order creation client experience

**Decision:** `POST /api/orders` returns **`202 Accepted`** with `status: PENDING` immediately. The
client polls `GET /api/orders/{id}` to observe `CONFIRMED` / `CANCELLED`. Rationale: honest
representation of an asynchronous multi-service Saga; does not tie an HTTP thread to a distributed flow.

## 4. Kafka in tests / CI

**Decision:** **Testcontainers Kafka** for end-to-end Saga integration tests; the lightweight
`spring-kafka-test` embedded broker for narrow producer/consumer unit tests. Rationale: the Saga
tests must exercise the real broker semantics (partitions, offsets, redelivery); narrow tests do not
need that cost.

## 5. Product images / S3

**Decision:** Product has a minimal `imageUrl` field. An optional `POST /api/products/{id}/image`
uploads to S3 (MinIO locally). No media pipeline (no resizing/CDN). Rationale: enough to justify and
demonstrate S3 + IAM without scope creep.

## 6. Overall scope

**Decision:** Keep all 8 services. Kubernetes and AWS are delivered as **written manifests +
validation on a local cluster (kind/minikube) + one AWS dry-run**, not an always-on cloud
deployment. Rationale: demonstrates the skills and keeps running cost at zero.
