# Feature Research

**Domain:** Axon Framework 5 Dynamic Consistency Boundary (DCB) demo — course enrollment with seat capacity and FIFO waitlist
**Researched:** 2026-11-10
**Confidence:** MEDIUM (Axon 5 DCB mechanics verified against official reference + framework source; feature-landscape and demo-practice findings are cross-referenced from reference demos, not from a single vendor spec)

**Framing note:** this is not a product. The user of this "feature set" is a reader (starting with the author) trying to *understand a boundary*. So the usual table-stakes test ("would users leave?") becomes "**would the reader conclude the demo is incomplete, or worse, unconvinced?**" That reframing drives every category below.

---

## Feature Landscape

### Table Stakes (A reader assumes these exist; missing = the demo looks unfinished or fake)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Seeded login (student + admin, no password management) | A multi-actor domain needs distinct capabilities; a demo that starts with "edit the code to change who you are" reads as a prototype | LOW | One admin and three student accounts, session cookie, no roles matrix. Seed credentials printed in the README. Must not become an auth project (see Anti-Features). |
| Create a course with title + seat capacity | This is the source of the invariant under study; without it there is nothing to be at capacity | LOW | Static creational command handler, `CourseCreated` tagged `courseId`. Capacity fixed at creation for v1. |
| Course catalog showing seats remaining and waitlist depth | Any capacity demo that hides the counter makes the boundary invisible; readers need to see headroom go 2 → 1 → 0 | LOW / MEDIUM | Read model projected from enrollment events. Must be a *projection*, not a re-source of the event store per request — otherwise the read side accidentally teaches the wrong thing. |
| Enroll a student in a course with seats left | Core use case | MEDIUM | The star slice. Decision model is scoped to `courseId` by `@EventCriteriaBuilder`; `studentId` is checked from the event payload. |
| Reject enrollment when the course is at capacity, with a specific reason | A generic 500 or a bare boolean makes the demo untrustworthy | LOW | Business rejection (`course is full`) must be distinguishable from a technical conflict (see Differentiators). |
| Reject duplicate enrollment | A student enrolled twice silently doubles the seat count — the invariant would be provably broken in the UI | LOW | Cheap, and it is the rule that *justifies* the student tag on the enrollment event. Don't skip it. |
| Explicit join waitlist when full | Capacity rejection must stay a clean business rejection; auto-joining on the same command would make the race outcome timing-dependent | LOW | A separate `JoinCourseWaitlist` command. Enroll fails with `course is full`; the client then offers "Join waitlist". |
| Drop an enrollment, freeing the seat | Without drop there is no promotion and no second boundary story | LOW | Emits `StudentDropped` tagged `courseId` + `studentId`. |
| FIFO promotion of the next waitlisted student on drop | The requirement; also the invariant that proves the boundary must be *wider* than one student | MEDIUM | Ordering comes from global event order (or an explicit sequence), not from a mutable queue table. Auto-promotion, not offer-with-expiry (see Anti-Features). |
| "My enrollments" for the signed-in student | Basic orientation; without it the student cannot tell what to drop | LOW | Second read model or a filtered projection. |
| One documented startup command, seeded scenario data | A demo that needs a dozen manual steps has already lost the reader | LOW / MEDIUM | `docker compose up -d --wait` after building the images (ADR-0006); the Gradle `bootRun --parallel` pair is the dev loop, not the demo. Seed via *real commands at startup* so the seed itself demonstrates the model: one open course, one full course, and one full course with a non-empty waitlist, plus the fixed accounts of `AUTH-01`, seeded in every profile. |
| REST API + thin React SPA covering the above | The chosen shape (PROJECT.md); also the mechanism by which a reader can drive the demo without reading Kotlin | MEDIUM | Intention-revealing endpoints (`POST /courses/{courseId}/enrollments`), not generic CRUD. UI stays plain. |
| Offline test suite (given/when/then per slice) | Axon's own teaching material is test-first; a DCB demo without tests is an assertion, not a demonstration | MEDIUM | `AxonTestFixture.with(application.configurer())` so tests use production configuration. |
| README command walkthrough (one command end-to-end) | The stated delivery mechanism; also how a reader decides in 60 seconds whether to keep reading | LOW | Copy-pasteable curl (or UI steps) with the expected output at each hop, including the *rejected* hop. |

