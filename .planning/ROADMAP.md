# Roadmap: Axon 5 DCB Showcase

## Overview

A course-enrollment app whose real product is a lesson: a command decides using exactly the events its invariant requires, proven by a last-seat race. Three services run behind Axon Server with a PostgreSQL read model, so the boundary must hold across process and network boundaries. Phases fail for one reason each: build skeleton → session and message path → contract → DCB spike → first slice → the boundary hinge → waitlist/drop atomicity → **the race proof** → read-side completeness → live updates → the lesson. The race comes before the UI polish on purpose: it is the Core Value, and a late defect there would invalidate everything built on top.

## Phases

- [ ] **Phase 1: Build and infrastructure skeleton** - Six modules, three boot apps, compose stack, formatting, FSD and Playwright MCP gated in CI
- [ ] **Phase 2: Session and message path** - Seeded login reaches the services and a probe command/query is routed through Axon Server
- [ ] **Phase 3: Message and tag contract** - Events, commands, queries, identifiers and tag keys are frozen in `domain`
- [ ] **Phase 4: DCB append-condition spike** - Two concurrent appends prove the boundary, and a post-commit emit reaches a remote subscriber
- [ ] **Phase 5: Course creation across services** - Command → event → read → UI works end to end, and creation is idempotent
- [ ] **Phase 6: Enroll under capacity** - The dynamic boundary enforces the seat cap and distinguishes conflict from business rejection
- [ ] **Phase 7: Waitlist and atomic drop/promotion** - Joining a waitlist is FIFO and a drop promotes in the same append
- [ ] **Phase 8: Last-seat race proof** - N distinct students, exactly one enrolled, boundary rejection proven and falsifiable
- [ ] **Phase 9: My enrollments and seeded scenarios** - A student sees only their own data, and the demo state is seeded through commands
- [ ] **Phase 10: Live updates** - Read-model snapshots and changes arrive over SSE; no polling
- [ ] **Phase 11: Teaching artifacts** - README, design note and the event × tag table complete the lesson

## Phase Details

### Phase 1: Build and infrastructure skeleton
**Goal**: `docker compose up -d` plus one Gradle run starts Axon Server, PostgreSQL and three empty Spring Boot services; the SPA is part of the same build graph; formatting, FSD and Playwright MCP are wired and CI enforces them.
**Mode**: mvp
**Depends on**: Nothing (first phase)
**Requirements**: PLAT-01, PLAT-05, QUAL-01, E2E-01, TOPO-01, TOPO-04, FE-01, FE-02
**Success Criteria** (what must be TRUE):
  1. The compose stack brings up Axon Server and PostgreSQL, and one Gradle run starts three services that all register with the server (PLAT-01).
  2. Each service is an independent Spring Boot application producing its own bootJar (TOPO-01).
  3. `./gradlew build` produces the SPA bundle and all three bootJars with no separate npm invocation (TOPO-04).
  4. CI builds the project on every PR, starts Axon Server and PostgreSQL for integration tests, and names the job that proves they ran (PLAT-05).
  5. CI fails on unformatted Kotlin (`spotlessCheck`), unformatted TS/TSX (`format:check`) and unformatted docs/workflows (`scripts/format-check.sh`) (QUAL-01).
  6. The SPA is scaffolded by FSD layers with a public `index.ts` per slice, and `npm run lint:fsd` fails on a layer violation or a deep import (FE-01, FE-02).
  7. The tracked `opencode.jsonc` configures the Playwright MCP server, and an agent can open the SPA shell headless through it (E2E-01).
**Plans**: 2/6 plans executed

Plans:
- [x] 01-01-PLAN.md — Build graph + `write` service registering with Axon Server (tracer)
- [ ] 01-02-PLAN.md — `read` and `api` services; three independent bootJars
- [x] 01-03-PLAN.md — Frontend module, FSD tree, steiger + Prettier gates
- [ ] 01-04-PLAN.md — SPA embedded in `api` bootJar + `scripts/smoke-stack.sh`
- [ ] 01-05-PLAN.md — CI `build` job (Node, Compose, integrationTest) + `.gitignore`
- [ ] 01-06-PLAN.md — Playwright MCP headless proof + local run-loop docs
**UI hint**: no

### Phase 2: Session and message path
**Goal**: A seeded user can log in, and the gateway can route one probe command and one probe query through Axon Server to the services that handle them.
**Mode**: mvp
**Depends on**: Phase 1
**Requirements**: AUTH-01, TOPO-02, TOPO-03, PLAT-03
**Success Criteria** (what must be TRUE):
  1. A user logs in as a seeded account and the identity is available to downstream services (AUTH-01).
  2. A probe command sent by `api` is handled by `write`, and a probe query by `read`, both through Axon Server; `api` depends only on `domain` (TOPO-02).
  3. Guard tests fail if `write` gains a datasource or JPA, if any service other than `write` gains an `EventAppender`, or if Flyway or an event processor appears outside `read` (TOPO-03, PLAT-03).
  4. The same integration test passes locally against the compose stack and in CI from a clean checkout (PLAT-03).
