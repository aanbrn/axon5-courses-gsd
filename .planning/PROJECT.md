# Axon 5 DCB Showcase

## What This Is

A small course-enrollment application built to explore Axon Framework 5's Dynamic Consistency Boundaries (DCB). Students enroll in courses with limited seats, drop out, and sit on a waitlist — while every decision reads only the events it actually needs instead of loading a whole aggregate. It exists as a personal learning sandbox: the app is the vehicle, the boundary design is the lesson.

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
- [ ] Student can drop an enrollment, freeing the seat
- [ ] Dropping a seat promotes the next waitlisted student (FIFO)
- [ ] Enrollment state is enforced without loading unrelated course history
- [ ] Concurrent enrollments for the last seat result in exactly one success
- [ ] README traces one command end-to-end through the dynamic boundary
- [ ] Code annotations mark where the consistency boundary is chosen
- [ ] Integration tests narrate the invariant holding under contention
- [ ] Design note explains why the boundary is dynamic rather than fixed

### Out of Scope

- Axon Server / Docker — embedded event store keeps startup to one JVM; no infra to manage for a learning sandbox
- Event-trace UI — the lesson is taught in docs and tests, not instrumentation; keeps the UI plain
- Real authentication and authorization — seeded credentials only, no OAuth, roles, or password management
- Multi-service architecture — one Spring Boot app, one React app; distribution would obscure the boundary story
- Production hardening — no scaling, monitoring, or deployment work
- Mobile / responsive polish — desktop browser demo is enough

## Context

- Single developer, learning Axon 5 and DCB by building.
- Axon Framework 5 is recent; DCB concepts (tags, dynamic boundaries, cross-stream invariants) are the unfamiliar ground, so research and code should stay close to official guidance.
- No prior codebase — greenfield.
- The interesting tension: a seat cap is a cross-cut invariant, and traditional aggregate boundaries either over-lock or fail to enforce it.

## Constraints

- **Tech stack**: Java + Spring Boot + Axon Framework 5 backend, React SPA frontend — the user wants to learn Axon 5 in a familiar Spring setting.
- **Runtime**: No Docker, embedded event store — the app must start with a single command and no external services.
- **Scope**: Single node, local use — concurrency is demonstrated within one process.
- **Audience**: The author — clarity of explanation outranks feature polish.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Course enrollment as the domain | Seat cap is an invariant spanning many student streams, so the dynamic boundary is visible rather than theoretical | — Pending |
| Embedded event store, no Docker | Zero-friction startup matters more than production parity for a learning tool | — Pending |
| React SPA over server-rendered UI | Clean API/UI split keeps the Axon-facing code uncluttered | — Pending |
| Plain UI, no event introspection | Explanation lives in README, annotations, tests, and a design note — avoids building observability before understanding | — Pending |
| Explicit last-seat race demo | The concurrency guarantee is the whole point; it must be reproducible, not asserted | — Pending |

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
*Last updated: 2026-11-10 after initialization*
