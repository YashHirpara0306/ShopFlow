# ADR-0008: Shared JWT-security building blocks in shopflow-common

**Status:** Accepted (Phase 5)

## Context

Phase 4 built the bearer-token filter, the JSON 401/403 responders, the method-security advice, and
the BCrypt encoder inside User Service. Phases 5-10 each need the identical pieces. Copy-paste would
drift.

## Decision

Move `JwtAuthenticationFilter`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`,
`SecurityExceptionAdvice` and a BCrypt `PasswordEncoder` into `shopflow-common`, exposed by a
`SecurityAutoConfiguration` (guarded by `@ConditionalOnWebApplication(SERVLET)` +
`@ConditionalOnClass(SecurityFilterChain, JwtValidator)` + `@ConditionalOnMissingBean`).
`spring-boot-starter-security` and `jackson-databind` become `optional` dependencies of common.

Each service still writes its own small `SecurityConfig` with a `SecurityFilterChain` bean listing
its own public paths and injecting the shared filter + handlers.

## Consequences

**Positive**
- One implementation of token handling and the error bodies; four fewer classes per service.
- Per-service path rules stay explicit and local.
- This is effectively part of Phase 14 (security hardening) pulled forward.

**Negative / accepted trade-offs**
- `shopflow-common` now optionally depends on Spring Security. Contained: web-type + class
  conditions, no business logic, services opt in by declaring `spring-boot-starter-security`.
- A service that wants different behaviour overrides the bean (`@ConditionalOnMissingBean`).
