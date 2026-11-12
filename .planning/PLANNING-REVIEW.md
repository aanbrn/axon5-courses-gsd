# Planning Review — 2026-11-10

Three independent reviewers audited the draft plan before human review: consistency, requirements/roadmap quality, and technical fact-checking. This file records every finding and what happened to it.

> **Historical note.** Entries from rounds 1–2 that mention ten phases, `projection`, `query`, `command` or `api-gateway` describe the state at that time. The current shape is three services (`courses-write`, `courses-read`, `courses-api`), six modules and eleven phases.

> **This file is history.** It records two manual review cycles that ran before the project moved to
> GSD's review commands. The current procedure is in `.planning/codebase/CONVENTIONS.md` § Review:
> `/gsd-review` and `/gsd-plan-review-convergence` for plans, `/gsd-code-review` for code,
> `/gsd-docs-update` for documentation claims, `/gsd-audit-milestone` for a milestone. Nothing here
> is a loop to re-run.

**Totals:** round 1 — 68 findings (9 blockers, 35 major, 24 minor); round 2 consistency — 27 (4/13/10); round 2 technical — 7 (2 major, 5 minor). Round 3 (full re-review) — 33 findings (2 blockers, 24 majors, 7 minors). Round 4 (verification) — 24 findings (2 blockers, 16 majors, 6 minors). Total 159 findings.

Status: **round 4 verification found 2 blockers and 16 majors**, all documentary leftovers from the renames and the service merge; they are being fixed in the current pass. One major is accepted by design (`RQ-06`, VIEW-01/AUTH-02 kept separate). Earlier rounds' earlier statuses are historical, not current claims.

## Rounds

The loop and its convergence rules live in `.planning/codebase/CONVENTIONS.md` § "Review loop".

| Round | Reviewers | Blockers | Majors | Minors | Outcome |
|-------|-----------|----------|--------|--------|---------|
| 1 | consistency · requirements/roadmap · technical | 9 | 35 | 24 | Fixes applied to PROJECT, REQUIREMENTS, ROADMAP, STACK, ARCHITECTURE, SUMMARY, FEATURES, PITFALLS, CONVENTIONS, AGENTS |
| 2 (consistency) | consistency | 4 | 13 | 10 | Exit criteria **not met**: 9 reopened findings triggered the oscillation stop. Fixed in one documentary pass. |
| 2 (technical) | technical | 0 | 2 | 5 | Found a real build defect in the `courses-platform` module (Gradle rejects BOMs inside `constraints`); remaining items are version alignment. Fixed. |
| 3 (full re-review) | consistency · requirements · technical | 2 | 24 | 7 | Fixed: race contract, phase split, SSE API, Flyway module, package. |
| 4 (verification) | consistency · technical | 2 | 16 | 6 | Leftovers from the `courses-write`/`courses-api` renames and the `courses-read` merge; fixed in the current pass. Consistency IDs: C-01…C-24; technical IDs reported separately. |

Round 2 exit status: **not met** (4 blockers, 13 majors, 9 reopened). Per the oscillation rule the loop stopped instead of running round 3. All round-2 findings were documentary — siblings of already-changed statements — so they were fixed directly and recorded below. No round 3 was run; a later change touching more than one file restarts at round 1.

Legend: **fixed** — document changed; **accepted** — considered and deliberately kept; **deferred** — belongs to a later phase and is now written there.

## Blockers

| ID | Where | Finding | Status | Resolution |
|----|-------|---------|--------|------------|
| RQ-01 / C3 | REQUIREMENTS, ROADMAP | Full course both rejects (ENRL-03) and auto-waitlists (ENRL-04) | fixed | Product decision: reject with 422, then an explicit `JoinCourseWaitlist` command. ENRL-03/04 rewritten. |
| RQ-02 / RM-03 | REQUIREMENTS, ROADMAP | Race "exactly one wins" is timing-dependent once auto-waitlist exists; tally false | fixed | RACE-02 states exactly one `enrolled`; RACE-03 moved to a forced-overlap variant asserting the boundary exception; tally shape defined in RACE-01. |
| RM-01 | ROADMAP | Phase 1 bundles infra, six modules, four apps, login, probes, spike and CI | fixed | Split into 10 phases: skeleton → session/message path → contract → DCB spike → … |
| RM-02 | ROADMAP | Phase 4 capacity criterion invalidated by Phase 5 | fixed | Capacity rejection lives in Phase 6; waitlist is a separate action in Phase 7. |
| C1 | ROADMAP, ARCHITECTURE | Phase 1 guard test required JPA in `query`, contradicting RDMD-02 | fixed | Guards now assert `projection` has JPA/Flyway and `query` does not. |
| C2 | SUMMARY, ARCHITECTURE | "`validate` in `query`" survives where `query` has no JPA | fixed | Replaced with the startup smoke check (`LIMIT 0`) everywhere. |
| C4 | ARCHITECTURE vs FEATURES | Dedicated `JoinCourseWaitlist` vs "no separate intent" | fixed | Separate command is canonical; FEATURES updated. |
| C5 | STACK vs PITFALLS | Unkeyed `EnrollStudent` vs "routing key = studentId" | fixed | Project decision: no routing key. Stated identically in STACK, PITFALLS, conventions. |
| C11 | ARCHITECTURE vs PITFALLS | Enroll criteria course-only vs `either(course, student)` | fixed | `courseId` only; `studentId` read from payload; `either(...)` documented as the v2 widening. |

