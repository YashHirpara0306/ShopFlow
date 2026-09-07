# ShopFlow — résumé & interview pack

Everything here reflects what is **actually implemented and tested** in this repo. No inflated claims.

---

## Résumé bullets (pick 3–5)

- **Built an event-driven e-commerce backend** — 8 Spring Boot 3 / Java 17 microservices behind a
  Spring Cloud Gateway, with PostgreSQL (database-per-service), Redis, and Apache Kafka — deployable
  via one `docker compose up` and a Kubernetes kustomize base.
- **Designed and implemented an orchestrated Saga** for order fulfilment (reserve inventory → charge
  payment → confirm / compensate) with a **transactional outbox**, per-consumer idempotency
  (`processed_event`), retry with exponential backoff and **dead-letter topics**, and a
  stuck-transaction watchdog — verified end to end with Testcontainers Kafka.
- **Prevented overselling under concurrency** with atomic conditional SQL updates
  (`UPDATE … WHERE available >= :qty`); a 16-thread integration test proves exactly one reservation
  wins for the last unit.
- **Implemented stateless JWT auth** — HS256 access tokens verified at the edge and re-verified in
  each service, plus opaque, hashed, **rotating refresh tokens with reuse detection** (a replayed
  token revokes the whole session family).
- **Made every cross-service step idempotent and observable** — client `Idempotency-Key`, DB unique
  constraints, event de-duplication, state-machine guards; a single correlation ID flows through
  every HTTP hop, every Kafka event, and every (structured JSON) log line, so a failed order is
  traceable across all services from one query.
- **Set up the delivery pipeline** — GitHub Actions (fast unit stage → Testcontainers integration
  stage), matrix container build/push to GHCR, Prometheus + Grafana dashboards, and operational
  runbooks.

## Technical skills demonstrated (only these)

Java 17 (records, sealed-ish enums, streams, `Optional`, `CompletableFuture`-free concurrency via
`ExecutorService` in a test) · Spring Boot 3 · Spring MVC · Spring WebFlux (gateway only) · Spring
Data JPA / Hibernate (`@EntityGraph`, `@Version`, `JpaSpecificationExecutor`, bulk `@Modifying`) ·
Spring Security (stateless JWT, method security, custom filter/entry-point/advice) · Spring Kafka
(`@KafkaListener`, `DefaultErrorHandler`, `DeadLetterPublishingRecoverer`, idempotent producer) ·
Spring Cloud Gateway (global filters, Redis `RequestRateLimiter`) · Spring Boot auto-configuration
(wrote a shared starter) · PostgreSQL (per-service schemas, indexing, optimistic + atomic-conditional
concurrency control, Flyway) · Redis (cache-aside + TTL + jitter + invalidation, hash-backed cart,
token bucket) · Apache Kafka (topics/partitions/consumer groups/offsets, DLT, keying for ordering) ·
Saga pattern · transactional outbox · idempotency patterns · REST API design (status codes,
pagination, standard error body, OpenAPI) · JUnit 5 · Mockito · MockMvc / WebTestClient ·
Testcontainers (Postgres, Redis, Kafka) · Docker · Docker Compose · Kubernetes (Deployments,
Services, ConfigMap/Secret, Ingress, HPA) · GitHub Actions · Micrometer / Prometheus / Grafana ·
structured logging · Clean Architecture / SOLID / DTO / repository / service-layer patterns.

**Not claimed**: production traffic, real payment/email integrations, distributed tracing backend,
multi-region, message-schema registry, service mesh.

---

## 2-minute explanation

> ShopFlow is a production-style e-commerce backend built to look like something a small team would
> actually run. There are eight services — users, products, cart, orders, inventory, payment,
> notifications, and an API gateway — each owning its own PostgreSQL database, with Redis for caching
> and the cart, and Kafka for asynchronous business events.
>
> The interesting part is order fulfilment. When a customer places an order, the Order Service
> creates a `PENDING` order and returns `202` immediately — it doesn't block the HTTP thread for the
> whole flow. Then an **orchestrated Saga** takes over: Order publishes `ORDER_CREATED`, Inventory
> reserves stock and publishes the result, Order then asks Payment to charge, and depending on the
> outcome the order is confirmed or cancelled with compensating actions — for example, releasing the
> reservation if payment fails. Inventory is always reserved *before* payment, so we can never charge
> someone for stock we don't have.
>
> Because it's distributed and eventual-consistent, everything is idempotent: HTTP creates carry an
> idempotency key, Kafka consumers de-duplicate on event id in the same transaction as their side
> effects, and the state machine ignores events that arrive for the wrong state. The Order Service
> uses a **transactional outbox** so a database commit and an event publish can't diverge, and a
> **watchdog** cancels any order that gets stuck. Failed messages go to dead-letter topics after a
> few retries.
>
> Auth is stateless JWT — verified at the gateway and again in each service — with rotating,
> hashed refresh tokens that detect reuse. There's a correlation ID on every request, every event,
> and every structured log line, so I can trace one failed order across all eight services with a
> single log query. It runs with one `docker compose up`, has Testcontainers integration tests
> including the full Saga over real Kafka, a GitHub Actions pipeline, Kubernetes manifests, and
> Grafana dashboards.

