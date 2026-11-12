# Roadmap: Axon 5 DCB Courses

## Overview

A course-enrollment app whose real product is a lesson: a command decides using exactly the events its invariant requires, proven by a last-seat race. Three services run behind Axon Server with a PostgreSQL read model, so the boundary must hold across process and network boundaries. Phases fail for one reason each: build skeleton → session and message path → contract → DCB spike → first slice → the boundary hinge → waitlist/drop atomicity → **the race proof** → read-side completeness → live updates → the lesson. The race comes before the UI polish on purpose: it is the Core Value, and a late defect there would invalidate everything built on top.

## Phases

- [x] **Phase 1: Build and infrastructure skeleton** - Six modules plus a test-support library, three boot apps, compose stack, formatting, FSD and Playwright MCP gated in CI
- [ ] **Phase 2: Session and message path** - Seeded login reaches the services and a probe command/query is routed through Axon Server
- [ ] **Phase 3: Message and tag contract** - Events, commands, queries, identifiers and tag keys are frozen in `courses-domain`
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
**Plans**: 6/6 plans executed

Plans:
- [x] 01-01-PLAN.md — Build graph + `courses-write` service registering with Axon Server (tracer)
- [x] 01-02-PLAN.md — `courses-read` and `courses-api` services; three independent bootJars
- [x] 01-03-PLAN.md — Frontend module, FSD tree, steiger + Prettier gates
- [x] 01-04-PLAN.md — SPA build wiring + `scripts/smoke-stack.sh` (the embedded-SPA step this plan originally scoped was superseded by ADR-0004)
- [x] 01-05-PLAN.md — CI `build` job (Node, Compose, integrationTest) + `.gitignore`
- [x] 01-06-PLAN.md — Playwright MCP headless proof + local run-loop docs
**UI hint**: no

### Phase 2: Session and message path
**Goal**: A seeded user can log in, and the gateway can route one probe command and one probe query through Axon Server to the services that handle them.
**Mode**: mvp
**Depends on**: Phase 1
**Requirements**: AUTH-01, TOPO-02, TOPO-03, PLAT-03
**Success Criteria** (what must be TRUE):
  1. A user logs in through `POST /session` as one of the four fixed accounts (one admin, three students), `GET /me` returns the principal, and the identity is available to downstream services (AUTH-01).
  2. A probe command sent by `courses-api` is handled by `courses-write`, and a probe query by `courses-read`, both through Axon Server; `courses-api` depends only on `courses-domain` (TOPO-02).
  3. Guard tests fail if `courses-write` gains a datasource or JPA, if any service other than `courses-write` gains an `EventAppender`, or if Flyway or an event processor appears outside `courses-read` (TOPO-03, PLAT-03).
  4. The same integration test passes through Testcontainers — locally and in CI from a clean checkout — without a pre-started Compose stack (PLAT-03, ADR-0003).
  5. The SPA is authenticated by a session cookie: the CORS mapping allows credentials, the client sends them on fetches and on `EventSource`, and a cross-origin request without the cookie is rejected with 401 (or 403 per PLAT-04) (AUTH-01, ADR-0011).
**Plans**: TBD
**UI hint**: yes

### Phase 3: Message and tag contract
**Goal**: The message types, identifiers and tag vocabulary every later phase compiles against are frozen, so a name or tag key cannot silently change under a later boundary.
**Mode**: mvp
**Depends on**: Phase 2
**Requirements**: ID-01, TOPO-05
**Success Criteria** (what must be TRUE):
  1. Events, commands and queries are Kotlin data classes in `courses-domain`, with no Spring or persistence dependency (TOPO-05).
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
  2. The spike uses message shapes from `courses-domain` and no ad-hoc event types.
  3. The spike proves the routing-key hazard: adding a `courseId` routing key serializes the same test and it stops producing a conflict.
  4. An emit issued from `@TransactionalEventListener(AFTER_COMMIT)` reaches a remote subscriber through Axon Server, and a rolled-back write emits nothing. The spike may use a throwaway projection and a throwaway query handler to get there; both are removed in Phase 5, which builds the real read side.
  5. A short note records the observed behavior of the commercial starter on Boot 4, including the Axon Server license window.
**Plans**: TBD

