---
phase: 01-build-and-infrastructure-skeleton
verified: 2026-11-11T00:41:17Z
status: passed
score: 7/7 success criteria verified
covered_files: [".github/workflows/ci.yml", ".gitignore", ".planning/phases/01-build-and-infrastructure-skeleton/01-01-PLAN.md", ".planning/phases/01-build-and-infrastructure-skeleton/01-01-SUMMARY.md", ".planning/phases/01-build-and-infrastructure-skeleton/01-02-PLAN.md", ".planning/phases/01-build-and-infrastructure-skeleton/01-02-SUMMARY.md", ".planning/phases/01-build-and-infrastructure-skeleton/01-03-PLAN.md", ".planning/phases/01-build-and-infrastructure-skeleton/01-03-SUMMARY.md", ".planning/phases/01-build-and-infrastructure-skeleton/01-04-PLAN.md", ".planning/phases/01-build-and-infrastructure-skeleton/01-04-SUMMARY.md", ".planning/phases/01-build-and-infrastructure-skeleton/01-05-PLAN.md", ".planning/phases/01-build-and-infrastructure-skeleton/01-05-SUMMARY.md", ".planning/phases/01-build-and-infrastructure-skeleton/01-06-PLAN.md", ".planning/phases/01-build-and-infrastructure-skeleton/01-06-SUMMARY.md", "api/build.gradle.kts", "api/src/main/kotlin/io/github/aanbrn/showcase/ApiApplication.kt", "api/src/main/resources/application.yml", "api/src/test/kotlin/io/github/aanbrn/showcase/api/AxonServerRegistrationIT.kt", "build.gradle.kts", "compose.yaml", "docs/tooling.md", "domain/build.gradle.kts", "frontend/.gitignore", "frontend/.oxlintrc.json", "frontend/.prettierignore", "frontend/build.gradle.kts", "frontend/index.html", "frontend/package-lock.json", "frontend/package.json", "frontend/src/app/index.css", "frontend/src/app/main.tsx", "frontend/src/pages/home/index.ts", "frontend/src/pages/home/ui/HomePage.tsx", "frontend/src/shared/ui/AppShell.tsx", "frontend/src/shared/ui/index.ts", "frontend/steiger.config.js", "frontend/tsconfig.app.json", "frontend/tsconfig.json", "frontend/tsconfig.node.json", "frontend/vite.config.ts", "gradle.properties", "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties", "opencode.jsonc", "platform/build.gradle.kts", "read/build.gradle.kts", "read/src/main/kotlin/io/github/aanbrn/showcase/ReadApplication.kt", "read/src/main/resources/application.yml", "read/src/test/kotlin/io/github/aanbrn/showcase/read/AxonServerRegistrationIT.kt", "scripts/smoke-stack.sh", "settings.gradle.kts", "write/build.gradle.kts", "write/src/main/kotlin/io/github/aanbrn/showcase/WriteApplication.kt", "write/src/main/resources/application.yml", "write/src/test/kotlin/io/github/aanbrn/showcase/write/AxonServerRegistrationIT.kt", "write/src/test/kotlin/io/github/aanbrn/showcase/write/InfrastructureReachabilityTest.kt"]
covered_digest: "v4:sha256:fe6682687a4c70d5c7a56a8258922e529e446c00d0baad130d83e6eb1bc61c50"
behavior_unverified: 0
overrides_applied: 0
---

# Phase 1: Build and infrastructure skeleton Verification Report

**Phase Goal:** `docker compose up -d` plus one Gradle run starts Axon Server, PostgreSQL and three empty Spring Boot services; the SPA is part of the same build graph; formatting, FSD and Playwright MCP are wired and CI enforces them.
**Verified:** 2026-11-11T00:41:17Z
**Status:** passed
**Re-verification:** No — initial verification

## Verdict

All seven roadmap success criteria are observably true in the codebase, and the two
behaviour-dependent ones (three services registering with Axon Server; the FSD gate rejecting a
violation) were re-run live rather than accepted from the summaries. No gaps block the phase goal.

## Criterion-by-Criterion Results

