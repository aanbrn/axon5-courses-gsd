---
phase: 01-build-and-infrastructure-skeleton
plan: 06
subsystem: tooling-verification
tags: [playwright, mcp, opencode, docker-compose, axon-server, documentation]

requires:
  - plan: 01-04
    provides: SPA served from the api bootJar and the full-stack smoke script
  - plan: 01-05
    provides: gradlew committed plus the build/integrationTest CI job

provides:
  - Recorded Playwright MCP headless proof that the tracked config drives the running SPA
  - "Running the stack locally" section documenting compose, bootRun ports, the smoke script
  - Axon registration proven by `integrationTest --tests '*AxonServerRegistrationIT'`, not health
  - The 12-hour unlicensed Axon Server window, stated instead of discovered mid-demo
  - The real `build` job description replacing the stale "once gradlew exists" placeholder

affects: [phase-08-e2e, demo-runbook, frontend-verification]

actuals:
  tokens: 1326
  tasks: 2
  commits: 1

tech-stack:
  added: []
  patterns:
    - "Agent-driven headless UI verification through the tracked Playwright MCP server"
    - "Documented two-command local run loop with a license-window caveat"

key-files:
  created: []
  modified:
    - docs/tooling.md

key-decisions:
  - "E2E-01 evidence is recorded in this SUMMARY, not encoded as a shell test: MCP navigation is agent-driven, and only the config half is greppable"
  - "The headless proof covers title, `Course catalog` heading and `data-testid=\"app-shell\"`, because the accessibility snapshot does not surface test ids"
  - "CI is documented as Docker-capable end to end: compose up, Docker-free `./gradlew build`, then tagged `integrationTest`"

patterns-established:
  - "Verification claims separate what a shell check proves from what an agent observed"

requirements-completed: [E2E-01]

coverage:
  - id: D1
    description: "The tracked opencode.jsonc carries the Playwright MCP server with the --headless flag"
    requirement: E2E-01
    verification:
      - kind: other
        ref: "grep -q '@playwright/mcp@0.0.85' opencode.jsonc && grep -q -- '--headless' opencode.jsonc"
        status: pass
    human_judgment: false
  - id: D2
    description: "An agent session drives the running SPA headless through that server and observes the shell"
    requirement: E2E-01
    verification:
      - kind: automated_ui
        ref: "playwright MCP, 2026-11-11: browser_navigate http://localhost:8080/ -> title 'Axon 5 DCB Courses'; browser_snapshot heading 'Course catalog' [level=1]; browser_evaluate data-testid app-shell -> true"
        status: pass
    human_judgment: true
    rationale: "The navigation is an agent-driven MCP session, not a repeatable suite: no Phase 1 test starts a browser, so a human must accept the recorded transcript as the proof."
  - id: D3
    description: "docs/tooling.md documents the local run loop, the registration proof and the 12-hour license window"
    requirement: E2E-01
    verification:
      - kind: other
        ref: "./scripts/format-check.sh && grep bootRun --parallel docs/tooling.md && grep -qi '12-hour|12 hour|license window' docs/tooling.md && ! grep 'once `gradlew` exists' docs/tooling.md && grep integrationTest docs/tooling.md"
        status: pass
    human_judgment: false

duration: 9min
completed: 2026-11-11
status: complete
---

# Phase 1 Plan 06: Playwright MCP headless proof and the local run loop Summary

**The tracked Playwright MCP server drove the running SPA headless — title, `Course catalog` heading and
`data-testid="app-shell"` all observed — and `docs/tooling.md` now documents the two-command run loop,
the registration proof and the 12-hour unlicensed Axon Server window.**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-11-11T00:26Z (approx.)
- **Completed:** 2026-11-11T00:35Z
- **Tasks:** 2/2
- **Files modified:** 1

## Accomplishments

- Proved E2E-01 end to end: the config already tracked in `opencode.jsonc` (`npx -y
  @playwright/mcp@0.0.85 --headless`) navigated the SPA served by `courses-api` on `:8080` and the
  accessibility tree showed the expected shell — no config edit was needed.
- Documented the local run loop in `docs/tooling.md`: `docker compose up -d --wait` for Axon Server
  (8024/8124) and PostgreSQL (5432), `./gradlew bootRun --parallel` for `courses-write` (8081), `courses-read` (8082)
  and `courses-api` (8080), plus what `./scripts/smoke-stack.sh` does and when to reach for it.
- Stated that Axon registration is proven by `integrationTest --tests
  '*AxonServerRegistrationIT'`, never by `/actuator/health`, matching what the smoke script asserts.
- Replaced the stale CI paragraph ("builds once `gradlew` exists, until then the step is skipped")
  with the real `build` job: setup-node, `docker compose up -d --wait`, Docker-free
  `./gradlew build`, tagged `integrationTest`, failure logs and always-teardown — plus the required
  `format` and `title` checks.
