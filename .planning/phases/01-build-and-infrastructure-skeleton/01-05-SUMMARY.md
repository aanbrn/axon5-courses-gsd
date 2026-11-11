---
phase: 01-build-and-infrastructure-skeleton
plan: 05
subsystem: infra
tags: [github-actions, ci, docker-compose, integration-test, prettier, gitignore, playwright-mcp]

# Dependency graph
requires:
  - phase: 01-build-and-infrastructure-skeleton
    provides: 01-01 Gradle skeleton (gradlew, multi-module build, integrationTest task shape)
    provides: 01-02 read/api services (compose-backed Axon Server registration tests)
    provides: 01-03 frontend module (frontend/package-lock.json for the npm cache key)
provides:
  - CI `build` job that starts Axon Server + PostgreSQL, builds Docker-free, runs integrationTest, and tears down
  - setup-node step pinned to a full commit SHA, cached on frontend/package-lock.json
  - `.playwright-mcp/` gitignored so an agent MCP verification run leaves no untracked noise
affects: [phase-02-write-side-dcb, phase-08-e2e-ci-job]

actuals:
  tokens: 917
  tasks: 2
  commits: 2

tech-stack:
  added:
    - actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1 (v7.1.0), installed in CI only
  patterns:
    - "Compose, not `services:` — CI reuses the local compose.yaml so image tags have one source of truth"
    - "Docker-free `./gradlew build` — the distributed proof is a separate tag-gated `integrationTest` task"
    - "Every third-party action pinned to a full commit SHA with a version comment"

key-files:
  created: []
  modified:
    - .github/workflows/ci.yml
    - .gitignore
    - compose.yaml

key-decisions:
  - "The integration test stays a separate `integrationTest` task; folding it into `./gradlew build` would make every local build require Docker"
  - "Infrastructure is started with `docker compose up -d --wait` instead of GitHub Actions `services:`, so the local and CI stacks cannot drift"
  - "`build` job timeout raised 20 -> 30 minutes to absorb container pull plus the integration run"
  - "The wrapper-detect placeholder was removed from the build job now that `gradlew` is committed"

patterns-established:
  - "Required-check names are load-bearing: `build`, `format`, `title` job ids never change"
  - "CI failure gives diagnostics via `docker compose logs --no-color` on `if: failure()`, and teardown is unconditional"

requirements-completed: [PLAT-05, QUAL-01]

coverage:
  - id: D1
    description: "CI `build` job starts Axon Server and PostgreSQL, runs `./gradlew build`, runs `./gradlew integrationTest`, and always tears the stack down"
    requirement: PLAT-05
    verification:
      - kind: other
        ref: "./scripts/format-check.sh && actionlint .github/workflows/ci.yml (job-id and step presence asserted by the plan's automated verify); a full GitHub Actions run is the runtime proof"
        status: pass
    human_judgment: false
  - id: D2
    description: "`format` and `title` jobs (QUAL-01 repository formatting and the PR-title convention) are untouched and still green"
    requirement: QUAL-01
    verification:
      - kind: other
        ref: "./scripts/format-check.sh — 'All matched files use Prettier code style!'"
        status: pass
    human_judgment: false
  - id: D3
    description: "`.playwright-mcp/` is gitignored, so an MCP-driven verification run leaves `git status` clean"
    verification:
      - kind: other
        ref: "grep -n playwright-mcp .gitignore -> 51:.playwright-mcp/"
        status: pass
    human_judgment: false

duration: 12min
completed: 2026-11-11
status: complete
---

# Phase 1 Plan 5: CI enforcement of the build and distributed test gates

**`build` CI job now starts Axon Server and PostgreSQL, builds Docker-free, runs the tag-gated `integrationTest`, and tears down unconditionally — with `format`/`title` untouched and `.playwright-mcp/` ignored.**

## Performance

