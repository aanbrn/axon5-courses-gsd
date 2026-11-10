# Architecture Research

**Domain:** Axon Framework 5 Dynamic Consistency Boundary (DCB) showcase — course enrollment with seat capacity, FIFO waitlist, React SPA
**Researched:** 2026-11-10
**Confidence:** MEDIUM (structure and DCB mechanics read from official AxonIQ docs + the official `AxonIQ/university-demo` source; the Spring Boot wiring for a composite-ID decision entity and the no-Axon-Server in-memory store are documented but less trodden — flagged as verification items)

## Standard Architecture

Axon Framework 5 has an opinionated answer, and the project should follow it almost exactly. The reference is AxonIQ's own `university-demo`, whose README states the design rule plainly: organized around **vertical slices** (`events`, `write`, `read`, `automation`), where "each slice can have its own entities based on the same events, and thus be totally independent." That is precisely this project's thesis, and the domain (courses, capacity, students subscribing) is nearly identical.

### System Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│ BROWSER — React SPA (Vite, :5173 dev / static in prod)                    │
│  ┌─────────────┐ ┌──────────────┐ ┌───────────────┐ ┌─────────────────┐  │
│  │ Login page  │ │ Course list  │ │ Course detail │ │ Race demo panel │  │
│  │ (session)   │ │ + seats left │ │ enroll/drop/  │ │ "fire N at once"│  │
│  │             │ │              │ │ waitlist      │ │                 │  │
│  └──────┬──────┘ └──────┬───────┘ └───────┬───────┘ └────────┬────────┘  │
│         └───────────────┴─────────────────┴──────────────────┘           │
│                     fetch('/api/…', credentials:'include')               │
└───────────────────────────────────┬──────────────────────────────────────┘
                                    │ HTTP/JSON
┌───────────────────────────────────┴──────────────────────────────────────┐
│ HTTP ADAPTER (Spring MVC)                                                 │
│  SecurityConfig (in-memory users, session cookie, CSRF)                   │
│  CourseController · EnrollmentController · DemoRaceController             │
│  ApiExceptionHandler  ← maps domain rejections + append conflicts         │
└───────────────────────────────────┬──────────────────────────────────────┘
                     CommandGateway.send()   QueryGateway.query()
                                    │
┌───────────────────────────────────┴──────────────────────────────────────┐
│ APPLICATION / MESSAGING (Axon)                                            │
│  CommandBus (SimpleCommandBus) · QueryBus · Command/Query dispatch        │
└───────────────┬───────────────────────────────────────┬──────────────────┘
                │                                       │
┌───────────────┴───────────────────────┐ ┌─────────────┴────────────────┐
│ WRITE SIDE — command slices            │ │ READ SIDE — projections       │
│  write/createcourse/                   │ │  read/courselist/             │
│  write/enrollstudent/                  │ │   @EventHandler → repo        │
│  write/joincoursewaitlist/             │ │  read/waitlist/               │
│  write/dropenrollment/                 │ │  read/mystudentseats/         │
│  write/changecoursecapacity/           │ │  @QueryHandler → repo         │
│    each = Command record               │ │  (pooled streaming processor) │
│         + CommandHandler               │ └─────────────┬────────────────┘
│         + @EventSourced decision State │               │ reads
│         + @EventCriteriaBuilder  ◄─────┼── THE BOUNDARY│
└───────────────┬────────────────────────┘               │
                │ append(TaggedEventMessage, AppendCondition)
┌───────────────┴────────────────────────────────────────┴─────────────────┐
│ EVENT STORE — StorageEngineBackedEventStore                              │
│   InMemoryEventStorageEngine (DCB-capable, single JVM, no Docker)         │
│   events carry @EventTag(tags) · ConsistencyMarker · AppendCondition      │
│   source(criteria) ──► events + marker ──► decide ──► append(condition)  │
└──────────────────────────────────────────────────────────────────────────┘
        ▲
        │ commands at startup (never direct appends)
┌───────┴──────────────────────────────────────────────────────────────────┐
│ SEEDING — ApplicationRunner dispatching CreateCourse / EnrollStudent /    │
│ JoinWaitlist through CommandGateway                                      │
└──────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Owns | Typical implementation |
|---|---|---|
| `enrollment/events/*` | The event contract. Every fact, with its `@EventTag`s and payload. | Java records; one record per file; `@EventTag(key = "courseId") CourseId courseId` etc. |
| `write/<slice>/` | One use case: the command record, the handler's `decide(...)` rules, the private decision state, and the `@EventCriteriaBuilder` that names the boundary | `@Component` handler class + nested (or top-level) `@EventSourced` state class |
| `@EventCriteriaBuilder` | **The dynamic boundary itself** — tags + event types this decision is allowed to see | static method returning `EventCriteria`, usually `EventCriteria.either(...)` |
| `read/<slice>/` | A projection (`@EventHandler`), its read-model record, its repository, its `@QueryHandler` | plain Spring beans + in-memory `ConcurrentHashMap` repository |
| REST controllers | Translate HTTP ↔ Axon messages; no business rules | `@RestController` calling `CommandGateway` / `QueryGateway` |
| `ApiExceptionHandler` | Map Axon/domain failures to HTTP (409 vs 400 vs 422) | `@RestControllerAdvice` |
| `AxonConfig` | Override the event store to in-memory; nothing else | `@Bean EventStorageEngine` |
| `SecurityConfig` + seed users | Seeded login, session cookie, role ADMIN/STUDENT, principal → `studentId` mapping | Spring Security `InMemoryUserDetailsManager` |
| `DataSeeder` | Seeded courses + waitlist so the app is demonstrable on first boot | `ApplicationRunner` → `CommandGateway.sendAndWait(...)` |
| `frontend/` | UI only; no domain logic, no event knowledge | Vite + React + TS, thin `api.ts` client |
| `DemoRaceController` | Fire N concurrent enroll commands and report the tally | `CommandGateway.send(...)` × N, `CompletableFuture` join |