---

## 10-minute deep-dive

**1. Shape & boundaries (1 min).** Eight services split by *what changes together* and *who owns the
fact*. Users own identity; Product owns the catalogue; Cart owns the transient cart; Order owns the
order aggregate and the Saga; Inventory owns stock; Payment owns money; Notification is a pure event
consumer; the Gateway owns edge concerns. Each service's PostgreSQL database is private — no other
service has its connection string, and there are no cross-service joins. Cart has no database at all;
Redis is its source of truth because a cart is ephemeral, key-accessed, and write-heavy.

**2. Sync vs async (1 min).** REST only when the caller can't make progress without the answer:
client→gateway→services, cart validation, price snapshot at order time, and Product telling Inventory
to create a stock row when a product is created. Kafka for everything that can happen later, must be
durable, may need replay, or fans out — which is the entire order Saga and all notifications.

**3. The order Saga (3 min).** `POST /api/orders` validates the cart over REST, snapshots the line
items and prices onto a new `PENDING` order, writes an `idempotency_key` row (the id is reserved
first so the unique constraint is the real guard), enqueues `ORDER_CREATED` into an **outbox** table
*in the same transaction*, advances to `INVENTORY_PENDING`, and returns `202`. A scheduled relay
publishes outbox rows to Kafka. Inventory consumes `ORDER_CREATED`, reserves each line with an atomic
`UPDATE stock SET available = available - qty WHERE available >= qty` — all-or-nothing, and a
shortage rolls back the partial decrement in a `REQUIRES_NEW` transaction without poisoning the
consumer's transaction — then publishes `INVENTORY_RESERVED` or `INVENTORY_RESERVATION_FAILED`. Order
consumes that (guarded: it only acts if the order is still `INVENTORY_PENDING`); on success it moves
to `AWAITING_PAYMENT` and outboxes `PAYMENT_REQUESTED`; on failure it cancels with reason
`OUT_OF_STOCK` and *no payment is ever requested*. Payment consumes `PAYMENT_REQUESTED`, is
idempotent on `payment.order_id`, simulates a charge, and publishes `PAYMENT_SUCCESS` or
`PAYMENT_FAILED`. Order consumes that: success → `CONFIRMED` + `ORDER_CONFIRMED` (Inventory converts
the reservation, Notification emails the customer); failure → `CANCELLED` + `ORDER_CANCELLED`
(Inventory *releases* the reservation as a compensation).

**4. Consistency model (1 min).** ACID inside each service; eventual consistency across services.
No 2PC, no distributed locks. Compensations don't roll back committed rows — they produce a
business-equivalent state. The two guarantees I actually make: a customer is never charged for an
order that isn't `CONFIRMED`, and stock is never permanently lost to a cancelled order.

**5. Idempotency & failure (2 min).** Every consumer has a `processed_event(event_id PK)` table and
inserts the marker in the same transaction as its side effects, so a duplicate delivery is a no-op
and a mid-handler crash leaves no marker. State-machine guards make late or re-ordered events (Kafka
only orders within a partition) harmless. The outbox removes the dual-write gap on the Order write
path; Inventory/Payment/Notification publish directly and rely on idempotent consumers plus the
**watchdog**, which cancels any order stuck pre-confirmation past five minutes and lets compensation
run. Handler exceptions retry 1/2/4 seconds then go to `<topic>.DLT`; a malformed envelope is
non-retryable and dead-letters immediately.

**6. Cross-cutting (1 min).** A shared library (`shopflow-common`) is a small Spring Boot
auto-configuration: the event envelope, the standard `ErrorResponse` + a global exception handler,
the correlation-id filter + structured logging, JWT issue/validate, the security filter chain, and
the Kafka setup. Services depend on it and get all of that with zero wiring; it contains no business
logic. Auth is stateless JWT verified at the gateway (which also strips spoofed `X-User-*` headers
and injects verified ones) and again in each service. A correlation ID is generated at the edge and
propagated through headers, event envelopes, and the MDC, so `grep <id>` across all services'
JSON logs reconstructs a failed order's whole timeline.

