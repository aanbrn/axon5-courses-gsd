<!-- GSD:project-start source:PROJECT.md -->

## Project

**Axon 5 DCB Showcase**

A small course-enrollment application built to explore Axon Framework 5's Dynamic Consistency Boundaries (DCB). Students enroll in courses with limited seats, drop out, and sit on a waitlist — while every decision reads only the events it actually needs instead of loading a whole aggregate. It is deliberately split into three services behind Axon Server — `write`, `read`, `api` — with a PostgreSQL read model, so the boundary also has to survive a real process boundary. It exists as a personal learning sandbox: the app is the vehicle, the boundary design is the lesson.

**Core Value:** A command decides using exactly the events its invariant requires — proven by a last-seat race where two concurrent enrollments hit the cap and exactly one succeeds.

### Constraints

- **Topology**: three Spring Boot services — `write`, `read`, `api` — plus a `domain` contract library, exchanging commands, events and queries through Axon Server. `read` consumes events, owns the PostgreSQL read model and answers queries.
- **Tech stack**: multi-module Gradle (Kotlin DSL, version catalog) with a `platform` module re-exporting the BOMs; Kotlin on JVM 21, Spring Boot 4.1.2, Axon 5.4.1; React SPA as the `frontend` Gradle module.
- **Infrastructure**: Docker Compose runs Axon Server and PostgreSQL. One command brings the infrastructure up; one Gradle run starts all three services.
- **Licensing**: the Axon Server connector and the Axon Spring Boot starter are commercial `io.axoniq.framework` modules; local development must run under their evaluation behavior, and any production use is explicitly out of scope.
- **Consistency**: events are the only cross-service *state* contract; commands and queries are shared message types in `domain`. Each service owns its own models, and read models are eventually consistent.
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin on JDK 21 | **Kotlin 2.4.21** (latest stable; 2.5.0 is Beta as of 2026-10-21), `jvmToolchain(21)` | Language / JVM | **Project decision.** AF5's hard floor is JDK 21. Kotlin data classes replace Java records for immutable messages; annotation targets differ — see "Kotlin adaptation". |
| Spring Boot | **4.1.2** | App runtime, DI, web | **Project decision.** Axon supports Boot 3 and 4 (`5.0.3+`); Boot 4 is the current stable line and manages Jackson 3 itself. Axon's own build baseline is still 3.5.16 — fallback documented under Alternatives. |
| Axoniq Framework BOM | **5.4.1** (`io.axoniq.framework:axoniq-framework-bom`) **[pivot]** | Version alignment for Axon + commercial modules | **Commercial license.** This BOM includes the OSS Axon modules and extensions, so the OSS BOM is not added separately (official docs). Chosen because distributed messaging and Axon Server connectivity are commercial. |
| Axoniq Spring Boot Starter | **5.4.1** (`io.axoniq.framework:axoniq-spring-boot-starter`) **[pivot]** | Auto-configuration for a distributed Axon app | The OSS starter no longer fits: it carries no Axon Server connector, and all three services need to talk to the server. |
| Axon Server connector | `io.axoniq.framework:axon-server-connector` (transitive via the starter) **[pivot]** | Command/query routing + event storage | Multi-process requires it, and `axoniq-spring-boot-starter` already depends on it — declare the starter alone. Local server via Docker; production use is licensed. |
| Axon Server | **2026.1.7** (Docker image `axoniq/axonserver`) **[pivot]** | Event store, command/query routing, token store | The series is "2026.1"; published tags are patch-level, so pin `2026.1.7`. Axon Server is the path that needs no commercial module beyond the connector — `axoniq-springcloud` + `axoniq-postgresql` form a second, also commercial, topology. Supports DCB. |
| PostgreSQL | **18** (Docker `postgres:18`; 17 is one major behind) **[pivot]** | Read model owned by `read` | Durable across restarts; one service writes and answers it, so snapshot and live updates share one source. |
| Gradle multi-module + `java-platform` | **8.14.x** wrapper + **Kotlin DSL** + version catalog | Build | Six modules (see topology). 8.14 is a deliberate pin for Spring Boot 3.5/4 plugin compatibility; Gradle 9.9 is current, so re-check the pin at scaffold time and upgrade deliberately. Versions live in `gradle/libs.versions.toml`; `:platform` re-exports the BOMs. |
| React | **19.3.1** | SPA UI | Current `latest` on npm; what `create-vite@9.3.4`'s `template-react-ts` pins. |
| Vite | **8.3.7** | Dev server + build | Current `latest`; proxy to the gateway removes CORS config. |
| TypeScript | **7.0.2** | Frontend types | Pinned by the official `create-vite` React-TS template. |
| Feature-Sliced Design | FSD v2 layer model | Frontend architecture | Layers `app → pages → widgets → features → entities → shared`, slices with a public `index.ts`, one-directional imports. Five screens still benefit: each slice owns its model and UI, and the SSE transport stays in `shared/api` instead of leaking into pages. |
| `steiger` | **0.10.x** | FSD structure linter | Machine-checks layer direction, public API and slice boundaries; runs in CI so the architecture does not decay into folders. |
| `@tanstack/react-query` | 5.x | Server state and cache | Initial snapshots and command-triggered invalidation; SSE updates write into the same cache entries. |
| `react-router-dom` | 7.x | Routing | One route per page slice. |
| Spotless | **8.11.0** | Kotlin formatting | `ktlint()` for `*.kt`/`*.kts`, plus whitespace and final-newline rules; `spotlessCheck` runs inside `check`. |
| Prettier | 3.x | Frontend formatting | `format` / `format:check` npm scripts, wired into `:frontend:check`. |

