# Project Research Summary

**Project:** Axon 5 DCB Showcase — course enrollment with seat capacity and FIFO waitlist
**Domain:** Event-sourced single-JVM teaching demo (Axon Framework 5 Dynamic Consistency Boundaries, Spring Boot backend, thin React SPA)
**Researched:** 2026-11-10
**Confidence:** MEDIUM (strong on API semantics and versions; the embedded-store DCB guarantee is the one load-bearing item still unproven)

## Executive Summary

This is not a product; it is a lesson. The reader must come away understanding that a Dynamic Consistency Boundary (DCB) lets a command decide over exactly the events its invariant needs, and that the event store — not the handler, not a database transaction — rejects the loser of a race for the last seat. Experts building this kind of showcase keep one small domain, one hard invariant (seat capacity), a boundary that is visible on one screen, and tests that are the spine of the project. AxonIQ's own `university-demo` and the `dcb.events` course-subscriptions example confirm this shape. The recommended build is Java 21, Spring Boot 3.5.16, Axon Framework 5.4.1 via the OSS starter only, an explicit `InMemoryEventStorageEngine` bean, Maven, and a React 19 / Vite / TypeScript SPA that talks to `/api` through a dev proxy.

The recommended approach is to treat the enroll decision as the hinge. Each command gets its own `@EventSourced` decision state and an annotated `@EventCriteriaBuilder` that names the tags and event types it depends on (`courseId` plus the event types that can change seats or membership). Enrollment stays unkeyed for command routing so racing enrollments really run concurrently. Drop and FIFO promotion are one command that appends `StudentDropped` and `WaitlistPromoted` in a single append, so promotion is never a separate automation. Read models are in-memory projections used only for display; command handlers never read them. The last-seat race is proven by a barrier-driven test with a negative control, not by a sleep-and-count test.

The key risks are all about the lesson being silently wrong while tests stay green: the in-memory store may not enforce append conditions (docs say "no", source says "yes"); command sequencing or a routing key can serialize the race away; criteria built with `havingTags(a, b)` instead of `either(...)` oversell; a missing event type in the criteria or a missing subject tag on drop hides the release of a seat; and accidental `spring-boot-starter-data-jpa` or the Axon Server connector silently swaps the DCB store or breaks startup. Phase 1 must prove the conflict with an executable two-thread spike before any domain slice is built.

## Key Findings

### Recommended Stack

Java 21 (hard minimum for AF5), Spring Boot 3.5.16 (the version Axon 5.4.1 builds and tests against), Axon Framework BOM 5.4.1, and `org.axonframework.extensions.spring:axon-spring-boot-starter` 5.4.1 (OSS; no Axon Server connector). Import the Jackson 3 BOM before the Spring Boot BOM so Jackson 3 payloads serialize correctly. Frontend: React 19.3.1, Vite 8.3.7, TypeScript 7.0.x (pinned by `create-vite` react-ts), `@tanstack/react-query` 5.x for refetch-after-command. No component library, no state manager, no router unless the second screen earns it.

**Core technologies:**
- **Axon Framework 5.4.1 (OSS starter, `axon-eventsourcing` / `axon-messaging`):** DCB-capable event store, `@EventSourced`, `@EventCriteriaBuilder`, `@InjectEntity` — the entire lesson lives here.
- **`InMemoryEventStorageEngine` (explicit `@Bean`):** zero infrastructure; source implements append-condition conflict detection, per-tag conflict checks, and consistency markers. Declare it explicitly so no auto-config can silently swap it.
- **Spring Boot 3.5.16 + Spring MVC + Spring Security (session, in-memory users):** app runtime and seeded login.
- **Maven wrapper:** single command `./mvnw spring-boot:run`; BOM import is unambiguous.
- **React 19 + Vite 8 + TypeScript + TanStack Query:** thin SPA; Vite proxy `/api` → `:8080` removes CORS.