### The DCB decision pipeline (the one diagram the README should reuse)

```
command
  │
  ├─► @EventCriteriaBuilder(id)        ← the boundary is CHOSEN here, per slice
  │        criteria = tags(courseId) ∩ types(CourseCreated, StudentEnrolled, …)
  │
  ├─► eventStore.source(criteria)      → [events…] + ConsistencyMarker
  │
  ├─► State.evolve(each event)         → decision model (capacity, seatsTaken, …)
  │
  ├─► assert invariants                 → throw / return no events
  │
  └─► eventAppender.append(events)
           └─ AppendCondition(criteria, marker)
                    ├─ store unchanged since marker → events stored
                    └─ matching event appeared after marker → AppendEventsTransactionRejectedException
```

The key property to teach: **the same `EventCriteria` is both the read filter and the write-conflict definition.** Narrow it as far as the invariant allows. This is why the seat cap can be enforced without loading a student's full history — and why two concurrent enrollments collide exactly once.

### Suggested boundaries, per command (the core design artifact)

All commands are in one bounded context, `enrollment`. Tags: `courseId`, `studentId`.

| Command | Sourced by | Event types the decision may see | Invariant it proves |
|---|---|---|---|
| `CreateCourse` | `courseId` | `CourseCreated` | no duplicate course |
| `ChangeCourseCapacity` | `courseId` | `CourseCreated`, `CourseCapacityChanged`, `StudentEnrolled`, `StudentDropped`, `WaitlistPromoted` | cannot shrink below seats already taken |
| `EnrollStudent` | `courseId` | `CourseCreated`, `CourseCapacityChanged`, `StudentEnrolled`, `StudentDropped`, `WaitlistPromoted` | `seatsTaken < capacity`; not already enrolled; not already waitlisted |
| `JoinCourseWaitlist` | `courseId` | `CourseCreated`, `CourseCapacityChanged`, `StudentEnrolled`, `StudentDropped`, `WaitlistJoined`, `WaitlistPromoted` | only waitlist when full; not a duplicate entry |
| `DropEnrollment` | `courseId` | `CourseCreated`, `StudentEnrolled`, `StudentDropped`, `WaitlistJoined`, `WaitlistPromoted` | dropper is enrolled; exactly one FIFO promotion |

Three teaching points fall out of this table and belong in the design note:

1. **Same tag, different type set.** Every slice filters on `courseId` alone, yet each includes a different subset of event types. The boundary is dynamic because the *criteria* is per-decision, not because the tag changes.
2. **Tags select; payloads decide.** `StudentEnrolled` is tagged with both `courseId` and `studentId`, but `EnrollStudent` only asks for `courseId`. The "already enrolled?" check still works because the event *payload* carries the student — tags define scope, not data.
3. **Widening later costs nothing.** Adding "a student may hold at most N seats" means adding `EventCriteria.havingTags(Tag.of("studentId", …))` to the `either(...)` union in that one `@EventCriteriaBuilder`. No migration, no aggregate redesign — the exact argument the design note must make.

### Recommended Project Structure