## Requirements findings

| ID | Severity | Status | Resolution |
|----|----------|--------|------------|
| RQ-03 | major | fixed | Added `AUTH-03` (student gets 403 on admin actions); out-of-scope now says "no role matrix", not "no roles". |
| RQ-04 | major | fixed | `ENRL-06` now names the exact tag and event-type set and requires a criteria-snapshot test. |
| RQ-05 | major | fixed | `RDMD-02` rewritten around observable behavior: read-only pool, no writes, fail-fast naming the column. |
| RQ-06 | major | accepted | `VIEW-01` (content) and `AUTH-02` (ownership enforcement) kept separate; different tests. |
| RQ-07 | major | fixed | `DROP-03` removed; FIFO-by-append-order folded into `ENRL-05`. |
| RQ-08 | major | fixed | `PLAT-03` is now behavior-only; the guard conditions live in `TOPO-03`. |
| RQ-09 | major | fixed | `PLAT-05` requires CI to start Axon Server and PostgreSQL and assert the race test ran. |
| RQ-10 | major | fixed | `VIEW-02` bounded: pending state, then new values within 2 s, never a false "seat free". |
| RQ-11 | major | fixed | `DOCS-02` is now a checklist: tag keys, exact types, consuming handler per type. |
| RQ-12 | major | fixed | `DOCS-03` now requires sentence-form names and an evidence listing. |
| RQ-13 | minor | fixed | `CRSE-02` merges seats and waitlist depth; waitlist rendering moved to Phase 7. |
| RQ-14 | minor | fixed | Added `CRSE-03`: blank title or capacity < 1 rejected; capacity immutable. |
| RQ-15 | minor | fixed | `RACE-01` defines N, the barrier and the response shape. |
| RQ-16 | minor | fixed | `RACE-04` is a tagged harness excluded from the default run. |
| RQ-17 | minor | fixed | Folded into `TOPO-03`. |
| RQ-18 | minor | fixed | `ENRL-02` covers already-waitlisted; `DROP-01` covers dropping what you do not own. |
| RQ-19 | minor | fixed | `PLAT-02` requires idempotent seeding across restarts. |
| RQ-20 | minor | fixed | Single artifact `docs/event-tag-table.md`; `DOCS-05` links and quotes it. |
| RQ-21 | minor | fixed | `PLAT-04` maps 401/403/404/409/422. |

## Roadmap findings

| ID | Severity | Status | Resolution |
|----|----------|--------|------------|
| RM-04 | major | fixed | Login and SPA shell moved to Phase 2, which carries `**UI hint**: yes`; Phase 1 builds the bundle only. |
| RM-05 | major | fixed | Contract (Phase 3) precedes the spike (Phase 4); the spike uses `courses-domain` message shapes. |
| RM-06 | major | fixed | Phase 3 gains `TOPO-05`; `CourseRenamed` dropped; capacity change is v2. |
| RM-07 | major | fixed | Waitlist/drop and "my enrollments + seeding" are separate phases (7 and 8). |
| RM-08 | major | fixed | `DOCS-02` lives in Phase 6 with the criteria; `DOCS-03` in Phase 9 with the tests. |
| RM-09 | major | fixed | `main` requires a green `build` check, so a later phase cannot merge with broken CI. |
| RM-10 | major | fixed | Added `TOPO-04` (SPA in one build graph) and `AUTH-03` (authorization). |
| RM-11 | minor | fixed | Phase 5 asserts seats only; drift is exercised with a deliberate fixture, not a hope. |
| RM-12 | minor | fixed | Phase 5 criterion 2 exercises the identity/role path (403) before Phase 8 relies on it. |
| RM-13 | minor | fixed | Replaced by the `ENRL-06` criteria snapshot. |
| RM-14 | minor | accepted | The linear dependency chain was already correct. |