**Critical version/config constraints:**
- **Keep `spring-boot-starter-data-jpa` off the classpath.** `JpaEventStoreAutoConfiguration` would install `AggregateBasedJpaEventStorageEngine`, which has no DCB support, and the app would silently stop being a DCB app.
- **Never add `axon-server-connector` or `io.axoniq.framework:*`.** Startup tries `localhost:8124` and violates the no-Docker constraint.
- **`axon.eventhandling.processors.<name>.mode=subscribing`** for projections, so the read model is up to date when the POST returns (default is POOLED and would make the demo look broken).
- **Do not use AF4 APIs** (`@Aggregate`, `@AggregateIdentifier`, `AggregateLifecycle.apply`, `GenericCommandGateway`, `TrackingEventProcessor`).

### Expected Features

**Must have (table stakes):**
- Seeded login for two actors (student + admin), printed credentials, no user management.
- Create course with title and seat capacity (`CourseCreated`, tagged by course).
- Course catalog showing seats remaining and waitlist depth (projection, not a re-source).
- Enroll with capacity rule, duplicate-enrollment rule, and auto-waitlist when full — the star slice.
- Rejection with a specific business reason ("Course is full"), distinct from technical conflict.
- Drop an enrollment, freeing the seat, with atomic FIFO promotion of the next waitlisted student.
- "My enrollments" for the signed-in student.
- One-command startup (`./mvnw spring-boot:run`), zero external services, seeded scenario data created through real commands (open course, full course, full course with waitlist).
- REST API and thin React SPA covering the above.
- Given/when/then tests via `AxonTestFixture.with(application.configurer())` per slice.
- README command walkthrough with expected output, including the rejected hop.

**Should have (differentiators):**
- **Last-seat race as an executable proof:** capacity 1, N distinct students, barrier-released burst, assert exactly one success. Deterministic in outcome, not "usually one".
- **Mechanism proof:** the loser fails on the append condition (`AppendEventsTransactionRejectedException`), not merely on a business rule.
- **Boundary annotations** at the exact line where the criteria is chosen, stating the invariant and why each event type is included or deliberately excluded.
- **Event × tag table** per command in the README — the highest-leverage teaching artifact.
- **Conflict (409, retryable) vs business rejection (422, not retryable)** in the API, explained in one README line.
- **Drop-vs-enroll race** to prove promotion is inside the boundary.
- **Design note with the fixed-aggregate counterfactual** (mega-course aggregate; student aggregate plus saga) and what each over-locks or fails to enforce.

**Defer (v2+ / explicitly out of scope; name them in README):**
- Saga or automation slice for waitlist promotion (describe in design note only).
- Waitlist offers with expiry, notifications, priority, per-student limits (as policy surface).
- Real auth, roles beyond two seeded actors, deployment hardening, snapshots, upcasting, persistence across restart, event-store browser UI.
- `ChangeCourseCapacity` slice is P3 — a good stretch goal after the core runs, not a launch requirement.

### Architecture Approach

A single bounded context `enrollment`, vertical slices with feature folders (`write/<use-case>/`, `read/<screen>/`, `events/`, `web/`, `seed/`, `config/`). Each command is a `@Component` handler with one `@CommandHandler`, a private `@EventSourced` decision state, and one `@EventCriteriaBuilder` that is the boundary. Events in `events/` are the contract that every slice depends on. The write side and the read side depend only on events; they never depend on each other. Controllers are thin adapters over `CommandGateway` and `QueryGateway`. Seeding dispatches real commands through the gateway.

**Major components:**
1. **`events/`** — event records with `@EventTag` on `courseId` and `studentId`; the only shared contract.
2. **`write/<slice>/`** — command record, handler with `decide(...)`, decision state, `@EventCriteriaBuilder`. Each slice has its own state class; never share one.
3. **`read/<screen>/`** — `@EventHandler` projection → in-memory `ConcurrentHashMap` repository → `@QueryHandler`. Display only.
4. **`web/`** — REST controllers + `ApiExceptionHandler` (maps conflict → 409 with conflicting tags, rule violation → 422 with the canonical message).
5. **`config/`** — `AxonConfig` (explicit `InMemoryEventStorageEngine`), `SecurityConfig` (seeded users, session cookie, CSRF decision), principal → `studentId`.
6. **`seed/`** — `ApplicationRunner` dispatching `CreateCourse`, `EnrollStudent`, `JoinWaitlist` through `CommandGateway`.
7. **`frontend/`** — Vite + React + TS; `api.ts` is the only URL-aware file; no domain logic in the browser.