### Differentiators (What separates "a CRUD app that uses Axon" from "a DCB lesson")

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Last-seat race as an executable proof** | The Core Value. Turns "DCB is safe under concurrency" from a claim into a green test | MEDIUM | Capacity 1, N distinct students, one barrier-released burst. Assert **exactly one success** — this is deterministic regardless of interleaving, because a loser either sourced after the winner (sees full → business rejection) or sourced before it (append condition rejects). No flaky "usually one" assertions. |
| **Mechanism proof: the loser fails on the append condition** | Without this, a skeptic can attribute the single success to incidental serialization and the demo teaches nothing | MEDIUM | A second, coordinated test that forces both commands through the capacity check and asserts the losing append fails with `AppendEventsTransactionRejectedException` (and, ideally, the conflicting event). Optionally wrap the store in `ConflictDiagnosingEventStorageEngine`. |
| **Boundary annotations in code, at the exact line the boundary is chosen** | The lesson is *where* the boundary is decided; a criteria builder buried in a 200-line entity teaches nothing | LOW | One comment block above `@EventCriteriaBuilder` stating: which tags, which event types, and one sentence on *why each type is included* ("included because an evolve method reacts to it"). Mirror it on the command handler. |
| **Event × tag table in the README (per command)** | This is the single highest-leverage teaching artifact in the reference material: dcb.events renders a "Query Item / Event Types / Tags" table for every command | LOW | Hand-written table, one row per read. Makes "the boundary is a query" concrete without any instrumentation. |
| **Narrated integration tests** | Test names + comments are the only place the invariant is *executed*; narration makes the failure message itself instructive | LOW | e.g. `lastSeat_twoStudentsRace_exactlyOneEnrolls_otherIsRejectedAsFull`. Comment the interleaving, not the API. |
| **Design note: why dynamic rather than fixed, with the aggregate counterfactual** | "Dynamic" is meaningless without the fixed boundary to compare against. Every effective DCB artifact researched does this comparison explicitly | LOW / MEDIUM | Include: the fixed-aggregate version (mega-course aggregate vs. student aggregate + saga), what each one over-locks or fails to enforce, and what a boundary change costs in each model. |
| **Conflict vs. business rejection in the API surface** | The most under-taught distinction in DCB: "you lost a race" (409, retryable) and "the course is full" (422, not retryable) look identical to a user but are different mechanisms | LOW | Map `AppendEventsTransactionRejectedException` → 409 with a body naming the conflicting tag set; map rule violations → 422 with the rule's message. One line in the README explaining why both exist. |
| **The race is demonstrated by its test, not by an endpoint** | The reader sees one success and N rejections from a command whose output is quotable — without a demo-only admin path in the API | LOW / MEDIUM | A contention test through the production `CommandGateway` prints `{enrolled:1, full:n-1, conflicted:0, maxInFlight:n}`. No demo endpoint and no UI trigger; event introspection stays in Out of Scope. |
| **Second race: drop vs. enroll (promotion under contention)** | Proves the boundary covers *promotion*, not just capacity — the hardest and most interesting part of the domain | MEDIUM | Student A drops (promoting B) while C enrolls. Invariant: enrolled ≤ capacity at all times. Assert enrolled count never exceeds capacity and no seat is double-promoted. |
| **"What the decision read" exhibit** | The literal claim of DCB is *narrow read*; showing it beats asserting it | LOW / MEDIUM | Cheapest honest form: a unit test asserting the criteria's tags/types (or a snapshot of its rendered form). Heavier forms (counting sourced events) slide toward the instrumentation anti-feature — keep it in a test, not a UI. |
| **Deferred-contrast note: why no saga / automation slice** | The reference demos all use DCB *instead of* coordination; saying so, with the eventual-consistency alternative spelled out, is the conceptual payoff | LOW | Doc-only. Note that drop+promote in one append is why the saga disappears; note what would change if promotion were a separate automation. |
| **Failure-mode walkthrough in the README** | A demo that only shows happy paths teaches half the model | LOW | Show the 422 "course is full", the 409 conflict, and the resulting event log tail — three commands, ~15 lines. |

### Anti-Features (Seem Reasonable, Actively Harm the Lesson)

