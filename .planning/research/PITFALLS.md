# Pitfalls Research

**Domain:** Axon Framework 5 Dynamic Consistency Boundaries (DCB) — course-enrollment demo across three services
**Researched:** 2026-11-10 (re-based after the distributed pivot and three independent reviews)

This file is specific to this project, not generic web advice. Each entry states the failure, how to notice it early, how to prevent it, and which phase owns it.

---

## DCB mechanics

### C1: `havingTags(a, b)` is AND, not OR

**Failure:** `EventCriteria.havingTags(Tag.of("courseId", …), Tag.of("studentId", …))` matches only events carrying *both* tags. Enrollment decisions see one student's history, the course looks empty, and capacity is oversold.
**Warning signs:** the course list shows fewer enrollments than exist; a fresh student can enroll in a full course; the seat counter never rises.
**Prevention:** one criterion per subject, combined with `EventCriteria.either(...)`. Assert the rendered criteria in a test rather than eyeballing it.
**Phase:** 3 (contract) and 6 (enroll).

### C2: no `@InjectEntity` → no append condition

**Failure:** a handler that builds state some other way and calls `EventAppender` has no sourcing condition, so the append condition degenerates to `ConsistencyMarker.INFINITY` and conflict checking is skipped.
**Warning signs:** the race test passes but the tally shows zero `conflicted`; two winners exist in the event stream.
**Prevention:** every decision takes `@InjectEntity` state; guard tests assert the criteria and the marker; the negative control (RACE-04) exists to catch this class.
**Phase:** 6, proven in 8.

### C3: a routing key serializes the race away

**Failure:** AF5 sequences commands by routing key (`RoutingKeySequencingPolicy` is the default). Putting `courseId` on `EnrollStudent` serializes every enrollment for that course, so the race never reaches the append condition.
**Warning signs:** the race always reports `conflicted = 0`; `maxInFlight == 1`; timing changes do not change results.
**Prevention:** no routing key on `EnrollStudent` (project decision). Phase 4 proves the hazard by adding the key temporarily and watching the conflict disappear.
**Phase:** 4.

### C4: the event-type set is too narrow

**Failure:** a decision omits a type that can move the invariant. The classic case: `EnrollStudent` omits `WaitlistPromoted`, so promotions silently consume seats the decision thinks are free.
**Warning signs:** capacity is exceeded after a drop/promote cycle; the decision model and the projection disagree.
**Prevention:** for each command, list every event that can change *its* invariant, and justify exclusions in the builder comment (DOCS-02). Do not copy a neighbour's set.
**Phase:** 3, 6, 7.

### C5: Kotlin annotation targets for `@EventTag`

**Failure:** `@EventTag` declares `@Target({METHOD, FIELD, ANNOTATION_TYPE})` — `PARAMETER` is absent. `@param:EventTag` is a compile error, not a silent no-op, and code written from a wrong assumption stalls.
**Warning signs:** compilation failure on the event class; or a stored-tag assertion finding no tags when the annotation was placed somewhere Kotlin did not emit it.
**Prevention:** put `@EventTag` on the constructor property with no use-site target (Kotlin picks the field) or use `@field:`; `@get:` also works. Prove with the stored-tag assertion.
**Phase:** 3.

### C6: static criteria builders in Kotlin

**Failure:** Axon requires a static method returning `EventCriteria`. Kotlin has no statics, so the metamodel never finds the builder.
**Warning signs:** the default criteria is used (entity id becomes the tag) and the boundary silently widens.
**Prevention:** `companion object { @JvmStatic @EventCriteriaBuilder fun criteria(...) }`, verified by asserting the rendered criteria.
**Phase:** 3, 6.

### C7: tags select, payloads decide

**Failure:** treating tags as data. A decision scoped to `courseId` can still check "is this student enrolled?" only by reading the payload of `StudentEnrolled`.
**Warning signs:** someone adds a `studentId` tag to an event that never needed it, or widens criteria to `either(...)` "to make the check work".
**Prevention:** document per command which tags select and which payload fields decide; keep `either(courseId, studentId)` as the explicit v2 widening example.
**Phase:** 3.

