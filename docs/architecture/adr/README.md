# Architecture Decision Records

Each ADR captures one significant decision: the context, the choice, and the consequences.
ADRs are immutable once accepted — if a decision changes, add a new ADR that supersedes the old one.

Format: `NNNN-short-title.md`, status one of `Proposed | Accepted | Superseded by NNNN`.

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-monorepo.md) | Monorepo with Maven multi-module | Accepted |
| [0002](0002-saga-orchestration.md) | Orchestrated Saga for the order workflow | Accepted |
| [0003](0003-no-service-discovery.md) | Static config + DNS instead of a service registry | Accepted |
| [0004](0004-cart-redis-only.md) | Cart Service is Redis-only | Accepted |
| [0005](0005-phase1-open-questions.md) | Resolutions to the six Phase 1 open questions | Accepted |
| [0006](0006-common-as-autoconfiguration.md) | shopflow-common ships as a Spring Boot auto-configuration | Accepted |
| [0007](0007-refresh-token-rotation.md) | Refresh tokens: opaque, hashed, rotating, with reuse detection | Accepted |
