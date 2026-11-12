# Requirements: Axon 5 DCB Courses

**Defined:** 2026-11-10
**Core Value:** A command decides using exactly the events its invariant requires — proven by a last-seat race where N concurrent enrollments hit the cap and exactly one succeeds.

## v1 Requirements

Each requirement maps to exactly one roadmap phase.

### Access

- [ ] **AUTH-01**: User can log in through `POST /session` as one of four fixed accounts — one admin and three students with committed UUIDv7 ids and README credentials, always seeded in every profile — and `GET /me` returns the current principal
- [ ] **AUTH-02**: A signed-in student sees only their own enrollments and waitlist positions; a request for another student's data is rejected
- [ ] **AUTH-03**: A student cannot create a course; `POST /courses` returns 403
- [ ] **AUTH-05**: The acting `studentId` of every student request is derived from the authenticated principal; a `studentId` supplied in a body or query parameter is rejected with 403

### Courses

- [ ] **CRSE-01**: Admin can create a course with a client-supplied id, title and seat capacity
- [ ] **CRSE-02**: User sees the catalog with seats remaining per course, served from the read model
- [ ] **CRSE-03**: Creating a course with a blank title or capacity below 1 is rejected with a named business error; capacity is set once in `CourseCreated` and appears in no other v1 command

### Idempotency

- [ ] **IDEM-01**: Repeating `CreateCourse` with the same course id returns the existing course and appends no second event

### Enrollment

- [ ] **ENRL-01**: Student can enroll in a course that has seats remaining
- [ ] **ENRL-02**: A student cannot enroll twice, and cannot enroll while already waitlisted for that course — each is a named business rejection (`already enrolled`, `already waitlisted`)
- [ ] **ENRL-03**: Enrolling in a course with no seats remaining is rejected with `course is full`, not a server error
- [ ] **ENRL-04**: A student can join the waitlist of a full course through an explicit action (`POST /courses/{courseId}/waitlist`); joining a course that still has seats is rejected with `course has seats available`, a repeat join with `already waitlisted`, and the catalog shows the updated waitlist depth
- [ ] **ENRL-05**: Waitlist promotion follows append order (oldest `WaitlistJoined` first), including when two joins carry identical payload timestamps; order is never derived from a mutable queue column, and a guard test asserts the read schema carries no position column
- [ ] **ENRL-06**: The enroll decision's `EventCriteria` selects exactly `CourseCreated`, `StudentEnrolled`, `StudentDropped`, `WaitlistJoined`, `WaitlistPromoted` over the `courseId` tag; a test asserts the rendered criteria equals this set
- [ ] **ENRL-07**: Replaying each prefix of a course's event journal yields active enrollments at or below capacity, the projection refuses any write that would exceed it, and a concurrent drop-and-promote test asserts the maximum over every sampled snapshot

### Drop and Promotion

- [ ] **DROP-01**: A student can drop only their own active enrollment; a command whose `studentId` holds no active enrollment is rejected with `not enrolled`, and an HTTP request that tries to name another student is rejected with 403 by AUTH-05
- [ ] **DROP-02**: Dropping a seat promotes the first waitlisted student in the same atomic append

### Race Proof

- [ ] **RACE-01**: An integration test dispatches N ≥ 2 `EnrollStudent` commands for one seat through the production `CommandGateway` against a live Axon Server, starting them from a shared barrier; it creates its own course and dispatches N distinct `studentId` values at the command level, records `{enrolled, full, conflicted, maxInFlight}` — the last being the peak of issued-but-unreturned sends, sampled from before the barrier releases until the final future completes — and prints the tally so the README can quote it; it is an `*IT` class in the `integrationTest` suite, run by class filter
- [ ] **RACE-02**: For a course with exactly one free seat and N distinct students, exactly one caller is `enrolled` and the tally is complete: `enrolled + full + conflicted == N`
- [ ] **RACE-03**: In a forced-overlap variant, the losing append fails with `AppendEventsTransactionRejectedException` (or the conflict-diagnosed equivalent), the rejection names the conflicting `courseId` tag, the phase note records which diagnosis mechanism was used, and the HTTP mapping to 409 is covered by PLAT-04 rather than by this harness
- [ ] **RACE-04**: A negative-control harness with deliberately over-wide criteria demonstrates more than one winner; it lives in its own source set (`src/negativeControl/kotlin`, suffix `*NC`), runs through its own `negativeControlTest` task and a manually triggered CI job, never inside `integrationTest`, and the README quotes the command and its output

### Views

- [ ] **VIEW-01**: A signed-in student sees their own enrollments with state (enrolled or waitlisted) and waitlist position
- [ ] **VIEW-02**: The UI receives read-model changes over a live stream and renders them within 2 seconds of the server-side emit, without a manual reload; it performs no client-side seat arithmetic
- [ ] **VIEW-03**: `courses-api` returns an SSE stream as a `Flux`: one subscription-query call yields the snapshot and later updates emitted by `courses-read`, each event carries an `id`, and a reconnect with `Last-Event-ID` recovers the final state