**Key patterns:**
- The same `EventCriteria` is both the read filter and the write-conflict definition: narrow it to what the invariant needs.
- Enroll boundary (proposed): tag `courseId`; types `CourseCreated`, `CourseCapacityChanged`, `StudentEnrolled`, `StudentDropped`, `WaitlistPromoted`; plus `either(...)` with a `studentId` criterion for the duplicate rule. Renaming a course must not invalidate enrollment, so `CourseRenamed` is deliberately excluded.
- `DropEnrollment` decides both the release and the promotion, appending two events in one append with one condition.
- Projections serve the UI; `seatsTaken` is a counter updated by sequential handlers (default `SequentialPolicy`).

### Critical Pitfalls

1. **The embedded store may not enforce append conditions (C1).** Migration table says in-memory DCB = No; source and the university demo say yes. Prove it in Phase 1 with a two-thread `CyclicBarrier` spike asserting exactly one commit and one `AppendEventsTransactionRejectedException`. If it fails, the fallback is a local PostgreSQL engine (commercial) or Axon Server — and `PROJECT.md`'s constraint must be updated rather than the guarantee faked.
2. **Command sequencing removes the race (C6, Anti-pattern 1).** Do NOT put `routingKey = "courseId"` on `EnrollStudent`; the default `RoutingKeySequencingPolicy` would serialize every enrollment for a course and the race disappears silently. Assert max-in-flight > 1 in the race test.
3. **`EventCriteria` boolean misuse (C3).** `havingTags(courseId, studentId)` means *both* tags, which hides every other student's enrollment and oversells. Use `either(courseCriteria, studentCriteria)`. Prove it with a test where another student's enrollment is visible in the decision.
4. **Missing event types or subject tags (C2, C7).** Every event that can change seats or membership must be in the criteria and must carry the course tag; a drop that is not read or not tagged leaves seats permanently lost. Keep an explicit event → tags table as a design artifact and test the enrol-after-drop and mixed enroll/drop contention paths.
5. **Promotion outside the boundary (C8) and projection as guard (C11).** Promotion in an event-handler automation can double-book; reading the projection in a command handler reintroduces the oversell. Keep promotion inside the drop handler; forbid query-side imports in `write/` (ArchUnit or package-private).
6. **A concurrency test that cannot fail (C10).** `AxonTestFixture` runs one action per phase; `parallel()` and back-to-back futures do not overlap. Use a barrier harness against the real configurer, 20–50 iterations, assert on *which* command was rejected and *why*, and run the negative control (drop `@InjectEntity` or break the criteria) and confirm it fails. Record that in the README.
7. **Retry masking the mechanism (M1, Recommended vs. Pitfall tension).** No automatic retry by default, so the conflict is observable as 409; the single-retry idiom (re-source the whole command) is documented in the design note as the production answer.
8. **Doc/version drift (M7).** Pin the 5.4 docs, grep for AF4 imports, decide the Spring `@EventSourced` annotation family once in Phase 1.

## Implications for Roadmap

Suggested phases (7). Ordering follows the dependency chain in ARCHITECTURE.md (store → events → course slice → read side → login → enroll → drop/waitlist → race → artifacts) and the feature-ordering constraints in FEATURES.md. Phase numbers are indicative; the roadmapper may merge 6 and 7 if scope is tight, but must not reorder 1 → 2 → 4.

### Phase 1: Walking skeleton and DCB store spike
**Rationale:** Everything depends on the store really enforcing append conditions. This is the single highest-risk assumption and is cheap to test first.
**Delivers:** Spring Boot 3.5.16 + Axon 5.4.1 OSS starter; explicit `InMemoryEventStorageEngine` bean; no JPA, no Axon Server (smoke test asserts boot with no network); Jackson 3 BOM ordering; `processors…mode=subscribing`; two-thread conflict spike (exactly one commit, one `AppendEventsTransactionRejectedException`); seeded login that works in one click; React + Vite shell proxying `/api`; CSRF decision frozen (recommended: disable for `/api/**`, documented as a showcase simplification); pinned doc set and version list.
**Addresses:** Seeded login (partial), one-command startup, zero external services.
**Avoids:** C1 (store not enforcing), M7 (version drift), M9 (SPA/login friction), C1 warning sign "race passes first try".