| # | Success criterion | Status | Evidence |
|---|-------------------|--------|----------|
| 1 | Compose brings up Axon Server and PostgreSQL; one Gradle run starts three services that all register with the server (PLAT-01) | PASS | `docker compose ps` → `axonserver` (`axoniq/axonserver:2026.1.7`) and `postgres` (`postgres:18.6`) both `Up (healthy)`; `docker compose config` shows `axoniq_axonserver_standalone: "true"` and the PG18 volume at `/var/lib/postgresql`. `./gradlew bootRun --parallel --dry-run` resolves to exactly `:write:bootRun`, `:read:bootRun`, `:api:bootRun`. A live run answered `/actuator/health` `UP` on 8081/8082/8080, and the Axon Server log recorded `Application connected: read`, `Application connected: write`, `Application connected: api` … `context = default` at 00:40:57Z. Per-service `AxonServerRegistrationIT` asserts `connections()["default"] == true` and passed fresh for all three (07:39 run: `tests="1" failures="0"` each) |
| 2 | Each service is an independent Spring Boot application producing its own bootJar (TOPO-01) | PASS | `write/build/libs/write-0.1.0-SNAPSHOT.jar`, `read/build/libs/read-0.1.0-SNAPSHOT.jar`, `api/build/libs/api-0.1.0-SNAPSHOT.jar`; each jar contains its own `BOOT-INF/classes/io/github/aanbrn/showcase/*Application.class` and a disabled plain `jar` task. `read`/`api` declare no JPA/JDBC/Flyway/datasource; `api` declares no `project(":write")`/`project(":read")` |
| 3 | `./gradlew build` produces the SPA bundle and all three bootJars with no separate npm invocation (TOPO-04) | PASS | `./gradlew build --dry-run` lists `:frontend:assembleFrontend`, `:frontend:check`, `:api:processResources`, `:api:bootJar`, `:read:bootJar`, `:write:bootJar`; `./gradlew build --no-daemon` → `BUILD SUCCESSFUL in 6s`. Integration tests are tag-excluded: `grep -c integrationTest` on the dry-run is `0`, so the build needs no Docker |
| 4 | CI builds the project on every PR, starts Axon Server and PostgreSQL for integration tests, and names the job that proves they ran (PLAT-05) | PASS | `.github/workflows/ci.yml` `build` job: `actions/setup-java` (21), `actions/setup-node` (24.20.0, cached on `frontend/package-lock.json`), `docker compose up -d --wait`, `./gradlew build --no-daemon`, `./gradlew integrationTest --no-daemon`, `docker compose logs` on failure, `docker compose down -v --remove-orphans` on `if: always()`. Triggers on `pull_request` to `main`; job id stays `build` (`format` in `ci.yml`, `title` in `pr-title.yml`). `actionlint` on both workflows is clean |
| 5 | CI fails on unformatted Kotlin, TS/TSX and docs/workflows (QUAL-01) | PASS | Kotlin: Spotless `ktlint` convention on every Kotlin subproject, `spotlessCheck` attached to `check` inside `./gradlew build` (green). TS/TSX: `:frontend:check` → `formatCheck` → `npm run format:check` (`All matched files use Prettier code style!`). Repo-level: `format` CI job runs `./scripts/format-check.sh` → `npx prettier@3.9.10 --check --editorconfig .`, which covers `docs/`, `.github/workflows/` and root JSON (verified exit 0) |
| 6 | SPA scaffolded by FSD layers with a public `index.ts` per slice, and `npm run lint:fsd` fails on a layer violation or a deep import (FE-01, FE-02) | PASS | Tree: `app` → `pages/home` → `shared/ui`; each slice has `index.ts` re-exporting its `ui/`. `npm run lint:fsd` → `✔ No problems found!`. Re-proved both negative controls myself: a deep import `@/shared/ui/AppShell` fails oxlint `eslint(no-restricted-imports)` (exit 1); an upward import `@/pages/home` from `shared/ui` fails steiger `fsd/forbidden-imports` (exit 1). Temporary probes removed — `git status` clean |
| 7 | Tracked `opencode.jsonc` configures the Playwright MCP server, and an agent can open the SPA shell headless through it (E2E-01) | PASS | `opencode.jsonc` → `mcp.servers.playwright` = `["npx","-y","@playwright/mcp@0.0.85","--headless"]`. Re-ran the proof live against the three-service stack: `browser_navigate http://localhost:8080/` → Page Title `Axon 5 DCB Showcase`; snapshot shows `heading "Course catalog" [level=1]` inside `main`; `browser_evaluate` → `{title:"Axon 5 DCB Showcase", hasAppShell:true, heading:"Course catalog"}`. Snapshot artifact written under `.playwright-mcp/` (gitignored) |

## Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Compose stack healthy with Axon Server standalone + PG18 volume path | ✓ VERIFIED | `docker compose config`, `docker compose ps` |
| 2 | One Gradle run starts three services on 8081/8082/8080 | ✓ VERIFIED | `bootRun --parallel` dry-run + live run, three `/actuator/health` `UP` |
| 3 | All three services register with Axon Server | ✓ VERIFIED | Axon Server log `Application connected: write/read/api … context = default`; three fresh `AxonServerRegistrationIT` runs |
| 4 | Three independent bootJars | ✓ VERIFIED | `*/build/libs/*-0.1.0-SNAPSHOT.jar` + embedded app classes |
| 5 | `./gradlew build` is Docker-free | ✓ VERIFIED | zero `integrationTest` tasks in build dry-run |
| 6 | SPA embedded at `BOOT-INF/classes/static/index.html` | ✓ VERIFIED | `unzip -l api/…jar` shows exactly one `static/index.html`, one JS and one CSS; `uniq -d` empty |
| 7 | FSD gate passes and rejects both violation classes | ✓ VERIFIED | `lint:fsd` exit 0; two injected probes exit 1 |
| 8 | Prettier gates pass for SPA and repo-level files | ✓ VERIFIED | `npm run format:check`, `./scripts/format-check.sh` |
| 9 | Playwright MCP drives the SPA headless | ✓ VERIFIED | Live MCP navigation + `data-testid="app-shell"` evaluate |

