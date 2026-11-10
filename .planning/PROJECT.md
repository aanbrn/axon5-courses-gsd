# Axon 5 DCB Showcase

## What This Is

A small course-enrollment application built to explore Axon Framework 5's Dynamic Consistency Boundaries (DCB). Students enroll in courses with limited seats, drop out, and sit on a waitlist — while every decision reads only the events it actually needs instead of loading a whole aggregate. It is deliberately split into three services behind Axon Server — `write`, `read`, `api` — with a PostgreSQL read model, so the boundary also has to survive a real process boundary. It exists as a personal learning sandbox: the app is the vehicle, the boundary design is the lesson.

## Core Value

A command decides using exactly the events its invariant requires — proven by a last-seat race where two concurrent enrollments hit the cap and exactly one succeeds.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] User can create courses with a seat capacity
- [ ] User can log in with seeded credentials
- [ ] Student can enroll in a course that has seats left
- [ ] Enrollment is rejected when the course is at capacity
- [ ] Student can join the waitlist of a full course through an explicit action
- [ ] Student can drop an enrollment, freeing the seat
- [ ] Dropping a seat promotes the next waitlisted student (FIFO)
- [ ] Enrollment state is enforced without loading unrelated course history
- [ ] Concurrent enrollments for the last seat result in exactly one success
- [ ] Reads are served from a PostgreSQL read model built and answered by one read service
- [ ] README traces one command end-to-end across the three services
- [ ] UI receives live read-model updates over SSE
- [ ] Code annotations mark where the consistency boundary is chosen
- [ ] Integration tests narrate the invariant holding under contention
- [ ] Design note explains why the boundary is dynamic rather than fixed

### Out of Scope

- Event-trace UI — the lesson is taught in docs and tests, not instrumentation; keeps the UI plain
- Real authentication and authorization — one seeded admin flag and one seeded student, no OAuth, no role matrix, no password management
- Kubernetes, autoscaling, HA, and production deployment — the topology is exercised locally via Docker Compose only
- Multiple read-model consumers beyond one `read` service — one writer of the read schema is the whole point
- Mobile / responsive polish — desktop browser demo is enough

## Context

- Single developer, learning Axon 5 and DCB by building.
- Axon Framework 5 is recent; DCB concepts (tags, dynamic boundaries, cross-stream invariants) are the unfamiliar ground, so research and code should stay close to official guidance.
- No prior codebase — greenfield.
- The interesting tension: a seat cap is a cross-cut invariant, and traditional aggregate boundaries either over-lock or fail to enforce it.
- The distributed topology is a deliberate second lesson: the boundary has to hold when `write`, `read` and HTTP live in different JVMs. It is also the reason the project now depends on commercial Axoniq modules and a Docker-hosted Axon Server.

## Working Language

All repository artifacts — code, comments, docs, commit messages, everything under `.planning/` — are written in English. Conversational language is a personal, per-participant preference and is never committed to this repo: GSD defaults to English, and the assistant mirrors the language each participant writes in. See `.planning/codebase/CONVENTIONS.md`.

## Constraints

- **Topology**: three Spring Boot services — `write`, `read`, `api` — plus a `domain` contract library, exchanging commands, events and queries through Axon Server. `read` consumes events, owns the PostgreSQL read model and answers queries.
- **Tech stack**: multi-module Gradle (Kotlin DSL, version catalog) with a `platform` module re-exporting the BOMs; Kotlin on JVM 21, Spring Boot 4.1.2, Axon 5.4.1; React SPA as the `frontend` Gradle module.
- **Infrastructure**: Docker Compose runs Axon Server and PostgreSQL. One command brings the infrastructure up; one Gradle run starts all three services.
- **Licensing**: the Axon Server connector and the Axon Spring Boot starter are commercial `io.axoniq.framework` modules; local development must run under their evaluation behavior, and any production use is explicitly out of scope.
- **Consistency**: events are the only cross-service *state* contract; commands and queries are shared message types in `domain`. Each service owns its own models, and read models are eventually consistent.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Course enrollment as the domain | Seat cap is an invariant spanning many student streams, so the dynamic boundary is visible rather than theoretical | — Pending |
| Three services behind Axon Server | Makes the boundary survive a real process split while keeping the read side canonical Axon; splitting projection from query was reverted because live updates would need a notification channel that commands cannot honestly provide | — Pending |
| PostgreSQL read model owned by `read` | A durable model survives restarts; one service writes and answers it, so snapshot and live updates share one source and no cross-service notification exists | — Pending |
| Commands, events and queries live in `domain` | Keeps `api` from depending on `write`/`read` implementations — it sends messages and Axon Server routes them | — Pending |
| React SPA over server-rendered UI | Clean API/UI split keeps the Axon-facing code uncluttered | — Pending |
| Plain UI, no event introspection | Explanation lives in README, annotations, tests, and a design note — avoids building observability before understanding | — Pending |
| SSE over Axon subscription queries, not an event feed | The browser needs fresh seats, not domain internals; a subscription query keeps the UI on read models and leaves the boundary where it belongs | — Pending |
| Explicit last-seat race demo | The concurrency guarantee is the whole point; it must be reproducible, not asserted | — Pending |
| `:platform` module for dependency versions | One `java-platform` re-exports the Boot and Axoniq BOMs, so no module repeats a version and upgrades happen in one file | — Pending |
| Multi-module Gradle with the SPA as a Gradle module | One build graph for the whole product; a stray `npm` step would drift from the build | — Pending |
| Spring Boot 4.1.2 over Axon's 3.5.16 build baseline | Boot 4 is officially supported by Axon and manages Jackson 3 itself; 3.5.16 remains the fallback | — Pending |
| Gradle Kotlin DSL + Kotlin-only backend | Author's preferred language and build tool. Kotlin interop with Axon annotations is an unverified risk settled in Phase 3 and proven in Phase 4 | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-11-10 after distributing the architecture (Axon Server + PostgreSQL)*
