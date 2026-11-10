# Pitfalls Research

**Domain:** Axon Framework 5 Dynamic Consistency Boundaries (DCB) — course-enrollment showcase
**Researched:** 2026-11-10
**Confidence:** HIGH on API semantics and failure mechanics (read from primary AxonIQ 5.4 docs and the framework source); MEDIUM on embedded-store guarantees (documentation and source contradict each other — see Pitfall C1)

> This file is specific to DCB showcases, not generic web-app advice. Every pitfall below is either a documented Axon 5 behaviour, a direct consequence of it, or a failure mode observed in the reference implementations of this exact domain (the Axon University demo and the `dcb.events` course-subscriptions example).
>
> **Phase N** in each entry is indicative. If the roadmap numbers phases differently, map by *topic* using the table at the end.

---

## Facts this file rests on

These are the load-bearing mechanics. Every pitfall below is a way of getting one of them wrong.

1. **The boundary is a query, not a tag set.** The decision model is built by *sourcing* events that match an `EventCriteria`. Tags are the index that makes that query possible. A perfectly tagged event that your criteria does not ask for is invisible to the decision.
2. **The append condition is derived from the sourcing condition.** "When appending, the framework combines the condition used for sourcing with the [tags] of newly appended events." No sourcing → no meaningful condition.
3. **`EventCriteria` is boolean logic, not a filter list.** `havingTags(a, b)` means *both*. `andBeingOneOfTypes(...)` means *any of these types*. `EventCriteria.either(x, y)` means *x OR y*. Tags and types combine differently, and getting the nesting wrong silently narrows the boundary.
4. **A conflict is detected against the consistency marker** returned as the terminal entry when sourcing. Appending fails if an event matching the condition landed after that marker.
5. **Appended events' tags widen the append condition.** This is why an enrolment event must be tagged with the course: it is what makes a concurrent enrolment to the same course collide.
6. **Commands are sequenced by routing key by default** (`RoutingKeySequencingPolicy`). Same routing key → handled sequentially. This can remove the race you are trying to demonstrate.
7. **The DCB spec's guarantee is "nothing matching my query happened since I looked"** — immediate, not eventual. Projections/read models are a *different* consistency model and cannot enforce a hard constraint.

---

## Critical Pitfalls

### Pitfall C1: The embedded event store may not give the guarantee the demo depends on

**What goes wrong:**
The project's Core Value is "concurrent enrolments for the last seat result in exactly one success". That guarantee is produced by the *event store* rejecting the second append — not by the handler, not by JPA, not by `@Transactional`. If the chosen engine does not enforce append conditions, both commands succeed and the demo silently disproves its own thesis.

The Axon documentation is not internally consistent here:

- The AF4→AF5 migration guide's storage-engine table lists `InMemoryEventStorageEngine` as **DCB support: No**, across 5.0–5.3.
- The AF5 getting-started tutorial states the in-memory store "still allows you to try out the new Command handling API and the new DCB-support just the same."
- The current framework source (`InMemoryEventStorageEngine`, main branch) *does* implement it: `appendEvents` evaluates `containsConflicts(condition)`, and `commit()` re-checks under a `ReentrantLock` before writing, failing with `AppendEventsTransactionRejectedException.conflictingEventsDetected(...)`.

So it probably works — but "probably" is not a foundation. And even when it works it is a **single-JVM lock**: the guarantee is scoped to one process and one engine instance, which is fine for a single-node showcase but must never be described as a distributed guarantee.

**Why it happens:**
The "no Docker, embedded store" constraint (correctly) pushes away from Axon Server, and the only *documented* DCB-capable engine is `PostgresqlEventStorageEngine` — which needs a PostgreSQL 16+ server and ships in `io.axoniq.framework:axoniq-postgresql`, separately from `org.axonframework`, with a production license. It is easy to defer this decision until "later", then discover in the middle of the race demo that the store does not enforce anything.

**How to avoid:**
Decide this in Phase 1 with an *executable* spike, not by reading docs:

1. Boot only the Axon config + `InMemoryEventStorageEngine`.
2. Register a trivial entity whose criteria are `havingTags("Course", "c1")` and whose handler appends `SeatTaken(courseId)` after asserting `count < capacity`.
3. From two threads behind a `CyclicBarrier`, dispatch two commands.
4. Assert exactly one commit and one `AppendEventsTransactionRejectedException`.

If that fails, fall back deliberately: a local PostgreSQL (still no Docker: `brew install postgresql@16`) with `PostgresqlEventStorageEngine`, or accept Axon Server. Then update `PROJECT.md`'s constraint rather than pretending the constraint and the guarantee are both intact.

Also decide now what "embedded" means for durability: in-memory events vanish on restart, so seeding must run on every boot and the README must say so.

**Warning signs:**
- The race test passes on the first run, every run, with no configuration work.
- Both concurrent requests return `201`.
- You cannot point to a line of code that returns a rejection for the loser.
- Someone says "the handler checks the capacity, so it's fine."

**Phase to address:** Phase 1 (walking skeleton / store spike) — before any UI or domain slice work.

---

### Pitfall C2: Tagging correctly but sourcing the wrong events

**What goes wrong:**
Every event carries the right `@EventTag`s, the tags table is beautiful, and the boundary is still wrong — because the decision model's `EventCriteria` omits event types that change the invariant. Typical omissions in this domain:

- `CourseCapacityChanged` missing → capacity drops are not seen → oversell.
- `StudentUnsubscribedFromCourse` / `EnrollmentDropped` missing → seat count only ever rises → waitlist never promotes, seats are permanently lost.
- `StudentEnrolledInFaculty` missing → "student exists" check passes/fails arbitrarily.

The events are present and correctly tagged; they are simply not part of the question being asked.

**Why it happens:**
DCB is marketed as "tag-based consistency", so tags feel like the boundary. They are not — the boundary is the query the handler's state is built from, and the tags only determine what *can* be asked for. The Axon docs make the same point from the other direction: "it's beneficial to define event types in the criteria… in this case, you load more than needed, and it's possible to clash with other changes."

**How to avoid:**
Write each invariant as one sentence, enumerate every event that can change it, then make the criteria list literally that enumeration, with the invariant stated in a comment next to it. For this app, the enrolment invariant is:

> *seats used = (enrolments for this course) − (drops for this course) must stay strictly below capacity, and a student may hold at most one seat in a course.*

That sentence names four event types at minimum: `CourseCreated`, `CourseCapacityChanged`, `StudentEnrolled`, `StudentDropped`. Any handler that does not read all four is wrong, and a test should fail if it does not.

Add one test per direction: *enrol after a drop succeeds*, *capacity decrease below current enrolments blocks the next enrol*, *drop frees exactly one seat*.

