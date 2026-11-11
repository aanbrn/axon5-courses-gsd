# Requirements: Axon 5 DCB Showcase

**Defined:** 2026-11-10
**Core Value:** A command decides using exactly the events its invariant requires — proven by a last-seat race where N concurrent enrollments hit the cap and exactly one succeeds.

## v1 Requirements

Each requirement maps to exactly one roadmap phase.

### Access

- [ ] **AUTH-01**: User can log in as one of the seeded accounts (student or admin)
- [ ] **AUTH-02**: A signed-in student sees only their own enrollments and waitlist positions; a request for another student's data is rejected
- [ ] **AUTH-03**: A student cannot create a course; `POST /courses` returns 403

### Courses

- [ ] **CRSE-01**: Admin can create a course with a client-supplied id, title and seat capacity
- [ ] **CRSE-02**: User sees the catalog with seats remaining per course, served from the read model
- [ ] **CRSE-03**: Creating a course with a blank title or capacity below 1 is rejected with a named business error; capacity is set once in `CourseCreated` and appears in no other v1 command

### Idempotency

- [ ] **IDEM-01**: Repeating `CreateCourse` with the same course id returns the existing course and appends no second event

### Enrollment

- [ ] **ENRL-01**: Student can enroll in a course that has seats remaining
- [ ] **ENRL-02**: A student cannot enroll twice, and cannot enroll while already waitlisted for that course — each is a named business rejection
- [ ] **ENRL-03**: Enrolling in a course with no seats remaining is rejected with `Course is full`, not a server error
- [ ] **ENRL-04**: A student can join the waitlist of a full course through an explicit action, and the catalog shows the updated waitlist depth
- [ ] **ENRL-05**: Waitlist promotion follows join order (oldest join first), including when two joins carry identical payload timestamps; order is never derived from a mutable queue column
- [ ] **ENRL-06**: The enroll decision's `EventCriteria` selects exactly `CourseCreated`, `StudentEnrolled`, `StudentDropped`, `WaitlistJoined`, `WaitlistPromoted` over the `courseId` tag; a test asserts the rendered criteria equals this set
- [ ] **ENRL-07**: At no observable point does the number of active enrollments for a course exceed its capacity, including during drop-and-promote

### Drop and Promotion

- [ ] **DROP-01**: A student can drop only their own active enrollment; dropping a non-enrollment or another student's enrollment is a named business rejection
- [ ] **DROP-02**: Dropping a seat promotes the first waitlisted student in the same atomic append

### Race Proof

- [ ] **RACE-01**: `POST /demo/race` accepts a course id and a count N ≥ 2 of distinct students who are neither enrolled nor waitlisted, starts them behind a server-side barrier, and returns `{enrolled, full, conflicted, maxInFlight}`
- [ ] **RACE-02**: For a course with exactly one free seat and N distinct students, exactly one caller is `enrolled`
- [ ] **RACE-03**: In a forced-overlap variant, the losing append fails with `AppendEventsTransactionRejectedException` (or the conflict-diagnosed equivalent), the 409 body names the conflicting `courseId`, and the phase note records which diagnosis mechanism was used
- [ ] **RACE-04**: A tagged negative-control harness with deliberately over-wide criteria demonstrates more than one winner; it runs via a separate Gradle task and a manually triggered CI job, and the README quotes the command and its output
- [ ] **RACE-05**: Each run creates its own one-seat course through `CreateCourse`, and demo seeding is disabled in the test profile, so repeated runs do not degrade into a no-op

### Views

- [ ] **VIEW-01**: A signed-in student sees their own enrollments with state (enrolled or waitlisted) and waitlist position
- [ ] **VIEW-02**: The UI receives read-model changes over a live stream and renders them within 2 seconds of the commit, without a manual reload; it performs no client-side seat arithmetic
- [ ] **VIEW-03**: `api` returns an SSE stream as a `Flux`: one subscription-query call yields the snapshot and later updates emitted by `read`, each event carries an `id`, and a reconnect with `Last-Event-ID` recovers the final state

### Read Model

- [ ] **RDMD-01**: Read models are persisted in PostgreSQL by `read` and survive a restart of all services
- [ ] **RDMD-02**: Query handlers read through Spring Data JDBC, never write, and a startup smoke check fails naming the missing or renamed column when the schema differs from the Flyway migrations

### Frontend

- [ ] **FE-01**: The SPA follows Feature-Sliced Design — layers `app → pages → widgets → features → entities → shared`, one-way imports, and a public `index.ts` per slice
- [ ] **FE-02**: `npm run lint:fsd` (steiger) passes in CI and fails the build on a layer violation or a deep import

### Identifiers

- [ ] **ID-01**: Course and student identifiers are UUIDv7, exposed as canonical strings, and wrapped in typed value classes at every boundary

### Frontend Verification

- [ ] **E2E-01**: The Playwright MCP server is configured in the tracked project config and can drive the running SPA headless
- [ ] **E2E-02**: Playwright end-to-end tests cover login, course creation, enroll, drop and the last-seat race against the full Compose stack, and run in CI