### Phase 2: Event contract and tag vocabulary
**Rationale:** Tags are a schema decision; changing them later is an event-migration problem. Every later slice depends on this contract.
**Delivers:** Event records (`CourseCreated`, `CourseCapacityChanged`, `StudentEnrolled`, `StudentDropped`, `WaitlistJoined`, `WaitlistPromoted`) with frozen names; tag-key constants used by both `@EventTag` and criteria builders; typed ids with `toString()` returning raw values; event × tag table committed to `docs/`; stored-tag-set assertion helper; test conventions (unique ids per test, one fixture per test, no sleeps).
**Addresses:** Event × tag table (P2 differentiator foundation).
**Avoids:** M2 (tag key/value drift), M3 (renaming breaks history), C7 (missing subject tags), m6 (seed bypassing commands).

### Phase 3: Course creation, catalog, and first full vertical slice
**Rationale:** Proves the read side and the UI on the simplest slice so later "seat count looks wrong" can be attributed to the boundary, not the plumbing (ARCH build-order 3–4).
**Delivers:** `CreateCourse` command slice; catalog projection (seats taken, waitlisted) with `@QueryHandler`; REST `GET /api/courses`; React course list; data seeder creating courses through commands; ArchUnit rule that `write/` does not depend on `read/`.
**Uses:** Subscribing processor, in-memory read model, Vite proxy.
**Addresses:** Create course, course catalog, one-command startup with seed data.
**Avoids:** C11 (projection as guard — rule enforced from here on), m6.

### Phase 4: Enroll under capacity — the dynamic boundary (the hinge)
**Rationale:** This is where the lesson lives. Everything after widens it. Command routing and criteria shape must be decided here, not in the race phase.
**Delivers:** `EnrollStudent` slice with `@EventSourced` decision state, `@EventCriteriaBuilder` using `either(courseCriteria, studentCriteria)`, `@InjectEntity`, `EventAppender`; decision id is decision-scoped (composite `courseId + studentId`), not `CourseId`; no routing key on the command; duplicate-enrollment rule; capacity rule; 422 "Course is full" on rule violation and 409 on append conflict; "sees other students" test; forced-conflict test; boundary annotations with invariant and included/excluded types; mapping of `AppendEventsTransactionRejectedException` (confirm wrapping at HTTP edge).
**Addresses:** Enroll, reject at capacity with named reason, reject duplicate, conflict vs rejection mapping.
**Avoids:** C2, C3, C4, C5, C6 (routing decided here), C11, M1, M4, m1, m2.
**Research flag:** NEEDS deeper research/verification during planning — `@EventSourced` nested state with composite `idType` on the OSS starter (only verified in the commercial-starter example); whether command gateway wraps `AppendEventsTransactionRejectedException`.

### Phase 5: Waitlist join and drop with atomic FIFO promotion
**Rationale:** Drop is the second half of the boundary and the pivot of the design. Atomicity must be chosen here; reversing it later is a rewrite.
**Delivers:** `JoinCourseWaitlist` (auto-invoked when full from enroll, or as the alternate outcome of enroll → `WaitlistJoined`); `DropEnrollment` with decision that both emits `StudentDropped` and `WaitlistPromoted` in one append; FIFO by stream position (never by timestamp); eligibility check skipping already-enrolled heads; duplicate-drop and double-enroll rejected; "enrol after drop" and "drop frees exactly one seat" tests; "my enrollments" read model; waitlist position display.
**Addresses:** Auto-waitlist, drop with FIFO promotion, my enrollments, waitlist depth.
**Avoids:** C8 (promotion outside boundary), C9 (timestamp FIFO), M4 (double drop), M5 (stale head), C2 (drop direction).
**Research flag:** Standard pattern once Phase 4 is proven; no separate research phase needed.

