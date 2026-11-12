---
gsd_state_version: "1.0"
current_phase: 2
current_phase_name: Session and message path
status: planning
stopped_at: Completed 01-06-PLAN.md
last_updated: "2026-11-12T04:16:16.746Z"
last_activity: 2026-11-12
last_activity_desc: "Extracted the Phase 1 learnings and the estimation calibration; the health-indicator mechanics moved into the architecture notes"
state_head: b822e96
progress:
  total_phases: 11
  completed_phases: 1
  total_plans: 6
  completed_plans: 6
  percent: 9
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-11-10)

**Core value:** A command decides using exactly the events its invariant requires — proven by a last-seat race where N concurrent enrollments hit the cap and exactly one succeeds.
**Current focus:** Phase 2 — Session and message path

## Current Position

Phase: 2 of 11 (Session and message path) — NOT STARTED
Plan: 0 of TBD
Status: Ready to plan
Last activity: 2026-11-12 — Extracted the Phase 1 learnings and the estimation calibration; the health-indicator mechanics moved into the architecture notes

Progress: [█░░░░░░░░░] 9%

## Performance Metrics

**Velocity:**
- Total plans completed: 6
- Average duration: 13 min
- Total execution time: 1.3 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1. Build and infrastructure skeleton | 6 | 6 | 13min |
**Per-Plan Metrics:**

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 1 P01 | 25min | 4 tasks | 19 files |
| Phase 1 P02 | 6min | 2 tasks | 8 files |
| Phase 1 P03 | 20min | 3 tasks | 18 files |
| Phase 1 P05 | 12min | 2 tasks | 3 files |
| Phase 1 P04 | 4min | 2 tasks | 2 files |
| Phase 01 P06 | 9min | 2 tasks | 1 files |

## Accumulated Context

**Decisions carried into planning:**
- Three services (`courses-write`, `courses-read`, `courses-api`) behind Axon Server 2026.1.7, PostgreSQL 18 read model.
- Kotlin 2.4.21 on JDK 21, Spring Boot 4.1.2, Axon 5.4.1 via the commercial `io.axoniq.framework` BOM.
- `EnrollStudent` carries no routing key; a full course rejects with 422 and the waitlist is an explicit command.
- Enroll criteria are scoped to `courseId`; `studentId` is read from the payload.
- `courses-read` is one service: JPA + Flyway for writes, Spring Data JDBC for reads, and the subscription-query emitter. Live updates never cross a service boundary.
- Gradle Kotlin DSL, six modules, `:courses-platform` re-exports the BOMs; the SPA is a Gradle module shipped as a standalone nginx image built with Paketo buildpacks.

**Open technical risks (owned by phases):**
- Phase 1 retired: the commercial starter resolves and boots on Boot 4.1.2, and the 12-hour unlicensed window is documented in `docs/tooling.md`.
- Phase 3: Kotlin `@EventTag` targets and `@JvmStatic` criteria builders.
- Phase 4: append-condition enforcement across processes.
- Phase 8: contention test harness; `AxonTestFixture` cannot test races.

**Deferred work:**
- Code coverage — backlog item 999.1 in `ROADMAP.md`: JaCoCo reporting first, ratchet once the phase-7
  decision slices exist; integration suites count, frontend measured separately with Vitest.
- Container image scanning — backlog item 999.2: Trivy/Grype once an image is actually published;
  there is nothing to scan while images stay local.

## Session Continuity

Last session: 2026-11-11T00:36:41.765Z
Stopped at: Completed 01-06-PLAN.md
Resume file: None

## Decisions

- [Phase 1]: Wrapper upgraded to Gradle 9.9.1 (`-all`, SHA-256 pinned); Kotlin Gradle Plugin 2.4.21 configures and builds on it (verified)
- [Phase 1]: No empty FSD layers: only app, pages/home and shared/ui exist, because steiger insignificant-slice flags placeholder slices; widgets/entities are created with their first real slice
- [Phase 1]: lint:fsd runs steiger --fail-on-warnings plus an oxlint no-restricted-imports regex on @/<layer>/<slice>/<...>, because steiger 0.10.0 does not flag deep imports inside shared/*
- [Phase 1]: `courses-read` and `courses-api` build files are copies of `courses-write`'s (same plugins, toolchain and `jvm-test-suite` split), differing only in name, port, app class and test fixture, so the three services cannot drift
- [Phase 1]: `courses-api` depends only on `:courses-platform` and `:courses-domain` plus the Axon starter — never on `courses-write` or `courses-read`; the SPA is a separate image, not an api resource
- [Phase 1]: A green Axon Server healthcheck does not mean the `default` context exists; stale runner volumes produce `AXONIQ-1400`/`AXONIQ-1302`, and `docker compose down -v` is the reset
- [Phase 01-build-and-infrastructure-skeleton]: CI build job runs integrationTest; each suite starts its own Axon Server and PostgreSQL through Testcontainers, and ./gradlew build stays Docker-free
- [Phase 1]: Registration is proven inside the app (AxonServerConnectionManager.connections()), never by scraping /actuator/health; the smoke script reads the `Application connected` lines from the Compose-managed Axon Server log
- [Phase 1]: set -m job control plus kill -- "-RUN_PID" replaces setsid (absent on macOS) so bootRun --parallel leaves no orphaned JVMs
- [Phase 01]: E2E-01 evidence is the recorded Playwright MCP session in the SUMMARY, not a shell test: only the tracked-config half is greppable, and no Phase 1 test drives a browser
- [Phase 01]: The accessibility snapshot omits data-testid, so the app-shell element is asserted with browser_evaluate rather than the heading alone
- [Phase 01]: docs/tooling.md documents the build job as it is: setup-node, Docker-free ./gradlew build, Testcontainers-backed integrationTest, no Compose steps
