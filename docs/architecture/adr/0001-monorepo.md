# ADR-0001: Monorepo with Maven multi-module

**Status:** Accepted (Phase 2)

## Context

ShopFlow is 8 services + a shared library, built by one developer as a portfolio project.
The two options are a monorepo (one Git repo, one Maven reactor) or polyrepo (one repo per service).

## Decision

Use a **monorepo** with a Maven multi-module build: a parent aggregator POM at the root, one
module per service under `services/`, and the shared library under `libs/`.

## Consequences

**Positive**
- One `git clone` + one `mvn verify` builds and tests everything.
- Dependency versions and plugin configuration are declared once in the parent POM.
- A change to the shared event contract compiles every consumer in the same commit — breakage is caught immediately.
- One CI workflow; no internal artifact repository needed.

**Negative / accepted trade-offs**
- Services cannot be versioned or released independently. Acceptable: this project deploys as a set.
- The reactor build grows over time. Mitigated by `mvn -pl <module> -am` for focused builds.
- A real multi-team organisation would likely choose polyrepo for team autonomy. Documented, not adopted.