### Read Model

- [ ] **RDMD-01**: Read models are persisted in PostgreSQL by `courses-read` and survive a restart of all services
- [ ] **RDMD-02**: Query handlers read through Spring Data JDBC, never write, and a startup smoke check fails naming the missing or renamed column when the schema differs from the Flyway migrations

### Frontend

- [x] **FE-01**: The SPA follows Feature-Sliced Design — layers `app → pages → widgets → features → entities → shared`, one-way imports, and a public `index.ts` per slice
- [x] **FE-02**: `npm run lint:fsd` (steiger) passes in CI and fails the build on a layer violation or a deep import

### Identifiers

- [ ] **ID-01**: Course and student identifiers are UUIDv7, exposed as canonical strings, and wrapped in typed value classes at every boundary

### Frontend Verification

- [x] **E2E-01**: The Playwright MCP server is configured in the tracked project config and can drive the running SPA headless
- [ ] **E2E-02**: Playwright end-to-end tests cover login, course creation, enroll and drop against the full Compose stack, and run in CI; the e2e job builds the four images with `bootBuildImage` and `dockerBuildImage` before `docker compose up -d --wait`, the one pipeline exception recorded in ADR-0005

### Quality

- [x] **QUAL-01**: Spotless (`spotlessCheck`), frontend Prettier (`format:check`) and repository Prettier (`scripts/format-check.sh`) run in CI; `spotlessApply` and the matching `format` scripts are the documented normalizers

### Topology

- [x] **TOPO-01**: Each of the three services is an independent Spring Boot application with its own bootJar
- [ ] **TOPO-02**: `courses-api` depends only on `courses-domain` and sends commands and queries as messages; it has no compile dependency on `courses-write` or `courses-read`
- [ ] **TOPO-03**: `courses-read` is the only module with a datasource, JPA, Flyway and an event processor; `courses-write` is the only module with an `EventAppender` and has no datasource; guard tests fail when either ownership moves, and no module other than `courses-read` declares a datasource
- [x] **TOPO-04**: `./gradlew build` produces the SPA bundle and all three bootJars in one build graph, with no separate npm step
- [ ] **TOPO-05**: Message types — including `WatchCourseViewQuery` — and tag keys are declared only in `courses-domain`, and a test freezes the set
- [ ] **TOPO-06**: Live updates are whole read-model snapshots emitted by `courses-read`, which also advertises the watch query; `courses-api` never subscribes to events and no domain event reaches the browser

### Teaching Artifacts

- [ ] **DOCS-01**: README walks one command across gateway, `courses-write` and `courses-read` with expected output, including a rejected hop and the 409-vs-422 distinction
- [ ] **DOCS-02**: Every `@EventCriteriaBuilder` carries a comment block naming its tag keys, the exact event types passed to the criteria, and the handler consuming each type
- [ ] **DOCS-03**: Race and drop/promotion tests use sentence-form scenario names and are listed in the README's evidence section
- [ ] **DOCS-04**: Design note compares the dynamic boundary with fixed-aggregate alternatives and explains why distribution does not weaken the append condition
- [ ] **DOCS-05**: README links `docs/event-tag-table.md` and quotes the table per command

### Platform

- [x] **PLAT-01**: `docker compose up -d --wait` starts Axon Server, PostgreSQL, the three services and the frontend; the development loop runs the infrastructure in Compose and the services from Gradle
- [ ] **PLAT-02**: Demo scenario data — one open course, one full course and one full course with a waitlisted student — is created through real commands at first boot and seeding is idempotent across restarts; the three scenario courses are disabled in the E2E profile, while the fixed accounts of AUTH-01 remain available in every profile
- [ ] **PLAT-03**: `courses-write` starts without a datasource, and a guard test fails if one appears
- [ ] **PLAT-04**: The API maps business rejections to 422 with the canonical message from the table below, append conflicts to 409 with the conflicting tag set, unknown resources to 404, and unauthenticated/unauthorized to 401/403
- [ ] **PLAT-07**: Neither the read path nor the live-update path appends events: a subscriber receives read-model changes with no write to the event store, and a rolled-back write emits nothing
- [x] **PLAT-05**: CI builds the whole project and runs unit and distributed integration tests on every PR to `main`, starting Axon Server and PostgreSQL

## v2 Requirements

Deferred to a future release. Tracked but not in the current roadmap.

### Courses

- **CRSE-04**: Admin can change a course's seat capacity

### Waitlist

- **ENRL-08**: Waitlisted student receives a time-limited offer before promotion
- **ENRL-09**: Student can leave a waitlist voluntarily
- **ENRL-10**: Per-student seat limit across courses (the `either(courseId, studentId)` widening)

### Read Model

- **RDMD-03**: Read models can be rebuilt from scratch by replaying the event stream

### Performance

- **PERF-01**: Read-side caching (Caffeine on query handlers or HTTP `ETag`) is introduced only if measurement justifies it, and evicts in the same `AFTER_COMMIT` hook that emits the SSE update