## Consistency findings

| ID | Severity | Status | Resolution |
|----|----------|--------|------------|
| C6–C9 | major | fixed | `PITFALLS.md` rewritten: Axon Server spike, no in-memory prescription, no reseed-on-boot, no 409-for-business-rejection. |
| C10 | major | fixed | Rewritten as "every type that can move *this* invariant", with per-command sets. |
| C12 | major | fixed | One event-name set (`StudentEnrolled`, `StudentDropped`, `WaitlistJoined`, `WaitlistPromoted`) across ARCHITECTURE, FEATURES, PITFALLS. |
| C13 | major | fixed | `CourseRenamed` removed from the frozen contract; `CourseCapacityChanged` deferred with capacity change. |
| C14 | major | fixed | "Events are the only shared *state* contract; commands and queries are shared message types." |
| C15 | major | fixed | `PLAT-03` narrowed to "no event-store writes on the read side". |
| C16 | major | fixed | `PLAT-05` now requires CI to start Axon Server and PostgreSQL in Phase 1; the race-execution assertion moved to Phase 9, where the test exists. |
| C17 | major | fixed | Conventions split slice tests (fixture) from contention tests (barrier harness). |
| C18 | major | fixed | FEATURES' v1 tier regenerated: mechanism proof, 409/422, race endpoint, event × tag table are v1. |
| C19 | major | fixed | Single-process claims replaced with distributed ones (tracking processor, persisted read model, visible lag). |
| C20 | minor | fixed | `AGENTS.md` regenerated from the updated conventions. |
| C21 | minor | fixed | SUMMARY phase list rewritten to the 10-phase roadmap. |
| C22 | minor | fixed | Harness decision moved to Phase 2. |
| C23 | minor | fixed | One event × tag artifact, created in Phase 3, linked in Phase 10. |
| C24 | minor | fixed | `.planning/codebase/ARCHITECTURE.md` exists and is mirrored into `AGENTS.md`. |
| C25 | minor | fixed | `ship.pr_body_sections` source points at `## v1 Requirements` and `## Traceability`. |
| C26 | minor | fixed | Tag vocabulary documented (`courseId`, `studentId`, type-implied values). |
| C27 | minor | fixed | Branching and PR policy added to conventions. |
| C28 | minor | fixed | Phase 1 `UI hint: no`; Phase 2 `yes`. |
| C29 | minor | fixed | Spike message shapes come from `courses-domain` (Phase 3). |
| C30 | minor | fixed | `axonserver:2026.1.7`, `postgres:18`. |

## Technical fact-check

| ID | Severity | Status | Resolution |
|----|----------|--------|------------|
| A1 | blocker→major | fixed | `2026.1` is a series name, not a tag; pinned `axoniq/axonserver:2026.1.7`. |
| A2 | major | fixed | `@EventTag` targets are METHOD/FIELD/ANNOTATION_TYPE — `@param:` does not compile; docs now say no use-site target or `@field:`. |
| A3 | minor | fixed | `StreamingCondition` moved to `org.axonframework.messaging.eventstreaming`. |
| A4 | minor | fixed | "Only open path" softened; `axoniq-springcloud` + `axoniq-postgresql` named as a second commercial topology. |
| A5 | minor | fixed | The starter already depends on the connector; catalog entry removed, deps simplified. |
| A6 | minor | fixed | PostgreSQL 18 pinned. |
| A7 | minor | accepted | Gradle 8.14 pinned deliberately; note added that 9.9 is current. |
| A8 | minor | fixed | Licensing documented: hourly dev permission + warning every 15 min; Axon Server shuts down after 12 h unlicensed. |
| B1–B13 | — | confirmed | Versions, coordinates, DCB API names, Jackson 3, Kafka/JGroups AF4-only, sequencing policy, exception hierarchy all verified. |
| C1–C6 (GH) | — | confirmed | SHA pinning, action versions, Node 24, workflow syntax, title enforcement, no injection. |
| C7 (GH) | — | verified manually | Required checks `build` + `title` confirmed via the API. |
| C8 (GH) | minor | accepted | Until `gradlew` exists, the `build` check is green without compiling; Phase 1 ends that stopgap. |