---

## Distributed topology

### D1: a datasource in `courses-write`

**Failure:** without an Axon Server connector, `spring-boot-starter-data-jpa` plus a `DataSource` lets `JpaEventStoreAutoConfiguration` install an aggregate-based store that cannot do DCB. With the connector present Axon Server storage wins, but a service that never needs a datasource should not carry the risk.
**Warning signs:** append conditions never conflict; entity tables appear in the database; the race becomes a no-op.
**Prevention:** no datasource dependency in `courses-write`; a guard test asserting no `EntityManagerFactory` bean.
**Phase:** 2.

### D2: the read side writes

**Failure:** a write-capable datasource outside `courses-read`, or an `EventAppender` anywhere on the read side, reopens the door to two owners of one model.
**Warning signs:** schema changes outside Flyway; events appended by a service that is not `courses-write`; a second datasource.
**Prevention:** guard tests: no datasource outside `courses-read`, no `EventAppender` outside `courses-write`, no DDL outside Flyway.
**Phase:** 2, 5.

### D3: schema drift inside the read model

**Failure:** `courses-read` owns Flyway; any DDL tooling outside it can alter columns, and the models diverge until a read fails in traffic.
**Warning signs:** columns no migration created; a clean database behaves differently from a warm one.
**Prevention:** the read path uses Spring Data JDBC only; Flyway is the sole DDL source; a startup smoke check runs every read query with `LIMIT 0` and fails fast naming the missing column.
**Phase:** 5.

### D4: eventual consistency presented as instant

**Failure:** the UI decrements seats optimistically; the projection lags; the seat count jumps back and the demo looks broken at its best moment.
**Warning signs:** counts change twice; the race tally disagrees with the catalog.
**Prevention:** pending state → refetch → new values (VIEW-02); never compute seat counts client-side.
**Phase:** 5–10.

### D5: projection starts at the wrong place

**Failure:** a fresh `courses-read` replays from the beginning and duplicates writes, or resumes from a stale token and never sees new events.
**Warning signs:** duplicated rows after restart; a catalog that stops updating.
**Prevention:** rely on the Axon Server token store; make seeding idempotent (PLAT-02); verify catch-up in Phase 5 with a restart.
**Phase:** 2, 5.

### D6: commercial starter + Boot 4 integration

**Failure:** the commercial `axoniq-spring-boot-starter` on Boot 4.1.2 is not exercised by Axon's own build (its baseline is 3.5.16); Jackson skew (Boot manages 3.1.7, Axon targets 3.2.3) can surface as conversion errors.
**Warning signs:** startup fails on auto-configuration; events fail to deserialize; two Jackson databind versions on the classpath.
**Prevention:** a bare boot test in Phase 1; one event round-trip in Phase 3; fall back to Boot 3.5.16 if the wall is real.
**Phase:** 1, 3.

### D7: the Axon Server license window

**Failure:** Axon Server 2026.0+ runs 12 hours without a license and then shuts down; AF 5.3.2+ requests an hourly, machine-tied development permission and logs a warning when it cannot.
**Warning signs:** the stack dies mid-session; license warnings every 15 minutes.
**Prevention:** document the window; restart or obtain a dev license for long sessions; never present the demo as production-ready.
**Phase:** 1.

---

## Testing

### T1: `AxonTestFixture` cannot test contention

**Failure:** the fixture dispatches one action per `when` phase, so a "race test" written with it never overlaps and always passes.
**Warning signs:** the race test has `maxInFlight == 1`; it passes even with a deliberately broken boundary.
**Prevention:** slice tests use the fixture; contention tests use a barrier-driven harness against the live Axon Server (RACE-01), with the source-set-isolated negative control from RACE-04.
**Phase:** 8.

### T2: a negative control that ships as a failing test

**Failure:** RACE-04 as a permanently failing test breaks CI and gets skipped.
**Warning signs:** a `@Disabled` test nobody reads; PLAT-05 green while the control does not run.
**Prevention:** put the harness in its own source set (`src/negativeControl/kotlin`, suffix `*NC`) with its own `negativeControlTest` task, never in `integrationTest`; execute it explicitly in a `workflow_dispatch` CI job and quote its output in the README (ADR-0003: no tag filtering).
**Phase:** 8.