| Anti-Feature | Why Requested | Why Problematic | Alternative |
|--------------|---------------|-----------------|-------------|
| Real authentication (OAuth/OIDC, roles, password reset, user CRUD) | "A demo should look real" | Consumes the entire scope budget and teaches nothing about boundaries; security work also invites production-hardening creep | Seeded users, printed credentials, no user management. Say so in the README in one line. |
| In-memory event store in the running system | Zero setup, but process-local: the other services see nothing | **Superseded by the pivot** — the topology is three services, so the store must be shared | Axon Server (or, for unit tests only, the in-memory engine). |
| Event trace UI / Axon Insights / event-store browser | "Seeing the events would be great" | Becomes the project. Also lets the reader bypass the code, which is where the boundary is | Event × tag table in the README + test output + the 409 body naming the conflicting tags. |
| Saga / process manager / automation slice for waitlist promotion | "Promotion is a reaction to a drop — that's an automation" | Splits one atomic decision into two boundaries and introduces a real oversell window; the demo would then need compensating logic — exactly the complexity DCB removes | One command, one append: `DropEnrollment` decides and emits both `StudentDropped` and `WaitlistPromoted`. Discuss the alternative in the design note, don't build it. |
| Waitlist offers with expiry / notifications / accept-decline | Industry-standard LMS behavior; "more realistic waitlist" | Adds a time dimension, a scheduler, and a second source of truth for "who holds the seat". Timers obscure the boundary story completely | Auto-promote the next FIFO student immediately. One sentence in the design note on why an offer model would need deadlines. |
| Pessimistic locking / `synchronized` / blanket command sequencing to "fix" the race | The race "fails" in a test and locking makes it pass | This *is* the failure mode the demo exists to refute: it hides the append condition and replaces an optimistic check with a lock | Let the append condition reject. If sequencing must be discussed, show it as a deliberate opt-out for the race (see Feature Dependencies). |
| Unlimited automatic retry on append conflict | "Better UX, the user shouldn't see a 409" | Retries until the state changes, then reports "full" — the conflict mechanism becomes invisible, and retry storms are a real operational hazard | Zero retries by default (so the mechanism is observable); document the single-retry idiom in the design note as the production answer. |
| Making one giant `Course` aggregate that contains all students | "Then the capacity check is trivial" | It is exactly the over-locking, low-cohesion model DCB exists to replace — and the demo would then be unable to show why the boundary is dynamic | Dynamic criteria over `courseId` + `studentId`; the design note names the mega-aggregate as the counterfactual. |
| Generic CRUD endpoints / resource-oriented PUTs / admin data tables | "RESTful, predictable" | Collapses intention-revealing commands into state replacement; the reader cannot see the decision that the boundary protects | `POST …/enrollments`, `DELETE …/enrollments`-style intents with named commands inside; the student is never named in the path. |
| Current-state table as the source of truth (dual-write, "for querying") | "Queries are easier against a table" | Two sources of truth; the demo now has to explain reconciliation, which is orthogonal and confusing | Projections derived from events only, persisted in PostgreSQL, rebuildable by replay (v2). |
| Seeding by writing events directly / importing fixtures into the store | "Faster startup" | The seed bypasses the model, so the seeded state may violate invariants the demo later claims to enforce; also unteachable | Seed by dispatching real commands at startup. |
| Priority points, admin override, overbooking, group bookings, transfers, refunds, grading, attendance, email | "Real courses have these" | Each one is a policy surface that dilutes the single seat-cap invariant and multiplies UI states | Name them in a "deliberately out of scope" README section — that list is itself evidence the author understood the domain. |
| Client-side seat counting / optimistic UI that shows a seat before the append commits | "Feels instant" | Lies exactly at the moment the demo is about; a reader who clicks fast sees the UI disagree with the server | Disable the button on the server's answer; refresh from the read model after the command returns. |
| Snapshots, upcasting, event versioning, event migration | "Production-ready event sourcing" | There is no history to migrate and no stream long enough to need a snapshot | Mention in the design note as "what you'd add at scale". |

---

## What Makes a DCB Demo Actually Teach (evidence from reference material)

