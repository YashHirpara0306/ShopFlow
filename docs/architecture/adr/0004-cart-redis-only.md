# ADR-0004: Cart Service is Redis-only

**Status:** Accepted (Phase 1)

## Context

The cart is a short-lived, per-user, write-heavy structure with no reporting or audit requirement.
Options: (a) PostgreSQL as source of truth with Redis as a cache, or (b) Redis as the source of truth.

## Decision

**Redis is the source of truth for carts.** No database for Cart Service. Storage is a Redis hash
per user (`cart:{userId}`) with a sliding TTL (default 14 days) refreshed on every write.

## Consequences

**Positive**
- Matches the data's nature: ephemeral, high-churn, key-access only.
- No schema, no ORM, no cache-invalidation code in Cart Service.
- Clean teaching contrast with Product Service, where Redis *is* a cache over Postgres.

**Negative / accepted trade-offs**
- If Redis loses data (no persistence, or a flush), affected carts are gone. Acceptable for this
  domain and explicitly documented; Redis runs with RDB snapshotting locally to reduce the window.
- Cart Service cannot fall back to a database when Redis is down — cart writes return `503` and the
  client retries. Documented in the failure-scenarios table.