---

## 30+ interview questions (with concise answers)

### Architecture & boundaries

1. **Why microservices here and not a modular monolith?** For the learning goal (distributed
   systems, Kafka, Saga). Honestly, a modular monolith would be a defensible choice for this scale;
   the services are split by data ownership so the boundaries would survive either way.
2. **How did you decide the service boundaries?** By what changes together and who is the source of
   truth for a fact. Identity, catalogue, cart, order+saga, stock, money, notifications each have a
   distinct change cadence and owner.
3. **Why does Cart have no database?** A cart is ephemeral, per-user, key-accessed, write-heavy, and
   has no reporting need — a Redis hash with a sliding TTL fits; a relational table would be
   over-engineering. Trade-off: a Redis data loss loses carts (documented, acceptable for the domain).
4. **REST vs Kafka — how do you choose per interaction?** REST when the caller cannot proceed without
   the response (reads, cart validation, price snapshot). Kafka when the work can happen later, must
   be durable, may need retry/replay, or fans out to multiple consumers.
5. **Why is Product→Inventory a REST call and not an event?** It's an immediate, one-to-one,
   request/response need (create a stock row when a product is created). A topic would add
   operational surface for no benefit.
6. **No service discovery — why?** Docker Compose and Kubernetes already provide DNS-based,
   health-checked service resolution. Eureka would duplicate that and add a component to run and
   secure.

### Saga & consistency

7. **Orchestration vs choreography — why orchestration?** The flow logic lives in one place (Order
   Service) and in `order_status_history`, which makes it debuggable and testable. Choreography is
   more decoupled but the workflow is smeared across services.
8. **Why reserve inventory before charging payment?** So "charged but no stock" is impossible. If
   payment fails, the only compensation needed is releasing the reservation.
9. **What's your consistency model, precisely?** ACID within a service; eventual consistency across
   services. Compensations produce a business-equivalent state rather than rolling back committed
   rows. No 2PC.
10. **A compensation itself fails — then what?** It retries (idempotent) and, failing that, the order
    is moved to `FAILED` and surfaced for manual attention; the watchdog and DLT inspection are the
    tools.
11. **The orchestrator crashes mid-Saga — is state lost?** No. Order state is in Postgres +
    `order_status_history`; the outbox holds un-published events; on restart the relay drains and the
    watchdog finishes anything stuck.
12. **What anomaly can a user observe during checkout?** Status moving `PENDING` →
    `INVENTORY_PENDING` → `AWAITING_PAYMENT` → `CONFIRMED` on refresh, and (for admins) a transient
    `reserved` stock count. Documented, not a bug.

### Idempotency

13. **Why can the same Kafka event be delivered twice?** At-least-once delivery + consumer rebalances
    + producer retries + a crash between handling and offset commit.
14. **How do you detect a duplicate event?** A `processed_event(event_id PK)` row inserted in the
    same transaction as the handler's side effects; `existsById` first (no exception on the common
    case), the PK constraint as the backstop for a true race.
15. **Record-then-process or process-then-record?** Same transaction — the marker and the side
    effects commit together, so a mid-handler crash leaves no marker and the event is safely
    redelivered.
16. **How is `POST /api/orders` made idempotent?** The client sends an `Idempotency-Key`; the order
    id is reserved and written into a `UNIQUE` `idempotency_key` row *before* the order is created,
    so a replay (same key, same request) returns the original order and a key clash is a 409.
17. **What if a duplicate event arrives for an order that's already CANCELLED?** The state-machine
    guard ignores it (logged, counted). Late and re-ordered events are harmless by design.

### Kafka

18. **Why key order events by `orderId`?** Kafka only orders within a partition. Keying by `orderId`
    puts one order's events on one partition (processed in order) while different orders parallelise
    across partitions.
19. **How many topics and why not one per event type?** Four business topics + their DLTs. Event
    type is a field in the envelope; a topic per type explodes operational surface for no gain at
    this scale.
20. **Explain your retry + DLT setup.** `DefaultErrorHandler` with exponential backoff (1/2/4s, 15s
    cap); on exhaustion `DeadLetterPublishingRecoverer` writes the record to `<topic>.DLT` on the
    same partition with the exception in headers; the main partition is never blocked. A malformed
    envelope (`IllegalArgumentException`) is classified non-retryable and dead-letters immediately.
21. **Why `acks=all` and `enable.idempotence=true`?** No silent loss on leader failover; no
    duplicate records from producer retries.
22. **What is the transactional outbox and what problem does it solve?** The dual-write problem: a
    service commits its DB change but crashes before publishing the event. The outbox writes the
    event into an `outbox_event` table in the same transaction; a relay publishes it. Order Service
    uses it for every event it emits.