**Plans**: TBD
**UI hint**: yes

### Phase 3: Message and tag contract
**Goal**: The message types, identifiers and tag vocabulary every later phase compiles against are frozen, so a name or tag key cannot silently change under a later boundary.
**Mode**: mvp
**Depends on**: Phase 2
**Requirements**: ID-01, TOPO-05
**Success Criteria** (what must be TRUE):
  1. Events, commands and queries are Kotlin data classes in `domain`, with no Spring or persistence dependency (TOPO-05).
  2. Identifiers are UUIDv7 wrapped in typed value classes, and their canonical string form is what lands in `@EventTag` (ID-01).
  3. `@EventTag` is applied with a working Kotlin use-site target, and a stored-tag assertion proves the tag set is persisted rather than only present in the payload.
  4. Tag keys (`courseId`, `studentId`) are constants used by both `@EventTag` and the criteria builders, and `docs/event-tag-table.md` exists as the single canonical event × tag artifact.
**Plans**: TBD

### Phase 4: DCB append-condition spike
**Goal**: Two concurrent appends whose criteria collide deterministically yield one commit and one boundary rejection against Axon Server; a post-commit emit reaches a remote subscriber; the Kotlin annotation path is proven.
**Mode**: mvp
**Depends on**: Phase 3
**Requirements**: *(no v1 requirement — this phase retires the project's biggest technical risks before feature work)*
**Success Criteria** (what must be TRUE):
  1. A two-thread spike with `maxInFlight > 1` produces exactly one commit and one `AppendEventsTransactionRejectedException` against Axon Server with DCB available under the server's default configuration; if a server-side flag turns out to be required, it is recorded in `compose.yaml` and the phase note.
  2. The spike uses message shapes from `domain` and no ad-hoc event types.
  3. The spike proves the routing-key hazard: adding a `courseId` routing key serializes the same test and it stops producing a conflict.
  4. An emit issued from `@TransactionalEventListener(AFTER_COMMIT)` inside `read` reaches a remote subscriber through Axon Server, and a rolled-back write emits nothing.
  5. A short note records the observed behavior of the commercial starter on Boot 4, including the Axon Server license window.
**Plans**: TBD

### Phase 5: Course creation across services
**Goal**: The first full distributed slice works in the browser — an admin creates a course, `write` appends it, `read` writes PostgreSQL and serves it, and the UI renders the catalog; repeating the create is idempotent.
**Mode**: mvp
**Depends on**: Phase 4
**Requirements**: AUTH-03, CRSE-01, CRSE-02, CRSE-03, IDEM-01, RDMD-01, RDMD-02
**Success Criteria** (what must be TRUE):
  1. An admin creates a course with a client-supplied id, title and capacity through the UI, and the catalog shows it (CRSE-01).
  2. Repeating the same create request returns the existing course, appends no second event, and leaves the catalog unchanged (IDEM-01).
  3. A student attempting the same call receives 403 (AUTH-03).
  4. Invalid creation (blank title, capacity < 1) is rejected with a named business error, and capacity cannot be changed afterwards (CRSE-03).
  5. The catalog renders seats remaining from the read model; restarting all services leaves it intact, `read` is the only module with JPA and Flyway, and a missing or renamed column fails startup (CRSE-02, RDMD-01, RDMD-02).
**Plans**: TBD
**UI hint**: yes

### Phase 6: Enroll under capacity
**Goal**: A student enrolls while seats remain, and the decision is enforced by a boundary that reads only the events the seat invariant needs — rejecting at capacity and on duplicates, and distinguishing a business rejection from an append conflict.
**Mode**: mvp
**Depends on**: Phase 5
**Requirements**: ENRL-01, ENRL-02, ENRL-03, ENRL-06, PLAT-04, DOCS-02
**Success Criteria** (what must be TRUE):
  1. A student enrolls and the catalog's seat count decreases once `read` has committed and pushed the change (ENRL-01).
  2. Enrolling twice, or while already waitlisted, is rejected with a named business error (ENRL-02).
  3. Enrolling in a full course returns 422 `Course is full`; an append conflict returns 409 with the conflicting tags; unknown course returns 404 (ENRL-03, PLAT-04).
  4. A test asserts the enroll `EventCriteria` equals exactly the frozen type set over `courseId` (ENRL-06).
  5. The enroll `@EventCriteriaBuilder` carries a comment block naming tag keys, the exact event types, and the handler consuming each type (DOCS-02).
**Plans**: TBD
**UI hint**: yes

### Phase 7: Waitlist and atomic drop/promotion
**Goal**: Joining a full course's waitlist is a separate explicit action with FIFO order, and dropping an enrollment promotes the first waitlisted student in the same append.
**Mode**: mvp
**Depends on**: Phase 6
**Requirements**: ENRL-04, ENRL-05, ENRL-07, DROP-01, DROP-02
**Success Criteria** (what must be TRUE):
  1. A rejected student can join the waitlist explicitly, and the catalog's waitlist depth increases (ENRL-04).
  2. Two joins with identical payload timestamps still promote in append order (ENRL-05).
  3. Dropping a non-enrollment or another student's enrollment is rejected; dropping your own frees the seat and promotes the first waitlisted student in one append (DROP-01, DROP-02).
  4. No observable state ever has more active enrollments than capacity, including during promotion (ENRL-07).
  5. The drop/promotion `@EventCriteriaBuilder` carries the same comment block as the enroll builder (DOCS-02).
**Plans**: TBD
**UI hint**: yes

### Phase 8: Last-seat race proof
**Goal**: A reproducible race across services proves that N distinct students competing for one seat yield exactly one enrollment, with the boundary rejection proven, the business rejection tallied separately, and a negative control proving the test can fail.
**Mode**: mvp
**Depends on**: Phase 7
**Requirements**: RACE-01, RACE-02, RACE-03, RACE-04, RACE-05, E2E-02, DOCS-03
**Success Criteria** (what must be TRUE):
  1. `POST /demo/race` starts N ≥ 2 distinct students behind a barrier against a freshly created one-seat course and returns `{enrolled, full, conflicted, maxInFlight}` (RACE-01, RACE-05).
  2. Exactly one caller is `enrolled`, and `enrolled + full + conflicted == N` (RACE-02).
  3. The forced-overlap variant asserts `AppendEventsTransactionRejectedException` with the conflicting `courseId`, and `maxInFlight > 1` proves the race was not serialized (RACE-03).
  4. The tagged negative-control harness runs via a separate Gradle task and a manually triggered CI job, yields more than one winner, and the README quotes the command and output (RACE-04).
  5. Playwright runs the full user journey against the Compose stack — login, create course, enroll, drop, race — and the job publishes its report (E2E-02).
  6. Race and drop/promotion tests use sentence-form names and are listed in the README's evidence section (DOCS-03).
**Plans**: TBD
**UI hint**: yes

### Phase 9: My enrollments and seeded scenarios
**Goal**: A signed-in student sees only their own enrollments and waitlist positions, and first boot seeds the three demo scenarios through real commands.
**Mode**: mvp
**Depends on**: Phase 8
**Requirements**: AUTH-02, VIEW-01, PLAT-02
**Success Criteria** (what must be TRUE):
  1. A student's view lists their enrollments with state and waitlist position (VIEW-01).
  2. Requesting another student's enrollments is rejected with 403, including when the id arrives in a body or query parameter (AUTH-02).
  3. First boot seeds an open course, a full course, and a full course with a waitlist through commands; a restart does not duplicate them, and demo seeding is disabled in the test profile (PLAT-02).
**Plans**: TBD
**UI hint**: yes

### Phase 10: Live updates
**Goal**: The UI receives read-model snapshots and subsequent changes over SSE, emitted by `read` after commit, with reconnect recovery and no polling.
**Mode**: mvp
**Depends on**: Phase 9
**Requirements**: VIEW-02, VIEW-03, TOPO-06
**Success Criteria** (what must be TRUE):
  1. One subscription-query call yields the snapshot and then a pushed update emitted by `read`; the UI renders it within 2 seconds of the commit and performs no seat arithmetic (VIEW-02, VIEW-03).
  2. A dropped client reconnects with `Last-Event-ID` and recovers the final state (VIEW-03).
  3. The stream carries whole read-model snapshots; `api` has no event-stream subscription and no domain event reaches the browser (TOPO-06).
  4. A rolled-back write produces no emitted update, and no client remains on a stale value after reconnect.
**Plans**: TBD
**UI hint**: yes

### Phase 11: Teaching artifacts
**Goal**: The lesson is complete and honest — the README traces one command across all three services, and the design note argues why the dynamic boundary beats fixed aggregates and why distribution does not weaken the guarantee.
**Mode**: mvp
**Depends on**: Phase 10
**Requirements**: DOCS-01, DOCS-04, DOCS-05
**Success Criteria** (what must be TRUE):
  1. The README walks one command across `api`, `write` and `read` with expected output, including a rejected hop and the 409-vs-422 distinction (DOCS-01).
  2. The README links `docs/event-tag-table.md` and quotes the table per command (DOCS-05).
  3. The design note compares the dynamic boundary with fixed-aggregate alternatives, covers the mega-course aggregate and student-aggregate-plus-saga counterfactuals, and explains the single-retry idiom and why distribution does not weaken the append condition (DOCS-04).
**Plans**: TBD

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Build and infrastructure skeleton | 2/6 | In Progress | - |
| 2. Session and message path | 0/TBD | Not started | - |
| 3. Message and tag contract | 0/TBD | Not started | - |
| 4. DCB append-condition spike | 0/TBD | Not started | - |
| 5. Course creation across services | 0/TBD | Not started | - |
| 6. Enroll under capacity | 0/TBD | Not started | - |
| 7. Waitlist and atomic drop/promotion | 0/TBD | Not started | - |
| 8. Last-seat race proof | 0/TBD | Not started | - |
| 9. My enrollments and seeded scenarios | 0/TBD | Not started | - |
| 10. Live updates | 0/TBD | Not started | - |
| 11. Teaching artifacts | 0/TBD | Not started | - |