### Quality

- [x] **QUAL-01**: Spotless (`spotlessCheck`), frontend Prettier (`format:check`) and repository Prettier (`scripts/format-check.sh`) run in CI; `spotlessApply` and the matching `format` scripts are the documented normalizers

### Topology

- [x] **TOPO-01**: Each of the three services is an independent Spring Boot application with its own bootJar
- [ ] **TOPO-02**: `api` depends only on `domain` and sends commands and queries as messages; it has no compile dependency on `write` or `read`
- [ ] **TOPO-03**: `read` is the only module with a datasource, JPA, Flyway and an event processor; `write` is the only module with an `EventAppender` and has no datasource; guard tests fail when either ownership moves, and no module other than `read` declares a datasource
- [ ] **TOPO-04**: `./gradlew build` produces the SPA bundle and all three bootJars in one build graph, with no separate npm step
- [ ] **TOPO-05**: Message types — including `WatchCourseViewQuery` — and tag keys are declared only in `domain`, and a test freezes the set
- [ ] **TOPO-06**: Live updates are whole read-model snapshots emitted by `read`, which also advertises the watch query; `api` never subscribes to events and no domain event reaches the browser

### Teaching Artifacts

- [ ] **DOCS-01**: README walks one command across gateway, `write` and `read` with expected output, including a rejected hop and the 409-vs-422 distinction
- [ ] **DOCS-02**: Every `@EventCriteriaBuilder` carries a comment block naming its tag keys, the exact event types passed to the criteria, and the handler consuming each type
- [ ] **DOCS-03**: Race and drop/promotion tests use sentence-form scenario names and are listed in the README's evidence section
- [ ] **DOCS-04**: Design note compares the dynamic boundary with fixed-aggregate alternatives and explains why distribution does not weaken the append condition
- [ ] **DOCS-05**: README links `docs/event-tag-table.md` and quotes the table per command

### Platform

- [x] **PLAT-01**: `docker compose up -d` plus one Gradle run starts Axon Server, PostgreSQL and all three services
- [ ] **PLAT-02**: Scenario data (open course, full course, full course with waitlist) is created through real commands, and seeding is idempotent across restarts
- [ ] **PLAT-03**: A full read cycle and the live-update path append no events, and `write` starts without a datasource
- [ ] **PLAT-04**: The API maps business rejections to 422 with the rule's canonical message, append conflicts to 409 with the conflicting tag set, unknown resources to 404, and unauthenticated/unauthorized to 401/403
- [ ] **PLAT-05**: CI builds the whole project and runs unit and distributed integration tests on every PR to `main`, starting Axon Server and PostgreSQL

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
| JPA event store in `write` | `AggregateBasedJpaEventStorageEngine` has no DCB support and would void the premise |
| Kubernetes, autoscaling, HA, production deployment | The distributed topology runs locally via Compose; operational hardening teaches nothing about DCB |
| Production licensing setup | Local evaluation behavior is enough; Axon Server's 12-hour unlicensed window is documented, not solved |
| A second read-model consumer beyond `read` | One writer of the read schema keeps the consistency story legible |
| Automation/saga slice for waitlist promotion | Breaks the atomicity the project demonstrates; appears only as a counterfactual |
| Event-trace / introspection UI | The lesson lives in README, annotations, tests, and the design note |
| Notifications, mobile, responsive polish | Not needed to prove the boundary |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | 2 | Pending |
| AUTH-02 | 9 | Pending |
| AUTH-03 | 5 | Pending |
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
| RACE-05 | 8 | Pending |
| VIEW-01 | 9 | Pending |
| VIEW-02 | 10 | Pending |
| VIEW-03 | 10 | Pending |
| RDMD-01 | 5 | Pending |
| RDMD-02 | 5 | Pending |
| ID-01 | 3 | Pending |
| IDEM-01 | 5 | Pending |
| FE-01 | 1 | Pending |
| FE-02 | 1 | Pending |
| TOPO-01 | 1 | Complete |
| TOPO-02 | 2 | Pending |
| TOPO-03 | 2 | Pending |
| TOPO-04 | 1 | Pending |
| TOPO-05 | 3 | Pending |
| TOPO-06 | 10 | Pending |
| E2E-01 | 1 | Pending |
| E2E-02 | 8 | Pending |
| QUAL-01 | 1 | Complete |
| PLAT-01 | 1 | Complete |
| PLAT-02 | 9 | Pending |
| PLAT-03 | 2 | Pending |
| PLAT-04 | 6 | Pending |
| PLAT-05 | 1 | Pending |
| DOCS-01 | 11 | Pending |
| DOCS-02 | 6 | Pending |
| DOCS-03 | 8 | Pending |
| DOCS-04 | 11 | Pending |
| DOCS-05 | 11 | Pending |

**Coverage:** 48 v1 requirements · 48 mapped · 0 unmapped

---
*Requirements defined: 2026-11-10*
*Last updated: 2026-11-10 after independent requirements/roadmap, consistency, and technical reviews*
