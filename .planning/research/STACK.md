# Stack Research

**Domain:** Event-sourced single-JVM application (Axon Framework 5 + Dynamic Consistency Boundaries) with a Spring Boot backend and a React SPA
**Researched:** 2026-11-10
**Overall confidence:** HIGH for versions and artifact coordinates (verified against `repo1.maven.org` metadata + official AxonIQ docs + AxonFramework source). MEDIUM for two items: in-memory-store DCB behaviour (docs contradict the source) and the React tooling pins.

> Confidence note: this file's confidence is **source-assessed**, not provider-assessed. The GSD `classify-confidence` seam returns a provider-baseline of `LOW` for `webfetch`/`websearch` because neither provider is on its curated allowlist. Every claim below was checked against a primary source — AxonIQ's own docs site, Maven Central `maven-metadata.xml`/POMs fetched from `repo1.maven.org`, npm registry metadata, or AxonFramework source on GitHub — and tagged individually.

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin on JDK 21 | **Kotlin 2.4.21** (latest stable on Maven Central; 2.5.0 is still Beta as of 2026-10-21), `jvmToolchain(21)` | Language / JVM | **Project decision — Kotlin instead of Java.** AF5's hard floor is JDK 21 (official prerequisites); Kotlin compiles to that target. Kotlin data classes replace Java records for immutable events, but annotation targets differ — see "Kotlin adaptation" below for the interop risks to settle in Phases 1–2. |
| Spring Boot | **3.5.16** | App runtime, DI, web | Axon 5.4.1's own parent POM pins `spring.boot.version=3.5.16` — it is the version Axon builds and runs its test matrix on. Official AF5 examples pin the same. Boot 4 is supported but adds Jackson 3 + modular starter churn that competes with the actual lesson. |
| Axon Framework BOM | **5.4.1** (`org.axonframework:axon-framework-bom`) | Version alignment for all Axon modules | Latest release on Maven Central (`latest=5.4.1`, metadata updated 2026-10-21). The BOM manages main modules **and** OSS Spring extensions (`axon-spring`, `axon-spring-boot-starter`, `axon-spring-boot-starter-test`), so you never hand-write an Axon version twice. |
| Axon Spring Boot Starter (OSS) | **5.4.1** (`org.axonframework.extensions.spring:axon-spring-boot-starter`) | Auto-config: handler/entity discovery, buses, processors | It is still published under `org.axonframework.extensions.spring` at 5.4.1 (verified in `maven-metadata.xml`). Its POM pulls only `axon-spring-boot-autoconfigure`, `axon-spring`, `axon-eventsourcing`, `axon-messaging`, `axon-modelling` — **no Axon Server connector**, which is exactly what a no-Docker app wants. |
| Embedded event store | `InMemoryEventStorageEngine` (in `axon-eventsourcing`, no extra artifact) | DCB-capable event store in-process | Zero infrastructure, and it is the default `EventStorageEngine` registered by `EventSourcingConfigurationDefaults`. Its source implements the full DCB contract: `AppendCondition` + per-tag conflict detection + `ConsistencyMarker` + tag/type-filtered `SourcingCondition`. |
| Gradle | 8.14.x wrapper + **Kotlin DSL** (`build.gradle.kts`) | Build | **Project decision — Gradle Kotlin DSL over the research recommendation (Maven).** BOM imports map to `dependencyManagement { imports { mavenBom(…) } }` (first imported wins, same as Maven); startup is `./gradlew bootRun`. Pin the wrapper with `gradle wrapper --gradle-version 8.14.3` and verify the Spring Boot 3.5.16 plugin on Gradle 9 before upgrading. |
| React | **19.3.1** | SPA UI | Current `latest` on npm; what `create-vite@9.3.4`'s `template-react-ts` pins. |
| Vite | **8.3.7** | Dev server + build | Current `latest`; instant HMR, no config needed for a proxy to the Spring backend. |
| TypeScript | **7.0.2** | Frontend types | Pinned as `~7.0.2` by the official `create-vite` React-TS template, so it is the mainstream default rather than an edge case. |

