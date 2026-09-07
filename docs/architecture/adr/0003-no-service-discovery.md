# ADR-0003: Static config + DNS instead of a service registry

**Status:** Accepted (Phase 1)

## Context

Services and the gateway need to find each other. Spring Cloud tutorials commonly add Netflix
Eureka (a service registry). We need to decide whether that component earns its place.

## Decision

**No service registry.** Service locations come from configuration (environment variables), and the
runtime platform provides name resolution:

- Local (dev): `http://localhost:<port>` from `.env`.
- Docker Compose: the Compose service name (`http://user-service:8081`).
- Kubernetes: the Service DNS name (`http://user-service.shopflow.svc.cluster.local`).

## Consequences

**Positive**
- One fewer component to run, secure, monitor, and explain.
- Kubernetes already does health-checked service discovery and load balancing; Eureka would duplicate it.
- Aligns with the project rule: no technology added just to look impressive.

**Negative / accepted trade-offs**
- No client-side load balancing across arbitrary instances outside an orchestrator. Not needed:
  Compose and K8s both load-balance behind a stable name.
- Adding/moving a service means a config change. Acceptable at 8 services.
