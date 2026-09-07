# ShopFlow

An event-driven e-commerce backend built as a production-style portfolio project:
Spring Boot 3 microservices, PostgreSQL, Redis, Apache Kafka, an orchestrated Saga for the
order workflow, Docker, Kubernetes, and CI.

> **Project status: Phase 11 of 22 — Kafka + the orchestrated Saga are wired.**
> All 7 services (**User, Product, Cart, Order, Inventory, Payment, Notification**) plus the full
> order Saga over Kafka: Order Service orchestrates (reserve → pay → confirm / compensate) with a
> **transactional outbox**, consumer idempotency (`processed_event`), retry + **dead-letter topics**,
> a **stuck-Saga watchdog**, and correlation IDs on every event. Kafka support lives in
> `shopflow-common` (ADR-0009). See [kafka-and-saga.md](docs/architecture/kafka-and-saga.md).
> Kafka activates only when `SPRING_KAFKA_BOOTSTRAP_SERVERS` is set; without it services fall back
> to a logging transport.
> **Phase 12** (Redis): cache-aside + TTL + jitter + invalidation (Product), Redis-only cart with
> 503 fallback, Redis rate limiting at the gateway.
> **Phase 13**: the [API Gateway](docs/architecture/services/api-gateway.md) — routing, edge JWT
> verification + `X-User-*` injection/stripping, correlation IDs, secure headers, CORS, standard
> error body. **Next: Phase 14 (security hardening) + Phase 16 (full Docker Compose).**
> Docs: [architecture](docs/architecture/phase-01-requirements-and-architecture.md) ·
> [conventions](docs/architecture/conventions.md) ·
> [event catalog](docs/architecture/events/event-catalog.md) ·
> services: [gateway](docs/architecture/services/api-gateway.md) ·
> [user](docs/architecture/services/user-service.md) ·
> [product](docs/architecture/services/product-service.md) ·
> [cart](docs/architecture/services/cart-service.md) ·
> [order](docs/architecture/services/order-service.md) ·
> [inventory](docs/architecture/services/inventory-service.md) ·
> [payment](docs/architecture/services/payment-service.md) ·
> [notification](docs/architecture/services/notification-service.md) ·
> [kafka & saga](docs/architecture/kafka-and-saga.md) ·
> [ADRs](docs/architecture/adr/README.md).

## Services

| Module | Port | Responsibility | Store |
|---|---|---|---|
| `api-gateway` | 8080 | Routing, JWT check, identity forwarding, rate limiting, correlation IDs | — (reactive) |
| `user-service` | 8081 | Registration, login, JWT, profile, roles | PostgreSQL |
| `product-service` | 8082 | Product/category CRUD, search, filter, pagination | PostgreSQL + Redis cache |
| `cart-service` | 8083 | Add/update/remove/get/clear cart | Redis (source of truth) |
| `order-service` | 8084 | Order lifecycle, history, **Saga orchestrator** | PostgreSQL |
| `inventory-service` | 8085 | Stock, reserve/release/confirm, oversell prevention | PostgreSQL |
| `payment-service` | 8086 | Simulated payments, idempotent charges, refunds | PostgreSQL |
| `notification-service` | 8087 | Consume events, simulate email/SMS, retry | PostgreSQL |
| `shopflow-common` (`libs/`) | — | Auto-config starter: event envelope, error handling, correlation id + logging, JWT validator, `PageResponse` | — |

## Repository layout

```
shopflow/
├── pom.xml                      # parent aggregator POM
├── docker-compose.yml           # local infra: Postgres, Redis, Kafka
├── .env.example                 # copy to .env
├── Makefile                     # convenience targets
├── libs/shopflow-common/        # thin shared library
├── services/<name>/             # one Maven module per service
├── deploy/
│   ├── docker/postgres/init/    # database bootstrap SQL
│   ├── k8s/                     # Kubernetes manifests (Phase 19)
│   ├── aws/                     # AWS deployment notes (Phase 20)
│   └── monitoring/              # Prometheus + Grafana config (Phase 17)
├── docs/architecture/           # requirements doc + ADRs + diagrams
└── .github/workflows/ci.yml     # build + test on every push/PR
```

## Prerequisites

- **JDK 21** (LTS). Your `JAVA_HOME` already points at one. JDK 17+ works; the build targets Java 17 bytecode.
- **Maven 3.9+**. Not currently installed on this machine — install it once:
  ```bash
  winget install --id Apache.Maven -e
  ```
  then open a new terminal. (Optional: `mvn -N wrapper:wrapper` in the repo root to add `./mvnw`.)
- **Docker Desktop** — only needed for `docker compose` and for integration tests (added from Phase 4).

## How to run (Phase 2)

Build and unit-test everything:

```bash
mvn -B -ntp verify
```

Start the infrastructure:

```bash
cp .env.example .env
docker compose up -d
docker compose ps          # postgres, redis, kafka should all be "healthy"
```

Run a single service (example):

```bash
mvn -pl services/user-service -am spring-boot:run
# then: curl http://localhost:8081/actuator/health   ->  {"status":"UP"}
```

Stop infrastructure (keep data with plain `down`, wipe with `-v`):

```bash
docker compose down
```

## Behind a TLS-intercepting antivirus/proxy (AVG, Kaspersky, corporate MITM)

If `mvn` fails with `PKIX path building failed: unable to find valid certification path`,
your HTTPS traffic to Maven Central is being intercepted and the JDK does not trust the
interceptor's root CA. Verified working workaround on this machine:

```bash
mvn -Dmaven.resolver.transport=wagon \
    -Dmaven.wagon.http.ssl.insecure=true \
    -Dmaven.wagon.http.ssl.allowall=true \
    -Dmaven.wagon.http.ssl.ignore.validity.dates=true \
    -B -ntp verify
```

To make it automatic **locally only**, put those four `-D` flags (one per line, without `mvn`)
into `.mvn/maven.config` — that file is git-ignored so it never reaches CI (GitHub's runners
have clean TLS and must keep certificate validation on). The cleaner permanent fix is to import
the interceptor's root CA into the JDK truststore (`$JAVA_HOME/lib/security/cacerts`).

## Verify the skeleton

| Check | Command | Expected |
|---|---|---|
| Whole reactor compiles | `mvn -B -ntp -DskipTests package` | `BUILD SUCCESS`, a `*.jar` in each `services/*/target/` |
| Shared-library tests pass | `mvn -B -ntp -pl libs/shopflow-common test` | `EventEnvelopeTest` green |
| Every service context starts | `mvn -B -ntp verify` | 8 × `contextLoads` pass |
| Compose file is valid | `docker compose config -q` | no output, exit 0 |
| Databases are created | `docker compose up -d` then `docker compose exec postgres psql -U shopflow -c "\l"` | `shopflow_user`, `shopflow_product`, `shopflow_order`, `shopflow_inventory`, `shopflow_payment`, `shopflow_notification` |
| Kafka is reachable | `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` | runs without error (no topics yet) |

## Development roadmap

22 phases, from requirements (Phase 1) to final documentation (Phase 22). See the roadmap table in
[`docs/architecture/phase-01-requirements-and-architecture.md`](docs/architecture/phase-01-requirements-and-architecture.md#15-development-roadmap).

## Branching

`main` (protected, always green) · `develop` (integration) · `feature/*` · `bugfix/*`.
Conventional Commits: `feat(order): ...`, `fix(payment): ...`, `test(inventory): ...`, `ci: ...`, `docs: ...`.