```
axon5-showcase/
├── pom.xml                                  # single Maven module, Java 21, Spring Boot 3.5+/4
├── README.md                                # the end-to-end trace
├── docs/
│   ├── design-note.md                       # why the boundary is dynamic
│   └── boundary-map.md                      # the table above, annotated
├── src/main/java/dev/showcase/enrollment/
│   ├── EnrollmentApplication.java
│   ├── config/
│   │   ├── AxonConfig.java                  # @Bean EventStorageEngine = InMemoryEventStorageEngine
│   │   ├── SecurityConfig.java              # seeded users, session, CSRF cookie
│   │   └── CurrentStudent.java              # principal → studentId resolution
│   ├── events/                              # THE CONTRACT — everything depends on this
│   │   ├── CourseCreated.java
│   │   ├── CourseRenamed.java
│   │   ├── CourseCapacityChanged.java
│   │   ├── StudentEnrolled.java             # tags: courseId + studentId
│   │   ├── StudentDropped.java              # tags: courseId + studentId
│   │   ├── WaitlistJoined.java              # tags: courseId + studentId
│   │   └── WaitlistPromoted.java            # tags: courseId + studentId
│   ├── shared/
│   │   ├── ids/{CourseId,StudentId}.java
│   │   └── errors/DomainRejection.java      # sealed set of business rule failures
│   ├── write/
│   │   ├── createcourse/{CreateCourse, CreateCourseHandler}.java
│   │   ├── changecoursecapacity/{…}.java
│   │   ├── enrollstudent/
│   │   │   ├── EnrollStudent.java
│   │   │   ├── EnrollStudentHandler.java     # decide(...) + assertions
│   │   │   └── EnrollmentDecision.java       # @EventSourced + @EventCriteriaBuilder  ◄ ANNOTATE ME
│   │   ├── joincoursewaitlist/{…}.java
│   │   └── dropenrollment/
│   │       ├── DropEnrollment.java
│   │       ├── DropEnrollmentHandler.java
│   │       └── CourseSeatingState.java       # wider boundary: seats + FIFO queue
│   ├── read/
│   │   ├── courselist/{CourseListProjection, CourseListRepository, CourseListItem, FindCoursesHandler}.java
│   │   ├── waitlist/{WaitlistProjection, WaitlistRepository, FindWaitlistHandler}.java
│   │   └── mystudentseats/{MySeatsProjection, MySeatsRepository, FindMySeatsHandler}.java
│   ├── seed/
│   │   ├── DataSeeder.java                  # ApplicationRunner → CommandGateway
│   │   └── SeedUsers.java
│   └── web/
│       ├── CourseController.java
│       ├── EnrollmentController.java
│       ├── DemoRaceController.java
│       ├── dto/*.java
│       └── ApiExceptionHandler.java
├── src/main/resources/
│   ├── application.yml                       # axon.axonserver.enabled=false, processor props
│   └── static/                               # built React app (prod) — or use vite proxy in dev
├── src/test/java/dev/showcase/enrollment/
│   ├── write/enrollstudent/EnrollStudentTest.java          # AxonTestFixture given/when/then
│   ├── write/dropenrollment/DropEnrollmentTest.java        # FIFO + two-event transaction
│   ├── read/courselist/CourseListProjectionTest.java
│   └── rac/LastSeatRaceIT.java                             # concurrency, exactly-one-winner
└── frontend/
    ├── package.json · vite.config.ts        # proxy: '/api' → http://localhost:8080
    └── src/
        ├── api.ts                           # the only place that knows URLs
        ├── App.tsx · pages/{Login,Courses,CourseDetail}.tsx
        └── components/{SeatBadge,WaitlistTable,RacePanel}.tsx
```

### Structure Rationale

- **`events/` before anything else.** Events are the contract between every slice; AxonIQ calls them "the system backbone." Writing them first (with their tags) forces the boundary conversation before any handler code exists.
- **Feature folders, not layers.** `write/enrollstudent/` holds the command, the rule, the state, and the boundary for one use case. There is no `service/` or `domain/` folder to hunt through — the folder names are the feature list.
- **One state class per slice, never shared.** Two slices reading the same events can hold different fields. Sharing an entity class re-couples slices and silently widens boundaries.
- **`read/` is genuinely separate.** Projections never talk to write handlers; they only see events. Controllers never touch the event store.
- **`web/` is disposable.** Every controller is a 3-line adapter. If the REST shape changes, no domain code moves.
- **`seed/` uses commands, not `EventAppender`.** Startup seeds must exercise the same handlers (and produce the same events) a user would; direct appends would bypass invariants and make the seeded state a lie.

## Architectural Patterns

### Pattern 1: Stateful command handler + private decision state (the slice)

**What:** A `@Component` class with exactly one `@CommandHandler` method. It receives the entity/decision state via `@InjectEntity`, runs `decide(...)`, and appends. The state class is private to the slice.
**When to use:** Every command in this app. This is the canonical Axon 5 vertical-slice shape.
**Trade-offs:** More files than putting handlers on an entity; in exchange each slice is independently testable and independently re-boundarizable.

```java
@Component
class EnrollStudentHandler {

    @CommandHandler
    void handle(EnrollStudent command,
                @InjectEntity EnrollmentDecision decision,
                EventAppender appender) {
        appender.append(decide(command, decision));
    }

    private List<StudentEnrolled> decide(EnrollStudent c, EnrollmentDecision d) {
        if (!d.courseExists())            throw new DomainRejection("course.unknown");
        if (d.alreadyEnrolled(c.studentId())) throw new DomainRejection("already.enrolled");
        if (d.seatsFree() <= 0)           throw new DomainRejection("course.full");
        return List.of(new StudentEnrolled(c.courseId(), c.studentId()));
    }
}
```

### Pattern 2: `@EventCriteriaBuilder` as the named, annotated boundary

**What:** A static method on the decision state that returns the `EventCriteria` for that slice. It is the single place where the consistency boundary is chosen — therefore the single place to annotate for the project's "code annotations mark where the boundary is chosen" requirement.
**When to use:** Whenever the default (entity id → tag) is not exactly right, and always here so the boundary is explicit and readable.
**Trade-offs:** Hand-writing criteria risks being too broad (false conflicts, no lesson) or too narrow (missed invariant). Mitigate with a comment per event type explaining why it is in the set.