| Practice | Evidence | How it applies here |
|----------|----------|---------------------|
| Tiny domain, one hard invariant | AxonIQ `university-demo`, `dcb.events` course subscriptions, `maxzillabong/axon-5-dcb-demo` (money transfer), MateuszNaKodach Heroes/Cinema — all are "small domain, one cross-entity rule" | Seat capacity is already the right invariant. Resist adding a second star scenario. |
| The contrast *is* the explanation | `maxzillabong/axon-5-dcb-demo` frames itself as "Axon 4: ~120 LOC + saga; DCB: ~30 LOC". `dcb.events` shows the aggregate repository, then the DCB repository, on the same model | Ship the counterfactual in the design note (and optionally a `docs/fixed-boundary.md` walkthrough). This is the cheapest high-value differentiator. |
| Tests are the spine, not an appendix | Axon's Getting Started is structured slice-by-slice with GWT tests shown in the README; `university-demo` documents "write slice: given→when→then" as *the* method | Make the race test the "hero" artifact linked from the top of the README. |
| The boundary must be visible in one screen | The reference explicitly demonstrates a `@EventCriteriaBuilder` per entity and prints an "Event Types × Tags" table per command | One annotated criteria method + one table in the README. Do not scatter boundary logic across helpers. |
| Runnable with minimal infrastructure | `university-demo` keeps Axon Server optional; this project deliberately does not | Pivot: build the images, then `docker compose up -d --wait` starts the whole stack; `bootRun --parallel` remains the dev loop. |
| The domain language stays honest | `dcb.events` reference uses `require … else reject "Course is full"` — rejection messages are part of the model, and this project's canonical spelling is lowercase `course is full` | Use one canonical rejection message per rule, and reuse it verbatim in tests, README and API responses. |

---

## Feature Dependencies

```
[Seeded login (student/admin)]
    └──requires──> [Role-gated UI/API]

[Create course (title + capacity)]
    ├──requires──> [Event store + command bus wiring]
    └──enables───> [Course catalog read model]

[Enroll (DCB boundary)]
    ├──requires──> [Create course]
    ├──requires──> [Decision model scoped to courseId, student checked from payload]
    ├──requires──> [Duplicate-enrollment rule]
    ├──enables───> [Capacity rejection]
    ├──enables───> [Explicit waitlist join]
    └──enables───> [Waitlist depth read model]

[Drop]
    ├──requires──> [Enroll]
    ├──requires──> [Ordering source (global event order / explicit sequence)]
    ├──requires──> [Atomic drop+promote in one append]
    └──enables───> [FIFO promotion]

[Last-seat race test]
    ├──requires──> [Enroll + capacity rejection]
    ├──requires──> [Append-condition enforcement by Axon Server]
    └──conflicts──> [Same-course command sequencing]

[Mechanism proof test]
    ├──requires──> [Last-seat race test]
    └──requires──> [Conflict-diagnosing store or exception assertion]

[Conflict vs rejection mapping]
    ├──requires──> [Enroll]
    └──enhances──> [Last-seat race as a test]

[README walkthrough]
    ├──requires──> [Create course, Enroll, Drop, Promotion, Race test]
    └──requires──> [REST endpoints or UI steps]

[Boundary annotations]
    └──requires──> [Enroll slice exists]

[Design note]
    ├──requires──> [Boundary annotations (the thing being justified)]
    ├──requires──> [Race test (the evidence)]
    └──requires──> [Drop+promote atomicity decision]

[Event × tag table]
    └──requires──> [Final tag set on every event]
```

### Dependency Notes