### Phase 6: Last-seat race proof and contention harness
**Rationale:** The core value is an executable proof; it needs enrollment, drop, and a working read model all in place.
**Delivers:** Barrier-driven race harness against the real configurer and command bus; asserts `succeeded == 1`, `rejectedByBoundary == n-1`, `maxInFlight > 1`, 20–50 iterations; negative-control variant (missing `@InjectEntity` or broken criteria) kept in repo and documented as failing; mixed enroll-vs-drop+promote race; optional `POST /api/demo/{courseId}/last-seat-race` returning tally, with `sendAndWait` forbidden in fan-out; UI race button with tally and rejection reason rendered verbatim.
**Addresses:** Last-seat race test, mechanism-proof test, race affordance, drop-vs-enroll race.
**Avoids:** C6 (serialized race), C10 (test cannot fail), M8 (UI lag as bug), m5 (optimistic UI hiding rejection).

### Phase 7: Teaching artifacts and UI polish
**Rationale:** Artifacts must describe real code and real behaviour, so they come last. The README trace is fiction if written earlier.
**Delivers:** README command walkthrough with expected output including the rejected hop and the 409/422 explanation; "scope" section naming deliberate omissions (auth, Axon Server, saga, waitlist policy); design note with the fixed-aggregate counterfactual and the duplicate-retry idiom; boundary-map doc; annotations audited against the invariant text; UI "last updated" label and reconciled seat counts after command.
**Addresses:** README walkthrough, design note, boundary annotations, narrated tests, failure-mode walkthrough.
**Avoids:** m4 (annotation noise), M8 (read-model lag narrative), Looks-done checklist items.

### Phase Ordering Rationale

- **1 before everything:** the store spike is the one assumption that, if false, changes the stack. Discover it before any slice exists.
- **2 before 3–5:** tags are a schema contract; changing them later invalidates every boundary.
- **3 before 4:** proves the projection and UI on the simplest slice so later count bugs are attributable.
- **4 is the hinge:** once `EnrollStudent`'s criteria rejects at capacity, the core claim is demonstrated; the routing-key and criteria-shape decisions are made here.
- **5 before 6:** the race needs a working drop path to test drop-vs-enroll and a populated waitlist.
- **7 last:** documents must describe real annotations and real output.
- **Parallelizable:** 3's read side and login work; the `ChangeCourseCapacity` stretch goal is independent of 5 but depends on 4.

### Research Flags

Phases likely needing deeper research during planning (`/gsd-plan-phase --research-phase`):
- **Phase 1:** Verify DCB conflict detection in `InMemoryEventStorageEngine` on 5.4.1 with the OSS starter — docs and source contradict; the spike is the research. Also confirm the Jackson 3 + record payload path.
- **Phase 4:** Confirm `@EventSourced` nested state with composite `idType` on the OSS starter (the verified example uses the commercial starter); confirm exception wrapping at the HTTP edge and property keys for processor mode.
- **Phase 6:** Confirm the default sequencing policy on 5.4.1 and whether any retry policy is active by default (affects `rejectedByBoundary` vs `rejectedByRule` tally).

Phases with standard patterns (skip research-phase):
- **Phase 2:** Event records, tag constants, and a stored-tag assertion are plain Java/Axon conventions.
- **Phase 3:** Standard command slice, in-memory projection, Spring MVC controller, React list.
- **Phase 5:** Pattern is already settled by Phase 4; the drop+promote single append is the documented DCB pattern.
- **Phase 7:** Documentation phase; no research needed beyond writing from working code.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Versions and coordinates verified against Maven Central metadata, official 5.4 docs, and the Axon parent POM. The in-memory DCB claim is MEDIUM until the Phase 1 spike passes. |
| Features | MEDIUM | Table stakes and anti-features are consistent across the reference demos and the DCB literature. Waitlist behaviour has no upstream precedent — the design note must carry that argument itself. |
| Architecture | MEDIUM | Slice structure and criteria shapes follow AxonIQ `university-demo` and the 5.x docs. Composite-id entity registration on the OSS starter and the processor property keys are less trodden and flagged for verification. |
| Pitfalls | HIGH on semantics, MEDIUM on embedded guarantee | Criteria boolean rules, append-condition derivation, sequencing defaults, and FIFO/timestamp issues are consistent across primary docs and source. The embedded store's enforcement is the one contradiction that drives the Phase 1 gate. |

**Overall confidence:** MEDIUM — the research is strong enough to plan, but the architecture rests on one assumption (the in-memory store enforces append conditions under real concurrency) that must be proven in Phase 1 before anything else is built.

### Gaps to Address

