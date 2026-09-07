# ADR-0006: shopflow-common ships as a Spring Boot auto-configuration

**Status:** Accepted (Phase 3)

## Context

`shopflow-common` provides cross-cutting web behaviour every servlet service needs: the
correlation-id filter and the global exception handler. Services only component-scan their own
`com.shopflow.<service>` package, so beans in `com.shopflow.common` are not picked up automatically.

Options to register them:
1. Each service adds `@Import(...)` or widens `@ComponentScan` to `com.shopflow`.
2. `shopflow-common` provides `@AutoConfiguration` classes listed in
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — i.e. it
   behaves like a Spring Boot starter.

## Decision

Option 2. `shopflow-common` contains `CorrelationIdAutoConfiguration`,
`WebErrorHandlingAutoConfiguration`, and `JwtAutoConfiguration`, each guarded by conditions
(`@ConditionalOnWebApplication(SERVLET)`, `@ConditionalOnClass`, `@ConditionalOnMissingBean`,
`@ConditionalOnProperty` for JWT). A service just declares the dependency.

Spring dependencies the auto-configuration compiles against (`spring-web`, `spring-webmvc`,
`spring-boot-autoconfigure`) are `optional`, and `jakarta.servlet-api` is `provided` — the standard
"starter" shape, so a non-web consumer is not forced to pull Spring MVC.

## Consequences

**Positive**
- Zero per-service wiring; consistency is automatic and hard to forget.
- Conditions make it safe: the reactive gateway skips the servlet pieces; a service with no JWT
  secret configured gets no `JwtValidator` (so the 32-byte-secret guard never trips prematurely).
- `@ConditionalOnMissingBean` lets a service override any piece locally.

**Negative / accepted trade-offs**
- `shopflow-common` is no longer "just POJOs" — it now depends (optionally) on Spring. Kept in check:
  no business logic, conditions everywhere, and the reactive equivalents live in the gateway module.
- A hidden `@AutoConfiguration` can surprise a reader. Mitigated by the `SharedConventionsWiringTest`
  in user-service that asserts the beans are present.