- **Everything read-side depends on the tag set.** Tags are a schema decision: once `EnrollmentEvent` carries `courseId` + `studentId`, both the course-count and per-student reads are possible. Decide the event × tag table in Phase 3, before any slice code — changing tags later is an event-migration problem (the exact pain DCB is meant to avoid).
- **Drop+promote atomicity is the pivot of the whole design.** If promotion is a separate command/automation, `Drop` and `Promote` have two boundaries and the invariant needs coordination. Building it as one command is *cheaper* and is the differentiator for the design note; the reverse order (automation first) would force a rewrite.
- **The race test constrains command wiring, not just the test.** Axon 5 enables command sequencing by routing key by default (`CommandSequencingInterceptor` + `RoutingKeySequencingPolicy`, default since 5.0.3). If the enroll command declares `@Command(routingKey = "courseId")`, same-course commands are serialized *before* the handler, and the append conflict never fires. **Project decision: `EnrollStudent` carries no routing key**, so enrollments run concurrently. **This is a build-order hazard: prove it in Phase 4** by adding a `courseId` key temporarily and watching the conflict disappear.
- **The timing (`@TargetEntityId` vs routing key) is a trap for the unwary.** In AF5, `@TargetEntityId` only selects which entity to load; sequential handling comes from `@Command(routingKey = …)`. Omitting `routingKey` compiles and passes unit tests while allowing parallel handling — the mirror image of the race hazard above.
- **Promotion ordering depends on a monotonic source.** FIFO needs a total order across waitlist joins. Global event position works within one bounded context; do not introduce a mutable `queuePosition` column (it becomes a second source of truth and a lock).
- **UI "seats left" lags the write.** The projection is a separate process with a tracking processor, so the POST returns before the read model updates. The demo must show a pending state, then the SSE update (VIEW-02) instead of pretending consistency; never compute seat counts client-side.
- **The mechanism-proof test depends on the race test existing** and on being able to observe the exception; it is deliberately not the MVP (the race test alone satisfies the requirement) but it is what makes the demo resistant to the "maybe it was just serialized" objection.

---

## MVP Definition

### Launch With (v1) — exactly the Active requirements in PROJECT.md, nothing more

- [ ] Seeded login (student + admin) — without actors the capability split is unreadable
- [ ] Create course with capacity — the invariant's source
- [ ] Enroll with capacity + duplicate rules inside one dynamic boundary — the star slice
- [ ] Reject at capacity with a named reason — makes the rule observable
- [ ] Explicit waitlist join after a `course is full` rejection — keeps the race outcome deterministic
- [ ] Drop, freeing the seat, promoting next FIFO atomically — the second half of the boundary story
- [ ] Course catalog (seats left, waitlist depth) + my enrollments — the read side that lets a human drive the demo
- [ ] Live read-model updates over SSE (snapshot + pushed changes, no polling) — makes eventual consistency visible instead of hidden
- [ ] Last-seat race test asserting exactly one success — the Core Value, executable
- [ ] Boundary annotations at the criteria builder + handler — where the lesson lives
- [ ] README command walkthrough (one command end-to-end) — the stated delivery path
- [ ] Narrated integration tests — the invariant under contention, readable
- [ ] Design note (dynamic vs fixed, with counterfactual) — the conceptual payoff
- [ ] One-command startup + seeded scenario data — the demo must be reproducible

### Add After Validation (v1.x) — only if the demo already runs end-to-end

Mechanism proof (RACE-03), 409/422 mapping (PLAT-04), the race proof as a test (RACE-01) and the event × tag table (DOCS-05) are **v1**, not v1.x — the independent review showed the proof is worthless without them.

- [ ] Drop-vs-enroll race test — extends the proof to promotion
- [ ] Criteria exhibit test ("what the decision read") — makes "narrow read" checkable
- [ ] `ChangeCourseCapacity` slice — a `docs`-only counterfactual note; the slice itself is v2 (CRSE-04)

### Future Consideration (v2+)

- [ ] Automation-slice contrast (promotion as an event reaction) built on a branch and documented, not shipped — deferred: doubles the surface and muddies the "no saga" lesson
- [ ] Any real waitlist policy (offers, expiry, priority) — deferred: policy surface without teaching value here
- [ ] Deployment/production hardening — deferred: explicitly out of scope

---

## Feature Prioritization Matrix