### Module topology **[pivot]**

| Module | Kind | Owns | Depends on |
|---|---|---|---|
| `platform` | `java-platform` | Re-exports the Boot and Axoniq BOMs via `api(platform(...))` with `allowDependencies()`; Boot manages Jackson | — |
| `domain` | library | Events, commands, queries, ids, tag keys, `DomainRejection` — the contract every service compiles against | Axon annotations only |
| `write` | Spring Boot app | `@CommandHandler` slices, decision state, `@EventCriteriaBuilder`, seeding is **not** here | `domain`, Axoniq starter (brings the connector) |
| `read` | Spring Boot app | `@EventHandler` projections writing **PostgreSQL** (JPA + Flyway), `@QueryHandler`s reading it through Spring Data JDBC, and `QueryUpdateEmitter` for live updates | `domain`, JPA, Spring Data JDBC, PostgreSQL, Axoniq starter |
| `api` | Spring Boot app | REST + security, `CommandGateway`/`QueryGateway`, seeding, race endpoint, serves the SPA | `domain`, Axoniq starter, web, security |
| `frontend` | node-gradle module | React/Vite build to `dist/` | — |

### Service responsibilities
- **`write`** — the only service that appends events. No datasource, no JPA, no read models. It does not need the read schema at all.
- **`read`** — one service, both halves of the read side. A tracking processor consumes events and writes denormalized tables in PostgreSQL (JPA + Flyway); the same service answers `@QueryHandler`s through Spring Data JDBC into immutable rows and emits subscription-query updates. Because the writer and the emitter live in one process, snapshot and updates cannot come from different sources.
- **`api`** — the only HTTP surface the browser sees. It never touches events or SQL directly; it sends commands and queries over Axon Server.
- **Seeding** lives in `api` because it must dispatch real commands through the gateway — exactly the path a user takes.
### Read model storage **[pivot]**
- PostgreSQL is the read model. One service owns it end to end: writes, migrations and reads.
- JPA is allowed **only** in `read` (the schema writer). It stays off `write`, where `JpaEventStoreAutoConfiguration` could install an aggregate-based, DCB-incapable store when the connector is absent or disabled.
- `read` runs Flyway and a startup smoke check that executes each query with `LIMIT 0`, so a missing table or renamed column fails at boot.
- Read models are eventually consistent: the gateway's POST returns before `read` commits. The UI shows the pushed update when it arrives; it never decrements seats client-side.
### Formatting

- **Kotlin:** Spotless 8.11.0 with `ktlint()` for `*.kt` and `*.kts`, plus `trimTrailingWhitespace()` and `endWithNewline()`. `spotlessCheck` is attached to `check`, so `./gradlew build` fails on drift.
- **Frontend:** Prettier with `format` (write) and `format:check` (verify) npm scripts; `:frontend:check` depends on `format:check`.
- **One line length: 100 everywhere.** ktlint reads `max_line_length = 100` from the root `.editorconfig` (Spotless picks up the top-level file by default); Prettier reads `.editorconfig` by default; `.prettierrc.json` pins `printWidth: 100` explicitly so both numbers match even if a call site forgets the flag. Markdown is not wrapped (`proseWrap: preserve`) — prose lines are a review concern, not a formatter's.
- **Shared baseline:** a root `.editorconfig` (LF, final newline, trimmed whitespace, 4-space Kotlin, 2-space TS/JS/JSON/CSS, `max_line_length = 100`).
- **Workflow:** `./gradlew spotlessApply` and `npm run format` are the only sanctioned normalizers. CI verifies; it never rewrites.
- **Repository-level files:** `scripts/format.sh` / `scripts/format-check.sh` run Prettier 3.9.10 over the repository, honouring `.prettierignore`: `.planning/`, `AGENTS.md`, `.agents/`, `.opencode/`, `*.jsonc`, lockfiles, build output and the SPA are excluded — the first two are machine-read, the SPA owns its own config.
- **Enforcement point:** CI only (project decision) — no pre-commit hook, so a local commit can be unformatted but cannot be merged.