### Phase 5: Course creation across services
**Goal**: The first full distributed slice works in the browser — an admin creates a course, `courses-write` appends it, `courses-read` writes PostgreSQL and serves it, and the UI renders the catalog; repeating the create is idempotent.
**Mode**: mvp
**Depends on**: Phase 4
**Requirements**: AUTH-03, CRSE-01, CRSE-02, CRSE-03, IDEM-01, RDMD-01, RDMD-02
**Success Criteria** (what must be TRUE):
  1. An admin creates a course with a client-supplied id, title and capacity through the UI, and the catalog shows it (CRSE-01).
  2. Repeating the same create request returns the existing course, appends no second event, and leaves the catalog unchanged (IDEM-01).
  3. A student attempting the same call receives 403 (AUTH-03).
  4. Invalid creation (blank title, capacity < 1) is rejected with a named business error, and capacity cannot be changed afterwards (CRSE-03).
  5. The catalog renders seats remaining from the read model; restarting all services leaves it intact, `courses-read` is the only module with JPA and Flyway, and a missing or renamed column fails startup (CRSE-02, RDMD-01, RDMD-02).
**Plans**: TBD
**UI hint**: yes

### Phase 6: Enroll under capacity
**Goal**: A student enrolls while seats remain, and the decision is enforced by a boundary that reads only the events the seat invariant needs — rejecting at capacity and on duplicates, and distinguishing a business rejection from an append conflict.
**Mode**: mvp
**Depends on**: Phase 5
**Requirements**: AUTH-05, ENRL-01, ENRL-02, ENRL-03, ENRL-06, PLAT-04
**Success Criteria** (what must be TRUE):
  1. A student enrolls and a repeated catalog GET shows one fewer seat once `courses-read` has committed; the pushed update arrives in Phase 10 (ENRL-01).
  2. Enrolling twice is rejected with `already enrolled`; enrolling while already waitlisted is rejected with `already waitlisted`, exercised through a given `WaitlistJoined` event because the waitlist command arrives in Phase 7 (ENRL-02).
  3. Enrolling in a full course returns 422 `course is full`; an append conflict returns 409 with the conflicting tags; unknown course returns 404 (ENRL-03, PLAT-04).
  4. A `studentId` supplied in the request body or a query parameter is rejected with 403; the acting student comes from the session (AUTH-05).
  5. A test asserts the enroll `EventCriteria` equals exactly the frozen type set over `courseId` (ENRL-06).
  6. The enroll `@EventCriteriaBuilder` carries a comment block naming tag keys, the exact event types, and the handler consuming each type; the same block is required for the remaining builders, tracked by DOCS-02 in Phase 7.
**Plans**: TBD
**UI hint**: yes

### Phase 7: Waitlist and atomic drop/promotion
**Goal**: Joining a full course's waitlist is a separate explicit action with FIFO order, and dropping an enrollment promotes the first waitlisted student in the same append.
**Mode**: mvp
**Depends on**: Phase 6
**Requirements**: DOCS-02, ENRL-04, ENRL-05, ENRL-07, DROP-01, DROP-02
**Success Criteria** (what must be TRUE):
  1. A rejected student can join the waitlist explicitly, and the catalog's waitlist depth increases (ENRL-04).
  2. Two joins with identical payload timestamps still promote in append order (ENRL-05).
  3. Dropping a non-enrollment or another student's enrollment is rejected; dropping your own frees the seat and promotes the first waitlisted student in one append (DROP-01, DROP-02).
  4. No observable state ever has more active enrollments than capacity, including during promotion (ENRL-07).
  5. The drop/promotion `@EventCriteriaBuilder` carries the same comment block as the enroll builder (DOCS-02).
**Plans**: TBD
**UI hint**: yes

### Phase 8: Last-seat race proof
**Goal**: A reproducible test across services proves that N distinct students competing for one seat yield exactly one enrollment, with the boundary rejection proven, the business rejection tallied separately, and a negative control proving the test can fail. There is no demo endpoint: the test is the artifact, and its output is quotable.
**Mode**: mvp
**Depends on**: Phase 7
**Requirements**: RACE-01, RACE-02, RACE-03, RACE-04, E2E-02, DOCS-03
**Success Criteria** (what must be TRUE):
  1. A contention `*IT` class dispatches N ≥ 2 `EnrollStudent` commands through the production `CommandGateway` against a live Axon Server, from a shared barrier, against a course it creates and N distinct `studentId` values dispatched at the command level; `./gradlew :courses-api:integrationTest --tests '*LastSeatRaceIT'` prints `{enrolled, full, conflicted, maxInFlight}` (RACE-01).
  2. Exactly one caller is `enrolled`, and `enrolled + full + conflicted == N` (RACE-02).
  3. The forced-overlap variant asserts `AppendEventsTransactionRejectedException` with the conflicting `courseId`, and `maxInFlight > 1` proves the race was not serialized (RACE-03).
  4. The negative-control harness in `src/negativeControl/kotlin` runs through `negativeControlTest` and a manually triggered CI job, yields more than one winner, and the README quotes the command and output (RACE-04).
  5. Playwright runs the full user journey against the Compose stack — login, create course, enroll, drop — the job builds the four images first and publishes the report (E2E-02).
  6. Race and drop/promotion tests use sentence-form names and are listed in the README's evidence section (DOCS-03).
