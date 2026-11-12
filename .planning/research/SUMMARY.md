# Project Research Summary

**Project:** Axon 5 DCB Courses — course enrollment with seat capacity and FIFO waitlist
**Domain:** Event-sourced teaching demo — Axon Framework 5 Dynamic Consistency Boundaries, three services behind Axon Server, PostgreSQL read model, thin React SPA
**Researched:** 2026-11-10
**Confidence:** MEDIUM (strong on DCB API semantics and artifacts; distributed wiring with the commercial Axoniq modules is the load-bearing item still unproven)

## Executive Summary

This is not a product; it is a lesson. The reader must come away understanding that a Dynamic Consistency Boundary lets a command decide over exactly the events its invariant needs, and that the event store — not the handler, not a database transaction — rejects the loser of a race for the last seat. AxonIQ's own `university-demo` confirms the shape: one small domain, one hard invariant (seat capacity), a boundary visible on one screen, and tests as the spine of the project.

**Architecture pivot (2026-11-10):** the plan moved from a single JVM with `InMemoryEventStorageEngine` to three services behind Axon Server with PostgreSQL as the read model. The lesson now also has to survive a process boundary, which is closer to production but adds commercial dependencies, infrastructure, and eventual consistency.

Recommended build: Kotlin 2.4.21 on JDK 21, Spring Boot 4.1.2, Axon 5.4.1 via the **commercial** `io.axoniq.framework` BOM and `axoniq-spring-boot-starter`, Axon Server 2026.1.7 and PostgreSQL 18 in Docker Compose, a six-module Gradle build plus `courses-test-support` (`courses-platform`, `courses-domain`, `courses-write`, `courses-read`, `courses-api`, `courses-frontend`), and a React 19 / Vite / TypeScript SPA.

The enroll decision remains the hinge: each command gets its own `@EventSourced` decision state and an `@EventCriteriaBuilder` naming the tags and event types it depends on (`courseId` plus the event types that can change seats or membership). Enrollment stays unkeyed for command routing so racing enrollments really run concurrently. Drop and FIFO promotion are one command that appends `StudentDropped` and `WaitlistPromoted` in a single append. Read models are written and read only by `courses-read`, which also emits live updates.

Key risks are now split between the DCB mechanics and the distribution:
- the append condition must be enforced by Axon Server, not by an accidental local store;
- `courses-write` must not get a JPA datasource;
- command sequencing or a routing key can serialize the race away;
- `havingTags(a, b)` (AND) instead of `either(...)` oversells capacity;
- eventual consistency makes "seats left" lag behind the POST;
- DDL tooling outside Flyway hides schema drift.

## Key Findings

### Recommended Stack

Kotlin on JDK 21, Spring Boot 4.1.2, Axon 5.4.1 through `io.axoniq.framework:axoniq-framework-bom` (which includes the OSS modules), `axoniq-spring-boot-starter` (which already brings `axon-server-connector`), Axon Server 2026.1.7, PostgreSQL 18 for the read model, Gradle Kotlin DSL with a version catalog, React 19 + Vite + TypeScript.

**Core technologies:**
- **Axoniq Framework BOM 5.4.1 (commercial):** one BOM for framework and commercial modules; do not also import the OSS BOM.
- **Axon Server 2026.1.7 (Docker):** event store, command/query routing, token store; supports DCB. Runs 12 hours without a license, then shuts down.
- **`courses-write` / `courses-read` / `courses-api`:** three independent Spring Boot apps; only `courses-write` appends events, only `courses-read` writes and serves the read model.
- **PostgreSQL (Docker):** read model owned by `courses-read`; Flyway there, plus a startup smoke check.
- **Gradle multi-module + `:courses-platform`:** six modules, versions in `gradle/libs.versions.toml`, BOMs re-exported once.
- **`courses-domain` module:** events, commands, queries, ids, tag keys — the only cross-service contract.
- **React + Vite + TypeScript + TanStack Query:** thin SPA; the Vite dev proxy covers the local loop, while the deployed SPA calls the gateway cross-origin and needs the fail-closed CORS list (ADR-0007).

**Critical version/config constraints:**
- Keep `spring-boot-starter-data-jpa` off `courses-write`'s classpath.
- Never give a second process DDL tooling or a write-capable pool.
- Never put a routing key on `EnrollStudent`.
- Kafka (4.12.1) and JGroups (4.12) extensions are AF4-era; they are not an alternative transport for AF5.
- `axon-server-connector` and `axoniq-spring-boot-starter` are commercial; local use runs under evaluation behavior, production licensing is out of scope.

### Expected Features