## Round 2 findings

| ID | Severity | Where | Status | Resolution |
|----|----------|-------|--------|------------|
| R2-01 | blocker | SUMMARY must-have list | fixed | "auto-waitlist when full" replaced with reject-then-explicit-join. |
| R2-02 | blocker | FEATURES launch list + dependency graph | fixed | "Auto-waitlist on full" and the graph node replaced with explicit join. |
| R2-03 | blocker | FEATURES criteria description | fixed | Enroll criteria restated as `courseId`-scoped with the student checked from payload. |
| R2-04 | blocker | STATE.md | fixed | Rewritten for 10 phases, Axon Server + PostgreSQL, commercial starter. |
| R2-05 | major | FEATURES `EnrollmentDropped` | fixed | Renamed to `StudentDropped`. |
| R2-06 | major | ARCHITECTURE `ChangeCourseCapacity` row and package | fixed | Removed; capacity change is v2. |
| R2-07 | major | STACK/SUMMARY/ARCHITECTURE "Phase 1 spike" | fixed | Re-attributed: Phase 1 boot/license, Phase 3 annotations, Phase 4 append condition. |
| R2-08 | major | FEATURES "decide the table in phase 1" | fixed | Changed to Phase 3. |
| R2-09 | major | FEATURES routing-key advice | fixed | Restated the project decision: no routing key, proven in Phase 4. |
| R2-10 | major | SUMMARY event-naming gap | fixed | Gap deleted; names are single-valued and frozen in Phase 3. |
| R2-11 | major | PLAT-05 phase mismatch | fixed | Traceability, roadmap and this record now agree on Phase 1. |
| R2-12 | major | PLAT-05 race assertion | fixed | Race execution moved to Phase 9; PLAT-05 covers build + integration infrastructure. |
| R2-13 | major | CRSE-02 vs waitlist timing | fixed | CRSE-02 narrowed to seats; waitlist depth asserted in Phase 7 with ENRL-04. |
| R2-14 | major | TOPO-03 positive assertion | fixed | TOPO-03 and Phase 5 now assert `projection` *has* JPA + Flyway, not only that others lack it. |
| R2-15 | major | PROJECT cross-service contract wording | fixed | "Shared *state* contract; commands and queries are shared message types." |
| R2-16 | major | this record's own status claims | fixed | Totals, round table and open items corrected. |
| R2-17 | minor | ARCHITECTURE tree `boundary-map.md` | fixed | Renamed to `event-tag-table.md`. |
| R2-18 | minor | ARCHITECTURE flow 2 short event names | fixed | Full names used. |
| R2-19 | minor | RACE-01 tally | fixed | `waitlisted` bucket removed; it cannot be non-zero. |
| R2-20 | minor | FEATURES race payload | fixed | Aligned to RACE-01. |
| R2-21 | minor | SUMMARY dependency list | fixed | Connector removed; Axon Server 2026.1.7. |
| R2-22 | minor | SUMMARY licensing phase | fixed | Phase 1. |
| R2-23 | minor | PLAT-03 / TOPO-03 overlap | fixed | PLAT-03 is behavioural (no events appended on a read cycle); module guards live in TOPO-03. |
| R2-24 | minor | VIEW-02 without a criterion | fixed | Phase 5 criterion 5 asserts the 2-second convergence and no false "seat free". |
| R2-25 | minor | PROJECT Active list and auth wording | fixed | Waitlist join added to Active; out-of-scope says "no role matrix", not "no authorization". |
| R2-26 | minor | FEATURES capacity-change placement | fixed | Moved out of v1.x by wording; the slice stays v2. |
| R2-27 | minor | PITFALLS contention citation | fixed | Cites RACE-01 and RACE-04. |


## Round 2 technical findings

