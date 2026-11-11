# Stack Research

**Domain:** Axon Framework 5 Dynamic Consistency Boundary (DCB) showcase — course enrollment with seat capacity, FIFO waitlist, React SPA
**Researched:** 2026-11-10
**Confidence:** MEDIUM (structure and DCB mechanics read from official AxonIQ docs + the official `AxonIQ/university-demo` source; the distributed topology and the commercial Axoniq modules are documented but less trodden here — flagged as verification items)

> Confidence note: every claim below was checked against a primary source — AxonIQ's docs site, Maven Central metadata/POMs, npm registry, or AxonFramework source.
>
> **Architecture pivot (2026-11-10):** the project moved from a single process with an embedded store to three services behind Axon Server, with a PostgreSQL read model. Rows below that depend on that pivot are marked **[pivot]**.

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
| Gradle multi-module + `java-platform` | **9.7.x** wrapper (9.7.0) + **Kotlin DSL** + version catalog | Build | Six modules (see topology). 9.7 is the highest line fully supported by Kotlin Gradle Plugin 2.4.21 (max 9.7.0); Gradle 9.9 is newer but outside the tested range, and Boot 4.1.2 requires only >= 8.14. Versions live in `gradle/libs.versions.toml`; `:platform` re-exports the BOMs. `./gradlew bootRun --parallel` starts all three services. |
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

**Commands and queries are messages in `domain`.** That is what keeps `api` from depending on `write` or `read` implementation modules: it sends message types and Axon Server routes them to whichever service has the handler.

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
- **One line length: 100 everywhere.** ktlint reads `max_line_length = 100` from the root `.editorconfig` (Spotless picks up the top-level file by default); Prettier reads `.editorconfig` by default; `.prettierrc.json` pins `printWidth: 100` explicitly so both numbers match even if a call site forgets the flag. Markdown prose is wrapped at the same 100 (`proseWrap: always`) for repo-level docs; `.planning` is excluded, so GSD's machine-read files are untouched.
- **Shared baseline:** a root `.editorconfig` (LF, final newline, trimmed whitespace, 4-space Kotlin, 2-space TS/JS/JSON/CSS, `max_line_length = 100`).
- **Workflow:** `./gradlew spotlessApply` and `npm run format` are the only sanctioned normalizers. CI verifies; it never rewrites.
- **Repository-level files:** `scripts/format.sh` / `scripts/format-check.sh` run Prettier 3.9.10 over the repository, honouring `.prettierignore`: `.planning/`, `AGENTS.md`, `.agents/`, `.opencode/`, `*.jsonc`, lockfiles, build output and the SPA are excluded — the first two are machine-read, the SPA owns its own config.
- **Enforcement point:** CI only (project decision) — no pre-commit hook, so a local commit can be unformatted but cannot be merged.

Root wiring:

```kotlin
group = "io.github.aanbrn.showcase"
version = "0.1.0-SNAPSHOT"

plugins {
    id("com.diffplug.spotless") version "8.11.0" apply false
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "com.diffplug.spotless")
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                ktlint()
                trimTrailingWhitespace()
                endWithNewline()
            }
            kotlinGradle { ktlint() }
        }
    }
}
```

### Read-side caching — deferred

No server-side cache in v1: queries hit an indexed, denormalized table, and the SPA already caches through react-query.

If it is added later (PERF-01), the only safe shape is a `@Cacheable` on the query handlers with `@CacheEvict` in the **same** `@TransactionalEventListener(AFTER_COMMIT)` that calls `QueryUpdateEmitter.emit`, keyed by `courseId`, with a TTL as a backstop. A cache invalidated anywhere else creates a split brain: SSE pushes the fresh value while the next GET returns the stale one.

### Frontend verification: Playwright

Two separate things, both used here.

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

```
frontend/src/
├── app/                    # composition root: providers, router, styles
│   ├── providers/          # QueryClient, session, SSE lifecycle
│   └── routes/
├── pages/                  # one route each, compose widgets and features
│   ├── login/
│   ├── courses/
│   ├── course-detail/
│   ├── my-enrollments/
│   └── race-demo/
├── widgets/                # composed blocks reused across pages
│   ├── course-table/
│   ├── waitlist-panel/
│   └── connection-status/
├── features/               # user actions
│   ├── session/login/
│   ├── course/create/
│   ├── enrollment/enroll/
│   ├── enrollment/drop/
│   ├── waitlist/join/
│   └── race/run/
├── entities/               # domain display + its API/model
│   ├── course/
│   ├── enrollment/
│   ├── waitlist/
│   └── student/
└── shared/                 # no business knowledge
    ├── api/                # fetch client, SSE client, query keys
    ├── ui/                 # primitives: Button, Table, Badge
    ├── lib/                # formatting, guards
    └── config/             # env, route constants
```