**Must have (table stakes):**
- Seeded login for four actors — one admin and three students — with printed credentials and no user management.
- Create course with title and seat capacity (`CourseCreated`, tagged by course).
- Course catalog showing seats remaining and waitlist depth, served from PostgreSQL.
- Enroll with capacity rule and duplicate-enrollment rule; a full course rejects with `course is full`, and the student may then join the waitlist explicitly.
- Rejection with a specific business reason (`course is full`), distinct from a technical conflict.
- Drop an enrollment, freeing the seat, with atomic FIFO promotion of the next waitlisted student.
- "My enrollments" for the signed-in student.
- Infrastructure and three services start from two documented commands.
- Seeded scenario data created through real commands.
- REST API and thin React SPA covering the above.
- Given/when/then tests per slice, plus integration tests where the boundary crosses services.

**Should have (differentiators):**
- **Last-seat race as an executable proof:** capacity 1, N distinct students, barrier-released burst, exactly one success.
- **Mechanism proof:** the loser fails on the append condition, not merely on a business rule.
- **Boundary annotations** at the exact line where the criteria is chosen.
- **Event × tag table** per command in the README.
- **Conflict (409) vs business rejection (422)** explained in one README line.
- **Cross-service trace** in the README: gateway → `courses-write` → Axon Server → `courses-read` → PostgreSQL → SSE.
- **Design note with the fixed-aggregate counterfactual** and a note on why distribution does not weaken the guarantee.

**Defer (v2+ / explicitly out of scope; name them in README):**
- Saga or automation slice for waitlist promotion.
- Waitlist offers with expiry, notifications, priority, per-student limits.
- Real auth, roles beyond two seeded actors, Kubernetes/HA, production licensing.
- Read-model rebuild by replay; event schema transformation.
- `ChangeCourseCapacity` slice.

### Architecture Approach

One bounded context `courses`, vertical slices, distributed across three services. `courses-domain` holds every message; `courses-write` holds handlers and decision state; `courses-read` subscribes, writes PostgreSQL and answers queries (including live watch subscriptions); `courses-api` is the only HTTP surface and only sends messages. Axon Server routes commands, queries, and the event stream.

**Major components:**
1. **`courses-domain`** — events, commands, queries, ids, errors; no Spring.
2. **`courses-write`** — `@CommandHandler` + `@EventSourced` state + `@EventCriteriaBuilder`; no datasource.
3. **`courses-read`** — tracking processor, JPA entities and Flyway for writes; Spring Data JDBC repositories and `@QueryHandler`s for reads; `QueryUpdateEmitter` for live updates.
5. **`courses-api`** — REST, security, gateways and seeding; it serves no static assets and has no demo endpoint.
6. **`courses-frontend`** — React/Vite, built by Gradle.

### Critical Pitfalls

1. **Accidental non-DCB store in `courses-write`** — a datasource plus the JPA auto-config installs an aggregate-based store. Keep JPA out.
2. **Serialized race** — default `RoutingKeySequencingPolicy` plus a routing key on `EnrollStudent` turns the race into a queue.
3. **Criteria semantics** — `havingTags(a, b)` is AND; the course looks empty and oversells.
4. **Missing `@InjectEntity`** — no append condition, no conflict detection; tagging alone does nothing.
5. **Read model drift** — DDL tooling outside Flyway hides drift; one writer plus a fail-fast smoke check is the only safe setup.
6. **Eventual consistency presented as instant** — the UI must refetch and tolerate lag.
7. **Retry masking the mechanism** — no automatic retry by default; document single-retry as the production idiom.
8. **Commercial licensing surprise** — verify local boot behavior and the 12-hour Axon Server window in Phase 1.

## Implications for Roadmap

Eleven phases, ordered by dependency and by failing for one reason.

1. **Build and infrastructure skeleton** — six modules, three boot apps, compose, SPA in the build graph, formatting, FSD linter and Playwright MCP wired into CI. Requirements: PLAT-01, PLAT-05, QUAL-01, E2E-01, TOPO-01, TOPO-04, FE-01, FE-02.
2. **Session and message path** — seeded login, identity propagation, probe command/query, guard tests, session-cookie credentials (ADR-0011) on Testcontainers infrastructure (ADR-0003). Requirements: AUTH-01, TOPO-02, TOPO-03, PLAT-03.
3. **Message and tag contract** — frozen messages, UUIDv7 identifiers, tag constants, stored-tag assertion, `docs/event-tag-table.md`. Requirements: ID-01, TOPO-05.
4. **DCB append-condition spike** — two-thread conflict, routing-key hazard, post-commit emit reaching a remote subscriber. No v1 requirement.
5. **Course creation across services** — command → event → read → UI; admin-only; idempotent create; read-only query path and fail-fast drift check. Requirements: AUTH-03, CRSE-01..03, IDEM-01, RDMD-01, RDMD-02.
6. **Enroll under capacity** — the dynamic boundary, 409/422/404 mapping, criteria snapshot test, boundary annotations, acting student taken from the session. Requirements: AUTH-05, ENRL-01..03, ENRL-06, PLAT-04.
7. **Waitlist and atomic drop/promotion** — explicit waitlist join, FIFO by append order, one-append promotion, capacity invariant, every criteria builder documented. Requirements: DOCS-02, ENRL-04, ENRL-05, ENRL-07, DROP-01, DROP-02.
8. **Last-seat race proof** — barrier-driven contention test through the production gateway, exactly-one-winner tally with `full` and `conflicted` buckets, forced-overlap boundary proof, negative control in its own suite, Playwright journey. Requirements: RACE-01..04, E2E-02, DOCS-03.
9. **My enrollments and seeded scenarios** — ownership enforced at the query boundary, idempotent seeding of the three demo courses, those courses disabled in the E2E profile. Requirements: AUTH-02, VIEW-01, PLAT-02.
10. **Live updates** — snapshot plus pushed changes over SSE, emitted by `courses-read` after commit, reconnect recovery, no writes on the live path. Requirements: PLAT-07, VIEW-02, VIEW-03, TOPO-06.
11. **Teaching artifacts** — README cross-service walkthrough, design note, event × tag table linked. Requirements: DOCS-01, DOCS-04, DOCS-05.