**Warning signs:**
- The state class has fewer `@EventSourcingHandler` methods than the number of event types that touch the number it computes.
- The seat count in the read model and the seat count in the decision model disagree after a mixed sequence.
- A manual walkthrough: create course, enrol 2, drop 1, enrol 1 → if the third enrol is rejected, the drop is not being read.
- The criteria can be summarised as "events tagged with this course" without mentioning types.

**Phase to address:** Phase 3 (enrol under capacity), extended in Phase 4 (drop).

---

### Pitfall C3: Getting `EventCriteria`'s boolean semantics wrong

**What goes wrong:**
The single most likely silent bug in this exact app. The enrolment decision needs *all* enrolments for the course **plus** all enrolments for the student. Writing:

```java
EventCriteria.havingTags(Tag.of("courseId", courseId), Tag.of("studentId", studentId))
```

returns only events carrying **both** tags — i.e. this student's enrolment in this course. Every *other* student's enrolment disappears, the course looks empty, and the capacity check always passes. The demo oversells by whatever concurrency the load generator produces.

The inverse mistake is just as common: dropping `.andBeingOneOfTypes(...)` entirely, which loads every event for the tag and creates conflicts with changes that have nothing to do with the decision (the docs call this out explicitly).

**Why it happens:**
`havingTags(a, b)` reads like "having tags a and b" (correct) but developers read it as "having tag a, or tag b" (the intent). There is no compiler help, no error, no log line — just a wrong number.

**How to avoid:**
One criterion per subject, OR-combined at the top:

```java
EventCriteria.either(
    EventCriteria.havingTags(Tag.of(CourseTags.COURSE_ID, courseId))
                 .andBeingOneOfTypes(CourseCreated.class.getName(),
                                     CourseCapacityChanged.class.getName(),
                                     StudentEnrolled.class.getName(),
                                     StudentDropped.class.getName()),
    EventCriteria.havingTags(Tag.of(StudentTags.STUDENT_ID, studentId))
                 .andBeingOneOfTypes(StudentEnrolled.class.getName(),
                                     StudentDropped.class.getName())
);
```

Then prove it: a test that seeds *another* student's enrolment in the same course and asserts the decision model sees it (e.g. enrolments-so-far == 1). That test fails loudly for the `havingTags(a, b)` version. Keep tag keys in constants — a typo in a string key produces an empty match, not an error.

**Warning signs:**
- Course capacity is never reached under a seed script that clearly fills it.
- The state's "enrolments for this course" equals "enrolments by this student" in a debugger or log.
- The criteria is a single `havingTags` call with two tags and no `either`.
- Removing the `studentId` tag from the criteria changes nothing.

**Phase to address:** Phase 3, verified by a dedicated "sees other students" test.

---

### Pitfall C4: The aggregate comes back through the identifier

**What goes wrong:**
The code has no `@Aggregate`, yet the design is an aggregate. Three routes back:

1. **Entity id = `CourseId`.** `@EventSourcedEntity(tagKey = "courseId")` plus `@TargetEntityId` returning `courseId` gives one state instance per course — a course aggregate with a course stream. Capacity is enforced, but the boundary is fixed, every enrolment to a course is serialized, and the design note's claim that the boundary is "dynamic" is false.
2. **Two entity states loaded separately.** `CourseState` and `StudentState` injected into one handler is aggregate-thinking with two boundaries and no atomicity between them.
3. **Routing key = `courseId`.** Even with a composite entity id, if `@Command(routingKey = ...)` or the default routing-key resolver yields the course, `CommandSequencingInterceptor` serializes all enrolments for that course *before* they reach the repository (see C6).

**Why it happens:**
The identifier is the one thing every AF4 habit says to define first, and "the course is the thing with the capacity" is a natural noun. The reference implementation avoids this only by introducing a deliberate composite key (`SubscriptionId(courseId, studentId)`) and stating why — a step that is easy to skip when you are learning the API and just want the test green.

**How to avoid:**
Name and type the entity after the **decision**, not the entity: `EnrollDecisionState` with `EnrollmentDecisionId(courseId, studentId)`. Ask of every handler: *"which commands must this one be mutually exclusive with?"* — the answer defines the id and the criteria. A course-scoped handler is only correct for `CreateCourse`/`ChangeCapacity`; a student-scoped id is only correct for "how many courses am I in".

Two cheap checks:
- Delete `tagKey` from the annotation. If the code still compiles and any test still passes, the boundary was implicit somewhere else.
- Write down, for each command, the *two* subjects involved. If there is only one, ask whether that is genuinely the invariant or just the noun you reached for.

**Warning signs:**
- A class named `Course`, `Student`, or `Enrollment` with `@EventSourcedEntity`.
- The entity id type is a domain entity id.
- Throughput of unrelated enrolments to the same course is 1 at a time.
- You cannot answer "why does this command need `studentId` in its criteria?" without saying "it's the id".

**Phase to address:** Phase 3 design (before implementation); audited in Phase 6 when writing the design note.

---

### Pitfall C5: No sourcing → no append condition → no guarantee

**What goes wrong:**
The handler validates against state it built *some other way*, or against nothing, and appends. Because the append condition is constructed from the sourcing condition, a handler that never sources has nothing to be consistent with. In `InMemoryEventStorageEngine` the check is literally skipped when the consistency marker is `ConsistencyMarker.INFINITY`. Two concurrent enrolments both pass their (identical, independently computed) checks and both commit.

Concrete routes:
- `@CommandHandler` without `@InjectEntity`, using `EventAppender`/`EventGateway` directly.
- Appending through the `EventGateway` after the handler returns, or from another thread — that is a separate unit of work with its own (absent) condition.
- Catching `AppendEventsTransactionRejectedException`, then re-appending from the stale decision instead of re-sourcing.
- "Optimising" by caching the decision model between requests.

**Why it happens:**
Stateless command handlers are the ergonomic default in AF5 (`@CommandHandler` methods that just append), and the whole "stateful handler" pattern with `@InjectEntity` looks like ceremony until you need it. The guarantee is invisible in the code: nothing says "this append is conditional".

**How to avoid:**
Every decision handler takes `@InjectEntity`. Never call `AppendCondition.withoutCondition()` in domain code. Add one test that *deliberately* forces a conflict and expects `AppendEventsTransactionRejectedException` — if the test cannot fail, the condition is absent. Keep the stateful handler and the criteria in the same file so the coupling is visible.

**Warning signs:**
- A command handler with no `@InjectEntity` that appends events.
- `EventGateway` used inside handlers where `EventAppender` is meant.
- The conflict test asserts on `success()` for both commands.
- Grep finds `withoutCondition`.

**Phase to address:** Phase 3, re-verified in Phase 5 (the race is the proof).

---

### Pitfall C6: Command sequencing quietly removes the race