| ID | Severity | Claim | Status | Resolution |
|----|----------|-------|--------|------------|
| V1–V12 | — | Axon Server/image tags, `@EventTag` targets, starter→connector, licensing, Jackson skew, `StreamingCondition`, `@EventSourced`, `@JvmStatic`, artifact existence | confirmed | No change; the round-1 fixes hold under independent reproduction. |
| V13 | major | `java-platform` cannot import BOMs inside `constraints { }`; platforms refuse real dependencies | fixed | `javaPlatform { allowDependencies() }` + `api(platform(...))` in `dependencies`. |
| V14 | major | `implementation(project(":courses-platform"))` is not a library dependency | fixed | `implementation(platform(project(":courses-platform")))` in all three services. |
| V15 | minor | `jackson-module-kotlin` resolves to 3.1.7 while databind is 3.2.3 | fixed | `:courses-platform` imports `tools.jackson:jackson-bom:3.2.3`. |
| V16 | minor | Versionless `kotlin-reflect` resolves to 2.3.21 via Boot's Kotlin BOM | fixed | `version.ref = "kotlin"`. |
| V17 | minor | This record's finding totals did not reconcile with its tables | fixed | Recounted above. |
| V18 | minor | "All blockers and majors resolved" was untrue | fixed | Two majors accepted/deferred explicitly. |
| V19 | minor | `spring-boot-starter-web` is deprecated in Boot 4 | fixed | Switched to `spring-boot-starter-webmvc`. |
| V12 note | — | A missing `@EventCriteriaBuilder` fails silently, falling back to tag+id criteria | fixed | Risk row updated; ENRL-06 requires a rendered-criteria assertion, not just stored tags. |


## Round 3 — full re-review after the SSE/FSD rework

Treated as a new round 1 under the protocol: the changes touched constraints (topology, live updates, frontend architecture, formatting). Three reviewers ran in parallel.

| Area | Findings | Status |
|---|---|---|
| Consistency | 1 blocker (race response defined twice), 12 majors (stale tables after the topology change, `courses-platform` wording, README claims, summary/phase drift), 18 minors | All blockers and majors fixed |
| Requirements/roadmap | 1 blocker (same race contract), 18 requirement findings, 13 roadmap findings (Phase 5 bloat, Phase 8 mixing SSE and seeding, race proved last-but-one, stale summary map) | All blockers and majors fixed |
| Technical | 2 majors (AF4-shaped SSE API; missing `flyway-database-postgresql`), 13 minor notes | Both majors fixed |

What changed as a result:

- Race contract unified as `{enrolled, full, conflicted, maxInFlight}` over N **distinct** students; `RACE-02` asserts exactly one `enrolled` and `enrolled + full + conflicted == N`; the boundary proof moved to a forced-overlap variant.
- Phase structure rebuilt to 11 phases: FSD and formatting gates moved to Phase 1, the race moved to Phase 8 (before read-side completion), Phase 8 split into "my enrollments + seeding" (9) and "live updates" (10), documentation last (11).
- Live updates redesigned: `projection`+`query` merged into one `courses-read` service, so the emitter, the writer and the query handler live in one process. `ViewChanged` and the `CommandGateway` notification were deleted — a command cannot honestly carry a fact.
- AF 5.4 API corrected: `subscriptionQuery` returns one `Publisher`; updates are emitted with `QueryUpdateEmitter` from `AFTER_COMMIT`; `Flux` is returned directly.
- `reactor-core` declared explicitly; `flyway-database-postgresql` added; `courses-platform` uses `allowDependencies()` + `api(platform(...))`; Jackson BOM imported in `:courses-platform`.
- Module names settled as `courses-write` / `courses-read` / `courses-api` for CQRS symmetry.

Residual minors accepted without change: historical review entries still mention former module names; `.prettierignore` excludes more than the prose lists (now documented as "honours .prettierignore").


### Round 4 technical findings

| ID | Severity | Claim | Status |
|----|----------|-------|--------|
| T1 | major | `reactor-core` placed in `courses-read` instead of `courses-api` | fixed: moved to the `courses-api` snippet |
| T2 | major | `TOPO-02` still said "no dependency on `courses-write` or `query`" | fixed: `courses-read` |
| T3 | major | Jackson BOM reasoning was wrong — Boot manages module-kotlin at 3.1.7; Axon's 3.2.3 applies to optional deps | fixed: explicit Jackson import removed, reasoning corrected |
| T4 | major | tooling script accepted Node 20 while the installer requires >= 24 | fixed: version check and docs |
| T5–T9 | minor | stale `projection`/`query` wording, `@EntityCreator` package, `emit` return value, SSE timeout claim, `.opencode` wording | fixed |
| T10 | minor | `format` job relies on the runner's preinstalled Node and a 5-minute timeout | fixed: timeout 10 minutes |
| T13 | minor | "verified on Gradle 9.9.0" against an 8.14 pin | fixed: verified on 8.14.3, reproduced on 9.9 |
| T11 | unverified | `steiger` fails the build only with `--fail-on-warnings` | fixed in docs; verified in Phase 1 when `courses-frontend/package.json` exists |
| T12 | unverified | Axon Server DCB availability under default configuration | recorded as a Phase 4 criterion |