**Plans**: TBD
**UI hint**: no

### Phase 9: My enrollments and seeded scenarios
**Goal**: A signed-in student sees only their own enrollments and waitlist positions, and first boot seeds the three demo scenarios through real commands.
**Mode**: mvp
**Depends on**: Phase 8
**Requirements**: AUTH-02, VIEW-01, PLAT-02
**Success Criteria** (what must be TRUE):
  1. A student's view lists their enrollments with state and waitlist position (VIEW-01).
  2. Requesting another student's enrollments is rejected with 403, including when the id arrives in a body or query parameter (AUTH-02, AUTH-05).
  3. First boot seeds the three demo courses through commands, a restart does not duplicate them, and the E2E profile disables only those scenario courses while the fixed accounts stay available (PLAT-02).
**Plans**: TBD
**UI hint**: yes

### Phase 10: Live updates
**Goal**: The UI receives read-model snapshots and subsequent changes over SSE, emitted by `courses-read` after commit, with reconnect recovery and no polling.
**Mode**: mvp
**Depends on**: Phase 9
**Requirements**: PLAT-07, VIEW-02, VIEW-03, TOPO-06
**Success Criteria** (what must be TRUE):
  1. One subscription-query call yields the snapshot and then a pushed update emitted by `courses-read`; the UI renders it within 2 seconds of the server-side emit and performs no seat arithmetic (VIEW-02, VIEW-03).
  2. A dropped client reconnects with `Last-Event-ID` and recovers the final state (VIEW-03).
  3. The stream carries whole read-model snapshots; `courses-api` has no event-stream subscription and no domain event reaches the browser (TOPO-06).
  4. A rolled-back write produces no emitted update, and no client remains on a stale value after reconnect.
  5. The whole live path appends no events: a subscriber receives updates with no write to the event store (PLAT-07).
**Plans**: TBD
**UI hint**: yes

### Phase 11: Teaching artifacts
**Goal**: The lesson is complete and honest — the README traces one command across all three services, and the design note argues why the dynamic boundary beats fixed aggregates and why distribution does not weaken the guarantee.
**Mode**: mvp
**Depends on**: Phase 10
**Requirements**: DOCS-01, DOCS-04, DOCS-05
**Success Criteria** (what must be TRUE):
  1. The README walks one command across `courses-api`, `courses-write` and `courses-read` with expected output, including a rejected hop and the 409-vs-422 distinction (DOCS-01).
  2. The README links `docs/event-tag-table.md` and quotes the table per command (DOCS-05).
  3. The design note compares the dynamic boundary with fixed-aggregate alternatives, covers the mega-course aggregate and student-aggregate-plus-saga counterfactuals, and explains the single-retry idiom and why distribution does not weaken the append condition (DOCS-04).
**Plans**: TBD

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Build and infrastructure skeleton | 6/6 | Complete | 2026-11-11 |
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

## Backlog

### Phase 999.1: Measure code coverage (BACKLOG)

**Goal**: The build reports how much of the production code the tests actually exercise, and a
committed baseline stops it from sliding once real domain logic exists.
**Requirements**: TBD
**Plans**: 0 plans

Captured context:

- Phase 1 has nothing meaningful to measure — empty Boot applications, configuration and
  registration tests — so a threshold there would be a formality.
- Step 1: add JaCoCo to `kotlin-conventions` in reporting mode only
  (`jacocoTestReport`, no `jacocoTestCoverageVerification`), so the number is visible without
  blocking.
- Step 2: once the decision slices exist (end of Phase 7 — `courses-domain`, `courses-write` and the
  read side carry real logic), record a baseline and turn on a ratchet: coverage may not fall below
  the committed value.
- Integration suites contribute: the report must merge `test` and `integrationTest` execution data,
  not just unit tests.
- Exclude generated, Spring/Axon wiring and `@Configuration`-only classes, otherwise the number
  measures framework glue.