**What goes wrong:**
AF5 registers a `CommandSequencingInterceptor` by default, backed by `RoutingKeySequencingPolicy`: commands with the same routing key are processed **sequentially**. If the enrol command's routing key resolves to the course (or to a constant, or to something every enrolment shares), the two "concurrent" enrolments are queued. Exactly one succeeds — because it is a queue, not because of DCB. The test is green, the README is wrong, and the lesson is inverted.

**Why it happens:**
Sequencing is presented as a good thing ("prevents optimistic locking conflicts"), so nobody checks whether it is the thing doing the work. It also produces *the exact expected output*, which is the most dangerous kind of false positive.

**How to avoid:**
Routing key per *student* (or the composite decision id) so racing enrolments from different students stay concurrent. Then assert concurrency rather than assuming it: instrument the handler to record in-flight count (an `AtomicInteger` incremented at entry, decremented at exit), and have the race test fail if max-in-flight is 1. That single assertion is what distinguishes "DCB worked" from "the framework serialized it for me". Document the routing key choice next to the boundary annotation.

**Warning signs:**
- The race test has never failed, not once, including before the criteria were right.
- The two request latencies are suspiciously additive (t₂ ≈ 2 × t₁).
- `maxInFlight == 1` in whatever instrumentation exists.
- Someone "fixed" a race by setting a routing key.

**Phase to address:** Phase 5 (race demo), with the routing key decided in Phase 3.

---

### Pitfall C7: Appended events missing a subject tag → the conflict is never seen

**What goes wrong:**
The append condition is widened by the tags on the events you append. If `EnrollmentDropped` or `SeatPromoted` is tagged only with `studentId`, then a concurrent enrolment's condition (course tags) never matches the release/promotion, and the two commits coexist: the seat is both taken and given away.

The mirror error is over-tagging: adding a `term`/`semester`/`catalog` tag to enrolment events makes every enrolment in the term mutually exclusive — correct, but with the parallelism of a single-row table.

**Why it happens:**
Tags are added where the identifier happens to be in the constructor. `StudentDropped(studentId, courseId)` naturally invites `@EventTag` on the student (the issuer) and forgetfulness on the course (the thing whose count changes).

**How to avoid:**
Maintain an explicit **event → tags table** as a design artifact and put it in the design note:

| Event | Tags | Why |
|---|---|---|
| `CourseCreated` | `course` | defines capacity |
| `CourseCapacityChanged` | `course` | changes capacity |
| `StudentEnrolled` | `student`, `course` | consumes a seat; may duplicate membership |
| `StudentDropped` | `student`, `course` | frees a seat |
| `StudentWaitlisted` | `student`, `course` | determines FIFO position |
| `StudentPromotedFromWaitlist` | `student`, `course` | consumes a seat |

Rule: *an event must carry a tag for every invariant it can affect.* Test it with a mixed-command contention test (enrol vs. drop+promote), because the pure "two enrolments" test will not catch a missing tag on drop.

**Warning signs:**
- Only the enrolment race reproduces; mixed races never conflict.
- Capacity is exceeded only in traces containing a drop or a promotion.
- Some events in the store have one tag, others two, with no stated rule.

**Phase to address:** Phase 3 for enrolment events; Phase 4 for drop/promotion; verified Phase 5.

---

### Pitfall C8: Waitlist promotion implemented outside the boundary

**What goes wrong:**
Dropping frees a seat; a separate event handler/automation sees `StudentDropped` and dispatches `PromoteNextStudent`. That automation runs later, in its own unit of work, with its own (or no) append condition. Consequences: two concurrent drops can promote two students into one freed seat; a new enrolment can take the freed seat while a promotion also lands; and the invariant "never above capacity" becomes eventually-consistent in the one place the app exists to make it strong.

**Why it happens:**
It is the textbook AF4-shaped reflex: "a drop happened, so react to it." It also *looks* cleaner — one handler per concern. And it works perfectly in a single-threaded click-through.

**How to avoid:**
Decide and append the promotion **inside the drop handler**, under the same append condition: the drop's decision model already contains the course's seat ledger and the waitlist, so it can both free the seat and name the next student. The events `StudentDropped` + `StudentPromotedFromWaitlist` go in one append. This is the DCB payoff — say so in the design note.

If you keep an automation (e.g. to model "promotion is a separate business process"), then the promotion command must *re-source the course boundary* and re-check capacity itself, and the design note must state that the seat is briefly unassigned. Anything less is a double-booking generator.

**Warning signs:**
- `@EventHandler` (or an "automation slice") that calls `commandGateway.send(...)` in the promotion path.
- A test that needs `await()` or a sleep before asserting seat counts.
- Two drops in the same millisecond leave capacity oversubscribed.
- The promotion logic reads the waitlist from a repository/read model.

**Phase to address:** Phase 4 (drop + FIFO promotion), re-verified Phase 5.

---

### Pitfall C9: FIFO ordered by a payload timestamp instead of the store's sequence position

**What goes wrong:**
`waitlist.stream().sorted(comparing(Waitlisted::requestedAt))` looks right and is wrong: two requests can share a millisecond (JSON round-trips truncate to millis), clients can supply timestamps, and equal keys give an arbitrary order. Promotions jump the queue non-deterministically — a bug that appears only under the concurrency the demo is designed to produce.

**Why it happens:**
The event payload is the obvious place to put "when", and the store's ordering is invisible because you never look at positions. DCB also encourages treating events as data rather than as a stream.

**How to avoid:**
Take order from the order the events are *read* — the sourcing stream is position-ordered and the spec requires sequence positions to resolve to a deterministic total order. If you need a human-readable timestamp, store it for display and never sort by it. Add a test with `capacity = 1`, three waitlisted students, and a forced timestamp collision; assert the promotion order matches join order.

**Warning signs:**
- A `Comparator` on `Instant`/`LocalDateTime` in the waitlist code.
- The order test uses `Thread.sleep` or distinct `Instant.now()` values.
- "It usually promotes the right one."

**Phase to address:** Phase 4.

---

### Pitfall C10: A concurrency test that cannot fail

**What goes wrong:**
The narrated contention test is the artifact that proves the whole project, and it is the easiest thing to fake:

- **The fixture is not a race harness.** `AxonTestFixture` runs one action per `when()` phase, in a single processing context. Two `when().command(...)` calls are sequential by construction. Behavioural tests from the fixture will never exercise the append condition.
- **Unsynchronised threads.** `IntStream.range(0,2).parallel()` or two `CompletableFuture`s usually run back-to-back; no overlap, no conflict.
- **Wrong assertion.** Asserting `both requests completed` or `no exception thrown`, or asserting the seat count afterwards without asserting *which* command failed and *why*.
- **Swallowed conflict.** A `catch (Exception e) { log }` in the test or in the API layer hides the very signal being demonstrated.
- **The negative control is missing.** Nothing proves the test would fail if the boundary were wrong — so a green test proves nothing.