### Axon Modules — what to depend on, and what each is for

| Artifact | Group | Purpose | Notes |
|----------|-------|---------|-------|
| `axon-framework-bom` | `org.axonframework` | BOM | Maven: `type=pom`, `scope=import`; Gradle: `dependencyManagement { imports { mavenBom … } }` |
| `axon-spring-boot-starter` | `org.axonframework.extensions.spring` | Auto-config | Pulls the four modules below transitively; declare alone |
| `axon-eventsourcing` | `org.axonframework` | `EventStore`, `EventStorageEngine`, `@EventTag`, `EventCriteria`, entities | Source of `InMemoryEventStorageEngine` (`...eventsourcing.eventstore.inmemory`) |
| `axon-messaging` | `org.axonframework` | `@CommandHandler`, `@EventHandler`, `@QueryHandler`, `EventAppender`, `Tag` | `Tag` lives in `org.axonframework.messaging.eventstreaming` |
| `axon-modelling` | `org.axonframework` | `@InjectEntity`, `EntityIdResolver`, entity metamodel | Only needed explicitly if you drop the starter |
| `axon-test` | `org.axonframework` | `AxonTestFixture` (given/when/then) | `test` scope; still OSS after the 5.1 commercial split |
| `axon-spring-boot-starter-test` | `org.axonframework.extensions.spring` | Spring-aware test wiring | `test` scope |

### Frontend Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `@tanstack/react-query` | 5.107.1 | Server state: fetch, cache, invalidate after commands | Use for every course/enrollment query. It gives you the "command succeeded → refetch" behaviour for free, which matters for showing a rejected enrollment. |
| `react-router-dom` | 7.18.5 | Two-route SPA (`/courses`, `/courses/:id`) | Optional. A single view with a selected-course panel is enough; add this only if the second screen earns it. |
| `oxlint` | ^1.90.0 | Lint | Ships with the Vite React-TS template; no ESLint config to maintain. |

Deliberately **no** component library, CSS framework, or state manager. The UI is a teaching surface, not a product.

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Gradle Wrapper | Reproducible backend build | `./gradlew bootRun` is the single startup command |
| `npm` (11.19.0) + Vite | Frontend dev/build | `npm run dev`; configure `server.proxy['/api'] → http://localhost:8080` so there is no CORS config |
| `AxonTestFixture` | Behaviour tests | `AxonTestFixture.with(application.configurer())` against the *production* configurer — the pattern AxonIQ's own University demo uses |
| Spring Boot Actuator | `/actuator/health` sanity check | Optional; do not build monitoring around it |

## The single most important configuration rule

**Keep `spring-boot-starter-data-jpa` off the classpath.**

`JpaEventStoreAutoConfiguration` is annotated `@ConditionalOnBean({EntityManagerFactory.class, PlatformTransactionManager.class})` + `@ConditionalOnMissingBean({EventStore.class, EventStorageEngine.class})` and registers itself at `EventSourcingConfigurationDefaults.ENHANCER_ORDER - 500` — i.e. *lower* than the in-memory default, so it **wins**. The engine it installs, `AggregateBasedJpaEventStorageEngine`, does **not** support DCB (official docs: "there is no means to store several tags per event"). A `spring-boot-starter-data-jpa` + H2 dependency added for a read model would therefore silently replace your DCB event store with an aggregate-based one and break the entire premise of the app. Keep read models in memory instead.

If you ever *must* add JPA, you must also declare an explicit `EventStorageEngine` bean so the JPA auto-config backs off.

## Embedded event store: the verified picture