### Phase Ordering Rationale

The skeleton must exist before any process-boundary behaviour can be observed; the contract is frozen before handlers because event names are a one-way door; the spike follows the contract so it can use real message shapes, and precedes features so a wall is found while the build is cheap. The first vertical slice proves the read path before the boundary work. Enroll is the hinge; waitlist and drop add the second atomicity trap. **The race comes next, before the read-side polish and the live-update pipeline**, because it is the Core Value: a late defect there would invalidate the SSE and UI work built on top. Live updates follow the read side they depend on, and the teaching artifacts are written from working code.

## Confidence Assessment

| Area | Level | Reason |
|------|-------|--------|
| DCB API semantics | HIGH | Primary docs cross-checked against framework source |
| Artifact coordinates and versions | HIGH | Maven Central metadata for Axon, Axoniq, Boot, Kotlin |
| Distributed topology | MEDIUM | Commercial modules are documented, but not exercised here yet |
| Axon Server DCB enforcement | MEDIUM–HIGH | Documented; must be proven in the Phase 4 spike |
| Read-model split | MEDIUM | Standard CQRS; schema ownership is a convention, not a framework guarantee |
| Kotlin interop | MEDIUM | No first-party Kotlin example for the DCB entity shape |

### Gaps to Address

- **Licensing behavior** — closed in Phase 1: warning plus a 12-hour window, documented in `docs/tooling.md`.
- **Axon Server image tag and PostgreSQL tag** — closed in Phase 1: pinned in the catalog and guarded by `checkComposeImages`.
- **Integration test infrastructure** — decided: Testcontainers, one container per class on random ports (ADR-0003). The race test needs a live Axon Server, which Testcontainers provides.
- Event names are single-valued (`StudentEnrolled`, `StudentDropped`, `WaitlistJoined`, `WaitlistPromoted`) and frozen in Phase 3.
- **Retry policy** — confirm no default retry is active on 5.4.1 (affects the tally labels).
- **Kotlin `axon-kotlin` extension** — worth evaluating for value-class ids; not assumed by the plan.

## Sources

### Primary (HIGH confidence)
- `https://docs.axoniq.io/axon-framework-reference/5.4/modules/` — main vs extension vs commercial modules; both BOMs
- `https://docs.axoniq.io/axon-framework-reference/5.4/spring-boot-integration/` — Boot support matrix, connector, JPA fallback
- `https://docs.axoniq.io/axon-framework-reference/5.4/events/event-store-internals/` — tags, criteria, append conditions
- `https://repo1.maven.org/maven2/io/axoniq/framework/axoniq-framework-bom/maven-metadata.xml` — 5.4.1
- `https://repo1.maven.org/maven2/io/axoniq/framework/axoniq-spring-boot-starter/maven-metadata.xml` — 5.4.1
- `https://repo1.maven.org/maven2/org/axonframework/axon-framework-bom/maven-metadata.xml` — 5.4.1
- `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml` — 4.1.2 stable
- `https://repo1.maven.org/maven2/org/axonframework/extensions/kafka/axon-kafka-spring-boot-starter/maven-metadata.xml` — 4.12.1 (no AF5)
- `https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml` — 2.4.21 stable

### Secondary (MEDIUM confidence)
- `https://github.com/AxonIQ/university-demo` — course-capacity DCB shape
- `https://github.com/AxonIQ/AxonFramework/tree/main/examples/university-java-springboot-3` — handler and criteria shape (Java)
- `https://docs.axoniq.io/axon-framework-reference/5.4/events/event-processors/streaming/` — token store and processor behavior

### Tertiary (LOW confidence — needs validation)
- Commercial module behavior without a license file
- Axon Server DCB enforcement under true concurrency across two processes
- Kotlin-specific annotation behavior on the DCB entity