**Why it happens:**
Concurrency tests are fiddly, flaky, and green is satisfying. And because C5/C6 can make the system *correct for the wrong reason*, a naive test agrees with a broken implementation.

**How to avoid:**
Build the race test as a small harness with explicit control:

1. Boot the real `ApplicationConfigurer` (not a mock), real store, real handlers.
2. `CyclicBarrier`/`CountDownLatch` so both commands enter the handler before either appends.
3. N threads (2 is enough; 8 is a better demo), each a distinct student.
4. Assert **exactly**: `successes == 1`, `rejections == n-1`, `maxInFlight > 1`, and the rejection is the expected one.
5. Repeat 20–50 times in a loop — a single run proves little.
6. **Negative control:** temporarily widen/narrow the criteria (or drop `@InjectEntity`) and confirm the test *fails*. Commit that fact into the README ("here is the version that breaks, and here is why").

Keep the narration *in the test method names and comments* ("two students, one seat", "loser sees the winner's event") rather than in a wall of logging.

**Warning signs:**
- `Thread.sleep` in the test.
- Flaky: passes locally, fails in CI, or vice versa.
- Test still passes after you delete `@InjectEntity` or break the criteria (run this experiment).
- Assertions are about counts of HTTP 2xx, not about which invariant rejected whom.
- Only one iteration.

**Phase to address:** Phase 5 — and treat "negative control fails as expected" as the phase's exit criterion.

---

### Pitfall C11: Read model used as the invariant source

**What goes wrong:**
The obvious implementation of "seats left" is a `courses` table with `seats_taken`, updated by a projector. The command handler then reads that table. It is fast, it is convenient, and it is eventually consistent: two concurrent enrolments read `seats_taken = capacity - 1`, both write, and the projector ends at `capacity + 1`. The read model is a *view*, not a guard.

**Why it happens:**
It is how every CRUD app works, and the projection already exists for the UI. DCB's whole premise ("decide from events") is easy to accept in theory and easy to violate the moment a repository is in scope.

**How to avoid:**
Hard rule: **command handlers read events; read models serve the UI.** Enforce it structurally — the write package must not depend on the query package (an ArchUnit rule or a package-private boundary is enough). If a query-side type ever appears in a handler signature, the rule is broken.