## Open items carried forward

1. **Phase 2:** compose services vs Testcontainers for integration tests — decided there, with one test in CI.
2. **Phase 4:** commercial starter on Boot 4.1.2 — proven by boot, not by docs.
3. **Phase 9:** `AxonTestFixture` cannot test contention — harness approach already specified.
4. **Licensing:** 12-hour unlicensed Axon Server window — documented, mitigated by a dev license or restart.

---

# Round 1 — 2026-11-12 (the `courses` refactor)

Trigger: the uncommitted refactor renamed the domain, split the frontend into its own deployable, added
`build-logic`, ADRs 0001–0010, Dependabot, GitHub-native vulnerability scanning, the buildpack-updates
workflow, the prerequisites report and the `connect-mcp-servers` skill. Three reviewers ran in parallel:
consistency, requirements/roadmap quality, technical fact-check.

**Totals:** 55 findings — 2 blockers, 22 major, 31 minor (consistency 0/10/10, requirements/roadmap
2/9/9, technical 3/12/0).

| ID | Severity | Status | Resolution |
|----|----------|--------|------------|
| RQ-01 | blocker | fixed | Added AUTH-05: the acting `studentId` comes from the principal, and a value supplied in the body is 403. |
| RM-01 | blocker | fixed | E2E-02 now names the mechanism: the e2e job builds the four images before `docker compose up -d --wait`; ADR-0005 records that job as the single exception to "CI does not build them". |
| C-01 | major | fixed | Removed "serves SPA" from both research tables and SUMMARY; the Phase 1 plan line is marked as superseded by ADR-0004. |
| C-02 | major | fixed | The six topology guard rules moved into `CONVENTIONS.md` as bullets, because the `AGENTS.md` generator drops a paragraph-and-heading block there; the rules are now readable in `AGENTS.md`. The generator's empty `## Shape` / `### Read-side caching` sections remain a generator quirk — the sources are authoritative. |
| C-03 | major | fixed | The negative control gets its own source set (`src/negativeControl/kotlin`, `*NC`) and `negativeControlTest` task; CONVENTIONS, RACE-04 and PITFALLS T2 now agree, and no tag filtering is reintroduced. |
| C-04 | major | fixed | PITFALLS T3 and the Phase 2 open item now point at ADR-0003 (Testcontainers) instead of re-opening the decision. |
| C-05 | major | fixed | "and Jackson BOMs" removed from both architecture tables; Boot manages Jackson. |
| C-06 | major | fixed | Added a canonical endpoint table to REQUIREMENTS; ARCHITECTURE and FEATURES now use the same enroll path. |
| C-07 | major | fixed | STATE.md frontmatter moved to phase 2, progress and velocity recomputed from six plans. |
| C-08 | major | fixed | `frontend-conventions` added to both tables; each convention now names the modules that apply it. |
| C-09 | major | fixed | Added the `dev` Exec task to `courses-frontend`, so the documented `./gradlew :courses-frontend:dev` exists. |
| C-10 | major | fixed | PITFALLS L4 restated in reactive terms (cancel the subscription in `doFinally`) and explicitly forbids `SseEmitter`. |
| RQ-02 | major | fixed | RACE-01 defines `maxInFlight` as the peak of issued-but-unreturned `EnrollStudent` sends, sampled from before the barrier releases. |
| RQ-03 | major | fixed | ENRL-04 now defines the seat-available and repeat-join rejections. |
| RQ-04 | major | fixed | PLAT-03 split; PLAT-07 covers the live path and is mapped to Phase 10. |
| RQ-05 | major | fixed | PLAT-02 fixes the seed: one admin, three students with committed UUIDv7 ids, README credentials, three scenarios. |
| RQ-06 | major | fixed | Added a canonical-rejections table; PLAT-04 references it. |
| RM-02 | major | fixed | Phase 2 criterion 4 now requires Testcontainers, not a Compose stack. |
| RM-03 | major | fixed | Phase 4 may use a throwaway projection and query handler, both removed in Phase 5. |
| RM-04 | major | fixed | Phase 6 criterion 1 asserts a repeated catalog GET; the push is Phase 10. |
| RM-05 | major | fixed | Same fix as C-07. |
| T-01 | major | fixed | The `dev` task now exists (C-09). |
| T-02 | major | fixed | The catalog snippet in STACK is labelled as the research proposal, with the committed catalog as the source of truth; `node-gradle` removed. |
| T-03 | major | fixed | The compose snippet is valid YAML again, uses `${PROJECT_VERSION:-…}`, and defers to the committed `compose.yaml`. |
| C-11…C-20, RQ-07…RQ-09, RM-06…RM-11, T-04…T-15 | minor | fixed | Text-only corrections applied: module count, port notes, CORS scope, compose service keys, bounded-context name, Gradle 9.9.1, npm-not-node-gradle, future suites, actionlint wording, `build` gate wording, PostgreSQL only in the read suite, SPA on `:3000`, dependabot grouping wording, unreferenced catalog entries, `/gsd-progress`, backlog triggers. |
| RM-10 / RM-11 | minor | partly accepted | The backlog items now carry a trigger and a done condition; the image-scanning item records that CI must publish images first, which is itself a later decision (amend ADR-0005 when it fires). |