**Score:** 7/7 success criteria verified (0 present-but-behaviour-unverified)

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `compose.yaml` | Axon Server 2026.1.7 + PostgreSQL 18.6, healthchecks, standalone | ✓ VERIFIED | Both containers healthy |
| `settings.gradle.kts` | Six modules included | ✓ VERIFIED | `platform, domain, write, read, api, frontend` |
| `platform/build.gradle.kts` | `java-platform` re-exporting Boot + Axoniq BOMs | ✓ VERIFIED | `allowDependencies()` + both `api(platform(...))` |
| `write|read|api/build.gradle.kts` | Boot apps, own bootJar, `integrationTest` tag split | ✓ VERIFIED | Real, non-stub; `jar` disabled |
| `*/application.yml` | name + fixed port + Axon servers | ✓ VERIFIED | 8081 / 8082 / 8080 |
| `*/…/AxonServerRegistrationIT.kt` | Registration gate on `connections()["default"]` | ✓ VERIFIED | Present in all three; passed fresh |
| `frontend/build.gradle.kts` | npm-driven tasks + `assemble`/`check` wiring | ✓ VERIFIED | `npmInstall`, `assembleFrontend`, `lintFsd`, `formatCheck` |
| `frontend/src/**` | FSD tree with public `index.ts` | ✓ VERIFIED | 6 source files, 3 layers |
| `scripts/smoke-stack.sh` | One-command stack proof | ✓ VERIFIED | Executable, shellcheck-clean per summary; structure re-read |
| `.github/workflows/ci.yml` | `build` job with Node, Compose, integration tests, teardown | ✓ VERIFIED | `actionlint` clean |
| `opencode.jsonc` | Playwright MCP entry, headless | ✓ VERIFIED | Version-pinned command |
| `docs/tooling.md` | Run loop + license window | ✓ VERIFIED | `bootRun --parallel`, `12-hour`, `integrationTest` references |

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `write`/`read`/`api` build files | `:platform` | `implementation(platform(project(":platform")))` | ✓ WIRED | All three |
| `api/build.gradle.kts` | `frontend/dist` | `processResources` `dependsOn(":frontend:assembleFrontend")` + `from(...)` | ✓ WIRED | Bundle present in jar |
| `frontend/build.gradle.kts` | npm scripts | `Exec` tasks `npm ci` / `npm run build|lint:fsd|format:check` | ✓ WIRED | Ran in the build graph |
| `ci.yml` `build` job | `compose.yaml` | `docker compose up -d --wait` | ✓ WIRED | Present |
| `ci.yml` `build` job | `*/integrationTest` | `./gradlew integrationTest --no-daemon` | ✓ WIRED | Present |
| `pages/home/ui/HomePage.tsx` | `shared/ui` | `@/shared/ui` public API import | ✓ WIRED | Rendered in browser |
| `app/main.tsx` | `pages/home` | `@/pages/home` public API import | ✓ WIRED | Rendered in browser |

## Data-Flow Trace (Level 4)

Not applicable in the dynamic-data sense: Phase 1 services are deliberately empty Boot apps and the
SPA is a static shell. The one span that could be hollow — SPA bytes reaching the browser — was
traced end to end: `frontend/src` → `vite build` → `frontend/dist` → `processResources` →
`BOOT-INF/classes/static` → served by `api` on 8080 → rendered in the headless browser with the
expected heading. ✓ FLOWING.

## Behavioral Spot-Checks

