# Requirements: Axon 5 DCB Showcase

**Defined:** 2026-11-10
**Core Value:** A command decides using exactly the events its invariant requires — proven by a last-seat race where two concurrent enrollments hit the cap and exactly one succeeds.

## v1 Requirements

Requirements for the initial release. Each maps to exactly one roadmap phase.

### Access

- [ ] **AUTH-01**: User can log in as one of the seeded accounts (student or admin)
- [ ] **AUTH-02**: Signed-in student sees only their own enrollments

### Courses

- [ ] **CRSE-01**: Admin can create a course with a title and a seat capacity
- [ ] **CRSE-02**: User can view a course catalog showing seats remaining per course
- [ ] **CRSE-03**: User can see the waitlist depth for each course

### Enrollment

- [ ] **ENRL-01**: Student can enroll in a course that has seats remaining
- [ ] **ENRL-02**: A student cannot enroll twice in the same course
- [ ] **ENRL-03**: Enrolling in a course with no seats remaining is rejected with a "Course is full" business error
- [ ] **ENRL-04**: Student can join the waitlist of a full course
- [ ] **ENRL-05**: Waitlist position is first-in, first-out by join order
- [ ] **ENRL-06**: The enroll decision reads only course and student events relevant to the seat invariant

### Drop and Promotion

- [ ] **DROP-01**: Student can drop their enrollment, freeing a seat
- [ ] **DROP-02**: Dropping a seat promotes the first waitlisted student in the same atomic append
- [ ] **DROP-03**: Promotion order follows event-store sequence, not payload timestamps

### Race Proof

- [ ] **RACE-01**: A demo endpoint releases N concurrent enrollments for the last seat
- [ ] **RACE-02**: Exactly one of the concurrent enrollments succeeds
- [ ] **RACE-03**: The losing append is rejected by the consistency boundary (`AppendEventsTransactionRejectedException`), not only by a business rule
- [ ] **RACE-04**: A negative control with deliberately broken criteria makes the race test fail

### Views

- [ ] **VIEW-01**: Student can view their own enrollments and waitlist positions
- [ ] **VIEW-02**: Views reflect a command's effect immediately after the REST call returns

### Teaching Artifacts

- [ ] **DOCS-01**: README walks one command end-to-end with expected output, including a rejected hop
- [ ] **DOCS-02**: Code annotations mark the exact line where the consistency boundary is chosen and why each event type is included
- [ ] **DOCS-03**: Integration tests read as a narrated proof of the invariant holding under contention
- [ ] **DOCS-04**: Design note compares the dynamic boundary with fixed-aggregate alternatives and their failure modes
- [ ] **DOCS-05**: README contains an event × tag table per command

### Platform

- [ ] **PLAT-01**: The whole app starts with one command and zero external services
- [ ] **PLAT-02**: Scenario data (open course, full course, full course with waitlist) is created through real commands at startup
- [ ] **PLAT-03**: A guard test asserts the active event store is the in-memory DCB store and no JPA store is present
- [ ] **PLAT-04**: API distinguishes a conflict (409) from a business rejection (422)

## v2 Requirements

Deferred to a future release. Tracked but not in the current roadmap.

### Courses

- **CRSE-04**: Admin can change a course's seat capacity

### Waitlist

- **ENRL-07**: Waitlisted student receives a time-limited offer before promotion
- **ENRL-08**: Student can leave a waitlist voluntarily

### Platform

- **PLAT-05**: Event store persists across restarts
- **PLAT-06**: Event schema versioning and upcasting

### Access

- **AUTH-03**: Real authentication with registration and password management

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Axon Server / Docker | Embedded in-memory store keeps startup to one JVM; the no-Docker constraint is a project constraint |
| JPA or relational event store | `AggregateBasedJpaEventStorageEngine` has no DCB support and silently voids the showcase's premise |
| Automation/saga slice for waitlist promotion | Breaks the atomicity the project exists to demonstrate; covered only as a counterfactual in the design note |
| Event-trace / introspection UI | Lesson is taught in README, annotations, tests, and the design note; avoids building observability before understanding |
| Notifications (email, in-app) | Not needed to prove the boundary |
| Multi-node / distributed deployment | The DCB guarantee demonstrated is single-JVM; distribution would change the claim |
| Mobile or responsive polish | Desktop browser demo is sufficient |

## Traceability

Which phases cover which requirements. Populated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| (pending) | — | — |

**Coverage:**
- v1 requirements: 29 total
- Mapped to phases: 0
- Unmapped: 29 ⚠️

---
*Requirements defined: 2026-11-10*
*Last updated: 2026-11-10 after initial definition*