**Warning signs:**
- A `Repository`/`JpaRepository` (other than Axon's `Repository` for entities) in a `@CommandHandler`.
- `@Transactional` on a command handler.
- "It works when I click slowly."
- Capacity is exceeded exactly under load and never in manual testing.

**Phase to address:** Phase 3 (establish the rule), audited whenever a slice is added.

---

## Moderate Pitfalls

### Pitfall M1: Conflating "append condition conflicted" with "course is full"

**What goes wrong:**
The loser of the race fails with `AppendEventsTransactionRejectedException`, which extends `AxonNonTransientException`. Surfaced through the REST layer it becomes a `500` (or a `409 Conflict` with an internal message). The demo then shows "one student enrolled, the other got a server error" — which contradicts the requirement "enrollment is rejected when the course is at capacity" and is a poor teaching artifact: it makes DCB look like a failure mode.

**Why it happens:**
The framework is doing exactly what it should (refusing a stale decision), and there is no built-in "retry and re-evaluate" by default. Retry behaviour requires registering a `RetryScheduler`/`RetryPolicy` explicitly in AF5.

**How to avoid:**
Treat a conflict as *"your decision was stale; decide again"*, not as a business outcome. Easiest and most explicit: at the edge, catch `AppendEventsTransactionRejectedException`, re-dispatch the same command once, and let the fresh decision produce the domain rejection ("course is full"). Then the demo shows `201` + `409 course is full`, and the invariant is demonstrated twice over. Document this deliberately in the design note — it is a genuine DCB design topic, and a good one to teach.

**Warning signs:**
- The loser's response body mentions "append", "consistency marker", or a stack trace.
- Two different failure modes for the same user action ("full" vs. an infra-looking error).
- No retry policy and no documentation of that choice.

**Phase to address:** Phase 5 (race demo); the error mapping in Phase 3.

---

### Pitfall M2: Tag keys and tag values drifting silently

**What goes wrong:**
A tag is `key + value.toString()`. Three silent-drift routes:

- **Key drift.** `@EventTag String courseId` derives the key `"courseId"` from the field name. Rename the field (or use `@EventTag(key = "Course")` on one event and `"courseId"` on another) and the criteria stops matching — no error, just an empty or partial stream. Axon's own docs recommend a stable, type-conveying key (`"Course"`, not `"courseId"`) for exactly this reason.
- **Value drift.** Typed identifiers are turned into tag values via `toString()`. A record `CourseId(UUID value)` inherits `toString()` → `"CourseId[value=1f3…]"`. If the criteria is built from `courseId.value().toString()`, the strings never match.
- **Case/spacing drift.** `Course` vs `course`, `c-1` vs `c1`.

All three produce the same symptom: the decision model is empty or partial, and the app behaves as if the course has no history.

**Why it happens:**
String-keyed indexing has no schema. It compiles, it runs, and the failure is a wrong number rather than an exception.

**How to avoid:**
- Tag key constants in one class (`CourseTags.COURSE = "Course"`), used by *both* `@EventTag(key = ...)` and the criteria builders.
- Explicit `@EventTag(key = ...)` rather than field-name inference.
- Typed ids: override `toString()` to return the raw value, and use the same single conversion helper (`Tags.course(id)`) everywhere.
- A test that asserts the *stored tag set* of an appended event (`thenStoredTags`-style assertion), not just the payload. This is the one assertion with no payload-side equivalent.

**Warning signs:**
- `Tag.of("courseId"` appears in more than one file.
- Criteria built with `.toString()` in some places and `.value()` in others.
- "It worked before I renamed the field."

**Phase to address:** Phase 2 (establish the tag/tag-key constants and a stored-tags assertion), audited in Phase 3–4.

---

### Pitfall M3: Renaming or repackaging an event breaks historical sourcing

**What goes wrong:**
In AF5 the event type is a `QualifiedName` derived from the class (and the criteria lists `X.class.getName()`). Moving `StudentEnrolled` to another package, renaming it, or splitting it therefore changes the name that historical events were stored under. The criteria no longer matches history: capacity appears unused, waitlist appears empty, and the invariant is violated *retroactively* — while all tests pass, because tests build their `given` events with the new class name.

AF5 stores a revision as part of the message type, so revisions are supported; class renames are not free.

**Why it happens:**
Renaming a class during a refactor is routine in Java, and nothing ties the class name to storage in a visible way. The docs' guidance ("resolve qualified names via `MessageTypeResolver` rather than constructing `QualifiedName` directly") is easy to skim past.

**How to avoid:**
For a short-lived showcase: treat event class names as frozen once written — add a comment on each event record saying so, and never reuse a name for a different shape. If a rename is unavoidable, keep the old type as a deprecated alias, add an upcaster/mapping, or wipe the in-memory store (legitimate here — say it in the README). Use the `MessageTypeResolver` when you need the name programmatically.

**Warning signs:**
- An event class in a different package after a "cleanup" commit.
- Capacity behaviour changes after a refactor with no test change (tests pass, app doesn't).
- Two classes with the same simple name in different packages.

**Phase to address:** Phase 2 (convention), enforced through Phase 6 (documented in the design note as a boundary-stability consideration).

---

### Pitfall M4: Non-idempotent drop (and latent double-promotion)

**What goes wrong:**
A double-click on "Drop" produces two `StudentDropped` events. The seat counter decrements twice, two waitlisted students are promoted, and capacity is exceeded by one. The same shape applies to double-enrol (two seats consumed by one student, if the "already enrolled" check is not part of the decision).

**Why it happens:**
The handler asserts "seat is free" but not "this student currently holds a seat". Events are facts, so appending the same fact twice is not obviously wrong at the store level.

**How to avoid:**
Every state transition that consumes or releases a seat must read its own prior occurrence as part of the decision: *"am I currently enrolled?"* / *"am I currently waitlisted?"*. Because the state is built from `StudentDropped`/`StudentEnrolled` tagged with both subjects, the second command sees the first and is rejected. Add a test that dispatches the identical drop command twice and asserts one event and one rejection.

**Warning signs:**
- Drop succeeds even when the student is not enrolled.
- `seats_taken` goes negative or capacity is exceeded after repeated clicks.
- No test sends the same command twice.

**Phase to address:** Phase 4.

---

### Pitfall M5: A stale or ineligible waitlist head

**What goes wrong:**
The head of the queue is a student who is already enrolled in the course (via a retry, a manual admin action, or a prior promotion that was retried), or who no longer exists. Promotion blindly picks the head, appends a duplicate enrolment, and either double-counts the seat or violates "one seat per student". Alternatively, promotion of a stale head is rejected and *no* seat is filled, so the queue stalls.

**Why it happens:**
FIFO semantics are implemented as "peek the oldest entry", and eligibility is assumed rather than checked — the entry was valid when it was written, and it is treated as still-true.

**How to avoid:**
Promotion is a decision, not a pointer move: iterate the queue in order, skip entries whose student already holds a seat (or is no longer eligible), promote the first eligible one. Test it with a hand-built state where the head is already enrolled.

**Warning signs:**
- Promotion code has no eligibility check.
- A course with a free seat and a non-empty waitlist stays unfilled.
- Duplicate enrolment for the same student/course in the read model.

**Phase to address:** Phase 4.

---

### Pitfall M6: Test isolation and processor catch-up in `AxonTestFixture`

**What goes wrong:**
`AxonTestFixture` runs the *real* application configuration, so events accumulate across tests and across test classes. Tests that previously passed start failing depending on execution order; parallel test execution makes it worse. Separately, the fixture does not automatically wait for event processors to catch up at phase transitions, so a test that seeds events and then queries a projection reads a stale read model and becomes intermittently red.

**Why it happens:**
The fixture's fidelity is its point — it is not a sandbox. And the catch-up behaviour is a known open issue (AxonFramework #4252), so the failure looks like your code.

**How to avoid:**
- Unique ids per test (`CourseId.random()`, `StudentId.random()`) — never a fixed `"c1"`.
- One fixture per test (`@BeforeEach` create, `@AfterEach` `stop()`), never a shared static one.
- For read-model assertions, use the fixture's awaiting/`await(...)` facilities rather than sleeping.
- For the *contention* test, do not use the fixture at all (see C10) — boot the configurer and drive the command bus directly.

**Warning signs:**
- Tests pass individually and fail as a suite, or in random order.
- A `@BeforeEach` that does not reset the store.
- Fixed literal ids in tests.
- Read-model assertions that are green on a fast machine.

**Phase to address:** Phase 2 (test conventions), revisited in Phase 5.

---

### Pitfall M7: Axon 5 version/doc drift and AF4 muscle memory

**What goes wrong:**
Days lost to APIs that no longer exist or that moved. Concretely observed:

- Documentation pages for 5.0→5.4 coexist and disagree (the in-memory DCB row in C1 is one instance), and search results mix versions freely.
- AF4 material (`@Aggregate`, `@AggregateIdentifier`, `AggregateLifecycle.apply`, `EventSourcingRepository`, `GenericCommandGateway`, `TrackingEventProcessor`, `IntervalRetryScheduler`) is still the top hit for most queries and does not compile against AF5.
- AF5 has **two** annotation sets: `@EventSourcedEntity` (configuration-API style) and `@EventSourced` (Spring Boot). Mixing them yields "no handler found"-class failures.
- Artifact coordinates split: `org.axonframework:*` (framework, Apache-2.0) vs `io.axoniq.framework:*` (Axon Server connector, PostgreSQL engine — with a production licence).
- JDK 21 is a hard baseline (Kotlin compiles to it; the language choice does not change the floor).
- The project is young enough that release notes matter (5.3.2 added tags to `AppendEventsTransactionRejectedException`; minor releases carry real behaviour changes).

**Why it happens:**
A fast-moving framework plus a large AF4 corpus. Every tutorial you find is probably AF4 unless it says 5.x.

**How to avoid:**
- Pin one doc version (5.4) and link it from the README; never read "latest" and "5.0" interchangeably.
- Pin the framework version in the build and check the release notes when bumping.
- Prefer the framework's own artifacts (the `university-demo` repository, the `dcb.events` course example) over blog posts.
- Decide the annotation style (Spring `@EventSourced`) once, in Phase 1, and write it down.

**Warning signs:**
- Compile errors mentioning classes you cannot find in the reference.
- An import from `org.axonframework.commandhandling.gateway` in new code.
- Two different annotation families in the same slice.
- The README links a URL containing `/4.` or `/5.0/`.

**Phase to address:** Phase 1 (pin versions and the doc set).

---

### Pitfall M8: The UI presenting read-model lag as a bug

**What goes wrong:**
The React SPA shows "1 seat left" (from a projection), the student clicks Enrol, and gets "course is full". Or the seat count does not update after a successful enrol because the projector has not caught up. Presented without framing, this reads as a defect — in the one app whose lesson is that the write side is strongly consistent and the read side is not.

**Why it happens:**
The demo has two consistency models by design, and the UI naturally presents them as one.

**How to avoid:**
Make the asymmetry explicit and visible: after a successful enrol, refetch the course; label the seat count as "last updated"; and put one paragraph in the README explaining that the *decision* is immediate while the *display* lags. Ideally, have the API return the authoritative seats-remaining in the command response so the UI can reconcile. Do not "fix" it by reading the projection in the handler (that is C11).

**Warning signs:**
- The UI does optimistic updates without reconciling.
- Someone proposes making the command wait for the projector.
- A UAT note saying "seat count sometimes wrong".

**Phase to address:** Phase 3 (API shape), Phase 5/6 (narrative).

---

### Pitfall M9: SPA + seeded login friction eating the schedule

**What goes wrong:**
The learning goal is DCB; the time goes to Spring Security defaults. Typical: `401`/`403` on `POST` because CSRF is enabled and the SPA sends no token; CORS failures between the Vite dev server and the API; session cookie not sent cross-origin; the seeded user not being created because seeding runs in the wrong lifecycle phase; a login page that returns HTML instead of JSON so `fetch` parsing fails.

**Why it happens:**
Generic web plumbing, but it blocks the *first* end-to-end run, which is exactly when momentum matters.

**How to avoid:**
Solve it in one sitting in Phase 1 and freeze it: form login or a simple session cookie, CSRF disabled for the `/api/**` JSON endpoints (documented as a deliberate showcase simplification), a `@CrossOrigin`/proxy configuration for dev, an idempotent seeder keyed on the seeded user id. Then never touch it again.

**Warning signs:**
- More than a day spent on auth for a single-user demo.
- Requests failing with `302` to `/login` where JSON was expected.
- Seeding logic entangled with domain slices.

**Phase to address:** Phase 1.

---

## Minor Pitfalls

### Pitfall m1: Over-wide tags turn the showcase into a single-threaded app
Tagging enrolment events with a coarse shared value (`term`, `catalog`, `all`) makes every enrolment conflict with every other. Correct, and useless: the "dynamic" boundary now behaves like one global lock. Prevention: tags identify *subjects involved in the constraint* (the course, the student), never context. Warning sign: an unrelated course's enrolment latency moves when one course is busy.

**Phase:** Phase 3.

### Pitfall m2: Sourcing a whole course history when a running count suffices
The state only needs counts and a queue head. Folding every `StudentEnrolled` for a popular course on every command is fine at demo scale and will quietly become the reason the app is slow — and it invites the wrong lesson ("DCB is slow"). Prevention: fold into counters in the `@EventSourcingHandler` (as the reference implementation does), keep the criteria narrow by type, and say in the design note that this is a projection *inside* the decision.

**Phase:** Phase 3.

### Pitfall m3: Pre-emptive snapshotting
Snapshots are a performance feature with their own consistency story (and with a DCB store the snapshot store should ideally be the same backend). At demo scale they add configuration surface and one more way to be wrong. Prevention: don't; if you must discuss them, put it in "what I deliberately didn't do".

**Phase:** out of scope; mention in the design note.

### Pitfall m4: Annotation noise at the boundary
A comment like `// this is the dynamic consistency boundary` above a criteria that omits `CourseCapacityChanged` teaches the wrong thing: it says the boundary is *declared*, not *derived from the invariant*. Prevention: the annotation must state the invariant and the events that can break it — "capacity can change, so this query must include `CourseCapacityChanged`" is useful; "DCB here" is not.

**Phase:** Phase 6.

### Pitfall m5: Optimistic UI that hides the rejection
If the React app removes the seat before the command returns, the student never sees the rejection that is the whole demo. Prevention: render the server's outcome; on rejection show the domain reason verbatim.

**Phase:** Phase 3.

### Pitfall m6: Seeded data that recreates the boundary conditions by accident
Seeding "capacity 3, 3 enrolments" then demonstrating "course is full" is fine; seeding 50 events through a projector-only path (bypassing commands) means the events exist but with the wrong tags, and every later criterion fails mysteriously. Prevention: seed **through commands** whenever possible; if you seed raw events, assert their tags.

**Phase:** Phase 1/2.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|---|---|---|---|
| In-memory event store, no durability | Zero setup, single JVM, meets "no Docker" | Events lost on restart; must reseed every boot; guarantee is JVM-local | Acceptable for this showcase **if** the append-condition spike passes and the README states the scope |
| `@Transactional` on command handlers | Looks safe, familiar | Suggests the DB is the guard; masks the absence of an append condition; can wrap events in a second transaction | Never for a handler that makes a DCB decision |
| Reading the projection for seat counts "just for the check" | One query, fast | Reintroduces oversell; destroys the project's thesis | Never |
| No retry on append conflict | Less code | Loser sees a 500 instead of "course is full"; demo looks broken | Only if the README/design note explains it, and the API maps it to a clear 409 |
| Promotion as an event-handler automation | Cleaner-looking slices | Lost atomicity, double promotions, eventual capacity violations | Never for the seat invariant; acceptable for genuinely soft side effects (notification, email) |
| Fixed test ids (`"c1"`) | Faster tests | Cross-test pollution; order-dependent failures | Never (fixture uses the real store) |
| Hardcoded per-student course limit, no config | One less thing | Fine here — the reference demo does the same | Acceptable |
| Tagging with field-name inference (`@EventTag String courseId`) | Less typing | Key drifts on rename; criteria silently stops matching | Only with frozen field names and a stored-tags test |
| Skipping the negative control in the race test | Saves an hour | You cannot tell a working boundary from a serialized one | Never |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|---|---|---|
| Axon 5 + Spring Boot | Mixing `@EventSourcedEntity` (config API) with `@EventSourced` (Spring) | Pick the Spring annotation family in Phase 1; register the entity via the Spring path and keep slices consistent |
| Axon 5 + Spring Boot | Expecting AF4 auto-configuration (`@Aggregate` scanning, `Configurer`) | AF5 uses `ApplicationConfigurer`/`EventSourcingConfigurer`; wire slices explicitly (`CommandHandlingModule.named(...)`) |
| Embedded store | Assuming the in-memory engine behaves like a persistent DCB store across restarts | Reseed on boot; state the scope in the README; verify conflict detection with the Phase 1 spike |
| Embedded store | Assuming the documented "DCB support: No" row is accurate (or that "Yes" is enough) | Prove it with the two-thread test; trust the executable check over the docs table |
| Retry on conflict | Catching `AppendEventsTransactionRejectedException` and retrying the *append* | Retry the whole *command* so the decision is re-sourced |
| `@InjectEntity` | Forgetting it and appending "directly" | Every decision handler sources; assert one append condition per append |
| Value objects in events | `record CourseId(UUID v)` used as `@EventTag` without `toString()` override | Override `toString()` to the raw value; centralise tag construction |
| React dev server ↔ API | CORS/CSRF/session-cookie mismatch | Vite proxy in dev, explicit CORS config, CSRF disabled for JSON API (documented) |
| Read model | One JPA entity per projection reused for the command side | Keep write and query packages apart; enforce with an ArchUnit rule |
| JSON serialisation of events | Renaming record components breaks stored payloads | Freeze event shapes; treat them as published contracts |

---

## Performance Traps

At single-node demo scale these are mostly about *not mistaking a design bug for a performance bug*.

| Trap | Symptoms | Prevention | When It Breaks |
|---|---|---|---|
| Course-wide serialization (via routing key or entity id) | One enrolment at a time per course; latency scales linearly with class size | Routing key per student; composite decision id | Immediately, and it is the bug that matters most here |
| Over-wide tags | Unrelated courses contend | Tag only the subjects in the constraint | As soon as two courses are used concurrently |
| Sourcing entire course history per command | Handler time grows with enrolment count | Fold into counters; filter by type | ~thousands of enrolments per course (not a concern, but it will look like "DCB is slow") |
| One record per waitlist mutation with no index | Waitlist scan per drop | Keep it in the decision state; at demo scale a list is fine | Thousands of waitlisted students |
| In-memory engine with `ConcurrentSkipListMap` + one `ReentrantLock` per commit | Throughput plateaus under load | Expected and acceptable; note it in the design note | Not a concern at demo scale; relevant only if you claim production properties |

---

## Security Mistakes

Domain-specific, and mostly about keeping the showcase's scope honest.

| Mistake | Risk | Prevention |
|---|---|---|
| Putting personal data in tags (email as the student tag, username as a key) | Tags are indexed and visible in the store/UI; PII leaks into an immutable log | Tag with opaque ids only; the DCB guidance explicitly says hash/anonymise |
| Seeded credentials committed as if real | Confusion for anyone reusing the repo | Mark them clearly as demo-only in README, and disable all real auth flows |
| Admin endpoints (create course) unauthenticated | Anyone can change capacity in a public deployment | Out of scope per `PROJECT.md`, but say so explicitly rather than leaving it implicit |
| Trusting client-supplied ids/capacity | Negative or zero capacity, enrolment as another student | Validate at the API edge (Bean Validation); the domain should still reject nonsensical capacity |
| Logging full event payloads with student data | Noise + PII in logs | Log ids and event types |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---|---|---|
| Seat count shown as authoritative | User is surprised by "course is full" | Label it as a snapshot; refetch after commands; explain the asymmetry in the README |
| Rejection shown as a generic error toast | The teaching moment is lost | Show the domain message verbatim ("Course is full — you can join the waitlist") |
| Waitlist position not shown | FIFO is invisible, so the promotion demo is confusing | Show position and update it after drops |
| No path from "full" to "waitlist" | User hits a dead end | Offer waitlist join in the rejection response |
| Optimistic UI removing the seat before commit | Rejection is never rendered | Render server outcome |
| No way to reproduce the race from the UI | The core demo only exists in a test | Provide a "two students, one seat" button/endpoint, or document the exact two-tab procedure |

---

## "Looks Done But Isn't" Checklist

- [ ] **Seat cap:** appears enforced — verify by *not* using the UI: two concurrent `curl` enrolments for the last seat must yield exactly one `201`.
- [ ] **Append condition exists:** grep for `@InjectEntity` in every decision handler; grep for `withoutCondition` (expect none). Confirm a forced conflict throws `AppendEventsTransactionRejectedException`.
- [ ] **Boundary is real:** the decision model is built from an `EventCriteria` that names *event types*, not just tags; and it includes `CourseCapacityChanged` and the drop event.
- [ ] **Boundary is not an aggregate:** the entity id is not `CourseId`; the routing key is not shared by all enrolments; state classes are named after decisions.
- [ ] **Tags complete:** every event that changes capacity carries the course tag; a mixed enrol/drop contention test conflicts correctly.
- [ ] **FIFO:** promotion order equals join order when timestamps collide; order comes from stream position.
- [ ] **Promotion atomicity:** drop + promotion are appended together, or the automation re-checks capacity; no `await` needed in tests.
- [ ] **Idempotency:** duplicate drop is rejected; duplicate enrol is rejected.
- [ ] **Race demo fails when it should:** the negative control (broken criteria / missing `@InjectEntity`) makes the contention test red — run this experiment, don't assume it.
- [ ] **Loser's response is a domain rejection**, not a 500 and not an internal exception name.
- [ ] **Docs pinned to one version**; no AF4 imports; README links only 5.x pages.
- [ ] **Annotations explain the invariant**, including which event types are deliberately excluded and why.
- [ ] **Design note shows the counterfactual:** what breaks with a fixed aggregate boundary, and what breaks with a narrower one.
- [ ] **Reseed on restart** stated explicitly, since the store is in memory.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---|---|---|
| Store does not enforce append conditions (C1) | MEDIUM | Switch engine (local PostgreSQL via `PostgresqlEventStorageEngine`, or Axon Server); re-run the two-thread spike; update `PROJECT.md` constraint and README |
| Boundary built on tags only, missing event types (C2) | LOW | Extend the criteria, add the missing `@EventSourcingHandler`s, add the "enrol after drop" test |
| Wrong `EventCriteria` boolean nesting (C3) | LOW | Split into `either(...)` per subject; add the "sees other students" test |
| Aggregate shape via id/routing key (C4) | MEDIUM | Introduce a decision-scoped id type; re-derive criteria; re-run contention tests; update the design note |
| Missing append condition (C5) | LOW | Add `@InjectEntity`, remove gateway appends, add the forced-conflict test |
| Race serialized by command sequencing (C6) | LOW | Change routing key to per-student; add the max-in-flight assertion |
| Missed tag on drop/promotion (C7) | LOW–MEDIUM | Add tags; recompute the event→tags table; run the mixed contention test; historical events may need a reseed |
| Promotion as an automation (C8) | MEDIUM | Move the decision into the drop handler (single append); delete the automation; rerun contention tests |
| FIFO by timestamp (C9) | LOW | Sort by read order; add the collision test |
| Race test cannot fail (C10) | LOW | Replace with barrier-driven harness + negative control; keep both the passing and failing variants in the repo |
| Projection as guard (C11) | LOW–MEDIUM | Remove the read from the handler; add ArchUnit rule; retest under load |
| Tag/`toString` drift (M2) | LOW | Centralise tag construction; add stored-tag assertions; reseed if historical tags are wrong |
| Event renamed (M3) | MEDIUM | Alias/upcast, or reseed (in-memory); freeze names going forward |

---

## Pitfall-to-Phase Mapping

Indicative phase model: **1** walking skeleton (Axon 5 + store spike + Spring Boot + seeded login + React shell) → **2** course creation/admin → **3** enrol under capacity → **4** drop + FIFO waitlist → **5** last-seat race demo and narrated contention tests → **6** teaching artifacts (README, boundary annotations, design note). Remap by topic if the roadmap differs.

| Pitfall | Prevention Phase | Verification |
|---|---|---|
| C1 store may not enforce append conditions | Phase 1 | Two-thread spike: exactly one commit, one `AppendEventsTransactionRejectedException`; recorded in README |
| C2 criteria omits event types | Phase 3 (and 4) | "enrol after drop", "capacity decrease blocks enrol", "drop frees one seat" tests |
| C3 `EventCriteria` boolean semantics | Phase 3 | Test seeding another student's enrolment; assert the model sees it |
| C4 aggregate shape via id/routing key | Phase 3 design; audit Phase 6 | Design note states the decision id and why; routing key per student; max-in-flight > 1 |
| C5 no sourcing → no condition | Phase 3 | Forced-conflict test throws; grep finds no `withoutCondition` |
| C6 sequencing masks the race | Phase 5 | Max-in-flight assertion; latencies overlap |
| C7 missing subject tags | Phase 3–4 | Mixed enrol/drop contention conflicts; event→tags table in design note |
| C8 promotion outside the boundary | Phase 4 | No automation in the promotion path; no `await`/sleep in seat tests; two concurrent drops cannot oversubscribe |
| C9 FIFO by timestamp | Phase 4 | Colliding-timestamp promotion test asserts join order |
| C10 race test cannot fail | Phase 5 | Negative control fails as designed; 20–50 iterations stable; no `Thread.sleep` |
| C11 projection as guard | Phase 3; audited per slice | ArchUnit rule (write package ↛ query package) |
| M1 conflict vs. business rejection | Phase 5 (mapping Phase 3) | Loser returns the domain rejection with a 4xx, not a 500 |
| M2 tag key/value drift | Phase 2 | Stored-tag-set assertion; tag constants used by both annotation and criteria |
| M3 event rename breaks history | Phase 2 convention; Phase 6 note | Event names frozen; no cross-package moves after first release |
| M4 double drop / double enrol | Phase 4 | Duplicate-command test: one event, one rejection |
| M5 stale waitlist head | Phase 4 | Head-already-enrolled scenario promotes the next eligible student |
| M6 fixture isolation/catch-up | Phase 2 (conventions), Phase 5 | Suite passes in random order; no fixed ids; awaiting for read-model assertions |
| M7 version/doc drift | Phase 1 | Versions pinned; README links only 5.x; no AF4 imports |
| M8 read-model lag in UI | Phase 3 (API), Phase 6 (narrative) | Rejection reason rendered; README explains the asymmetry |
| M9 SPA/login friction | Phase 1 | One-click login works before any domain slice exists |
| m1 over-wide tags | Phase 3 | Two courses enrol concurrently without conflict |
| m2 sourcing whole history | Phase 3 | Counters folded in handlers; criteria filtered by type |
| m3 premature snapshots | — | Absent, and named as a non-goal |
| m4 annotation noise | Phase 6 | Each boundary annotation names the invariant and the included/excluded event types |
| m5 optimistic UI hides rejection | Phase 3 | Rejection visible in the UI |
| m6 seeding bypasses commands | Phase 1–2 | Seeded events carry expected tags (asserted) |

---

## Sources

**Primary (official, 5.4 docs — fetched directly):**
- Axon Framework 5.4 reference, *Event Store Internals* — DCB, tags, `EventCriteria`, append/source/stream conditions, consistency marker.
- Axon Framework 5.4 reference, *Events → Infrastructure* — storage engines and DCB support; `PostgresqlEventStorageEngine` as the default without Axon Server; in-memory engine scope.
- Axon Framework 5.4 reference, *Migration → Event Store* — storage-engine DCB support table (in-memory listed as "No", 5.0–5.3).
- Axon Framework 5.4 reference, *Testing* / *Test Fixture Migration* — `AxonTestFixture`, `asIntegrationTest()`, `disableAxonServer()`.
- Axon Framework 5.4 reference, *Tuning → Command Processing* and *Sequencing Policy Migration* — `CommandSequencingInterceptor`, `RoutingKeySequencingPolicy` default, `NoOpSequencingPolicy`.
- Axon Framework 5.4 reference, *Commands → Infrastructure* — retry policies (`RetryPolicy`, `MaxAttemptsPolicy`, `FilteringRetryPolicy`, `AsyncRetryScheduler`).
- Axon Framework 5.4 reference, *Event Tagging with @EventTag* — one tag per event without DCB; `toString()` for tag values; stable, type-conveying tag keys.
- Axon Framework 5.4 reference, *Event-Sourced Entities* — `@EventSourcedEntity` vs `@EventSourced`, `tagKey`, `@EventCriteriaBuilder`, declarative `criteriaResolver`.
- AxonIQ Getting Started (AF5), *Feature: Subscribe Student* — composite `SubscriptionId` + `@TargetEntityId`, `@EventCriteriaBuilder`, and the explicit warning about `either` vs. `havingTags(a, b)`.
- API docs (5.4.1): `EventCriteria`, `AppendCondition`, `EventSourcedEntity`, `AppendEventsTransactionRejectedException`, `PostgresqlEventStorageEngine`.

**Primary (framework source):**
- `org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine` (main branch) — append-condition evaluation, `ReentrantLock` around commit, `ConsistencyMarker.INFINITY` short-circuit.

**Domain references:**
- `dcb.events` specification (query, tags, append condition, sequence position).
- `dcb.events` *Course subscriptions* example — the canonical shape of this app, including which tags each event carries.
- `dcb.events` *Tags* topic — missing events break invariants; too many events kill parallelism; re-tagging caveats; tags must be derivable from the payload; avoid PII.
- `dcb.events` FAQ — DCB does not imply weak consistency; tags are semantic, not indexes.
- AxonIQ `university-demo` (GitHub) — reference implementation and its in-memory/Axon Server switching.

**Community / issue tracker:**
- AxonFramework issue #4252 — `AxonTestFixture` processor catch-up at phase transitions; test-to-test event accumulation.

**Confidence notes:**
- API semantics, the criteria boolean rules, the append-condition derivation, and command sequencing: **HIGH** (primary docs + source, mutually consistent).
- Embedded-store guarantee: **MEDIUM** — docs table and framework source disagree; resolve with the Phase 1 spike before committing the architecture.
- Event-type rename consequences: **MEDIUM** — follows from `QualifiedName`-based types and revision support, not from an explicit "don't rename" statement.

---

*Pitfalls research for: Axon 5 DCB course-enrolment showcase*
*Researched: 2026-11-10*