| Behaviour | Command | Result | Status |
|-----------|---------|--------|--------|
| Build graph is Docker-free | `./gradlew build --dry-run` \| `grep -c integrationTest` | `0` | ✓ PASS |
| Build passes | `./gradlew build --no-daemon` | `BUILD SUCCESSFUL in 6s` | ✓ PASS |
| Three bootRun tasks | `./gradlew bootRun --parallel --dry-run` | `:api:bootRun`, `:read:bootRun`, `:write:bootRun` | ✓ PASS |
| Three services register | live `bootRun --parallel` + Axon Server log | `Application connected: write/read/api … context = default` | ✓ PASS |
| Registration ITs | `./gradlew integrationTest --tests '*AxonServerRegistrationIT' --rerun` | 3 tasks executed, 3 tests, 0 failures | ✓ PASS |
| FSD gate green | `npm --prefix frontend run lint:fsd` | `✔ No problems found!` exit 0 | ✓ PASS |
| Deep import rejected | probe file + `npx oxlint` | exit 1, `no-restricted-imports` | ✓ PASS |
| Layer violation rejected | probe file + `npx steiger ./src --fail-on-warnings` | exit 1, `fsd/forbidden-imports` | ✓ PASS |
| SPA formatting | `npm --prefix frontend run format:check` | clean, exit 0 | ✓ PASS |
| Repo formatting | `./scripts/format-check.sh` | clean, exit 0 | ✓ PASS |
| Playwright MCP headless | `browser_navigate` + `browser_evaluate` | title `Axon 5 DCB Showcase`, `hasAppShell: true` | ✓ PASS |
| Workflow validity | `actionlint` on both workflows | clean | ✓ PASS |

## Probe Execution

Step 7c: no `scripts/*/tests/probe-*.sh` files exist and no PLAN/SUMMARY declares one — not applicable.

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PLAT-01 | 01-01, 01-02, 01-04 | `docker compose up -d` + one Gradle run starts Axon Server, PostgreSQL and all three services | ✓ SATISFIED | Compose healthy; three services up; Axon Server log records all three connected |
| PLAT-05 | 01-05 | CI builds the whole project and runs unit + distributed integration tests on every PR, starting Axon Server and PostgreSQL | ✓ SATISFIED | `ci.yml` `build` job (structure + actionlint + locally reproduced steps) |
| QUAL-01 | 01-01, 01-03, 01-05 | Spotless, frontend Prettier and repository Prettier gates run in CI | ✓ SATISFIED | `spotlessCheck` in `build`; `:frontend:check` → `formatCheck`; `format` job → `format-check.sh` |
| E2E-01 | 01-06 | Playwright MCP configured in tracked config and drives the SPA headless | ✓ SATISFIED | `opencode.jsonc` entry + live headless navigation |
| TOPO-01 | 01-01, 01-02 | Three independent Boot apps with own bootJars | ✓ SATISFIED | Three jars, own app classes |
| TOPO-04 | 01-04 | `./gradlew build` produces SPA bundle and three bootJars in one graph | ✓ SATISFIED | Dry-run + build + jar contents |
| FE-01 | 01-03 | FSD layers, one-way imports, public `index.ts` per slice | ✓ SATISFIED | Tree + steiger clean |
| FE-02 | 01-03 | `npm run lint:fsd` passes in CI and fails on violations | ✓ SATISFIED | Green run + two negative controls |

No orphaned requirements: every Phase 1 ID in `REQUIREMENTS.md` is claimed by a plan.

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No `TBD`/`FIXME`/`XXX`, no `TODO`/`HACK`/`PLACEHOLDER`, no empty-body handlers, and no hardcoded-empty data path in phase files | ℹ️ Info | None |

The three service classes are intentionally empty (Phase 1 scope); they are not stubs of a promised
behaviour — their only Phase 1 obligation (boot and register) is proven.

## Deferred Items

| Item | Addressed In | Evidence |
|------|--------------|----------|
| Playwright end-to-end regression suite (`frontend/e2e/`, `e2e` CI job) | Phase 8 | Phase 8 SC 5: "Playwright runs the full user journey against the Compose stack … and the job publishes its report" (E2E-02) |
| JPA + Flyway + PostgreSQL datasource in `read` | Phase 5 | Phase 5 SC 5: "`read` is the only module with JPA and Flyway" (RDMD-01/RDMD-02) |

## Notes / Residual Risks

- **MVP mode label.** ROADMAP.md marks Phase 1 `Mode: mvp`, but its goal is infrastructure, not a
  user story. The User Flow Coverage format does not apply because no user flow exists yet; the
  roadmap success criteria are the contract and were verified in full.
- **First real CI run.** The `build` job is validated structurally (`actionlint`) and every step it
  runs was reproduced locally, but GitHub Actions itself has not executed it yet. Cold-runner
  Compose pull timing is the only untested variable; the 30-minute job timeout is the margin and the
  job id does not change if `services:` is later substituted.
- **Local Axon Server state.** A stale volume can leave the `default` context inactive while the
  healthcheck is green; `docker compose down -v --remove-orphans` is the documented remedy and is
  already the script's teardown.
- `.playwright-mcp/` artifacts are gitignored by design; the MCP proof is reproducible with the
  documented command and the tracked config.

---

_Verified: 2026-11-11T00:41:17Z_
_Verifier: the agent (gsd-verifier)_

VERIFICATION: PASSED