- Frontend is separate: Vitest + v8 coverage when the SPA gains logic; Playwright E2E is not
  counted as unit coverage.
- Done means: CI's integration step merges `test` + `integrationTest` into one JaCoCo report (`build`
  itself stays Docker-free), the
  baseline file is committed, and CI fails when coverage drops below it.

Plans:
- [ ] TBD (promote with /gsd-review-backlog when ready)

### Phase 999.2: Scan the container images (BACKLOG)

**Goal**: A published image is scanned for vulnerable OS and JVM packages, so a stale run image is
visible before it reaches a registry.
**Requirements**: TBD
**Plans**: 0 plans

Captured context:

- Not actionable yet: images are built locally, never pushed, so there is no artifact to scan and no
  registry to scan it in.
- Precondition: CI must build and publish the images first — a decision that amends ADR-0005, taken
  when the trigger fires, not before.
- Trigger: the first time an image is published — a registry push, a release, or a hosted demo.
- Then: Trivy or Grype over the four images in the pipeline, failing on `HIGH`/`CRITICAL` with a
  suppression file that carries a reason and an expiry per entry, the same pattern as an audit
  baseline.
- Related: ADR-0005 records that OS patches arrive from the Paketo run image, so the scanner reports
  on the image that was built, not on the buildpack version in the catalog.
- Dependabot alerts (ADR-0010) cover dependency CVEs, not the base OS inside the run image.

Plans:
- [ ] TBD (promote with /gsd-review-backlog when ready)

### Phase 999.3: Harden the actuator endpoints (BACKLOG)

**Goal**: Actuator endpoints are reachable only by the consumers that need them, with an explicit
posture rather than a permissive default.
**Requirements**: TBD
**Plans**: 0 plans

Captured context:

- Three axes, decided together: authorization (Spring Security `EndpointRequest` for a management
  chain), a separate management port or address (`management.server.port`, `management.server.address`)
  that is not published, and which endpoints are exposed at all — today `health,info` through
  `management.endpoints.web.exposure.include`.
- The services differ: `courses-api` publishes `:8080`, `courses-write` and `courses-read` stay on the
  Compose network. Any difference in policy has to be deliberate and documented.
- `show-details` and `show-components` follow from the transport decision, not the other way around:
  component detail is safe only where the endpoint is not publicly reachable.
- Consumers to account for before moving the port or adding auth: the Compose healthchecks
  (`/workspace/health-check` with `THC_PATH`), `scripts/smoke-stack.sh`, and any future readiness probe.
- Constraint: standard Boot and Security mechanisms only; no hand-written servlet filters.
- Trigger: Phase 2 introduces authentication, so the actuator posture is decided inside Phase 2's
  discussion rather than after it. Reviewed on 2026-11-12 and kept in the backlog for that reason:
  promoting it as a phase would place it after Phase 11, which is later than its own trigger asks for.
- Related: `docs/tooling.md` explains why registration is proven by a test rather than by
  `/actuator/health`; this item decides what health may be used for.

Plans:
- [ ] TBD (promote with /gsd-review-backlog when ready)

### Phase 999.4: Generate upstream evidence tables from commands (BACKLOG)

**Goal**: A claim table in `docs/upstream-reports/` is produced and checked by a script, so a citation
that does not resolve, or a table row that does not render, fails before review rather than during it.
**Requirements**: TBD
**Plans**: 0 plans

Captured context:

- Observed over eight review passes on 0001 and three on 0002: the recurring defect was never the
  finding, it was the citation. Wrong line, wrong range, an unescaped `\|\|` that broke a Markdown
  table, a claim tightened in the table while the public fence kept the older sentence.
- Three of the last five findings were that last class alone — a divergence between what the table says
  and what the draft publishes.
- A generator would extract every `file:line` from a report, print the cited line next to the claim,
  validate that each table row parses, and fail on a citation that no longer resolves. It cannot judge
  whether the line supports the claim; that stays with the reviewer.
- Scope: a script behind the existing gate, invoked from the task that writes the draft — not a new
  GSD command, and not a replacement for the independent review pass.
- Trigger: the next upstream report, or the third citation defect found in review, whichever comes
  first. 999.4 exists because the second already happened twice.
- Related: `docs/upstream-reports/README.md` defines the gate; `.planning/codebase/CONVENTIONS.md`
  § Upstream reports defines what may be published.

Plans:
- [ ] TBD (promote with /gsd-review-backlog when ready)
