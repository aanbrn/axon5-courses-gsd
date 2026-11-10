# Architecture

## Shape

Three Spring Boot services behind Axon Server, one PostgreSQL read model owned by `read`, one React SPA in Feature-Sliced Design, six Gradle modules. The browser talks only to `api`; the gateway sends messages; Axon Server routes them to the service that handles them.

## Modules

| Module | Kind | Responsibility | Must not |
|---|---|---|---|
| `platform` | `java-platform` | Re-exports the Spring Boot, Axoniq and Jackson BOMs via `api(platform(...))` with `allowDependencies()` | Contain code |
| `domain` | library | Events, commands, queries, ids, tag keys, `DomainRejection` | Depend on Spring, JPA, or a service module |
| `write` | app | Command handlers, decision state, `@EventCriteriaBuilder`, the only `EventAppender` | Have a datasource, JPA, or read models |
| `read` | app | Tracking processor and JPA writes, Flyway migrations, `@QueryHandler`s over Spring Data JDBC, `QueryUpdateEmitter` | Append events or answer commands |
| `api` | app | REST, security, gateways, SSE stream as `Flux`, seeding, race endpoint, serves the SPA | Touch SQL, append events, subscribe to the event stream, or depend on `write`/`read` |
| `frontend` | node-gradle | React/Vite build to `dist/` | Contain domain logic |

## Boundaries

- Decisions are scoped by `courseId`; `studentId` is checked from the event payload.
- `@EventCriteriaBuilder` is the boundary: its `EventCriteria` is both the read filter and the write-conflict definition.
- `EnrollStudent` carries no routing key, so concurrent enrollments really race.
- `read` writes the row and emits its subscription-query update from the same service; the emit happens after commit, so a rolled-back write never reaches a subscriber.
- The gateway never subscribes to events; clients receive read-model snapshots only.

## Data flow

- The gateway sends a command to Axon Server; `write` sources the criteria, evolves state, and appends with `AppendCondition`.
- Axon Server stores the event and rejects a conflicting append with `AppendEventsTransactionRejectedException`.
- `read` consumes the event, writes PostgreSQL, and after commit emits the new view.
- The UI receives the snapshot and updates over SSE from the gateway; point-in-time reads go through `QueryGateway`.

## Identifiers and idempotency

- UUIDv7 wrapped in `@JvmInline value class` ids; the canonical string is the tag value.
- `CreateCourse` takes a client-supplied id and is idempotent by key; other commands are idempotent by effect and return named rejections on repeat.
- No automatic command retries.

Decision log: `.planning/PROJECT.md`. Rationale and counterfactuals: `.planning/research/ARCHITECTURE.md`.