### Read-side caching — deferred

### Frontend verification: Playwright

- **Playwright MCP** (`@playwright/mcp` 0.0.85) — configured in the tracked `opencode.jsonc` under `mcp.servers`. The agent drives the running SPA on `:8080` headless: navigate, click, assert visible state, and observe an SSE update without a reload. It is a verification aid, not a test suite, and it never replaces CI.
- **`@playwright/test` 1.65.0** — the regression suite in `frontend/e2e/`, with `playwright.config.ts` at `baseURL: http://localhost:8080`. Tests run against the full Compose stack (`docker compose up -d` + the three services), never against a mocked backend, because the point is to prove the distributed path.
- **Selectors:** `data-testid` only on elements the tests own; no text or CSS-structure selectors, which break on copy changes.
- **Synchronisation:** assert on application state, never `waitForTimeout`. For SSE, wait for the specific value to appear (Playwright's auto-retrying assertions handle it); a timeout is a test bug, not a UI bug.
- **State isolation:** each test creates its own course with a UUIDv7 id; the demo seed is disabled in the E2E profile, so parallel tests do not fight over one course.
- **CI:** an `e2e` job is a Phase 8 deliverable: it runs after the stack is up and publishes the HTML report as an artifact. Until then its absence is a missing gate, not a pass.
- **FSD boundary:** E2E code lives outside `frontend/src`, so steiger's layer rules do not apply to it; shared helpers live in `frontend/e2e/fixtures`.
### Idempotency and retries

- **Course creation is idempotent by natural key.** `POST /api/courses` takes a client-supplied `courseId`; the handler decides against `courseId`-tagged events, and a repeat returns the existing course without appending. This closes the only command with no natural uniqueness.
- **Other commands are idempotent by effect, not by response.** A repeated enroll, waitlist join or drop appends nothing and returns a named business rejection (`already.enrolled`, `already.waitlisted`, `not.enrolled`). The state stays correct; the caller learns it was a repeat.
- **No automatic retries anywhere.** The gateway does not retry 409 or 422; a 5xx may be retried only by the caller and only for `CreateCourse`, which is keyed. This is a policy, not a framework default — Axon does not document command deduplication by message id, so none is assumed.
- **Seeding is separately idempotent** (PLAT-02): it queries before dispatch, so a restart does not duplicate scenarios.

### Identifiers: UUIDv7

- **Format:** UUIDv7 (RFC 9562) — time-ordered, so ids sort roughly chronologically and B-tree indexes keep locality. Random UUIDv4 is explicitly rejected: it fragments indexes and makes an event journal unreadable.
- **Library:** `com.github.f4b6a3:uuid-creator:6.1.1` (verified on Maven Central; `UuidCreator.getTimeOrderedEpoch()`). JDK 21 has no UUIDv7 generator.
- **Kotlin representation:** `@JvmInline value class CourseId(val value: UUID)` and `StudentId(val value: UUID)`. `toString()` returns the canonical UUID string, which is what `@EventTag` stores; there is no second string format to drift.
- **Boundaries:** JSON and URL carry the canonical string; Spring converts `@PathVariable UUID`; the read model stores the native `uuid` column.
- **Creation:** the client generates the id with the `uuid` npm package (`v7()`), which is what makes idempotent `CreateCourse` possible without a server round-trip. Server-side code generates v7 for seeded data.
- **Tests:** ids are fixed constants, never generated, so assertions and event counts stay stable.
- **Not UUIDv4, not a sequence.** A database sequence cannot be client-supplied, and v4 breaks the chronological property the demo relies on when reading the journal.
### Frontend architecture: Feature-Sliced Design

- **Import direction is one-way:** `app → pages → widgets → features → entities → shared`. A slice may import only from layers below it.
- **No cross-imports inside a layer.** Slices of the same layer never import each other; composition happens in the layer above.
- **Every slice has a public API** (`index.ts`). Internal files are private; deep imports are a lint error.
- **Segments are consistent:** `ui`, `model`, `api`, `lib`, `config` — only the ones a slice needs.
- **`shared` holds nothing domain-specific.** No `CourseCard` in `shared/ui`; that belongs to `entities/course`.
- **Pages compose, they do not fetch.** Data loading lives in entity/feature `api` segments.
- **SSE lives in `shared/api/sse`** as a generic `EventSource` wrapper; `entities/<x>/api` types it with the read-model shape; features and widgets subscribe through the entity's public API.
- **Enforcement:** `steiger ./src --fail-on-warnings` in CI (`npm run lint:fsd`) plus the `@/` path alias so boundaries are expressible and reviewable. Without `--fail-on-warnings` several FSD diagnostics stay warnings and the build stays green.
### Live updates: SSE over subscription queries **[pivot]**
- **Transport:** `text/event-stream` returned as `Flux<ServerSentEvent<CourseView>>` from `api`. Spring MVC 7 adapts a `Flux` like `SseEmitter`, with reactive backpressure — no manual emitter bridging.
- **Subscription:** one `QueryGateway.subscriptionQuery(...)` call returns a single `Publisher`: the first item is the snapshot, later items are updates. There is no `SubscriptionQueryResult` in AF 5.4.1.
- **Who emits:** `read` advertises `WatchCourseViewQuery`, writes the row in the same service, and is therefore the only node that emits — no cross-service notification exists. The `@EventHandler` publishes a Spring application event carrying the new view; a `@TransactionalEventListener(phase = AFTER_COMMIT)` calls `QueryUpdateEmitter.emit(...)` with it, so a rolled-back write never reaches subscribers.
- **No `ViewChanged` command.** An earlier design sent one through `CommandGateway`; that was wrong twice — a command expresses intent, not a fact, and it forced `read` to hold a command handler it should not have.
- **Ordering:** the view carries a monotonically increasing `version`; `read` ignores an emission older than the last one it sent, which covers reordered or duplicated handler invocations.
- **Delivery is at-most-once across the commit boundary:** an emit that fails after commit is lost until the next change; a reconnecting client still receives a fresh snapshot from the subscription's initial item. An outbox would close the gap at the cost of a second writer — rejected for this project.
- **Dependencies:** `reactor-core` is **mandatory** for subscription queries (Axon docs); declare it explicitly in `api`. `spring-webflux` is not required for a `Flux` return.
- **Catch-up:** each event carries `id:` = read-model `version`; honour `Last-Event-ID` on reconnect.
- **Lifecycle:** Spring MVC 7 uses `-1` (no timeout) for a streaming `Flux`, so `spring.mvc.async.request-timeout` is not the control here — rely on a heartbeat plus the container's async timeout, and register `QueryShutdownManager.closeImmediately()` so `SIGTERM` does not hang on open streams.
- **Snapshot and updates share one source:** both are produced by `read`, so the "GET then subscribe" gap disappears. That pattern remains forbidden regardless.
- **Test seam:** `curl -N` shows snapshot plus one pushed update; a killed client drops the subscription; the Phase 8 integration test asserts all three.

### DCB in the Java API — the real names
| Concept | Type / annotation | Package |
|---------|-------------------|---------|
| Event tag | `@EventTag(key = "...")` on a data-class property | `org.axonframework.eventsourcing.annotation` |
| Tag value object | `Tag.of(String key, String value)` | `org.axonframework.messaging.eventstreaming` |
| Query criteria | `EventCriteria.havingTags(...).andBeingOneOfTypes(...)`, `EventCriteria.either(...)` | `org.axonframework.messaging.eventstreaming` |
| Criteria on an entity | `@EventCriteriaBuilder` — static method returning `EventCriteria` | `org.axonframework.eventsourcing.annotation` |
| Entity declaration | `@EventSourced(tagKey = ..., idType = ...)` | `org.axonframework.extension.spring.stereotype` |
| Command handler | `@CommandHandler` (bean method or entity method) | `org.axonframework.messaging.commandhandling.annotation` |
| State injection | `@InjectEntity State state`, `@InjectEntity(idResolver = ...)` | `org.axonframework.modelling.annotation` |
| Event append | `EventAppender` (commit-scoped, transactional) | `org.axonframework.messaging.eventhandling.gateway` |
| Evolve | `@EventSourcingHandler` | `org.axonframework.eventsourcing.annotation` |
| Creator | `@EntityCreator` | `org.axonframework.eventsourcing.annotation.reflection` |
| Write guards | `AppendCondition`, `ConsistencyMarker`, `SourcingCondition` | `org.axonframework.eventsourcing.eventstore` |
| Streaming guard | `StreamingCondition` | `org.axonframework.messaging.eventstreaming` |
### The shape of a DCB command handler

- **`@EventCriteriaBuilder` is the annotation that marks where the boundary is chosen** — that is the project's "code annotations mark the boundary" requirement.
- Kotlin has no statics: the builder lives on a `companion object` and is annotated `@JvmStatic`.
- The entity id type is a *handle for the decision*, not an aggregate identity.
## Installation
| Command | What it does |
|---|---|
| `docker compose up -d` | Starts Axon Server (`:8124`) and PostgreSQL (`:5432`). |
| `./gradlew build` | Builds every module, including the SPA via `:frontend`. |
| `./gradlew bootRun --parallel` | Starts `write`, `read`, `api` in one Gradle run. |
| `./gradlew :frontend:dev` | Vite on `:5173` with `/api` proxied to the gateway. |

## Rules that keep the topology honest

## Kotlin adaptation — what research did NOT verify

| Risk | Why | How to settle |
|------|-----|---------------|
| `@EventTag` annotation target | `@EventTag` is declared `@Target({METHOD, FIELD, ANNOTATION_TYPE})` — `PARAMETER` is absent, so `@param:` is a compile error, not a silent no-op. | Put `@EventTag` on the constructor property with no use-site target (Kotlin emits it on the field) or use `@field:`; `@get:` also works. Prove it with the stored-tag assertion. |
| `@EventCriteriaBuilder` statics | Axon expects a static method; Kotlin has none. Missing it fails **silently**: `resolve()` falls back to `havingTags(tagKey, id)` with no exception, and that fallback is behaviourally close enough that no black-box test catches it. | `companion object { @JvmStatic @EventCriteriaBuilder fun criteria(...) }`; assert the rendered criteria (ENRL-06), not just stored tags. |
| Kotlin events + Jackson | Data classes need the Kotlin Jackson module for constructor binding. On Boot 4, Axon and Spring MVC share Jackson 3. | Add `tools.jackson.module:jackson-module-kotlin`; confirm one round-trip and no Jackson 2 databind on the classpath. |
| Final classes by default | Kotlin classes/methods are final; Spring proxying and Axon enhancement expect open types where they subclass. | `kotlin("plugin.spring")` opens `@Component`/`@Configuration`/`@Transactional`; add `allOpen` only if the metamodel insists. |
| Nullability at boundaries | Axon/Spring APIs are platform types; `!!` hides real null cases. | Declare message/handler signatures non-null; treat Axon return values as nullable until checked. |
## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Three services + Axon Server **[pivot]** | Single process, one app | If the goal is DCB only: no Axon Server, no Postgres, no license, no eventual consistency. This was the original plan and remains the lower-risk fallback. |
| Axon Server 2026.1.7 (Docker) | Embedded `InMemoryEventStorageEngine` | Tests and spikes only. It cannot be shared across processes, so it cannot carry this topology. |
| Axoniq Spring Boot starter (commercial) | OSS `org.axonframework.extensions.spring:axon-spring-boot-starter` | Only in the single-process fallback; the OSS starter has no Axon Server connector. |
| PostgreSQL read model | In-memory read models | Only in the single-process fallback, where the read side shares a JVM with everything else. |
| Spring Boot 4.1.2 | Spring Boot 3.5.16 | Fallback if Boot 4 fights the commercial starter or Spring Security 7. Re-introduces manual Jackson 3 BOM ordering. |
| One `read` service | Split projection / query service | Only if live updates are dropped: a split forces a cross-service notification, and commands cannot honestly carry one. |
## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `InMemoryEventStorageEngine` in a running service | Process-local; the other three services would see nothing | Axon Server |
| `axon-server-connector` together with `spring-boot-starter-data-jpa` in `write` | JPA event-store auto-config competes with the connector's store; DCB support is lost if it wins | No datasource in `write` |
| An ORM in the query path | A reader with write-capable mapping invites accidental schema changes | Spring Data JDBC into immutable rows; writes stay in the projection path |
| Read models read inside a command handler | The decision must source events, not the projection | `@InjectEntity` + `EventCriteria` |
| Sharing JPA entities between the write and read paths | One mutable entity serving two purposes makes the read path write-capable again | JPA entities for writes, immutable row types for reads, in the same service |
| AF4 aggregate APIs (`@Aggregate`, `@AggregateIdentifier`, `AggregateLifecycle.apply`) | Removed/renamed in AF5; tutorials and LLM output still emit them | `@EventSourced` + `@EventCriteriaBuilder` + `@InjectEntity` |
| Kafka/JGroups extensions for AF5 | Latest Kafka extension is **4.12.1** and JGroups docs are **4.12** — AF4-era, not AF5 | Axon Server |
| `axoniq-springcloud` | Commercial, and it distributes commands/queries only — events still need Axon Server or a shared store | Axon Server |
## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `io.axoniq.framework:axoniq-framework-bom:5.4.1` | `org.axonframework:*` 5.4.1, `axoniq-spring-boot-starter` 5.4.1 | The commercial BOM includes the OSS modules; do not import both BOMs. `axoniq-spring-boot-starter` already depends on `axon-server-connector`, so declare the starter alone |
| `axoniq-spring-boot-starter:5.4.1` | Spring Boot 4.1.2 (supported), 3.5.16 (Axon's baseline) | Boot 2 unsupported |
| Axon 5.4.x converters | Jackson 3 (`tools.jackson:*`) | Boot 4.1.2 manages `tools.jackson:jackson-bom` 3.1.7, which includes `jackson-module-kotlin`, so module and databind stay aligned. Axon's `3.2.3` property applies to **optional** Jackson dependencies that do not reach a consumer graph — do not force 3.2.3 on top of the Boot-tested set |
| Axon Server 2026.1.7 | AF 5.4.x | Supports DCB; tags are patch-level, so `2026.1.7` is a real image tag and `2026.1` is not |
| React 19.3.1 / Vite 8.3.7 / TS 7.0.2 | Node 24.20.0 | Matches `create-vite@9.3.4` `template-react-ts` pins |
## Sources
- `https://docs.axoniq.io/axon-framework-reference/5.4/modules/` — main vs extension vs **commercial** modules, both BOMs, `axoniq-postgresql`/`axon-server-connector`/`axoniq-distributed-messaging`/`axoniq-springcloud` grouped as licensed
- `https://docs.axoniq.io/axon-framework-reference/5.4/spring-boot-integration/` — supported Boot matrix (`5.0.3+` → Boot 3 and 4), connector behavior, JPA fallback wording
- `https://docs.axoniq.io/axon-framework-reference/5.4/events/event-store-internals/` — `Tag`, `@EventTag`, `EventCriteria`, `AppendCondition`, `ConsistencyMarker`
- `https://docs.axoniq.io/axon-framework-reference/5.4/events/infrastructure/` — storage-engine catalogue and DCB support per engine
- `https://repo1.maven.org/maven2/io/axoniq/framework/axoniq-framework-bom/maven-metadata.xml` — latest `5.4.1`
- `https://repo1.maven.org/maven2/io/axoniq/framework/axoniq-spring-boot-starter/maven-metadata.xml` — latest `5.4.1`
- `https://repo1.maven.org/maven2/org/axonframework/axon-framework-bom/maven-metadata.xml` — OSS BOM `5.4.1`
- `https://repo1.maven.org/maven2/org/axonframework/extensions/kafka/axon-kafka-spring-boot-starter/maven-metadata.xml` — latest **4.12.1**, i.e. no AF5 Kafka extension
- `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml` — stable `4.1.2`; `4.2.0-RC2` pre-release
- `https://repo1.maven.org/maven2/org/axonframework/axon-parent/5.4.1/axon-parent-5.4.1.pom` — `spring.boot.version=3.5.16`, `jackson.version=3.2.3`
- `https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml` — stable `2.4.21`
- `https://github.com/AxonIQ/AxonFramework/tree/main/examples/university-java-springboot-3` — DCB command-handler shape quoted above
## Gaps / must validate during implementation

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

## Language policy

- **Repository artifacts are English.** Every file written into this repo — source code, comments, KDoc, commit messages, README, design notes, and everything under `.planning/` — is written in English.
- **Responses follow the participant's language, defaulting to English.** No language preference is committed to this repo: participants may work in different languages, so GSD's default is English and the assistant mirrors the language a participant writes in.
- **Never translate identifiers.** Class, function, event, command, tag-key and endpoint names stay in English regardless of the conversation language.
- Subagent prompts stay in English; subagent summaries follow the invoking conversation's language.
## Kotlin

- Kotlin on JVM 21, Gradle Kotlin DSL (`build.gradle.kts`).
- Events and commands are immutable `data class`es; no mutable state in the event contract.
- Prefer `val`, explicit non-null types at boundaries, and no `!!` on Axon/Spring return values.
- Keep Axon decision-state classes small and side-effect free; they are decision inputs, not aggregates.
- Annotate external/API boundaries, not obvious internals.

## Event sourcing
- Events are the only shared *state* contract; commands and queries are shared message types in `domain`, and no service depends on another service's implementation.
- Tag keys are constants in one shared tag-keys object used by both `@EventTag` and `@EventCriteriaBuilder`.
- Never read a read model inside a command handler — the decision sources events.
- Live updates are whole read-model snapshots over SSE; only the service advertising the watch query emits, and the gateway never forwards domain events.
- The read service emits from `@TransactionalEventListener(AFTER_COMMIT)`, never directly inside an `@EventHandler`; a rolled-back write must not reach subscribers.
- `read` owns both halves in one process: JPA + Flyway for the write path, Spring Data JDBC into immutable rows for the read path. Do not introduce a second read process, and keep the two persistence styles in separate packages.
## Testing
- Slice tests use `AxonTestFixture.with(application.configurer())` against the production configurer; contention tests never do — the fixture dispatches one action per `when` phase and cannot overlap.
- One fixture per test, unique ids per test, no `Thread.sleep`.
- Contention tests are barrier-driven against the live Axon Server, assert on the rejection mechanism, and carry a negative control that is tagged and excluded from the default run.
- Test names read as sentences describing the invariant.

## Git
- Commit messages: conventional prefixes (`feat`, `fix`, `docs`, `chore`, `test`, `refactor`, `perf`, `ci`, `build`, `style`, `revert`), optional scope (`feat(command): ...`), imperative, English, ≤72 characters on the subject line.
- The PR title becomes the squash commit in `main`, so it must follow the same format; CI enforces it (`pr-title` workflow).
- One logical change per commit; planning artifacts commit alongside the work they describe.

## Review loop (planning artifacts)

### When it runs

- Any substantial change to `PROJECT.md`, `REQUIREMENTS.md`, `ROADMAP.md`, `research/*`, or this file.
- The loop completes before a human is asked to read the artifact.

### Rounds

- **Round 1:** three reviewers in parallel, read-only — consistency, requirements/roadmap quality, technical fact-check.
- **Round 2+:** one consistency reviewer plus only the specialist whose domain still has open findings.
- Later rounds review the diff since the previous round and its cross-file consequences; only round 1 re-reads the whole corpus.
- Findings are classified `blocker`, `major`, `minor`.

### Exit criteria

- zero open `blocker` and `major`;
- the round's finding count is not greater than the previous round's;
- no finding closed in an earlier round is reopened.

### Convergence control

- **Hard cap: 3 rounds.** A round 3 with open blockers or majors freezes the documents and escalates to the human with the open list; a fourth round needs an explicit human decision.
- **Oscillation stop:** a reopened finding, or two findings prescribing opposite fixes, ends the loop and becomes a decision recorded in `PROJECT.md` Key Decisions.
- **Diminishing-returns stop:** a round producing only minor findings ends the loop; minors are recorded, not fixed by default.
- **Reviewer bar:** reviewers report only defects that would change implementation or mislead a reader; style preferences are not findings.

### Recording

- Every round appends to `.planning/PLANNING-REVIEW.md`: round number, reviewers, counts by severity, status per finding.
- Acceptance marks the artifact reviewed; a later change touching more than one file or any constraint starts a new round 1.
## Branching

- One branch per phase: `gsd/phase-{phase}-{slug}` (config template), merged into `main` through a pull request.
- `main` is protected: no direct pushes, no force-push, no deletion, `build`, `format` and `title` checks required, conversation resolution required.
- PR titles follow the same conventional-commit format as commits — the squash commit in `main` takes the PR title and body.
- Only squash merges are allowed; the branch is deleted after merge.

## Formatting

- Kotlin: Spotless + ktlint. Normalize with `./gradlew spotlessApply`; CI verifies via `spotlessCheck`.
- Frontend: Prettier. Normalize with `npm run format` in `frontend/`; CI verifies via `npm run format:check`.
- `.editorconfig` in the root is the shared baseline; do not override it per editor.
- Line length is **100** for Kotlin (via `.editorconfig` `max_line_length`) and for Prettier (`printWidth`); Markdown prose is not rewrapped.
- Repo-level files (docs, README, workflows, root JSON): `scripts/format.sh` to write, `scripts/format-check.sh` to verify.
- `.planning/`, `AGENTS.md` and `*.jsonc` are never Prettier-formatted: they are machine-read or generated.
- CI never rewrites files — a red formatting check is fixed locally and pushed.
## Frontend (Feature-Sliced Design)

- Layers: `app → pages → widgets → features → entities → shared`; imports only point downward.
- No cross-imports inside a layer — compose in the layer above.
- Every slice exposes `index.ts`; deep imports are forbidden.
- Segments: `ui`, `model`, `api`, `lib`, `config` — only what a slice uses.
- `shared` contains no domain knowledge; `CourseCard` belongs to `entities/course`, not `shared/ui`.
- Pages compose; data fetching lives in entity/feature `api` segments.
- SSE transport lives in `shared/api/sse`; entity `api` segments type it; features subscribe via public APIs.
- `npm run lint:fsd` (steiger) runs as part of `build` once the frontend module exists.
## Retries and idempotency

- No automatic command retries in the gateway; 409 and 422 are terminal for a request.
- `CreateCourse` carries a client-supplied `courseId` and is safe to retry; a repeat returns the existing course.
- Enroll, waitlist join and drop are idempotent by effect: a repeat appends nothing and returns a named business rejection.
- Never retry a command you cannot key.

## End-to-end tests

- Playwright lives in `frontend/e2e/`, outside `src`, so FSD layer rules do not apply to it.
- Tests run against the real Compose stack; never mock the backend — the distributed path is the subject.
- Select by `data-testid`; never by text or DOM structure.
- No `waitForTimeout`; wait for the observable value (Playwright retries assertions).
- Each test creates its own course with a UUIDv7 id; demo seeding is disabled in the E2E profile.
- The Playwright MCP server is for agent-driven exploration; it does not replace the suite.
## Identifiers

- UUIDv7 everywhere (`uuid-creator` server-side, `uuid` npm `v7()` in the SPA). UUIDv4 is rejected: no chronological order, worse index locality.
- Wrap ids in `@JvmInline value class` (`CourseId`, `StudentId`); never pass raw `UUID` through domain code.
- The canonical UUID string is the only string form — it is what `@EventTag` stores and what URLs carry.
- Tests use fixed id constants, never generated ones.
## Tags

- Tag keys are domain-neutral and type-implied: `courseId`, `studentId`. Do not repeat the type in the value.
- `either(courseId, studentId)` is the sanctioned widening for a rule that genuinely spans two subjects (v2: per-student seat limit).

## Tooling
- After cloning, run `/setup-tooling` in OpenCode (or `./scripts/setup-tooling.sh` without an agent) — it installs the pinned GSD Core (`1.20.1`, OpenCode, project scope) or verifies an existing install.
- `.opencode/` is generated and gitignored. Only hand-written project commands under `.opencode/commands/` are tracked, each with an explicit unignore line in `.gitignore`; never hand-edit the generated `opencode.json`.
- Project MCP servers belong in the tracked root `opencode.jsonc` under `mcp.servers`; secrets use `{env:NAME}`.
- `docs/tooling.md` is the human-facing version of these rules.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

## Shape

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
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

| Skill | Description | Path |
|-------|-------------|------|
| gh-cli | GitHub CLI (gh) comprehensive reference for repositories, issues, pull requests, Actions, projects, releases, gists, codespaces, organizations, extensions, and all GitHub operations from the command line. | `.agents/skills/gh-cli/SKILL.md` |
| github-actions-workflows | Background knowledge (not a command) on writing and reviewing GitHub Actions workflows, composite actions, and reusable workflows. Covers permissions and GITHUB_TOKEN, pull_request_target and workflow_run, script injection through github.event context, SHA pinning of every uses reference with a version comment, OIDC instead of stored cloud credentials, secrets inherit versus named secrets, environments, concurrency and cancel-in-progress, timeout-minutes, fail-fast, actions/cache keys and cache poisoning, upload-artifact and download-artifact, paths filters on required checks, workflow_dispatch inputs, cron, reusable workflow vs composite action and workflow_call, action.yml rules, thin SHA-pinned wrapper actions, Dependabot github-actions configuration and cooldown, GITHUB_OUTPUT, actionlint and zizmor findings, and how to verify a workflow change. Use when adding or changing a file under .github/workflows, an action.yml, or Dependabot configuration for actions. | `.agents/skills/github-actions-workflows/SKILL.md` |
| pull-request-review | Review a pull request or branch diff like a staff engineer. Verify it does what the issue asks, find correctness and security defects with file and line evidence, check tests and compatibility, and deliver prioritized, actionable comments. Use for PR review, branch review, and "review my changes". | `.agents/skills/pull-request-review/SKILL.md` |
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-fast` for a trivial task inline, with no subagents and no PLAN.md
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
