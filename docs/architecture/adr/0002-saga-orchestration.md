# ADR-0002: Orchestrated Saga for the order workflow

**Status:** Accepted (Phase 1)

## Context

Creating an order spans three services with their own databases (Order, Inventory, Payment).
There is no safe distributed ACID transaction across them. The choices are:

1. **Choreography** — each service listens for an event and emits the next one; no coordinator.
2. **Orchestration** — one service runs an explicit state machine and drives each step.
3. Two-phase commit (XA) — rejected: couples services, holds cross-network locks, poor fit for microservices.

## Decision

Use an **orchestrated Saga**. The **Order Service** owns the state machine, decides the next step,
and issues compensations on failure. Steps run **sequentially**: reserve inventory first, then take
payment. Coordination happens over Kafka (durable, retryable), not synchronous REST.

## Consequences

**Positive**
- The whole workflow is visible in one place and in the `order_status_history` table.
- Failure handling and compensation logic are centralised and testable.
- Reserving inventory before charging means "charged but no stock" cannot happen.
- Strong, demonstrable interview material for the Saga pattern.

**Negative / accepted trade-offs**
- The Order Service carries more logic and is a coordination hotspot. Mitigated: it stays stateless
  (state is in Postgres), scales horizontally, and a watchdog finishes Sagas orphaned by a crash.
- Slightly more coupling than pure choreography (the orchestrator knows the step sequence). Accepted
  for the debuggability win.

## Consistency model

Eventual consistency across services; ACID only within a service. Compensations produce a
business-equivalent "as if it never happened" state — they do not roll back committed rows.