| Engine | Artifact | DCB? | Verdict for this project |
|--------|----------|------|--------------------------|
| `InMemoryEventStorageEngine` | `org.axonframework:axon-eventsourcing` (OSS) | **Yes** — see note | ✅ **Use this.** Default engine when nothing else is present; zero config; source implements append conditions, tag-scoped conflict detection, `ConsistencyMarker`, and tag/type-filtered sourcing. |
| `AggregateBasedJpaEventStorageEngine` | `axon-eventsourcing` (OSS) | **No** (explicit in docs) | ❌ Never. Kills DCB. |
| `PostgresqlEventStorageEngine` | `io.axoniq.framework:axoniq-postgresql` (**commercial**) | Yes | ❌ Violates the no-Docker / no-license constraint (PostgreSQL 16+, Axoniq license). |
| `AxonServerEventStorageEngine` | `io.axoniq.framework:axon-server-connector` (**commercial**) | Yes | ❌ Requires Axon Server 2025.2+ and Docker. |
| `SimpleEventBus` | `axon-messaging` | N/A | Not an event store — volatile, no replay. Do not use as the primary event bus. |

> ### ⚠️ Documentation contradiction — verify early (MEDIUM confidence)
> The "Event Store Migration" page at `docs.axoniq.io/axon-framework-reference/5.4/migration/paths/event-store/` lists `InMemoryEventStorageEngine` with **DCB Support = No**. That table is stale. Counter-evidence, all primary:
> 1. `InMemoryEventStorageEngine` (5.4.x source) takes `AppendCondition`, computes `tagsOf(condition)`, rejects with `AppendEventsTransactionRejectedException.conflictingEventsDetected(...)`, returns a `ConsistencyMarker`, and filters sourcing via `condition.matches(qualifiedName, taggedEvent.tags())`.
> 2. Its test suite (`InMemoryEventStorageEngineTest`) uses `EventCriteria.havingTags(new Tag("TEST","batch"))` and the shared `StorageEngineTestSuite`.
> 3. AxonIQ's own DCB demo (`github.com/AxonIQ/university-demo`) README: "The in-memory Event Store supports the DCB (Dynamic Consistency Boundary) concept".
>
> **Action:** the very first executable slice should be one integration test that appends two conflicting enrollments to the in-memory store and asserts exactly one append wins. If that fails, fall back to an explicit `StorageEngineBackedEventStore` wiring check before building anything else.

### Explicit bean (recommended, even though it is the default)

```java
@Configuration
class AxonConfig {

    @Bean
    EventStorageEngine eventStorageEngine() {
        return new InMemoryEventStorageEngine();
    }
}
```
`import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;`

Declaring it makes the intent visible (the file is a showcase), and it hard-blocks any future auto-config from silently swapping the engine.

## DCB in the Java API — the real names

`DcbDecider` does **not** exist in Axon Framework 5 (verified: no class matching `Decider`/`Dcb`/`DCB` in the `modelling`, `messaging`, or `eventsourcing` main source trees). DCB is expressed through **tags + criteria + an event-sourced entity whose criteria you supply**. The vocabulary:

| Concept | Type / annotation | Package |
|---------|-------------------|---------|
| Event tag | `@EventTag(key = "...")` on a record component | `org.axonframework.eventsourcing.annotation` |
| Tag value object | `Tag.of(String key, String value)` | `org.axonframework.messaging.eventstreaming` |
| Query criteria | `EventCriteria.havingTags(...).andBeingOneOfTypes(...)`, `EventCriteria.either(...)` | `org.axonframework.messaging.eventstreaming` |
| Criteria on an entity | `@EventCriteriaBuilder` — static method returning `EventCriteria` | `org.axonframework.eventsourcing.annotation` |
| Entity declaration | `@EventSourced(tagKey = ..., idType = ...)` | `org.axonframework.extension.spring.stereotype` |
| Command handler | `@CommandHandler` (on a bean method or entity method) | `org.axonframework.messaging.commandhandling.annotation` |
| State injection | `@InjectEntity State state`, `@InjectEntity(idResolver = ...)` | `org.axonframework.modelling.annotation` |
| Event append | `EventAppender` (commit-scoped, transactional) | `org.axonframework.messaging.eventhandling.gateway` |
| Evolve | `@EventSourcingHandler`, `@EntityCreator` | `org.axonframework.eventsourcing.annotation` / `...annotation.reflection` |
| Write guards | `AppendCondition`, `ConsistencyMarker`, `SourcingCondition`, `StreamingCondition` | `org.axonframework.eventsourcing.eventstore` |
| Customisation seams | `TagResolver`, `CriteriaResolver<T>`, `EntityIdResolver<T>` | `...eventstore` / `...eventsourcing` |