Rules:

- **Import direction is one-way:** `app → pages → widgets → features → entities → shared`. A slice may import only from layers below it.
- **No cross-imports inside a layer.** Slices of the same layer never import each other; composition happens in the layer above.
- **Every slice has a public API** (`index.ts`). Internal files are private; deep imports are a lint error.
- **Segments are consistent:** `ui`, `model`, `api`, `lib`, `config` — only the ones a slice needs.
- **`shared` holds nothing domain-specific.** No `CourseCard` in `shared/ui`; that belongs to `entities/course`.
- **Pages compose, they do not fetch.** Data loading lives in entity/feature `api` segments.
- **SSE lives in `shared/api/sse`** as a generic `EventSource` wrapper; `entities/<x>/api` types it with the read-model shape; features and widgets subscribe through the entity's public API.
- **Enforcement:** `steiger ./src --fail-on-warnings` in CI (`npm run lint:fsd`) plus the `@/` path alias so boundaries are expressible and reviewable. Without `--fail-on-warnings` several FSD diagnostics stay warnings and the build stays green.

### Live updates: SSE over subscription queries **[pivot]**

The emitter is local to the node that advertises the query (`QueryUpdateEmitter` resolves the local `QueryBus`). Therefore the writer announces, and the reader fans out.

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

```kotlin
@Component
class EnrollStudentHandler {

    @CommandHandler
    fun handle(
        command: EnrollStudent,
        @InjectEntity decision: EnrollmentDecision,
        appender: EventAppender,
    ) {
        appender.append(decide(command, decision))
    }

    private fun decide(c: EnrollStudent, d: EnrollmentDecision): List<StudentEnrolled> {
        if (!d.courseExists) throw DomainRejection("course.unknown")
        if (d.isEnrolled(c.studentId)) throw DomainRejection("already.enrolled")
        if (d.seatsFree <= 0) throw DomainRejection("course.full")
        return listOf(StudentEnrolled(c.courseId, c.studentId))
    }
}
```

## Installation

`settings.gradle.kts`:

```kotlin
rootProject.name = "axon5-dcb-showcase"
include("platform", "domain", "write", "read", "api", "frontend")
```

Version catalog — `gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.4.21"
springBoot = "4.1.2"
axon = "5.4.1"
node = "24.20.0"
nodeGradle = "7.1.0"

[libraries]
spring-boot-bom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "springBoot" }
axoniq-bom = { module = "io.axoniq.framework:axoniq-framework-bom", version.ref = "axon" }
axoniq-spring-boot-starter = { module = "io.axoniq.framework:axoniq-spring-boot-starter" }
axon-test = { module = "org.axonframework:axon-test" }
axon-kotlin = { module = "org.axonframework.extensions.kotlin:axon-kotlin" }
jackson-kotlin = { module = "tools.jackson.module:jackson-module-kotlin" }
kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "kotlin" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "springBoot" }
node-gradle = { id = "com.github.node-gradle.node", version.ref = "nodeGradle" }
```

`platform/build.gradle.kts` — the single place BOMs are imported:

```kotlin
plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(platform(libs.axoniq.bom))
    // Axon's BOM does not manage Jackson 3; align module-kotlin with Axon's databind.
}
```

> **Gradle rule (verified on 8.14.3 and 9.9, pinned to 9.7.1):** a `java-platform` cannot take BOMs inside `constraints { }` — the notation is a dependency, not a constraint, and platforms refuse real dependencies unless `allowDependencies()` is set. Import BOMs with `api(platform(…))` inside `dependencies { }`.

`write/build.gradle.kts` — write side only:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(platform(project(":platform")))
    implementation(project(":domain"))
    implementation(libs.axoniq.spring.boot.starter)
}

// No datasource. No JPA. No read models.
```

`read/build.gradle.kts` — event handlers writing PostgreSQL, query handlers reading it, emitter for live updates:

```kotlin
dependencies {
    implementation(platform(project(":platform")))
    implementation(project(":domain"))
    implementation(libs.axoniq.spring.boot.starter)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")  // Flyway 12 core has no PostgreSQL support
}
```

`api/build.gradle.kts` — HTTP surface only:

```kotlin
dependencies {
    implementation(platform(project(":platform")))
    implementation(project(":domain"))
    implementation(libs.axoniq.spring.boot.starter)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")  // `-web` is deprecated in Boot 4
    implementation("io.projectreactor:reactor-core")  // mandatory for subscription queries
    implementation("org.springframework.boot:spring-boot-starter-security")
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(":frontend:assembleFrontend")
    from(project(":frontend").layout.projectDirectory.dir("dist")) { into("static") }
}
```

`compose.yaml` — the only infrastructure:

```yaml
services:
  axonserver:
    image: axoniq/axonserver:2026.1.7
    ports: ["8024:8024", "8124:8124"]
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: enrollment
      POSTGRES_USER: enrollment
      POSTGRES_PASSWORD: enrollment
    ports: ["5432:5432"]