### T3: integration tests need the world

**Failure:** race and guard tests need Axon Server and PostgreSQL; discovering this in Phase 8 turns the harness into a project of its own.
**Warning signs:** "run manually" tests; sleeps standing in for readiness; flaky CI.
**Prevention:** already decided — Testcontainers, one container per class on random ports (ADR-0003); wire one integration test through CI in Phase 2 and gate on container readiness, never on a pre-started Compose stack.
**Phase:** 2, extended in 8.

---

## Operations

### O1: moving container tags

**Failure:** `axoniq/axonserver:2026.1` does not exist — published tags are patch-level (`2026.1.7`), and `postgres:17` is one major behind (18 is current).
**Warning signs:** `docker compose up` fails to pull; a rebuild silently changes the runtime.
**Prevention:** pin exact patch tags in `compose.yaml` and record them in the stack doc.
**Phase:** 1.

### O2: committed state that pretends to be ephemeral

**Failure:** documents or code claiming events vanish on restart while the real store is Axon Server, leading to "reseed every boot" logic that duplicates data.
**Warning signs:** duplicated courses after restart; a seeder without an existence check.
**Prevention:** seeding is idempotent and first-boot-only (PLAT-02); the store is durable.
**Phase:** 9.

---

## Live updates (SSE)

### L1: streaming events instead of views

**Failure:** the gateway subscribes to the event stream (or the client consumes raw events) because "it is already there". The UI then depends on event names, revisions and payload shapes, and the read model becomes optional.
**Warning signs:** frontend imports event types; a rename breaks the UI; the page needs to fold events to render a seat count.
**Prevention:** the gateway relays a subscription query only; TOPO-06 asserts it has no event subscription; the payload is a read-model DTO.
**Phase:** 10.

### L2: a gap between snapshot and subscription

**Failure:** the client fetches a snapshot, then subscribes; an update lands between the two and is lost until the next change.
**Warning signs:** a seat count that is stale until something else happens; a test that only passes when the update is delayed.
**Prevention:** one `subscriptionQuery` call: its first item is the snapshot, later items are updates. Both are produced by `courses-read`, so there is no second source. "GET then open stream" stays forbidden.
**Phase:** 10.

### L3: reconnect loses the final state

**Failure:** `EventSource` reconnects but the server starts from scratch or from "now", so the client shows an outdated catalog.
**Warning signs:** after a network blip the UI disagrees with a manual reload.
**Prevention:** emit `id:` per event and honour `Last-Event-ID`; on an unknown id, resend the snapshot.
**Phase:** 10.

### L4: leaked emitters and dead clients

**Failure:** the subscription behind a `Flux<ServerSentEvent<…>>` is never cancelled, so a closed tab keeps a subscription and its sink forever.
**Warning signs:** growing subscription count; memory climbing in a long demo; updates to nobody.
**Prevention:** cancel the underlying subscription in `doFinally`; a heartbeat sink detects half-open connections; bounded per-client buffers. Do not introduce an `SseEmitter` — STACK forbids manual emitter bridging.
**Phase:** 10.

### L5: slow client backpressure

**Failure:** a stalled client makes the update sink unbounded, and one tab stalls the publisher.
**Warning signs:** memory growth correlated with a slow consumer; updates delayed for everyone.
**Prevention:** bounded buffer per subscriber with drop-and-resnapshot semantics, never an unbounded `Sinks.Many` shared across clients.
**Phase:** 10.

---

## Frontend (FSD)

### F1: `shared` becomes a dumping ground

**Failure:** anything reused twice lands in `shared/ui` — including `CourseCard` and enrollment rules. `shared` then depends on domain vocabulary while claiming to know nothing.
**Warning signs:** `shared/ui` imports entity types; `shared/lib` knows what a seat is.
**Prevention:** a component is `shared` only if it renders no domain concept; everything else belongs to an entity. Steiger cannot catch semantics — review it.
**Phase:** 5.

### F2: deep imports bypass the public API