### The shape of a DCB command handler (verified against Axon's University example)

```kotlin
@Component
class EnrollStudentCommandHandler {

    @CommandHandler
    fun handle(cmd: EnrollStudent, @InjectEntity state: State, appender: EventAppender) {
        if (state.seatsTaken >= state.capacity) {
            throw IllegalStateException("Course is fully booked")   // exactly one of N concurrent callers lands here
        }
        appender.append(StudentEnrolled(cmd.courseId, cmd.studentId))
    }

    @EventSourced(idType = EnrollmentBoundaryId::class)   // the *scope* of the decision, not an aggregate root
    class State {                                         // Kotlin nested class = static nested, as Axon expects
        var capacity: Int = 0
        var seatsTaken: Int = 0

        @EntityCreator
        constructor()

        @EventSourcingHandler fun on(e: CourseCreated) { capacity = e.capacity }
        @EventSourcingHandler fun on(e: CourseCapacityChanged) { capacity = e.capacity }
        @EventSourcingHandler fun on(e: StudentEnrolled) { seatsTaken++ }
        @EventSourcingHandler fun on(e: StudentDropped) { seatsTaken-- }

        companion object {
            // ← the dynamic boundary lives here
            // Kotlin has no statics: @JvmStatic is what makes this resolvable by Axon's entity metamodel.
            @JvmStatic
            @EventCriteriaBuilder
            fun resolveCriteria(courseId: CourseId): EventCriteria =
                EventCriteria.havingTags(Tag.of("courseId", courseId.toString()))
                    .andBeingOneOfTypes(
                        CourseCreated::class.java.name,
                        CourseCapacityChanged::class.java.name,
                        StudentEnrolled::class.java.name,
                        StudentDropped::class.java.name,
                    )
        }
    }
}
```

Two design notes that fall out of the real API:
- **`@EventCriteriaBuilder` is the annotation that marks where the boundary is chosen.** That is your "code annotation marks the boundary" requirement — no custom annotation needed.
- The entity id type (`EnrollmentBoundaryId`) is a *handle for the decision*, not an aggregate identity. Keep it a small immutable data class and let the criteria do the scoping.

## Installation