```java
@EventSourced(tagKey = "courseId")            // Spring Boot stereotype
class EnrollmentDecision {

    private CourseId courseId;
    private int capacity;
    private int seatsTaken;
    private final Set<StudentId> enrolled = new HashSet<>();

    @EntityCreator EnrollmentDecision() {}

    @EventSourcingHandler void evolve(CourseCreated e)          { courseId = e.courseId(); capacity = e.capacity(); }
    @EventSourcingHandler void evolve(CourseCapacityChanged e)  { capacity = e.capacity(); }
    @EventSourcingHandler void evolve(StudentEnrolled e)        { seatsTaken++; enrolled.add(e.studentId()); }
    @EventSourcingHandler void evolve(WaitlistPromoted e)       { seatsTaken++; enrolled.add(e.studentId()); }
    @EventSourcingHandler void evolve(StudentDropped e)         { seatsTaken--; enrolled.remove(e.studentId()); }

    // ═══ THE DYNAMIC CONSISTENCY BOUNDARY FOR EnrollStudent ═══
    // tag: this course only. Types: exactly those that can change seat count
    // or this student's membership. CourseRenamed is deliberately absent —
    // renaming a course must not invalidate an enrollment decision.
    @EventCriteriaBuilder
    static EventCriteria boundary(EnrollmentKey key) {
        return EventCriteria
                .havingTags(Tag.of("courseId", key.courseId().toString()))
                .andBeingOneOfTypes(
                        CourseCreated.class.getName(),
                        CourseCapacityChanged.class.getName(),
                        StudentEnrolled.class.getName(),
                        WaitlistPromoted.class.getName(),
                        StudentDropped.class.getName());
    }
}
```

`EventCriteria.either(courseCriteria, studentCriteria)` is the escape hatch when a rule genuinely spans two subjects — e.g. a future "max N seats per student". The union of the two per-tag selections becomes the boundary, and the append condition covers both.

### Pattern 3: One decision, several events, one append (drop + FIFO promotion)

**What:** `DropEnrollment` decides both the release and the promotion in the same handler, appending `StudentDropped` + `WaitlistPromoted` in a single append with a single append condition.
**When to use:** Whenever two facts must be true together. Here, "a seat is free but nobody was promoted" must never be observable.
**Trade-offs:** The slice needs the waitlist in its decision model, so its boundary includes `WaitlistJoined` — wider than `EnrollStudent`'s. That asymmetry is the lesson, not a defect. The alternative (an `automation/` slice reacting to `StudentDropped` and dispatching `PromoteFromWaitlist`) is more decoupled but eventual, can double-promote under concurrency, and hands the capacity invariant to a second actor.

```java
private List<Object> decide(DropEnrollment c, CourseSeatingState s) {
    if (!s.isEnrolled(c.studentId())) throw new DomainRejection("not.enrolled");
    var next = s.waitlistHead();                       // FIFO by arrival: fold order
    return next
        ? List.of(new StudentDropped(c.courseId(), c.studentId()),
                  new WaitlistPromoted(c.courseId(), next.get()))
        : List.of(new StudentDropped(c.courseId(), c.studentId()));
}
```

FIFO needs no explicit position field: the store returns matching events in order, so folding `WaitlistJoined` in order and appending to a queue gives arrival order for free.

### Pattern 4: Projections as independent read slices

**What:** One `@EventHandler` class per screen-shaped read model, writing to its own repository, served by its own query handler.
**When to use:** Always. Three read models cover the whole app: course list (with seats left), waitlist per course, my seats per student.
**Trade-offs:** N read models means N replay costs and eventual consistency. At this scale both are irrelevant; the separation is what makes "the write side never reads the read side" true.

```java
@Component
class CourseListProjection {
    private final CourseListRepository repo;
    CourseListProjection(CourseListRepository repo) { this.repo = repo; }

    @EventHandler void on(CourseCreated e)       { repo.save(new CourseListItem(e.courseId(), e.title(), e.capacity(), 0, 0)); }
    @EventHandler void on(StudentEnrolled e)     { repo.update(e.courseId(), i -> i.seatsTaken(i.seatsTaken() + 1)); }
    @EventHandler void on(WaitlistPromoted e)    { repo.update(e.courseId(), i -> i.seatsTaken(i.seatsTaken() + 1)); }
    @EventHandler void on(StudentDropped e)      { repo.update(e.courseId(), i -> i.seatsTaken(i.seatsTaken() - 1)); }
    @EventHandler void on(WaitlistJoined e)      { repo.update(e.courseId(), i -> i.waitlisted(i.waitlisted() + 1)); }
}
```

Note on sequencing: with a DCB store Axon falls back to `SequentialPolicy` for event handlers — everything sequential in a segment. That is *correct and desirable* here (the `seatsTaken` counter is order-sensitive). Do not "optimize" this into `PropertySequencingPolicy("courseId")` unless segments are also raised, or the counter will race.

### Pattern 5: Thin REST adapter over gateways

**What:** Controllers are message senders. Commands in via `CommandGateway`, reads out via `QueryGateway`.
**When to use:** Every endpoint.
**Trade-offs:** Query handlers add a layer over a repository you could call directly. Worth it: the read side then has one entry point, and a subscription query could be added for live seat counts without touching controllers.