```

| Command | What it does |
|---|---|
| `docker compose up -d` | Starts Axon Server (`:8124`) and PostgreSQL (`:5432`). |
| `./gradlew build` | Builds every module, including the SPA via `:frontend`. |
| `./gradlew bootRun --parallel` | Starts `write`, `read`, `api` in one Gradle run. |
| `./gradlew :frontend:dev` | Vite on `:5173` with `/api` proxied to the gateway. |

Port map: `api` on `:8080`, `write` on `:8081`, `read` on `:8082`, Axon Server on `:8124`, PostgreSQL on `:5432`, Vite on `:5173`. Set them explicitly — three Boot apps on one host collide on `:8080` otherwise.

## Rules that keep the topology honest

1. **`write` has no datasource and no JPA.** One classpath mistake installs an aggregate-based event store and silently voids DCB. Assert it in a guard test: no `EntityManagerFactory`, connector present.
2. **`read` never appends.** It consumes events and answers queries, but only `write` calls `EventAppender`.
3. **Flyway migrations and the event processor live in `read` only.** A guard test fails if another module gains either.
4. **The gateway never touches SQL or the event store.** It only sends messages.
5. **`domain` has no Spring dependency.** It is the compile-time contract; leaking `@Component` into it would let services depend on each other's implementations.
6. **Do not put a routing key on `EnrollStudent`.** Command sequencing defaults to `RoutingKeySequencingPolicy`; a shared routing key serializes the last-seat race away and the demo proves nothing.

## Kotlin adaptation — what research did NOT verify

| Risk | Why | How to settle |
|------|-----|---------------|
| `@EventTag` annotation target | `@EventTag` is declared `@Target({METHOD, FIELD, ANNOTATION_TYPE})` — `PARAMETER` is absent, so `@param:` is a compile error, not a silent no-op. | Put `@EventTag` on the constructor property with no use-site target (Kotlin emits it on the field) or use `@field:`; `@get:` also works. Prove it with the stored-tag assertion. |
| `@EventCriteriaBuilder` statics | Axon expects a static method; Kotlin has none. Missing it fails **silently**: `resolve()` falls back to `havingTags(tagKey, id)` with no exception, and that fallback is behaviourally close enough that no black-box test catches it. | `companion object { @JvmStatic @EventCriteriaBuilder fun criteria(...) }`; assert the rendered criteria (ENRL-06), not just stored tags. |
| Kotlin events + Jackson | Data classes need the Kotlin Jackson module for constructor binding. On Boot 4, Axon and Spring MVC share Jackson 3. | Add `tools.jackson.module:jackson-module-kotlin`; confirm one round-trip and no Jackson 2 databind on the classpath. |
| Final classes by default | Kotlin classes/methods are final; Spring proxying and Axon enhancement expect open types where they subclass. | `kotlin("plugin.spring")` opens `@Component`/`@Configuration`/`@Transactional`; add `allOpen` only if the metamodel insists. |
| Nullability at boundaries | Axon/Spring APIs are platform types; `!!` hides real null cases. | Declare message/handler signatures non-null; treat Axon return values as nullable until checked. |

The OSS `axon-kotlin` extension (AF5) exists and is worth evaluating for value-class ids and reified gateways — but it is not assumed by this plan until proven.

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

1. **Commercial license behavior — known constraints.** AF 5.3.2+ requests an hourly, machine-tied development permission from the Axoniq Platform and logs a warning every 15 minutes when it cannot. **Axon Server 2026.0+ runs 12 hours without a license and then shuts down.** For a long demo session that is a real stop condition; a dev license or a restart is the mitigation.
2. **DCB on Axon Server 5.4.1.** Append conditions and tag-scoped conflict detection across processes — the Phase 4 spike replaces the old in-memory spike.
3. **Token store default.** With Axon Server, tracking-processor tokens should live server-side; confirm `read` starts from the stream on first boot and resumes after restart.
4. **`@EventSourced` on a nested Kotlin `State` + `idType`** with the commercial starter — the docs example is Java and the starter differs from the OSS one.
5. **Axon Server image tag and Postgres tag** — exact versions to pin.
6. **Integration testing.** The race test now needs a live Axon Server (Testcontainers or a shared compose stack); decide which before Phase 2.
7. **Jackson 3 + Kotlin data classes** — one converter stack, no Jackson 2 leak.
