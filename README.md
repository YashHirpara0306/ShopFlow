# ShopFlow

An event-driven e-commerce backend built as a production-style portfolio project:
Spring Boot 3 microservices, PostgreSQL, Redis, Apache Kafka, an orchestrated Saga for the
order workflow, Docker, Kubernetes, and CI.

> **All 22 build phases complete**, plus a **[React storefront](frontend/)** (`frontend/`) — a
> Rebt-branded shop (home, catalogue + search/filter, product detail, cart, checkout, live
> order-status timeline, account, admin product/inventory screens) wired to the gateway. 8 services +
> an orchestrated order Saga over Kafka, all run with one `docker compose up`.
> See **[FINAL_CHECKLIST.md](docs/FINAL_CHECKLIST.md)** for the quality bar (and where "verified in
> CI" vs "run locally" applies), and **[docs/resume/](docs/resume/README.md)** for the résumé
> bullets, 2- / 10-minute pitches, and 40 interview Q&A.
>
> **Not production-ready, and it doesn't claim to be** — it's *production-style*: the patterns are
> real and tested. Simulated payment + email; no real distributed tracing; single-replica infra
> locally. Full caveats in [security.md](docs/architecture/security.md) and
> [performance.md](docs/architecture/performance.md).

## What it demonstrates

Orchestrated **Saga** (reserve inventory → charge payment → confirm / compensate) · **transactional
outbox** · consumer **idempotency** (`processed_event`, `Idempotency-Key`, unique constraints, state
guards) · retry + **dead-letter topics** · **stuck-Saga watchdog** · oversell-safe concurrency
(atomic conditional `UPDATE`) · **cache-aside** with TTL jitter + invalidation · Redis-native cart ·
stateless **JWT** verified at the edge and per-service · **rotating refresh tokens with reuse
detection** · edge **rate limiting** · **correlation IDs** on every request, event, and JSON log line
· one shared Spring Boot **auto-configuration** starter · **Testcontainers** ITs (Postgres/Redis/Kafka)
including the full Saga · two-stage **GitHub Actions** · **Kubernetes** kustomize base · **AWS**
deployment guide · **Prometheus + Grafana** + runbooks.

## Documentation

- **[SYSTEM_DESIGN.md](docs/architecture/SYSTEM_DESIGN.md)** — the consolidated 14-topic design.
- **[Phase 1](docs/architecture/phase-01-requirements-and-architecture.md)** — full requirements & architecture.
- [conventions](docs/architecture/conventions.md) · [event catalog](docs/architecture/events/event-catalog.md) ·
  [kafka & saga](docs/architecture/kafka-and-saga.md) · [security](docs/architecture/security.md) ·
  [observability](docs/architecture/observability.md) · [performance](docs/architecture/performance.md) ·
  [failure scenarios](docs/architecture/failure-scenarios.md) · [testing](docs/architecture/testing.md) ·
  [CI/CD](docs/architecture/cicd.md) · [ADRs](docs/architecture/adr/README.md) · [runbooks](docs/runbooks/)
- Per service: [gateway](docs/architecture/services/api-gateway.md) ·
  [user](docs/architecture/services/user-service.md) · [product](docs/architecture/services/product-service.md) ·
  [cart](docs/architecture/services/cart-service.md) · [order](docs/architecture/services/order-service.md) ·
  [inventory](docs/architecture/services/inventory-service.md) · [payment](docs/architecture/services/payment-service.md) ·
  [notification](docs/architecture/services/notification-service.md)
- Deploy: [Docker Compose](docker-compose.yml) · [Kubernetes](deploy/k8s/) · [AWS](deploy/aws/README.md) · [monitoring](deploy/monitoring/)

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

## How to run

### Everything, in Docker

```bash
cp .env.example .env
mvn -DskipTests package        # build the service jars the images copy in
docker compose up -d --build   # storefront + 8 services + Postgres + Redis + Kafka + Prometheus + Grafana
docker compose ps              # wait for all "healthy" (first build downloads deps; give it time)
./scripts/smoke.sh             # register → browse → cart → order → CONFIRMED  (needs curl + jq)
```

- **Storefront: `http://localhost:3000`** (React) · Gateway: `:8080` · Prometheus: `:9090` · Grafana: `:3001` (anon)
- `docker compose logs -f order-service` — JSON logs with `correlationId`
- `docker compose down` (keep data) · `docker compose down -v` (wipe)

### Build & test the code

```bash
mvn -B -ntp verify             # unit + slice + Testcontainers integration tests
mvn -B -ntp -DskipITs verify   # skip the Docker-based integration tests
```

### One service against local infra

```bash
docker compose up -d postgres redis kafka
export SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:29092   # host access to the compose Kafka
mvn -pl services/user-service -am spring-boot:run
curl http://localhost:8081/actuator/health
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