```java
@RestController
@RequestMapping("/api/courses")
class CourseController {
    private final CommandGateway commands;
    private final QueryGateway queries;

    @GetMapping
    List<CourseView> list() {
        return queries.query(new FindCourses(), CourseView.list()).join();
    }

    @PostMapping("/{courseId}/enroll")
    @ResponseStatus(HttpStatus.CREATED)
    void enroll(@PathVariable UUID courseId, @AuthenticationPrincipal AppUser user) {
        commands.sendAndWait(new EnrollStudent(new StudentId(user.studentId()), new CourseId(courseId)));
    }
}
```

### Pattern 6: Seeding through the command gateway

**What:** An `ApplicationRunner` dispatches the seed commands (`CreateCourse` × 4, `EnrollStudent` × n, `JoinCourseWaitlist` × n) so the event store is built the same way a user would build it.
**When to use:** Every boot, because the in-memory store starts empty. Guard with a "is the course list empty?" query so the seeder is idempotent if the store ever becomes persistent.
**Trade-offs:** Slightly slower startup; in exchange the demo state is real, replayable, and the seed data doubles as executable documentation.

### Pattern 7: The last-seat race as a first-class backend endpoint

**What:** `POST /api/demo/{courseId}/last-seat-race` fans out N concurrent `EnrollStudent` commands for N *distinct* seeded students against a course with exactly one free seat, then returns `{attempted, succeeded, rejectedByBoundary, rejectedByRule}`.
**When to use:** This is the project's core value, so it deserves a stable, testable seam rather than a UI-only trick.
**Trade-offs:** A demo endpoint in production code is usually a smell; here it is the exhibit, and it is also the exact code path the integration test asserts on. Keeping it in the backend makes the race reproducible in one request instead of depending on browser timing.

```java
@PostMapping("/demo/{courseId}/last-seat-race")
RaceResult race(@PathVariable UUID courseId, @RequestParam(defaultValue = "8") int attempts) {
    var students = studentPool.first(attempts);                     // distinct → distinct boundaries
    var futures = students.stream()
            .map(s -> commands.send(new EnrollStudent(s, new CourseId(courseId)))
                               .exceptionally(ex -> ex))           // NEVER sendAndWait here
            .toList();
    return RaceResult.tally(futures);                              // exactly one success
}
```

Critically: **do not put a routing key on `EnrollStudent`.** Command sequencing defaults to `RoutingKeySequencingPolicy`; a command with no routing key is *not* sequenced and runs concurrently. Give `EnrollStudent` `routingKey = "courseId"` and the framework serializes every enrollment for that course — the race silently disappears and the showcase proves nothing.

## Data Flow

### Flow 1 — Enroll (happy path, and the invariant)

```
click "Enroll"
  → POST /api/courses/{id}/enroll            (session cookie)
  → CourseController → CommandGateway.sendAndWait(EnrollStudent)
  → CommandBus → EnrollStudentHandler
      → resolve id (courseId + studentId)
      → @EventCriteriaBuilder → criteria(courseId, {CourseCreated, CapacityChanged,
                                                   Enrolled, Dropped, Promoted})
      → eventStore.source(criteria) → events + ConsistencyMarker
      → EnrollmentDecision.evolve(...)  → capacity=10, seatsTaken=9
      → decide(): 9 < 10 ✓, not enrolled ✓ → [StudentEnrolled]
      → append with AppendCondition(criteria, marker)
  → 201 Created
  → 200 from projection polling a moment later (see "eventual consistency" below)
```

### Flow 2 — Drop + FIFO promotion (one transaction, two facts)

```
click "Drop"
  → DropEnrollmentHandler
      → boundary: courseId, types {CourseCreated, Enrolled, Dropped,
                                   WaitlistJoined, WaitlistPromoted}
      → CourseSeatingState: enrolled(Dave), waitlist=[Erin, Frank]
      → decide(): Dave enrolled ✓; head = Erin
      → append [StudentDropped(Dave), WaitlistPromoted(Erin)]  ← one append, one condition
  → two events stored atomically; projections see both or neither
```

### Flow 3 — Projection update (write → read, always via events)

```
event store commits StudentEnrolled
  → PooledStreamingEventProcessor (SequentialPolicy)
  → CourseListProjection.on(StudentEnrolled)      → CourseListRepository (seatsTaken + 1)
  → WaitlistProjection.on(StudentEnrolled)        → removes/marks (if promoted)
  → MySeatsProjection.on(StudentEnrolled)         → adds seat for that student
```

Ordering matters and is guaranteed by the sequential policy: `seatsTaken` is a counter, not a recomputation.

### Flow 4 — Read

```
React polls GET /api/courses (e.g. every 500 ms while on the page)
  → QueryGateway.query(FindCourses)
  → FindCoursesHandler → CourseListRepository.findAll()   ← in-memory map, no event store
  → JSON: [{id,title,capacity,seatsTaken,waitlisted}]
```

### Flow 5 — The race

```
POST /api/demo/{id}/last-seat-race
  → N × CommandGateway.send(EnrollStudent(student_i, course)) in parallel
  → all N source ~same marker, all N see seatsTaken = capacity-1
  → first append commits (marker advances)
  → remaining N-1 appends fail: AppendEventsTransactionRejectedException
  → tally: succeeded=1, rejectedByBoundary=N-1
  → if the loser is retried instead: it re-sources, now sees seatsTaken == capacity,
    and fails with DomainRejection("course.full") → rejectedByRule, still one winner
```