| Feature | Reader Value | Implementation Cost | Priority |
|---------|--------------|---------------------|----------|
| Enroll with dynamic boundary | HIGH | MEDIUM | P1 |
| Capacity rejection with named reason | HIGH | LOW | P1 |
| Drop + FIFO promotion (atomic) | HIGH | MEDIUM | P1 |
| Last-seat race test | HIGH | MEDIUM | P1 |
| Boundary annotations | HIGH | LOW | P1 |
| README walkthrough | HIGH | LOW | P1 |
| Design note with counterfactual | HIGH | LOW | P1 |
| Narrated tests | HIGH | LOW | P1 |
| Create course + catalog + my enrollments | MEDIUM | LOW | P1 |
| Seeded login | MEDIUM | LOW | P1 |
| One-command startup + seed data | HIGH | LOW | P1 |
| Event × tag table | HIGH | LOW | P1 |
| Conflict vs rejection mapping | MEDIUM | LOW | P1 |
| Mechanism-proof test | HIGH | MEDIUM | P1 |
| Race proof as a runnable test | HIGH | LOW | P1 |
| Drop-vs-enroll race test | MEDIUM | MEDIUM | P2 |
| Criteria exhibit test | MEDIUM | LOW | P2 |
| `ChangeCourseCapacity` slice | MEDIUM | LOW | P3 |
| Automation-slice contrast | LOW | HIGH | P3 |

**Priority key:** P1 = launch (the demo fails its purpose without it) · P2 = should have, add once v1 runs · P3 = nice to have, future consideration

---

## Competitor Feature Analysis (reference implementations)

| Feature | AxonIQ `university-demo` | `dcb.events` course subscriptions | `maxzillabong/axon-5-dcb-demo` | Our Approach |
|---------|--------------------------|-----------------------------------|-------------------------------|--------------|
| Domain | University faculty: students + courses with capacity | Courses + students, subscription counts | Bank transfer between two accounts | Course enrollment with capacity + waitlist |
| Boundary shape | Split decision models per slice (`SubscribeStudentToCourse` with its own `State`) | Projections per tag, reads combined per command | Two entities injected in one handler | One decision model per command, criteria scoped to `courseId`; `studentId` is checked from the payload; `either(...)` is the v2 widening |
| Capacity invariant | Yes (`Course is fully booked`) | Yes | N/A (balance invariant) | Yes — the star |
| Per-student limit | Yes (max 3) | Yes (max 5) | N/A | Not in v1 (duplicate rule instead) |
| Capacity editing | Yes (`CourseCapacityChanged`) | Yes | No | Deferred (P3) |
| Waitlist | **No** | **No** | No | **Yes — FIFO promotion, our unique feature** |
| Frontend | None (API/tests only) | Notation + prose | None | React SPA + REST |
| Concurrency demo | Implicit in tests | Scenarios (no concurrency) | Not a focus | **Explicit race test, no UI trigger** |
| Teaching artifacts | README with event model, slice structure, GWT example | Notation + per-command courses-read/tag tables + scenario lists | 4.x-vs-5 LOC comparison | README walkthrough + annotations + narrated tests + design note + tag table |
| Runtime | Axon Server + PostgreSQL | Language-agnostic spec | Maven + Axon Server | Three services, compose + one Gradle run |

**Gap analysis:** none of the reference implementations analyzed above models a waitlist at all. That is both the differentiator and the risk — there is no upstream pattern to copy for atomic drop+promote, so the design note has to carry that argument itself. Everything else has a precedent to lean on. (Claim scope: limited to the projects analyzed; other DCB demos may exist that were not surveyed.)

---

## Roadmap Implications (feature-ordering constraints)

1. **Event and tag schema first** — the events, their tags, and the event × tag table must be settled before any slice, because tags are effectively a schema contract.
2. **Enroll slice before anything else** — it carries the boundary lesson and calibrates the command-routing decision (the sequencing hazard).
3. **Drop + promotion second, as one command** — atomicity choice must be made here; reversing it later is a rewrite.
4. **Race proof + annotations + README + design note together** — these are one artifact set ("the evidence"); splitting them across phases leaves a window where the code claims something no document proves.
5. **UI last** — it consumes read models and endpoints; building it earlier freezes API shapes before the boundary design stabilizes.

---

## Sources