23. **Why not the outbox in every service?** Scope. Order Service is the orchestrator and the place
    where the synchronous HTTP path emits the first event, so correctness matters most there.
    Inventory/Payment/Notification rely on idempotent consumers + the watchdog; a full rollout is
    documented as the follow-up.

### Data & concurrency

24. **How do you prevent overselling?** An atomic conditional update:
    `UPDATE stock SET available = available - :q, reserved = reserved + :q WHERE product_id = :id AND available >= :q`.
    Concurrent transactions serialize on the row lock and re-check the predicate; losers update zero
    rows. `@Version` on the stock entity covers the admin set/adjust path.
25. **Optimistic vs pessimistic locking — where did you use which?** Optimistic (`@Version`) for
    low-contention edits (product, stock admin path, order aggregate). The atomic conditional update
    for the high-contention reservation path — it's effectively a single-statement lock+check.
26. **How do you avoid N+1 queries?** `@ManyToOne(LAZY)` + `@EntityGraph(attributePaths = …)` on the
    read methods that map to DTOs, and `open-in-view=false` so a missed fetch fails a test instead
    of silently issuing queries.
27. **Why snapshot price/name onto the order?** An order must reflect what the customer agreed to;
    a later catalogue edit must not rewrite history. It also avoids a cross-service read at display
    time.
28. **Why one database per service instead of shared schemas?** Independent deployability, small
    blast radius, each service picks its own indexing/locking strategy, and the "who owns this fact"
    question is always answerable.

### Redis

29. **Cache-aside — walk through a read and a write.** Read: check `product:{id}`; miss → load from
    DB → `SET` with a TTL. Write: update the DB, then **delete** the key (not update it — delete
    avoids stale-value races); the next read repopulates.
30. **What's the TTL jitter for?** ±10% so a burst of populates for the same key don't all expire on
    the same tick and stampede the DB.
31. **Redis goes down — what happens?** Product reads fall back to the DB (slower, logged); the cart
    returns `503` because there's no fallback store; the gateway rate-limiter errors that request.
    Nothing corrupts.

### Security

32. **Why verify the JWT in each service if the gateway already did?** Defence in depth — a service
    must not blindly trust `X-User-*` headers in case it's reachable directly in the cluster.
33. **Explain refresh-token rotation with reuse detection.** Each refresh issues a new refresh token
    and revokes the presented one; tokens from one login share a `family_id`. If an already-revoked
    token is presented, someone has a copy they shouldn't — the whole family is revoked, forcing
    re-login. This revocation is committed even though the request then fails
    (`@Transactional(noRollbackFor = …)`).
34. **Why hash refresh tokens with SHA-256 but passwords with BCrypt?** Passwords are low-entropy →
    need a slow, salted hash. Refresh tokens are 256 bits of randomness → a fast hash is enough, and
    a fast lookup matters.
35. **How do you avoid leaking whether an email is registered on login?** Run `matches()` against a
    real dummy BCrypt hash when the email is unknown, and return the same generic 401.
36. **Where can a `@PreAuthorize` failure turn into a 500, and how did you fix it?** Method-security
    denials are thrown inside the DispatcherServlet, so the shared catch-all `@ExceptionHandler`
    could swallow them. A high-precedence advice re-routes `AccessDeniedException` /
    `AuthenticationException` to the same 403/401 responders used at the filter level.

### Operability

37. **How do you debug a failed order across eight services?** Take the correlation ID from the
    client's `X-Correlation-Id` response header (or `order_status_history`), `grep` it across all
    services' JSON logs (or one Loki query) — it's on every line and every event — then check
    `order_status_history`, `outbox_event`, each service's `processed_event`, and the `*.DLT` topics.
38. **What metrics matter for this system?** HTTP request/error rate and latency, Kafka consumer
    lag, DLT depth, order confirmed/cancelled counts, payment success/failure, inventory reservation
    failures, cache hit ratio, DB pool saturation.
39. **How would you add distributed tracing?** Micrometer Tracing + an OTLP collector; the
    correlation ID already gives 80% of the value, so tracing is a documented follow-up.
40. **How does this scale?** Every service is stateless, so horizontal scaling + rolling deploys are
    free. Kafka consumer groups scale to the partition count. HPAs on the gateway and Order Service.
    The DB is the first ceiling — pagination, indexes, and caching keep read load off it, and pool
    sizing must respect `replicas * pool_size < max_connections`.
41. **What's the biggest thing you'd do differently for real production?** The outbox in every
    service (not just Order), a schema registry for event contracts, real distributed tracing, and
    an automated DLT re-drive tool.