- **In-memory DCB under real concurrency:** docs say no, source and university-demo say yes. Resolve with the Phase 1 two-thread spike. If it fails, fall back to a local PostgreSQL engine (commercial licence) or Axon Server, and update `PROJECT.md`'s no-Docker and no-license constraints explicitly rather than faking the guarantee.
- **Event naming and tag-key inconsistency across research:** ARCHITECTURE uses `WaitlistJoined` / `WaitlistPromoted`; PITFALLS uses `StudentWaitlisted` / `StudentPromotedFromWaitlist`; PITFALLS also uses tag keys `course`/`student` where ARCHITECTURE and STACK use `courseId`/`studentId`. Pick one set in Phase 2 (recommended: ARCHITECTURE's names and `courseId`/`studentId` keys, since STACK's code samples use them) and record it in the event × tag table.
- **Automatic retry on conflict:** ARCHITECTURE and FEATURES assume zero retries (so the 409 is observable); PITFALLS M1 recommends one re-dispatch at the edge. Recommended resolution: zero retries in the default path, 409 mapping in the API, single-retry documented as the production idiom in the design note. Confirm no default retry policy is active on 5.4.1 (affects the tally labels).
- **`@EventSourced` composite id and nested state on the OSS starter:** verified only in the commercial-starter example. Confirm in Phase 4 with a boot test; fallback is registering the entity module manually as a bean.
- **Exception wrapping at the HTTP edge:** confirm whether `CommandGateway` wraps `AppendEventsTransactionRejectedException` in `CommandExecutionException`, so the 409 mapping matches runtime, not the class name.
- **Jackson 3 record payloads on Spring Boot 3.5.16:** confirm `tools.jackson` is used for event conversion rather than silently falling back to Jackson 2. `Jackson2Converter` is the documented escape hatch.
- **Subscribing processor property keys:** confirm `axon.eventhandling.processors.<name>.mode=subscribing` resolves in 5.4.1's `EventProcessorProperties`.
- **TypeScript 7.0.x with `@tanstack/react-query` types:** low risk; the first `npm run build` is the check.
- **Security decision:** CSRF handling for the SPA (STACK/ARCH suggest cookie-based CSRF or a documented ignore for `/api/**`). Decide in Phase 1 and freeze.

## Sources

### Primary (HIGH confidence)
- Axon Framework 5.4 reference (`docs.axoniq.io/axon-framework-reference/5.4/…`) — event store internals, infrastructure, command processing, sequencing policy migration, testing, Spring Boot integration, event-sourced entities.
- Maven Central metadata and Axon parent POM (`repo1.maven.org`) — BOM and starter coordinates, `spring.boot.version=3.5.16`, `jackson.version=3.2.3`.
- AxonFramework source (`InMemoryEventStorageEngine`, `EventSourcingConfigurationDefaults`, `JpaEventStoreAutoConfiguration`, `EventProcessorProperties`) — per STACK.md and PITFALLS.md.
- npm registry and `create-vite@9.3.4` `template-react-ts` — frontend versions.

### Secondary (MEDIUM confidence)
- `github.com/AxonIQ/university-demo` and `AxonIQ/AxonFramework/examples/university-java-springboot-3` — reference slice structure, `@EventCriteriaBuilder`/`@EventSourced` shapes, in-memory DCB claim.
- `dcb.events` specification and course-subscriptions example — per-command read/tag tables; tag-semantics FAQ.
- AxonIQ concept pages and DCB announcement (`axoniq.io/concepts/dynamic-consistency-boundary`, `axoniq.io/blog/dcb-in-af-5`).
- AxonFramework issue #4252 — `AxonTestFixture` processor catch-up at phase transitions.
- `maxzillabong/axon-5-dcb-demo` — DCB-vs-saga framing.

### Tertiary (LOW confidence — needs validation)
- Waitlist policy practice (UBC, UCR Banner, Workday, Absorb LMS) — used only to confirm that auto-promotion versus offer-with-expiry is the real policy fork; product docs, not academic.
- Showcase-repo practice (Nimbus, evo9/es-wallet, andreschaffer/event-sourcing-cqrs-examples) — inferred practice, not documented standard.

---
*Research completed: 2026-11-10*
*Ready for roadmap: yes*
