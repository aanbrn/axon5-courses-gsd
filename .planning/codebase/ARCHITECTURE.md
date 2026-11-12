# Architecture

## Shape

Three Spring Boot services behind Axon Server, one PostgreSQL read model owned by `courses-read`, one React SPA in Feature-Sliced Design shipped as a standalone nginx image, six application modules plus a `courses-test-support` library. The browser loads the SPA from the frontend origin and the SPA talks only to `courses-api`; the gateway sends messages; Axon Server routes them to the service that handles them.

## Modules

| Module | Kind | Responsibility | Must not |
|---|---|---|---|
| `courses-platform` | `java-platform` | Re-exports the Spring Boot and Axoniq BOMs (Boot manages Jackson) via `api(platform(...))` with `allowDependencies()` | Contain code |
| `courses-domain` | library | Events, commands, queries, ids, tag keys, `DomainRejection` | Depend on Spring, JPA, or a service module |
| `courses-write` | app | Command handlers, decision state, `@EventCriteriaBuilder`, the only `EventAppender` | Have a datasource, JPA, or read models |
| `courses-read` | app | Tracking processor and JPA writes, Flyway migrations, `@QueryHandler`s over Spring Data JDBC, `QueryUpdateEmitter` | Append events or answer commands |
| `courses-api` | app | REST, security, gateways, SSE stream as `Flux`, seeding | Serve the SPA, touch SQL, append events, subscribe to the event stream, or depend on `courses-write`/`courses-read` |
| `courses-frontend` | Gradle module + container | React/Vite build to `build/dist`, shipped as a standalone nginx image built with Paketo buildpacks | Contain domain logic |
| `courses-test-support` | library (test) | Shared test infrastructure: the Axon Server registration base and the Testcontainers image lookup | Be on a production classpath |

Shared build configuration lives in the `build-logic` included build, which is not one of the modules: `kotlin-conventions` covers Kotlin, the JDK 21 toolchain, Spotless, the `:courses-platform` BOM and the suite-suffix rule; `spring-boot-conventions` adds the Boot service wiring and the common test-suite block; `frontend-conventions` adds the SPA's `stageImageFiles` step and the Paketo `dockerBuildImage` task. Module scripts declare their own project dependencies and their `integrationTest` suite.

The integration suites consume `courses-test-support` with `implementation(project(":courses-test-support"))`; only test classpaths see it, and `courses-domain` stays free of Spring.

The SPA is not an artifact of the gateway. `courses-frontend` builds `build/dist` and ships it as its own nginx image; `start.sh` renders `config.js` from `COURSES_API_BASE_URL` at container start, and the gateway allows the UI's origin through `COURSES_CORS_ALLOWED_ORIGINS` (fail-closed).

## Boundaries

- Decisions are scoped by `courseId`; `studentId` is checked from the event payload.
- `@EventCriteriaBuilder` is the boundary: its `EventCriteria` is both the read filter and the write-conflict definition.
- `EnrollStudent` carries no routing key, so concurrent enrollments really race.
- `courses-read` writes the row and emits its subscription-query update from the same service; the emit happens after commit, so a rolled-back write never reaches a subscriber.
- The gateway never subscribes to events; clients receive read-model snapshots only.

## Health and registration

- The Axon starter contributes to actuator: `AxonServerHealthIndicator` exposes an `axonServer` component, and `AxonServerStatusAggregator` orders `DOWN, OUT_OF_SERVICE, WARN, UP, UNKNOWN`, so a partial connection reports `WARN` and a total loss `DOWN`.
- Two gaps keep it from being the evidence the project cites: `/actuator/health` hides component detail unless `management.endpoint.health.show-details` is set, and the indicator reports `UP` while its connections map is empty — the state before the first connection attempt.
- Registration is therefore asserted directly: `AxonServerConnectionManager.connections()["default"] == true`, under a bounded Awaitility poll, in `AxonServerRegistrationIT`.
- Actuator security — authorization, a non-published management port, what is exposed — is backlog item 999.3, decided with Phase 2's authentication.

## Data flow

- The gateway sends a command to Axon Server; `courses-write` sources the criteria, evolves state, and appends with `AppendCondition`.
- Axon Server stores the event and rejects a conflicting append with `AppendEventsTransactionRejectedException`.
- `courses-read` consumes the event, writes PostgreSQL, and after commit emits the new view.
- The UI receives the snapshot and updates over SSE from the gateway; point-in-time reads go through `QueryGateway`.

## Identifiers and idempotency

- UUIDv7 wrapped in `@JvmInline value class` ids; the canonical string is the tag value.
- `CreateCourse` takes a client-supplied id and is idempotent by key; other commands are idempotent by effect and return named rejections on repeat.
- No automatic command retries.

Decision log: `.planning/PROJECT.md`. Rationale and counterfactuals: `.planning/research/ARCHITECTURE.md`.