- **Duration:** ~12 min (approximate — the start timestamp was not captured separately)
- **Completed:** 2026-11-11
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- `build` job extended: `actions/setup-node` (v7.1.0, full SHA, npm cache keyed on `frontend/package-lock.json`, Node 24.20.0), `docker compose up -d --wait`, `./gradlew build --no-daemon`, `./gradlew integrationTest --no-daemon`, `docker compose logs --no-color` on failure, and `docker compose down -v --remove-orphans` on `if: always()`.
- `./gradlew build` stays Docker-free: the distributed proof is the separate tagged `integrationTest` task, so a local build never needs a running server.
- The wrapper-detect placeholder is gone; `timeout-minutes` raised to 30.
- `.playwright-mcp/` added to the Playwright block in `.gitignore` (the MCP server writes snapshots there, distinct from `.playwright/`).
- `compose.yaml` reformatted by the sanctioned `scripts/format.sh` to keep the `format` job green (see Deviations).

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend the `build` CI job with Node, Compose, and the integration test** — `44d6129` (ci)
2. **Task 2: Ignore the Playwright MCP snapshot directory** — `b8cf334` (chore)

**Plan metadata:** `d1c3e74` (docs: complete plan)

Measured against the plan ledger `plan_head_before: 650980ffe2fe08e7f0ccd2bb9475f2c3f62812d6` → `plan_head_after: b8cf334f6c6605151a0edd1f38f3f07c16919317` = 2 commits.

## Files Created/Modified

- `.github/workflows/ci.yml` — `build` job rewritten: setup-java + setup-node (both full-SHA pinned), Compose start, Docker-free build, integration tests, failure logs, unconditional teardown. `format` job unchanged.
- `.gitignore` — `.playwright-mcp/` added next to `.playwright/`; existing `.opencode` rules and `.planning/research/.cache/` left intact.
- `compose.yaml` — Prettier normalization only (double → single quotes); no behavioural change.

## Decisions Made

- **Keep `./gradlew build` Docker-free.** The tag split is the guard: if the integration test leaked into `test`, every local build would need a server and the loop would die.
- **Compose over `services:`.** One `compose.yaml` means the image tags (`axoniq/axonserver:2026.1.7`, `postgres:18.6`) cannot drift between local and CI. `services:` remains the fallback if the runner proves flaky, and the required check name is `build` either way.
- **`actions/setup-node` pinned to `949feb2…` (v7.1.0)**, re-verified against the GitHub API during this run, with a version comment per T-01-07.
- **No `pull_request_target`, no title interpolation.** The PR-title pattern continues to live in `pr-title.yml` with the environment-variable hand-off, untouched.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `compose.yaml` failed the Prettier format gate**
- **Found during:** Task 1 verification (`./scripts/format-check.sh`)
- **Issue:** `compose.yaml`, committed by an earlier plan in this phase, used double quotes where Prettier expects single; the `format` job would have been red independently of this plan's change. Task 1's automated verify runs `format-check.sh`, so it blocked completion.
- **Fix:** Ran the sanctioned normalizer `./scripts/format.sh`, which rewrote only `compose.yaml` (quote style). `ci.yml` was already Prettier-clean.
- **Files modified:** `compose.yaml`
- **Verification:** `./scripts/format-check.sh` → "All matched files use Prettier code style!"; `actionlint` on both workflows → clean.
- **Committed in:** `44d6129` (part of Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Quote-style only; no behavioural change to the stack. Required to keep the `format` job green, which is this plan's QUAL-01 deliverable.

## Issues Encountered

- None. `actionlint` is available locally and validated both workflows (job ids, expressions, `if:` usage).

## Known Stubs

None. Neither file introduces placeholder values, TODOs, or unwired data paths.

## Threat Flags

None new. The change adds one third-party action (`actions/setup-node`), pinned to a full commit SHA with a version comment per T-01-07; no new secrets, endpoints, or trust-boundary surface.

## User Setup Required

None — no external service configuration required. The integration test path needs Docker on the runner, which `ubuntu-24.04` provides out of the box.

## Next Phase Readiness

- PLAT-05 and QUAL-01 are now enforced on every PR to `main` without renaming a required check.
- Phase 2 can add command handlers and tests knowing `./gradlew build` stays fast and Docker-free locally, while CI still exercises the distributed path.
- Phase 8 owes the `e2e` job (Playwright suite); the `build` job shape added here is the template for it.
- Unverified until the first CI run: Compose startup timing on a cold runner (the 30-minute job timeout is the margin). If it proves flaky, switch to `services:` — the job id does not change.

---
*Phase: 01-build-and-infrastructure-skeleton*
*Completed: 2026-11-11*

## Self-Check: PASSED

All created/modified files exist and both task commits are ancestors of HEAD.
