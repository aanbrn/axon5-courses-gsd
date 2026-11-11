---
gsd_state_version: '1.0'
status: planned
progress:
  total_phases: 11
  completed_phases: 0
  total_plans: 6
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-11-10)

**Core value:** A command decides using exactly the events its invariant requires — proven by a last-seat race where N concurrent enrollments hit the cap and exactly one succeeds.
**Current focus:** Phase 1 — Build and infrastructure skeleton

## Current Position

Phase: 1 of 11 (Build and infrastructure skeleton)
Plan: 0 of 6 (6 plans in 4 waves)
Status: Planned
Last activity: 2026-11-11 — Phase 1 planned: 6 plans in 4 waves; plan-checker ran 3 iterations, two documentary blockers fixed by the orchestrator

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: — min
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| — | — | — | — |

## Accumulated Context

**Decisions carried into planning:**
- Three services (`write`, `read`, `api`) behind Axon Server 2026.1.7, PostgreSQL 18 read model.
- Kotlin 2.4.21 on JDK 21, Spring Boot 4.1.2, Axon 5.4.1 via the commercial `io.axoniq.framework` BOM.
- `EnrollStudent` carries no routing key; a full course rejects with 422 and the waitlist is an explicit command.
- Enroll criteria are scoped to `courseId`; `studentId` is read from the payload.
- `read` is one service: JPA + Flyway for writes, Spring Data JDBC for reads, and the subscription-query emitter. Live updates never cross a service boundary.
- Gradle Kotlin DSL, six modules, `:platform` re-exports the BOMs; the SPA is a Gradle module.

**Open technical risks (owned by phases):**
- Phase 1: commercial starter on Boot 4.1.2; Axon Server 12-hour unlicensed window.
- Phase 3: Kotlin `@EventTag` targets and `@JvmStatic` criteria builders.
- Phase 4: append-condition enforcement across processes.
- Phase 8: contention test harness; `AxonTestFixture` cannot test races.

## Session Continuity

Last session: 2026-11-10
Stopped at: Plan revised and reviewed; awaiting approval to commit and open the PR
Resume file: .planning/PLANNING-REVIEW.md