- [Axon Framework reference — Event Store Internals (tags, `EventCriteria`, `AppendCondition`, consistency markers)](https://docs.axoniq.io/axon-framework-reference/5.4/events/event-store-internals/) — official; MEDIUM
- [Axon Framework reference — Event Store migration (DCB support per storage engine)](https://docs.axoniq.io/axon-framework-reference/5.4/migration/paths/event-store/) — official; MEDIUM
- [Axon Framework reference — Message migration (`@TargetEntityId` vs `@Command(routingKey=…)`)](https://docs.axoniq.io/axon-framework-reference/5.4/migration/paths/messages/) — official; MEDIUM
- [Axon Framework reference — Command Processing / command sequencing](https://docs.axoniq.io/axon-framework-reference/5.4/tuning/command-processing/) — official; MEDIUM. Sequencing shipped in 5.0.3 ([release notes](https://docs.axoniq.io/axon-framework-reference/5.0/release-notes/minor-releases/), PR #4173); confirm the default on the exact version you pin before relying on it.
- [Axon Framework reference — Testing (`AxonTestFixture`)](https://docs.axoniq.io/axon-framework-reference/5.4/testing/) and [Advanced testing / `@AxonSpringBootTest`](https://docs.axoniq.io/axon-framework-reference/5.4/testing/advanced-testing/) — official; MEDIUM
- [`AppendEventsTransactionRejectedException` API](https://apidocs.axoniq.io/latest/org/axonframework/eventsourcing/eventstore/AppendEventsTransactionRejectedException.html), [`AppendCondition` API](https://apidocs.axoniq.io/latest/org/axonframework/eventsourcing/eventstore/AppendCondition.html), [`ConflictDiagnosingEventStorageEngine` API](https://apidocs.axoniq.io/latest/org/axonframework/eventsourcing/eventstore/ConflictDiagnosingEventStorageEngine.html) — official API docs; MEDIUM
- `InMemoryEventStorageEngine` source, `axon-5.0.x` branch (append-condition check at insert and again under lock at commit; consistency marker as terminal sourcing entry) — framework source; MEDIUM. *Note: the 5.4 migration table marks in-memory as "No" for DCB support while the source implements conflict detection in-process and `university-demo` documents it as DCB-capable. Treat "no" as production-readiness, not functionality — and re-verify against the version you pin.*
- [AxonIQ `university-demo`](https://github.com/AxonIQ/university-demo) (course capacity, per-student cap, slice structure, GWT testing, in-memory vs Axon Server) — reference implementation; MEDIUM
- [Axon Framework 5 Getting Started guide](https://docs.axoniq.io/axon-framework-5-getting-started/5.4/) and [University domain chapter](https://docs.axoniq.io/axon-framework-5-getting-started/5.4/sample-domain-event-modeling/) — official tutorial narrative; MEDIUM
- [AxonIQ — Dynamic Consistency Boundary concept](https://www.axoniq.io/concepts/dynamic-consistency-boundary) and [DCB in AF 5 blog](https://www.axoniq.io/blog/dcb-in-af-5) — vendor explainer; MEDIUM
- [`maxzillabong/axon-5-dcb-demo`](https://github.com/maxzillabong/axon-5-dcb-demo) — DCB-vs-saga comparison framing; MEDIUM
- [dcb.events — Course subscriptions example](https://dcb.events/examples/course-subscriptions/) (per-command courses-read/tag tables, scenarios, no waitlist) — MEDIUM
- [dcb.events — Aggregates / DCB topic](https://dcb.events/topics/aggregates/) (the "rule spanning two aggregates" framing that matches seat capacity) — MEDIUM
- Waitlist policy practice: [UBC CS waitlists](https://www.cs.ubc.ca/students/undergrad/courses/waitlists), [UCR Banner waitlist procedures](https://studentdocs.ucr.edu/ucrbanner/Waitlist_Management_Procedures.pdf), [Workday waitlist policies](https://doc.workday.com/admin-guide/en-us/student/student-records/student-registration/student-registration-setup/set-up-course-waitlist-policies.html), [Absorb LMS waitlist lifecycle](https://support.absorblms.com/hc/en-us/articles/52138472001811-Waitlist-Lifecycle) — used to confirm that auto-promotion vs. offer-with-expiry is the real policy fork; LOW/MEDIUM (product docs, not academic)
- Demo-repo practice: [Nimbus in-depth example](https://nimbus.overlap.at/guide/in-depth-example.html), [evo9/es-wallet](https://github.com/evo9/es-wallet), [andreschaffer/event-sourcing-cqrs-examples](https://github.com/andreschaffer/event-sourcing-cqrs-examples) — LOW/MEDIUM (inferred practice, not documented standard)

---

*Feature research for: Axon 5 DCB demo (course enrollment / capacity / waitlist)*
*Researched: 2026-11-10*