### Platform

- **PLAT-06**: Event schema versioning and transformation
- **AUTH-04**: Real authentication with registration and password management

## Out of Scope

| Feature | Reason |
|---------|--------|
| JPA event store in `courses-write` | `AggregateBasedJpaEventStorageEngine` has no DCB support and would void the premise |
| Kubernetes, autoscaling, HA, production deployment | The distributed topology runs locally via Compose; operational hardening teaches nothing about DCB |
| Production licensing setup | Local evaluation behavior is enough; Axon Server's 12-hour unlicensed window is documented, not solved |
| A second read-model consumer beyond `courses-read` | One writer of the read schema keeps the consistency story legible |
| Automation/saga slice for waitlist promotion | Breaks the atomicity the project demonstrates; appears only as a counterfactual |
| Event-trace / introspection UI | The lesson lives in README, annotations, tests, and the design note |
| Notifications, mobile, responsive polish | Not needed to prove the boundary |

## Canonical rejections

One message per rule, used verbatim by the API, the tests, the README and the E2E suite (`PLAT-04`):

| Rule | Message | HTTP |
|------|---------|------|
| CRSE-03 | `invalid course` | 422 |
| ENRL-02 duplicate enroll | `already enrolled` | 422 |
| ENRL-02/ENRL-04 while waitlisted | `already waitlisted` | 422 |
| ENRL-03 | `course is full` | 422 |
| ENRL-04 on a course with seats | `course has seats available` | 422 |
| DROP-01 | `not enrolled` | 422 |
| AUTH-02, AUTH-03, AUTH-05 | `forbidden` | 403 |

## Canonical endpoints

The gateway contract, stated once so the phase plans and the UI cannot drift. Paths are relative to
the `courses-api` origin, which the SPA reaches through `COURSES_API_BASE_URL`.

| Method and path | Purpose | Phase |
|-----------------|---------|-------|
| `POST /session` | log in as a fixed account | 2 |
| `DELETE /session` | log out | 2 |
| `GET /me` | current principal | 2 |
| `GET /courses` | catalog | 5 |
| `GET /courses/{courseId}` | one course | 5 |
| `POST /courses` | create a course (admin) | 5 |
| `POST /courses/{courseId}/enrollments` | enroll | 6 |
| `DELETE /courses/{courseId}/enrollments` | drop | 7 |
| `POST /courses/{courseId}/waitlist` | join the waitlist | 7 |
| `GET /me/enrollments` | own enrollments | 9 |
| `GET /courses/{courseId}/view` | SSE stream of read-model snapshots | 10 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | 2 | Pending |
| AUTH-02 | 9 | Pending |
| AUTH-03 | 5 | Pending |
| AUTH-05 | 6 | Pending |
| CRSE-01 | 5 | Pending |
| CRSE-02 | 5 | Pending |
| CRSE-03 | 5 | Pending |
| ENRL-01 | 6 | Pending |
| ENRL-02 | 6 | Pending |
| ENRL-03 | 6 | Pending |
| ENRL-04 | 7 | Pending |
| ENRL-05 | 7 | Pending |
| ENRL-06 | 6 | Pending |
| ENRL-07 | 7 | Pending |
| DROP-01 | 7 | Pending |
| DROP-02 | 7 | Pending |
| RACE-01 | 8 | Pending |
| RACE-02 | 8 | Pending |
| RACE-03 | 8 | Pending |
| RACE-04 | 8 | Pending |
| VIEW-01 | 9 | Pending |
| VIEW-02 | 10 | Pending |
| VIEW-03 | 10 | Pending |
| RDMD-01 | 5 | Pending |
| RDMD-02 | 5 | Pending |
| ID-01 | 3 | Pending |
| IDEM-01 | 5 | Pending |
| FE-01 | 1 | Complete |
| FE-02 | 1 | Complete |
| TOPO-01 | 1 | Complete |
| TOPO-02 | 2 | Pending |
| TOPO-03 | 2 | Pending |
| TOPO-04 | 1 | Complete |
| TOPO-05 | 3 | Pending |
| TOPO-06 | 10 | Pending |
| E2E-01 | 1 | Complete |
| E2E-02 | 8 | Pending |
| QUAL-01 | 1 | Complete |
| PLAT-01 | 1 | Complete |
| PLAT-02 | 9 | Pending |
| PLAT-03 | 2 | Pending |
| PLAT-04 | 6 | Pending |
| PLAT-05 | 1 | Complete |
| PLAT-07 | 10 | Pending |
| DOCS-01 | 11 | Pending |
| DOCS-02 | 7 | Pending |
| DOCS-03 | 8 | Pending |
| DOCS-04 | 11 | Pending |
| DOCS-05 | 11 | Pending |

**Coverage:** 49 v1 requirements · 49 mapped · 0 unmapped

---
*Requirements defined: 2026-11-10*
*Last updated: 2026-11-12 after the second planning-review cycle*