**Failure:** `pages/courses` imports `entities/course/model/seat-count` directly. The slice can no longer change its internals and the boundary is fiction.
**Warning signs:** import paths with more than one segment after the slice name.
**Prevention:** `index.ts` per slice; steiger's public-API rule fails CI on deep imports.
**Phase:** 5.

### F3: pages that fetch

**Failure:** `pages/course-detail` calls `useQuery` with a raw URL. Data loading spreads across pages, cache keys drift, and SSE updates have no single place to land.
**Warning signs:** `fetch(` or query keys inside `pages/`.
**Prevention:** fetching lives in `entities/*/api` or `features/*/api`; pages call hooks exposed by the slice.
**Phase:** 5.

### F4: FSD ceremony without slices

**Failure:** layers exist as empty folders; everything sits in `pages`. The structure passes a directory listing and teaches nothing.
**Warning signs:** `widgets/` and `features/` empty; a page of 400 lines.
**Prevention:** create a layer only when its first real slice appears; keep pages to composition plus layout.
**Phase:** 5.

---

## End-to-end tests

### E1: testing a mock and calling it an E2E

**Failure:** the suite runs against a stubbed API because the real stack is slow to start. It then proves nothing about Axon Server, PostgreSQL, or the projection lag.
**Warning signs:** MSW/mock imports inside `e2e/`; green tests while the app is broken.
**Prevention:** the E2E profile always talks to the real gateway; the e2e CI job builds the four images and starts Compose, and the local run uses the same Compose path.
**Phase:** 8.

### E2: flaky waits

**Failure:** `waitForTimeout(2000)` around SSE or projection lag. It passes on a fast machine and fails in CI.
**Warning signs:** sleep calls; retries configured to hide failures.
**Prevention:** assert on the value with Playwright's auto-retrying assertions; if a value never converges, that is a product bug.
**Phase:** 8.

### E3: shared state between tests

**Failure:** every test enrolls the same seeded student into the same seeded course; order dependence and seat exhaustion.
**Warning signs:** tests pass alone and fail together.
**Prevention:** each test creates its own course with a UUIDv7 id and acts as one of the fixed seeded students; the E2E profile disables only the three demo courses, never the accounts.
**Phase:** 8.

### E4: the MCP is mistaken for CI

**Failure:** a green agent-driven Playwright MCP session is treated as evidence, while no automated test exists.
**Warning signs:** "we tested it manually with the agent"; no `e2e` CI job.
**Prevention:** E2E-02 requires the suite in CI; the MCP is exploration only.
**Phase:** 1 and 8.

### L6: emitting from the wrong node

**Failure:** a service that does not own the data calls `emit(...)` for a query it does not answer. `QueryUpdateEmitter` resolves the *local* query bus, so the update reaches only subscriptions this node holds — usually none. The stream silently never updates.
**Warning signs:** SSE delivers the snapshot and then nothing; `emitAndCount` returns an empty result; cross-node tests pass only with one service in the stack.
**Prevention:** the node advertising the query must also own the data it emits. Here `courses-read` writes the row and emits it, so there is no cross-service emit at all.
**Phase:** 4, used in 10.

### L7: announcing before the commit

**Failure:** the read service calls `QueryUpdateEmitter.emit(...)` directly inside its `@EventHandler`. If the JPA transaction later rolls back, subscribers have already been told about a row that never committed.
**Warning signs:** updates that occasionally show a value a later read cannot reproduce; UI state that jumps and then reverts.
**Prevention:** publish an application event inside the `@Transactional` handler and emit from `@TransactionalEventListener(AFTER_COMMIT)`; carry a `version` so an out-of-order emission is dropped.
**Phase:** 5, exercised in 10.

### L8: a cache that outlives the emit

**Failure:** query results are cached, but eviction happens on a different path (or on a timer) than the SSE emit. The stream shows the new seat count while the next GET returns the old one.
**Warning signs:** reload shows a different value than the live update; tests pass because the cache is cold.
**Prevention:** if caching is ever added (PERF-01), evict in the same `AFTER_COMMIT` listener that emits, keyed by `courseId`; otherwise do not cache.
**Phase:** none in v1 — deferred decision.