Round 1 closed with no open blocker or major. Round 2 re-reviews the changed corpus for regressions.

## Round 2 — 2026-11-12 (regression control)

Two reviewers, diff-scoped: consistency and technical fact-check. **25 findings — 1 blocker, 8 major,
16 minor.**

| ID | Severity | Status | Resolution |
|----|----------|--------|------------|
| R2-C-01 / R2-T-01 | blocker | fixed | RACE-01 no longer takes a course id; RACE-06 owns creation; Phase 8 cites RACE-01 + RACE-06, and RACE-05 moved to Phase 9. |
| R2-C-02 / R2-T-09 | major | fixed | Every "images are not built in CI" claim now names the e2e job as the single exception. |
| R2-C-03 | major | fixed | DROP-01 separates the command rejection (`not enrolled`) from the HTTP 403; FEATURES has no `enrollments/{studentId}`. |
| R2-C-04 | major | fixed | RACE-05 mapped to Phase 9 and asserted by its criterion 3. |
| R2-C-05 | major | fixed | SUMMARY per-phase lists gained AUTH-05, RACE-06 and PLAT-07. |
| R2-C-06 | major | fixed | Testcontainers no longer re-opened in SUMMARY or STACK. |
| R2-C-07 | major | fixed | PROJECT out-of-scope narrowed to production auth (AUTH-04) with one admin and three students. |
| R2-T-02 | major | fixed | STACK pseudo-code and idempotency bullet use the canonical rejection strings. |
| R2-T-03 | major | fixed | PostgreSQL is attributed to the read-side suite only. |
| R2-C-08…C-12, R2-T-04…T-13 | minor | fixed | Endpoint path, `state.json` cache, three conventions in ARCHITECTURE, CONVENTIONS pointer, STATE activity and risks, catalog sections, negative-control tense, dependabot grouping, ADR-0006 images, backlog done condition, shutdown wiring, module count, credential criterion. |

## Round 3 — 2026-11-12 (verification, hard cap)

Two reviewers verified the round-2 fixes. **All round-2 items closed except three minor wording
leftovers; 6 new majors were found and fixed immediately**: the seed count in FEATURES/SUMMARY, the
canonical `course is full` spelling in five places, the SSE test seam attributed to Phase 10 instead
of Phase 8, the credential-mode criterion pointing at an ADR it never decided (now ADR-0011), the
enroll pseudo-code conflating `invalid course` with a missing course (now `CourseNotFound` → 404), and
the tally identity missing from RACE-02.

Per the converged-loop cap, the loop **stops here**: round 3 found majors, so a fourth verification
round needs an explicit human decision rather than another automatic pass. Current state: no known open
blocker or major; the findings fixed in round 3 have not been re-verified by a reviewer.

---

# Round 1 — 2026-11-12 (second cycle: unprefixed endpoints, no demo endpoint)

Three reviewers over the current corpus. **Findings: 46 — 1 blocker, 12 major, 33 minor** (consistency
1/6/8, requirements 0/5/9, technical 0/6/9, with overlap counted once).

Blockers and majors, all fixed:

