# Testing

## Layers

| Layer | Tooling | Runs | What it proves |
|---|---|---|---|
| **Unit** (`*Test`) | JUnit 5 + Mockito + AssertJ | always (Surefire) | domain logic, service orchestration, state machine, simulators - boundaries mocked |
| **Slice** (`*Test`, `@WebMvcTest`) | + Spring Security test + the *real* `SecurityConfig` | always | controller status codes, validation → 400, 401/403 rules, JSON shape |
| **Integration** (`*IT`) | `@SpringBootTest` + Testcontainers (Postgres / Redis / Kafka) + `spring-boot-testcontainers` `@ServiceConnection` | `mvn verify` (needs Docker; CI job 2) | real SQL + Flyway + Redis + Kafka + the full filter chain; the Saga end to end |

`mvn -DskipITs verify` runs unit + slice only (no Docker). CI runs both.

## "Important test scenarios" coverage (Phase 1 §15)

| Scenario | Where |
|---|---|
| Successful order | `OrderSagaOrchestratorIT.happyPathAdvancesToConfirmed...` |
| Invalid product / input | `ProductControllerTest`, `CartControllerTest`, `*ControllerTest` validation cases |
| Insufficient inventory | `OrderSagaOrchestratorIT.inventoryShortageCancels...`, `InventoryConcurrencyIT` |
| Payment failure | `OrderSagaOrchestratorIT.paymentFailureCancelsTheOrder...`, `PaymentIT.anAmountEndingInThirteenAlwaysFails` |
| Duplicate payment event | `PaymentServiceTest.processIsIdempotentPerOrder` (+ `processed_event` guard in the listener) |
| Duplicate inventory event | `OrderSagaOrchestratorIT.aDuplicateInventoryReservedEventIsIgnored` |
| Kafka consumer retry / DLT | `OrderSagaOrchestratorIT.aMalformedEventGoesStraightToTheDeadLetterTopic` |
| Order cancellation | `OrderLifecycleIT.cancellingWorksOnceThenConflicts` |
| Concurrent inventory reservation | `InventoryConcurrencyIT.concurrentReservationsForTheLastUnitDoNotOversell` (16 threads → exactly 1 wins) |
| Unauthorized API access | `UserControllerTest.meRequiresAuthentication`, `GatewayRoutingTest.protectedRouteWithoutATokenIs401...` |
| Admin-only API access | `*ControllerTest` `...IsForbiddenForCustomers` / `...ForAdmin`, `ProductCatalogIT.writeEndpointsRequireAdmin` |
| Refresh rotation + reuse detection | `UserAuthIT.refreshRotatesTokensAndReusingTheOldOneRevokesTheFamily` |
| Cache populate + invalidate | `ProductCatalogIT.readingAProductPopulatesTheCacheAndUpdatingEvictsIt` |

## Conventions

- One assertion focus per test; names read as sentences.
- Slice tests drive the **real** security chain (mock only `JwtValidator` + the service), so
  filter/handler wiring is exercised, not stubbed.
- ITs clean up in `@AfterEach` (repos + Redis keys) so test order doesn't matter.
- No coverage-chasing tests; the matrix above is the target, not a percentage.