- Recorded the 12-hour unlicensed Axon Server window, the restart remedy and the local-only
  `courses/courses/courses` credential, so nobody presents the stack as production-ready.

## E2E-01 Evidence

Executed against `docker compose up -d --wait` plus `./gradlew bootRun --parallel` (all three
services reported `{"groups":["liveness","readiness"],"status":"UP"}`):

1. `browser_navigate` → `http://localhost:8080/`
   - **Page URL:** `http://localhost:8080/`
   - **Page Title:** `Axon 5 DCB Courses`
2. `browser_snapshot` →

   ```yaml
   - generic [ref=e2]:
     - banner [ref=e3]:
       - strong [ref=e4]: Axon 5 DCB Courses
     - main [ref=e5]:
       - heading "Course catalog" [level=1] [ref=e6]
   ```

3. `browser_evaluate` `() => document.querySelector('[data-testid="app-shell"]') !== null` → `true`
4. `curl -fsS http://localhost:8080/ | grep -q '<div id="root">'` → served the SPA shell from the
   api jar (`<title>Axon 5 DCB Courses</title>`, `<div id="root"></div>`).

Only console entry: `404` for `/favicon.ico` — pre-existing and out of scope for this docs-only
plan (see Deferred Issues).

**Scope of the proof:** step 3 is what a shell verify cannot reproduce — the automated check in this
plan (`grep` for `@playwright/mcp@0.0.85` and `--headless` in `opencode.jsonc`) proves only the
"configured" half of E2E-01. The navigation above is a recorded agent session, and it is the half
that shows the server can actually drive the SPA.

## Task Commits

1. **Task 1: Drive the SPA headless through Playwright MCP and record the proof** — no file delta:
   the plan requires no config change and the deliverable is the recorded evidence above.
2. **Task 2: Document the local run loop and the 12-hour license window** — `cd02165` (docs)

**Plan metadata:** committed separately (`docs(01-06): complete ... plan`).

## Files Created/Modified

- `docs/tooling.md` — Playwright MCP usage note, new "Running the stack locally" section (ports,
  smoke script, registration proof, license window) and the rewritten CI section.

## Decisions Made

- **Evidence lives in the SUMMARY, not in a script.** The plan says so explicitly, and a shell test
  cannot start a browser session; claiming otherwise would overstate what CI proves.
- **Test ids are asserted with `browser_evaluate`, not the snapshot.** The accessibility tree omits
  `data-testid`, so the heading alone would leave the `app-shell` element unverified.
- **The CI section describes the job as it is, not as planned.** `build` runs after Compose is up and
  the integration test is a separate tagged task, so "Docker-free build" is a property of
  `./gradlew build`, not of the job.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `set -m` fails in this shell, so the stack was launched through bash**
- **Found during:** Task 1 (starting the stack for the MCP navigation)
- **Issue:** the plan's `set -m … ./gradlew bootRun --parallel … &` snippet ran in zsh, which reports
  `zsh:set:1: can't change option: -m` and exits 1 before Gradle starts.
- **Fix:** ran the same sequence under `bash -c 'set -m; … &'`, giving the job its own process group;
  the group was later reaped with `kill -- "-PGID"` (the same technique `scripts/smoke-stack.sh`
  uses).
- **Files modified:** none — process invocation only.
- **Verification:** all three services reached `status":"UP"`, the SPA answered on 8080, and ports
  8080/8081/8082 were released after the kill.
- **Committed in:** n/a (no repository change)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** No scope change; the launch mechanism differs, the outcome the plan asked for
(the stack up for MCP navigation) is identical.

## Issues Encountered

- None blocking. The MCP server wrote its snapshot and console log into `.playwright-mcp/`, which
  plan 01-05 already added to `.gitignore` — `git status` stayed clean, confirming that fix.

## Deferred Issues

- `GET /favicon.ico` returns 404, so every browser session logs one console error. Cosmetic and
  outside this plan's files; a frontend asset task should add a favicon.
- Axon Server is still running unlicensed from this session (12-hour window). Restarting the
  container starts a fresh window; the window is now documented rather than surprising.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- E2E-01 is closed: the tracked config drives the SPA headless and the transcript is recorded.
- The local run loop is reproducible from `docs/tooling.md` alone.
- Phase 8's Playwright suite can reuse this MCP setup for exploratory checks; the real regression
  suite still has to be authored there against the full Compose stack.

---

*Phase: 01-build-and-infrastructure-skeleton*
*Completed: 2026-11-11*

## Self-Check: PASSED

- FOUND: `docs/tooling.md`
- FOUND: `.planning/phases/01-build-and-infrastructure-skeleton/01-06-SUMMARY.md`
- FOUND: commit `cd02165` (ancestor of HEAD)

## Later changes

- `docs/tooling.md` now documents Testcontainers-backed integration suites (no pre-started Compose,
  no tag filters) and a `build` job without Compose steps.
