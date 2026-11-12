# Walking Skeleton — Axon 5 DCB Courses

> Historical record of Phase 1 as it was scoped. Two statements below were superseded during the
> phase: the SPA is its own image, not an artifact of `courses-api` (ADR-0004), and the wrapper runs
> Gradle 9.9.1. Read the code and `docs/tooling.md` for the current state.

**Phase:** 1
**Generated:** 2026-11-11

## Capability Proven End-to-End

> A developer runs one Compose command plus one Gradle command and three empty Spring Boot services register with Axon Server, with the SPA shell served by `courses-api` on `:8080`.

`docker compose up -d --wait` brings up Axon Server and PostgreSQL; `./gradlew bootRun --parallel` starts `courses-write` (8081), `courses-read` (8082), and `courses-api` (8080); the registration is proven by an integration test asserting the Axon connection map contains `default=true` — not by scraping `/actuator/health` (which does not surface the Axon component in this starter combination).

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | Spring Boot 4.1.2 + Kotlin 2.4.21 on JDK 21 | Boot 4 is the current stable line; Kotlin is a locked project decision. `kotlin-reflect` is pinned to 2.4.21 because Boot's BOM still carries `kotlin-bom` 2.3.21 |
| Messaging / event store | Axon Server 2026.1.7 via `io.axoniq.framework:axoniq-spring-boot-starter:5.4.1` | Commercial starter is the only AF5 path with an Axon Server connector; requires `axoniq_axonserver_standalone: "true"` or every client loops on `AXONIQ-1302 default: not found` |
| Data layer | PostgreSQL 18.6 (read model, Phase 5+) | Volume mounts at `/var/lib/postgresql` (PG18 refuses `/var/lib/postgresql/data`). No datasource/JPA in any service yet |
| Build | Gradle 9.7.0 multi-module + `java-platform` + version catalog | One `./gradlew build` produces all three bootJars and the SPA bundle; `:courses-platform` re-exports the BOMs via `allowDependencies()` + `api(platform(...))` |
| Frontend | React 19.3.1 + Vite 8.3.7 + TypeScript 7.0.2, Feature-Sliced Design | FSD enforced by `steiger --fail-on-warnings`; TS 7 removed `baseUrl`, so the `@/` alias uses `paths` alone |
| Directory layout | Six modules: `courses-platform`, `courses-domain`, `courses-write`, `courses-read`, `courses-api`, `courses-frontend` | `courses-write` appends events (no datasource), `courses-read` owns the read model (Phase 5+), `courses-api` is the only HTTP surface and serves the SPA, `courses-frontend` is a Gradle node module |
| Local run | `docker compose up -d --wait` + `./gradlew bootRun --parallel` | Compose reuses the exact image tags; Gradle runs same-named `bootRun` tasks concurrently across projects |

## Stack Touched in Phase 1

- [x] Project scaffold (Gradle, version catalog, wrapper, Spotless, module graph)
- [ ] Routing — at least one real route (deferred: Phase 2 session/login)
- [ ] Database — at least one real read AND one real write (deferred: Phase 5 course creation)
- [ ] UI — at least one interactive element wired to the API (deferred: Phase 5)
- [x] Deployment — documented local full-stack run command (`scripts/smoke-stack.sh`)

## Out of Scope (Deferred to Later Slices)

> Explicit so later phases do not re-litigate Phase 1's minimalism.

- No command handlers, projections, or business UI — this phase proves wiring only.
- No datasource or JPA outside `courses-read` (TOPO-03); none exists anywhere yet.
- No DCB-specific Axon Server flags (Phase 4 decides whether `standalone-dcb` is required).
- No `/api` SPA forwarder and no history-mode routing (Phase 2 adds the login route).
- No authentication — actuator exposes `health,info` only until Phase 2 adds security.
- No E2E test suite — E2E-01 is the agent-driven MCP proof; the Playwright suite is Phase 8.
- **License window:** Axon Server runs unlicensed for 12 hours then shuts down; restart or license for long sessions (documented, never "solved" here).

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering its architectural decisions:

- Phase 2: seeded login reaches the services; one probe command and query route through Axon Server.
- Phase 3: events, commands, queries, identifiers and tag keys frozen in `courses-domain`.
- Phase 4: DCB append-condition spike — two concurrent appends, one winner.
- Phase 5: course creation across services (command → event → read → UI, idempotent).
- Phase 6: enroll under capacity — the dynamic boundary enforces the seat cap.
- Phase 7: waitlist and atomic drop/promotion.
- Phase 8: the last-seat race proof.
- Phase 9: my enrollments and seeded scenarios.
- Phase 10: live updates over SSE.
- Phase 11: teaching artifacts (README, design note, event × tag table).