Both outcomes prove the invariant; showing *both* side by side is the best teaching artifact the app can produce (boundary conflict vs. business rule rejection).

### Flow 6 — Login

```
POST /api/login (form)  → Spring Security session cookie (HttpOnly)
  → subsequent fetches send the cookie; principal resolves to {studentId, role}
  → ADMIN may create/rename/resize courses and open the race panel
  → STUDENT may enroll/drop/waitlist only as themselves (studentId from principal, never body)
```

## Frontend / Backend Split

| Concern | Backend | Frontend |
|---|---|---|
| Domain rules / invariants | 100% — no rule in JS | none |
| Seat counts, waitlist order | read models | render only |
| Identity | session + principal → `studentId` | login form, `credentials: 'include'` |
| Race orchestration | `DemoRaceController` | button + result table |
| Polling cadence | n/a | 500 ms on course list, stop on unmount |
| Error semantics | 409 + machine-readable `code` + human message | map `code` → message; never parse prose |

Keep the SPA dumb. The value of the app is in the backend boundary; every line of frontend logic is a line not spent on the lesson.

**Dev vs prod wiring.** In dev, Vite on :5173 proxies `/api` → :8080 so there are no CORS or cookie-SameSite surprises. In prod, `mvn package` builds the SPA into `src/main/resources/static` (or a `frontend-maven-plugin` step copies `dist/`), so one boot serves both. Do not add CORS config — the proxy removes the need.

**CSRF with a session cookie.** If Spring Security is wired with the default CSRF protection, the SPA must read the `XSRF-TOKEN` cookie and echo it in `X-XSRF-TOKEN`. Configure `CookieCsrfTokenRepository.withHttpOnlyFalse()` and a `CsrfTokenRequestAttributeHandler`, or (acceptable here) ignore CSRF for `/api/**` and rely on `SameSite=Lax`. Either is fine; pick one early because retrofitting CSRF into a working SPA is annoying.

## Alternative shapes (and why not)

| Alternative | What it looks like | Why not for this app |
|---|---|---|
| One `Course` aggregate with an enrollment child entity | Fixed course stream, all decisions lock the course | Hides the lesson: the boundary is chosen at design time, and the seat cap "just works" because everything is serialized |
| `Student` + `Course` entities injected into one handler (`@InjectEntity` × 2) | Two criteria unioned automatically by the framework | Excellent *comparison* slice to keep (AxonIQ ships `subscribestudentmulti` for this), but the hand-written single criteria is a clearer annotation site for "here is the boundary" |
| Promotion as an `automation/` slice | `@EventHandler(StudentDropped)` → dispatch `PromoteFromWaitlist` | Eventual, two decisions, can double-promote; loses the atomicity that makes the FIFO rule provable |
| Separate `Course` and `Enrollment` bounded contexts | Two packages, two event sets, cross-context messages | Splits one invariant across a boundary — the opposite of the point |
| Axon Server | DCB-enabled context | Excluded by the project: no Docker |

## Build Order (dependency order for phases)

```
1. Skeleton + in-memory DCB store            (nothing depends on it; everything depends on it)
        │
2. Event model + tag vocabulary              (contract; every slice depends on it)
        │
        ├──────────────► 3. Course write slice (create/rename/resize)
        │                        │
        │                        ├──► 4. Course list projection + query + REST + React list
        │                        │         (first full vertical slice visible in a browser)
        │                        │
        │                        └──► 5. Login + seeded users/students (principal → studentId)
        │                                  │
        │                                  ├──► 6. Enroll slice + dynamic boundary  ◄── the lesson
        │                                  │         │
        │                                  │         ├──► 7. Waitlist join slice
        │                                  │         │
        │                                  │         └──► 8. Drop slice (+ atomic FIFO promotion)
        │                                  │                   │
        │                                  │                   └──► 9. Race demo + contention test
        │                                  │
        └──────────────────────────────────┴──► 10. README trace, boundary annotations, design note
```

**Why this order:**

- **1 before 2:** you cannot validate a DCB event model until an event actually round-trips through a tag-based store. Boot the empty app first; if `AppendEventsTransactionRejectedException` and tag filtering are not working, everything after is built on sand.
- **2 before 3:** events are shared by all slices (AxonIQ: "there may be 0 to many handlers for the same event"). Changing a tag later invalidates every boundary that references it.
- **3–4 before 6:** the projection + query + REST + React path must be proven on the simplest slice. If the read side is broken, "seat count looks wrong" is indistinguishable from "boundary is wrong."
- **5 before 6:** `EnrollStudent` needs a trustworthy `studentId`. Deriving it from the session (not the request body) must be settled before any enrollment code exists, or the race demo needs fake identities.
- **6 is the hinge.** Once `EnrollStudent`'s `@EventCriteriaBuilder` exists and rejects at capacity, the project's core claim is demonstrated. Everything after widens the story.
- **7 then 8:** waitlist joining introduces the events; dropping consumes them. Building 8 first means an untested FIFO fold.
- **9 last among features:** the race needs enrollment (6) and a populated last seat, and it should assert against a read model that already works (4).
- **10 continuously, but finalized last:** the README trace needs real file paths and real annotation comments; writing it earlier produces fiction.