**Build: Gradle Kotlin DSL, backend in Kotlin (project decision).** The Axon coordinates and version pins below are still the source of truth; only the build file and language change. Pin the wrapper with `gradle wrapper --gradle-version 8.14.3` (Spring Boot 3.5's tested Gradle line) and verify Spring Boot 3.5.16's plugin on Gradle 9 before upgrading.

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    kotlin("plugin.allopen") version "2.4.21"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

kotlin { jvmToolchain(21) }

repositories { mavenCentral() }

dependencyManagement {
    imports {
        // FIRST: Jackson 3 must win jackson-annotations over Spring Boot 3.5's Jackson 2 BOM.
        // This plugin imports BOMs in declaration order and the first match wins — same
        // semantics as Maven's <dependencyManagement> order.
        mavenBom("tools.jackson:jackson-bom:3.2.4")
        mavenBom("org.axonframework:axon-framework-bom:5.4.1")
        // spring-boot-dependencies is imported automatically by the Spring Boot plugin.
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.axonframework.extensions.spring:axon-spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // NOT axon-server-connector. NOT axoniq-spring-boot-starter. NOT spring-boot-starter-data-jpa.

    testImplementation("org.axonframework:axon-test")
    testImplementation("org.axonframework.extensions.spring:axon-spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> { useJUnitPlatform() }
```

Equivalent guarantees to the Maven setup: the Axon version is declared once in the BOM, Jackson 3 wins over Spring Boot's Jackson 2 constraints, and the three forbidden artifacts are absent from `runtimeClasspath` — assert the latter in the Phase 1 guard test with an `EntityManagerFactory` bean check.

### Kotlin adaptation — what research did NOT verify

The research (and every Axon example it cites) is Java. These are the Kotlin-specific points to settle in Phases 1–2, in order of risk:

| Risk | Why | How to settle |
|------|-----|---------------|
| `@EventTag` annotation target | A Kotlin data-class constructor property is not a Java field. `@EventTag` may need `@field:EventTag`, `@param:EventTag`, or `@get:EventTag` — the wrong target is silently ignored and tags never get stored. | The Phase 2 stored-tag assertion test is exactly this check; try `@param:` first, assert the stored tag set, then fall back to `@field:`. |
| `@EventCriteriaBuilder` statics | Axon expects a static method; Kotlin has no statics. | Use `companion object { @JvmStatic @EventCriteriaBuilder fun criteria(...): EventCriteria }` and verify the entity metamodel picks it up. |
| Kotlin events + Jackson | Kotlin data classes need the Kotlin Jackson module when the converter uses constructor binding; AF5's default converter is Jackson 3 (`tools.jackson`), while Spring MVC on Boot 3.5 uses Jackson 2. | Add the Kotlin module for the converter actually in use (verify the `tools.jackson.module:jackson-module-kotlin` coordinate against the imported BOM; `com.fasterxml.jackson.module:jackson-module-kotlin` if the HTTP layer needs it) and confirm one round-trip in Phase 1. |
| Final classes by default | Kotlin classes/methods are final; Spring proxying and Axon's entity enhancement expect open types where they subclass. | `kotlin("plugin.spring")` opens `@Component`/`@Configuration`/`@Transactional`; keep Axon decision entities pure/immutable and add `allOpen` only if the metamodel requires it. |
| Nullability at boundaries | Axon/Spring APIs are platform types; sloppy `!!` hides real null cases. | Declare event/handler signatures non-null explicitly; treat Axon return values as nullable until checked. |

If a Kotlin/Axon interop wall turns out to be structural (not annotation-target level), the fallback is a thin Java seam for the DCB entity + criteria builder only — but do not pre-emptively mix languages; prove the wall first.

```bash
# Frontend (scaffold, then add query lib)
npm create vite@latest web -- --template react-ts
cd web
npm install
npm install @tanstack/react-query

# Run
./gradlew bootRun               # backend on :8080
npm run dev                     # frontend on :5173, proxying /api -> :8080
```

`application.properties` — the only Axon properties worth setting:

```properties
# Read model must be up to date before the HTTP response returns (single JVM, no polling)
axon.eventhandling.processors.course-projection.mode=subscribing
```

Spring Boot's `EventProcessorProperties` defaults to `Mode.POOLED`. With pooled processors the read model lags the command response, and a "list courses" query issued right after an enroll can show stale seat counts — which confuses the very demo you are building. Subscribing is the correct choice for a single-JVM teaching app.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Spring Boot 3.5.16 | Spring Boot 4.1.2 | Only if you specifically want to learn the Boot 4 story. Official support exists (docs: Axon 5.0.3+ supports Boot 3 and Boot 4), and Axon's own example pins 4.1.2 — but that example carries a comment warning that **4.1.0 deadlocks** on a circular reference between `applicationTaskExecutor` and Axon's `UnitOfWorkFactory`, plus a protobuf pin workaround. More variables, none of them about DCB. |
| OSS starter (`org.axonframework.extensions.spring`) | Commercial `io.axoniq.framework:axoniq-spring-boot-starter` | Only if you add Axon Server, PostgreSQL storage, distributed messaging, or a DLQ. Since Axon 5.1 those live in the commercial BOM and log a license warning in production. Axon's own examples use it because they run against Axon Server in Docker — which this project explicitly rejects. |
| `InMemoryEventStorageEngine` | `axoniq-postgresql` (`PostgresqlEventStorageEngine`) | Only if persistence across restarts becomes a requirement *and* PostgreSQL is acceptable. It is the one DCB-capable persistent engine, but it is commercial and needs a DB server. |
| JPA/`spring-boot-starter-data-jpa` for read models | In-memory read model (`ConcurrentHashMap` repository + `@EventHandler` projector) | The in-memory read model is what Axon's own University example does (`InMemoryCourseStatsRepository`). Since events are in-memory too, persistence symmetry is correct — a restart wipes both. Add JPA only if you accept the `EventStorageEngine`-bean workaround. |
| Gradle | Maven | Author preference (this project uses Gradle). Both work; the only care point is BOM import order, which `dependencyManagement { imports { … } }` preserves. |
| Spring Boot starter | Plain `EventSourcingConfigurer` (`UniversityAxonApplication` style) | The plain-Java path is closer to the metal and is what the standalone `university-demo` repo does. Use it for a single test-harness module if you want to see the configurer explicitly; not for the app. |
| React + Vite + TS | Server-rendered (Thymeleaf/htmx) | Would remove an entire process, but the project decision already commits to a React SPA to keep Axon code uncluttered. Not worth relitigating. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `spring-boot-starter-data-jpa` / any `DataSource` the JPA auto-config can latch onto | `JpaEventStoreAutoConfiguration` wins over the in-memory default and installs `AggregateBasedJpaEventStorageEngine`, which has **no DCB support**. The app compiles, starts, and silently stops being a DCB app. | No JPA. In-memory read model. |
| `AggregateBasedJpaEventStorageEngine` | Same as above — explicitly documented as DCB-incapable ("no means to store several tags per event") | `InMemoryEventStorageEngine` |
| `io.axoniq.framework:*` (Axoniq Framework) | Commercial license; the `axoniq-spring-boot-starter` also brings the Axon Server connector, which tries to reach `localhost:8124` at startup — a hard failure in a no-Docker setup | OSS `org.axonframework.extensions.spring:axon-spring-boot-starter` |
| `axon-server-connector` | Startup failure without a running Axon Server; directly violates the no-Docker constraint | Nothing — the OSS starter omits it |
| `spring-boot-docker-compose` | Present in Axon's examples only because they run Axon Server in Docker; it starts containers at boot | Omit entirely |
| `@EventSourcedAggregate` / `@AggregateRoot` / `@TargetAggregateIdentifier` / `@AggregateMember` aggregate modelling | AF5 removed/renamed the AF4 aggregate API. Tutorials and LLM output based on Axon 4 will not compile, and copying AF4 aggregate thinking defeats the point of the exercise | `@EventSourced` entity + `@EventCriteriaBuilder` + `@InjectEntity` |
| `JdbcEventStorageEngine`, `MongoEventStorageEngine` | Explicitly removed in AF5 with no replacement (official migration page) | In-memory for dev, `axoniq-postgresql` if you must persist |
| `SimpleEventBus` as the store | Volatile; forgets events once published; no sourcing, no replay, no DCB conflict detection | `InMemoryEventStorageEngine` |
| Axon 4.x coordinates (`org.axonframework:axon-spring-boot-starter` at 4.x, `axon-spring-boot-starter` under `org.axonframework.springboot`) | Wrong group ids, wrong packages, wrong config model | The 5.4.1 coordinates above |
| `@Configuration`-time `AggregateConfigurer` / `Configurer` (AF4) | Replaced by `ApplicationConfigurer` / `EventSourcingConfigurer` in AF5 | Bring-up via the starter; drop to `EventSourcingConfigurer` only in tests |
| Creating a "Course aggregate" that owns seat count *and* is loaded per enrollment | This is the fixed boundary the exercise exists to disprove; it either over-locks (one hot stream) or misses the cross-student invariant | Tag-scoped `EventCriteria` over `courseId` only |

## Stack Patterns by Variant

**If a test needs to replay a scenario without Spring:** build an `EventSourcingConfigurer` by hand and pass it to `AxonTestFixture.with(configurer)` — the same configurer the app uses, minus the web layer. This is how AxonIQ's University demo keeps tests honest.

**If the last-seat race test proves flaky:** the losing command must surface a rejected-append exception, not a silent no-op. Assert on the *conflict* (`AppendEventsTransactionRejectedException` family / a `CommandExecutionException` wrapping it), not merely on the winner's event count, and drive the race with a `CountDownLatch` on two threads against the same `CommandGateway`. Do not add a `RetryScheduler` for this test — retries would turn "exactly one succeeds" into "both eventually succeed", which is the wrong lesson.

**If the read model needs to be rebuildable:** the projection runs as a processor over the same event store, so wiping the in-memory read model and replaying from position 0 is free. Keep the projector idempotent and it doubles as the demo of CQRS.

**If you later want persistence without a license:** there is no OSS DCB-capable persistent engine at 5.4.1. The honest options are (a) accept amnesia, or (b) buy into Axoniq/PostgreSQL. Do not fake it with JPA.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `org.axonframework:axon-framework-bom:5.4.1` | JDK 21+ | 21 is the documented floor; 25 works ("or higher") but is not Axon's test target |
| `axon-spring-boot-starter:5.4.1` (OSS) | Spring Boot 3.5.16 (built/tested), Spring Boot 4.x (documented support from 5.0.3) | Boot 2 is unsupported from AF5 onward |
| Axon 5.4.x converters | Jackson 3 (`tools.jackson:*`) by default | `axon-conversion` POM carries **both** Jackson 2 and Jackson 3 databind at compile scope. `tools.jackson.core:jackson-databind:3.x` depends on `com.fasterxml.jackson.core:jackson-annotations` at **3.x**, but Spring Boot 3.5.16's BOM pins `jackson-annotations` to **2.21.x**. Import `tools.jackson:jackson-bom` **before** the Spring Boot BOM or Jackson 3 payloads break. Boot 4 manages Jackson 3 itself and does not need this. |
| Axon `JacksonConverter` | Jackson 3 | If Jackson 3 misbehaves (e.g. `final` fields no longer deserialize — a documented Jackson 3 change), use `Jackson2Converter` from `org.axonframework.conversion.jackson2`, or set `ALLOW_FINAL_FIELDS_AS_MUTATORS` |
| `@EventSourced` (`...extension.spring.stereotype`) | `@EventSourcedEntity` | `@EventSourced` is a Spring meta-annotation adding `@Component` + `@Scope("prototype")`; its `tagKey`/`idType`/`concreteTypes` attributes alias the core annotation |
| React 19.3.1 / Vite 8.3.7 / TS 7.0.2 | Node 24.20.0 (local) | Matches `create-vite@9.3.4` `template-react-ts` pins exactly |

## Sources

- `https://docs.axoniq.io/axon-framework-reference/5.4/modules/` — module list, group ids, both BOMs (verified 2026-11-10)
- `https://docs.axoniq.io/axon-framework-reference/5.4/events/infrastructure/` — storage-engine catalogue, DCB support per engine, Spring Boot selection rules
- `https://docs.axoniq.io/axon-framework-reference/5.4/events/event-store-internals/` — `Tag`, `@EventTag`, `EventCriteria`, `AppendCondition`, `ConsistencyMarker`, `@EventCriteriaBuilder`
- `https://docs.axoniq.io/axon-framework-reference/5.4/migration/prerequisites/` — JDK 21 minimum, Spring Boot 3+/Framework 6+
- `https://docs.axoniq.io/axon-framework-reference/5.4/migration/paths/event-store/` — engine/DCB table (**stale on in-memory**), JPA schema notes
- `https://docs.axoniq.io/axon-framework-reference/5.4/spring-boot-integration/` — Axon↔Spring Boot version matrix (5.0.3+ → Boot 3 and 4)
- `https://docs.axoniq.io/axon-framework-reference/5.4/conversion/` — default `JacksonConverter`, Jackson 3 `final`-field note, `Jackson2Converter`
- `https://docs.axoniq.io/axon-framework-reference/5.3/advanced-migration/paths/5.0-to-5.1/` — the 5.1 open-source/commercial split and exact coordinate mapping
- `https://www.axoniq.io/blog/dcb-in-af-5` — DCB announcement, Java 21 standardisation, DCB experimental status in 5.0
- `https://github.com/AxonIQ/university-demo` — in-memory store "supports the DCB concept" (contradicts the migration table)
- `https://github.com/AxonIQ/AxonFramework/tree/main/examples/university-java-springboot-3` — working Spring Boot 3.5.16 + Axon 5.4.1 DCB app; source of the command-handler/`@EventCriteriaBuilder`/`@EventSourced` shapes quoted above
- `https://github.com/AxonIQ/AxonFramework/tree/main/examples/university-java-springboot-4` — Boot 4.1.2 pin, the 4.1.0 deadlock warning, Jackson 3 BOM ordering
- `https://github.com/AxonIQ/AxonFramework` source: `InMemoryEventStorageEngine`, `EventSourcingConfigurationDefaults` (`ENHANCER_ORDER`), `JpaEventStoreAutoConfiguration` (order `-500`), `EventSourced` annotation, `EventProcessorProperties` (default `POOLED`)
- `https://repo1.maven.org/maven2/org/axonframework/axon-framework-bom/maven-metadata.xml` — `latest/release = 5.4.1`
- `https://repo1.maven.org/maven2/org/axonframework/extensions/spring/axon-spring-boot-starter/maven-metadata.xml` — OSS starter still published at 5.4.1
- `https://repo1.maven.org/maven2/.../axon-spring-boot-starter-5.4.1.pom` and `axon-spring-boot-autoconfigure-5.4.1.pom` — exact transitive set, no Axon Server connector
- `https://repo1.maven.org/maven2/org/axonframework/axon-parent/5.4.1/axon-parent-5.4.1.pom` — `spring.boot.version=3.5.16`, `jackson.version=3.2.3`
- `https://registry.npmjs.org/{react,vite,typescript,@tanstack/react-query,react-router-dom}` and `create-vite@9.3.4/template-react-ts/package.json` — frontend versions
- `https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml` — Kotlin `latest = 2.5.0-Beta2` (pre-release), newest stable `2.4.21`, metadata updated 2026-10-21

## Gaps / must validate during implementation

1. **In-memory DCB under real concurrency** — docs say no, source says yes. Validate with the last-seat race test in the very first executable slice. (MEDIUM → will resolve to HIGH once tested.)
2. **`@EventSourced` on a nested `State` class with `idType`** — verified only in Axon's Spring Boot 3 example; confirm it resolves the same on 5.4.1 with the OSS starter (the example uses the commercial starter).
3. **Jackson 3 + record payloads on Spring Boot 3.5.16** — the BOM-ordering trick is proven in Axon's own example; confirm `tools.jackson` is actually chosen for event conversion rather than a Jackson 2 fallback.
4. **Read-model processor mode** — `POOLED` is the coded default; `SUBSCRIBING` is recommended here. Confirm the property keys (`axon.eventhandling.processors.<name>.mode=subscribing`) resolve against 5.4.1's `EventProcessorProperties`.
5. **Whether the React scaffold's TS 7.0.x works cleanly with `@tanstack/react-query` types** — low risk, but the first `npm run build` is the check.

---
*Stack research for: Axon Framework 5 DCB showcase (course enrollment)*
*Researched: 2026-11-10*