| ID | Severity | Resolution |
|----|----------|------------|
| N-C-01 / N-RQ-01 / N-T-03 | blocker+major | The four accounts moved out of PLAT-02 into AUTH-01: they are fixed configuration seeded in every profile (`POST /session`), while PLAT-02 owns only the three demo courses, which the E2E profile disables. CONVENTIONS, STACK and PITFALLS no longer claim a test can create its own student. |
| N-C-02 / N-RQ-06 / N-T-02 | major | The canonical table gained `POST /session`, `DELETE /session`, `GET /me`, `GET /courses/{courseId}`, and the SSE route was renamed `/courses/{courseId}/view` to stop calling read-model snapshots "events". |
| N-C-03 / N-T-05 | major | `DemoRaceController`, the `race-demo` page, the `race/run` slice, the race panel and "five screens" removed from both research trees and FEATURES. |
| N-C-04 / N-RQ-05 / N-T-08 | major | `RACE-05` removed from SUMMARY; module count and numbering corrected. |
| N-C-05 / N-RM-02 / N-T-04 | major | The race test is an `*IT` class run by `--tests '*LastSeatRaceIT'`; the "own Gradle task" wording is gone, so ADR-0003 and the suite convention hold. |
| N-C-06 | major | ADR-0011 now decides `SameSite=Lax` as the CSRF posture instead of promising a Phase 2 addition. |
| N-RQ-03 | major | PLAT-03 narrowed to "no datasource in `courses-write`"; the read-cycle clause joined PLAT-07. |
| N-RM-01 | major | DOCS-02 mapped to Phase 7 (the last builder) and Phase 6 says so in its criterion. |
| N-RQ-04 | major | PROJECT's Key Decision now reads "last-seat race as an executable test", with no demo endpoint or UI trigger. |
| N-T-01 | major | The code caught up with ADR-0011: `WebCorsConfiguration` now allows credentials. |
| N-RQ-07…N-RQ-12, N-C-08…N-C-15, N-T-06…N-T-15 | minor | Testable ENRL-07 wording, RACE-03 HTTP clause dropped in favour of PLAT-04, 403 row covers AUTH-02, 2-second interval measured from the emit, guard rules marked as Phase-2 obligations, fixed-id rule scoped to unit/slice tests, `dev` task comments, STATE body, ADR-0002/0009 wording, Phase 8 UI hint, dates. Historical Phase-1 artifacts carry a superseded note instead of being rewritten. |

`./gradlew build` and `format-check` pass. A round 2 verification is next.

## Round 2 — 2026-11-12 (second cycle, verification)

Two reviewers verified the round-1 fixes. **11 findings — 4 major, 7 minor**, all fixed:

- `POST /session` was not proxied in the Vite dev server, so the documented login would 404; `/session`
  joined `/courses` and `/me` in the proxy and in the three places that list it.
- The race command was unscoped: root `./gradlew integrationTest --tests '*LastSeatRaceIT'` fails in
  modules without that class, because Gradle's test filter defaults to failing on no match. It is now
  `:courses-api:integrationTest --tests '*LastSeatRaceIT'`.
- FEATURES still attributed the fixed accounts to PLAT-02; they belong to AUTH-01.
- SUMMARY still mapped DOCS-02 to Phase 6; moved to Phase 7.
- Minor: "ids are never generated" restated for the test classes it actually governs, "read-model
  deltas" corrected to whole snapshots, the two-second window measured from the emit in the roadmap
  too, Phase 2's 401 reconciled with PLAT-04's 401/403, RACE-01 no longer implies a student-creation
  slice, ADR-0011's `SameSite=None` rationale un-inverted, and the FSD tree's `shared/courses-api`
  naming aligned with `shared/api` (the tree itself was corrected in round 3).

Phase-1 artifacts keep their original `/api` proxy and `dist/` lines: they are a record of what was
built then, and `SKELETON.md` carries the superseded note. Accepted as historical, not rewritten.

`./gradlew build` and `format-check` pass. Round 3 verification is next.

## Round 3 — 2026-11-12 (second cycle)

One reviewer over the round-2 fixes. **3 findings — 2 major, 1 minor**, all fixed:

- the FSD tree correction had landed inverted (`shared/courses-api/` instead of `shared/api/`), and the
  round-2 record claimed a fix the tree did not carry — both corrected;
- Phase 8 still said the race creates "students it creates itself", which no requirement allows; it now
  matches RACE-01: a course the test creates plus N distinct `studentId` values at the command level;
- PROJECT's Core Value still said "two concurrent enrollments" while REQUIREMENTS and STATE say N.

## Round 4 — 2026-11-12 (narrow convergence check)

A single narrow check over those three fixes and the four invariants (49/49 mapping, per-phase
equality, endpoint table completeness, no deleted paths or IDs outside history). **No open blockers or
majors.** The loop converges here for the second cycle.