**Parallelizable:** 4 (read side) and 5 (security) are independent; 7 and 3's rename/resize slice are independent of each other.

## Scaling Considerations

Not a goal — single node, single JVM, in-memory store. Recorded so nobody "fixes" the wrong thing later.

| Scale | Architecture adjustment |
|---|---|
| 1 user (now) | Current shape. `SimpleCommandBus`, `InMemoryEventStorageEngine`, no token store. |
| ~100 concurrent demo users | Still fine. The bottleneck is sequential event processing (default `SequentialPolicy`), not commands. Do not enable command sequencing to "help". |
| Multi-instance | Breaks: the in-memory store is per-JVM, so two instances see different histories. Requires Axon Server (or Postgres) — explicitly out of scope. |
| Restart | Events vanish; seeds re-run. Any "production hardening" would start with a persistent DCB engine, not with caching. |

**Order of first breakage:** (1) projection lag under a burst of events, (2) event-handler throughput from `SequentialPolicy`, (3) memory. None is worth pre-solving.

## Anti-Patterns

### Anti-Pattern 1: Declaring a routing key on the enroll command
**What people do:** `@Command(routingKey = "courseId") class EnrollStudent(...)` because "commands for a course should be serialized."
**Why it's wrong:** `RoutingKeySequencingPolicy` is the default and *serializes commands with the same routing key*. Two enrollments for the same course then never overlap, the append condition never conflicts, and the last-seat race becomes a single-threaded loop. The showcase's core value silently disappears — and every test still passes.
**Do this instead:** leave `EnrollStudent` unkeyed (or key strictly on the composite `courseId+studentId`, which still permits the race). Add a test that asserts a conflict actually occurs.

### Anti-Pattern 2: Enforcing the invariant by reading a projection
**What people do:** In the command handler, query the course-list read model for `seatsTaken` and reject if full.
**Why it's wrong:** The read model is stale and holds no consistency marker, so two concurrent commands both read "9" and both succeed. This is the exact bug DCB exists to prevent, reintroduced by hand.
**Do this instead:** Read only from sourced events inside the decision. Projections exist for display, never for decisions.

### Anti-Pattern 3: Business logic in `@EventSourcingHandler`
**What people do:** put validation or event emission inside `evolve(...)`.
**Why it's wrong:** Event sourcing handlers re-run during replay; logic there executes again for old events, producing divergent state and phantom rejections.
**Do this instead:** handlers assign fields only. All rules live in `decide(...)`, which runs once per command.

### Anti-Pattern 4: One giant `EventCriteria.havingAnyTag()` or no type filter
**What people do:** source everything to be safe.
**Why it's wrong:** the boundary becomes the whole store: every unrelated event conflicts, throughput dies, and the design note's claim ("reads only what the invariant needs") becomes false.
**Do this instead:** list event types explicitly with a comment for each; omit types the rule cannot observe (`CourseRenamed` is the canonical omission).

### Anti-Pattern 5: Sharing one state class across slices
**What people do:** create a `CourseEntity` used by create, enroll, drop, and resize.
**Why it's wrong:** slices become coupled again; one criteria must serve four rules, so it is either too broad for one or too narrow for another. You rebuild an aggregate with extra steps.
**Do this instead:** one state class per slice, each with its own `@EventCriteriaBuilder`, free to disagree about what matters.

### Anti-Pattern 6: `sendAndWait` in the race fan-out
**What people do:** loop `commands.sendAndWait(...)` N times.
**Why it's wrong:** fully sequential — the requests never overlap, so the race never happens. Also blocks the request thread N times.
**Do this instead:** `commands.send(...)` returning `CompletableFuture`, collect the futures, then join with `allOf`/`handle`.

### Anti-Pattern 7: Querying the projection immediately after the command
**What people do:** `POST /enroll` then redirect to a page that shows `seatsTaken` from the read model.
**Why it's wrong:** projections are asynchronous; the user sees the old count and concludes the feature is broken.
**Do this instead:** return the decision outcome from the command (success + the seat count the boundary saw), then poll. Optionally expose a subscription query for a live seat badge.

### Anti-Pattern 8: Appending events directly from the seeder or the controller
**What people do:** `eventAppender.append(new CourseCreated(...))` at startup to save time.
**Why it's wrong:** seeded state bypasses every invariant and produces events no handler was ever tested against.
**Do this instead:** dispatch commands through `CommandGateway`.

### Anti-Pattern 9: Shipping the Axon Server connector by accident
**What people do:** rely on the starter's transitive `axon-server-connector` and get a startup that tries `localhost:8124`.
**Why it's wrong:** the app then fails to start with no Docker — directly against the project constraint.
**Do this instead:** exclude the connector and/or set `axon.axonserver.enabled=false`, plus an explicit `@Bean EventStorageEngine` = `InMemoryEventStorageEngine`. Assert in a smoke test that the app boots with no network.

## Integration Points

### External Services

| Service | Integration | Notes |
|---|---|---|
| None | — | Explicit architectural property: no Docker, no Axon Server, no database, no message broker. Startup is one JVM. |

### Internal Boundaries

| Boundary | Communication | Notes |
|---|---|---|
| React ⇄ REST | JSON over HTTP, session cookie | Keep one `/api` prefix; Vite proxy in dev removes CORS. |
| REST ⇄ Axon | `CommandGateway` / `QueryGateway` | Controllers never see `EventStore`, entities, or events. |
| `web` ⇄ `write` | Command messages only | Commands are the only verbs; no service classes. |
| `web` ⇄ `read` | Query messages only | Query handlers are the read-side façade. |
| `write` ⇄ event store | `@InjectEntity` sourcing + `EventAppender` | The boundary is expressed by `@EventCriteriaBuilder`; nothing else in the slice touches the store. |
| `write` ⇄ `read` | **Nothing.** Both depend only on `events` | The one rule that keeps the architecture honest. |
| `seed` ⇄ `write` | Commands via gateway | Seeds are just another client. |
| Axon ⇄ event store | `EventStorageEngine` bean | Only the engine is swapped; DCB semantics identical in-memory and on Axon Server. |

## Verification Items (resolve during phase 1/2, before they cost a rewrite)

1. **In-memory DCB store under Spring Boot with no connector.** Docs show `@Bean EventStorageEngine storageEngine() { return new InMemoryEventStorageEngine(); }` and `axon.axonserver.enabled=false`; the auto-config fallback story ("JPA if present") is less clear for a project with neither. Prove it with a boot + append + source smoke test before writing any slices.
2. **Entity registration for a composite id + custom criteria.** The reference demo uses the Configuration API (`EventSourcedEntityModule.autodetected(SubscriptionId.class, State.class)`). Spring Boot auto-detects `@EventSourced` classes, but the id type for a composite key is not documented. Fallback: register the module as a bean and keep the `@EventSourced` annotation off. Decide once, in phase 2.
3. **Exact exception surfaced at the HTTP edge.** `AppendEventsTransactionRejectedException` is confirmed to exist and extend `AxonNonTransientException`; confirm whether the command gateway wraps it (AxonIQ docs show `CommandExecutionException` wrapping in other paths) so the 409 mapping matches reality rather than the class name.
4. **Whether any default retry exists.** Documented behavior is rejection; the Axon DCB reference material says "the command is retried," which may describe intent rather than framework behavior. If a retry policy exists, the race demo will report `rejectedByRule` instead of `rejectedByBoundary` — decide which story to tell and make the tally assert on it.

## Sources

- AxonIQ `university-demo` — package layout, `SubscribeStudentToCourseCommandHandler`, `FacultyTags`, `CoursesStatsProjection`, `CourseStatsConfiguration` (read directly from `github.com/AxonIQ/university-demo`, `main`): HIGH for structure, it is the reference implementation of this exact domain.
- Axon reference — *Event Store Internals* (DCB, tags, EventCriteria, AppendCondition, consistency markers): https://docs.axoniq.io/axon-framework-reference/5.3/events/event-store-internals
- Axon reference — *Stateful Command Handlers* (vertical slice architecture, `@InjectEntity`): https://docs.axoniq.io/axon-framework-reference/5.3/commands/entities/stateful-command-handler
- Axon reference — *Event-Sourced Entities* (`@EventTag`, `@EventSourced` vs `@EventSourcedEntity`): https://docs.axoniq.io/axon-framework-reference/5.2/commands/entities/event-sourced-entity
- Axon reference — *Command Processing / Command sequencing* (`RoutingKeySequencingPolicy`, unkeyed commands run concurrently): https://docs.axoniq.io/axon-framework-reference/5.4/tuning/command-processing
- Axon reference — *Sequencing Policy Migration* (DCB ⇒ `SequentialPolicy` default for handlers): https://docs.axoniq.io/axon-framework-reference/5.4/migration/paths/sequencing-policies
- Axon reference — *Spring Boot Integration* (starter coordinates, component detection, `@EventSourced`): https://docs.axoniq.io/axon-framework-reference/5.3/spring-boot-integration and https://docs.axoniq.io/axon-framework-reference/5.3/configuration/spring
- Axon reference — *Events / Infrastructure* (`InMemoryEventStorageEngine` Spring bean, `axon.axonserver.enabled`): https://docs.axoniq.io/axon-framework-reference/5.2/events/infrastructure
- Axon API — `AppendEventsTransactionRejectedException`, `ConsistencyMarker`, `AppendCondition`: https://apidocs.axoniq.io/latest/org/axonframework/eventsourcing/eventstore/AppendEventsTransactionRejectedException.html
- AxonIQ concept page — *Dynamic Consistency Boundary Explained* (criteria shown per slice, vertical-slice pairing): https://www.axoniq.io/concepts/dynamic-consistency-boundary
- Axon reference — *Axon Framework 5.0 → Axoniq Framework migration* (artifact coordinate changes, 5.1+): https://docs.axoniq.io/axon-framework-reference/5.4/advanced-migration/paths/5.0-to-5.1

---
*Architecture research for: Axon 5 DCB course-enrollment showcase*
*Researched: 2026-11-10*
