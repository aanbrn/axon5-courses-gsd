---
phase: 01-build-and-infrastructure-skeleton
plan: 04
subsystem: build-infra

tags: [gradle, processresources, bootjar, spa, smoke-script, process-group, axon-server, integration-test]

requires:
  - phase: 01-build-and-infrastructure-skeleton
    provides: "api and read service modules with their AxonServerRegistrationIT gates, :frontend:assembleFrontend producing dist/, and the compose stack"

provides:
  - "api bootJar carries the Vite bundle at BOOT-INF/classes/static (processResources dependsOn :frontend:assembleFrontend)"
  - "scripts/smoke-stack.sh: compose up --wait, per-service Axon registration gate, bootRun --parallel liveness polls, SPA-shell fetch, process-group teardown"
  - "PLAT-01 proven end to end: registration asserted inside write, read and api rather than scraped from /actuator/health"

affects: [phase-2, phase-5, phase-8]

actuals:
  tokens: 1002
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "A build artifact is copied, not built twice: processResources pulls :frontend/dist into static/ so the SPA ships inside the executable jar from the same ./gradlew build"
    - "A smoke script proves registration by running the per-module integration tests (--rerun), never by scraping /actuator/health"
    - "set -m job control plus kill -- \"-${RUN_PID}\" reaps the bootRun tasks and their three JVMs; setsid is unavailable on macOS"
    - "mkdir -p build before redirecting bootRun output, because a clean tree has no build/ directory"

key-files:
  created:
    - scripts/smoke-stack.sh
  modified:
    - api/build.gradle.kts

key-decisions:
  - "Registration is verified inside the smoke script by ./gradlew integrationTest --tests '*AxonServerRegistrationIT' --rerun, so one command proves both liveness and Axon registration"
  - "--rerun is mandatory on that invocation: the test task's inputs do not include Axon Server availability, so a cached result would make a repeat run prove nothing"
  - "set -m (POSIX job control) replaces setsid, which does not exist on darwin; the process-group kill is what prevents three orphaned JVMs"
  - "cleanup runs on every exit path (success, failure, INT, TERM) and always kills the process group before docker compose down -v --remove-orphans"

patterns-established:
  - "SPA-in-jar: one Gradle graph produces the bundle and the executable jar, no separate npm step"
  - "Smoke script failure path tails build/bootrun.log so a service that never became healthy is diagnosable without a rerun"

requirements-completed: [TOPO-04, PLAT-01]

coverage:
  - id: D1
    description: "The api executable jar embeds the Vite bundle at BOOT-INF/classes/static/index.html with no duplicate entries"
    requirement: "TOPO-04"
    verification:
      - kind: other
        ref: "./gradlew :api:bootJar --no-daemon && unzip -l api/build/libs/api-0.1.0-SNAPSHOT.jar#static/index.html once, static/assets/index-*.{js,css}"
        status: pass
    human_judgment: false
  - id: D2
    description: "One Docker-free ./gradlew build produces the SPA bundle, the three bootJars and stays free of integration tests"
    requirement: "TOPO-04"
    verification:
      - kind: other
        ref: "./gradlew build --no-daemon --dry-run#grep -c integrationTest = 0"
        status: pass
    human_judgment: false
  - id: D3
    description: "write, read and api each register with Axon Server (connections()[\"default\"] == true) in the live stack"
    requirement: "PLAT-01"
    verification:
      - kind: integration
        ref: "write|read|api AxonServerRegistrationIT#1 test each, 0 failures, run at 07:31"
        status: pass
    human_judgment: false
  - id: D4
    description: "One script brings up Compose plus the three services, proves liveness on 8081/8082/8080 and fetches the SPA shell from api"
    requirement: "PLAT-01"
    verification:
      - kind: e2e
        ref: "./scripts/smoke-stack.sh#EXIT=0, port 8081: up / port 8082: up / port 8080: up / SPA shell served by api"
        status: pass
    human_judgment: false
  - id: D5
    description: "The script is shellcheck-clean, executable, and leaves no service JVM or container behind"
    requirement: "PLAT-01"
    verification:
      - kind: other
        ref: "shellcheck scripts/smoke-stack.sh#no findings; lsof on 8080-8082 empty and docker compose ps empty after EXIT=0"
        status: pass
    human_judgment: false

duration: 4min
completed: 2026-11-11
status: complete
plan_head_before: 4c108d78f4e495d34d6eb90c99467301c32be803
plan_head_after: 2886e9f96a87c40364a21ba26fdb13307da2eb5d
---

# Phase 1 Plan 04: Build and infrastructure skeleton Summary

**The Vite bundle now ships inside `api`'s executable jar through `processResources`, and
`scripts/smoke-stack.sh` proves the live stack in one command: Compose healthy, all three services
registered with Axon Server, three actuator liveness polls green, and the SPA shell served by `api`.**

## Performance

- **Duration:** ~4 min (242 s)
- **Started:** 2026-11-11T00:27:53Z
- **Completed:** 2026-11-11T00:31:55Z
- **Tasks:** 2/2
- **Files created:** 1; modified: 1
- **Diff size:** 4,007 characters added ≈ 1,002 estimateTokens

## Accomplishments

- `./gradlew :api:bootJar` produces a jar whose `BOOT-INF/classes/static/` holds exactly one
  `index.html`, one hashed JS bundle and one hashed CSS file — no duplicate entries
  (`unzip -l … | sort | uniq -d` is empty).
- The copy is wired, not scripted: `processResources` depends on `:frontend:assembleFrontend` and
  reads `frontend/dist`, so a clean `./gradlew build` produces both the SPA and the jar.
- `./gradlew build --no-daemon` still succeeds and `--dry-run` contains zero `integrationTest`
  tasks, so the Docker-free build guarantee survives.
- `./scripts/smoke-stack.sh` exits 0 and prints, in order: `port 8081: up`, `port 8082: up`,
  `port 8080: up`, `SPA shell served by api`, `stack is up: …`.
- Registration is asserted inside each app — `write`, `read` and `api` `AxonServerRegistrationIT`
  each ran one test with zero failures at 07:31 — not scraped from `/actuator/health`, which carries
  no Axon component in this starter combination (RESEARCH § P5).
- Teardown is verified, not assumed: after a green run no process listens on 8080/8081/8082 and
  `docker compose ps` is empty.

## Task Commits

1. **Task 1: Embed the SPA bundle in the `api` bootJar** — `f63f9bd` (feat)
2. **Task 2: Add the full-stack smoke script** — `2886e9f` (feat)

**Plan metadata:** the SUMMARY commit that follows (docs). Measured commits in this plan: 2.

## Files Created/Modified

- `api/build.gradle.kts` — added, after the `jar { enabled = false }` line:

  ```kotlin
  tasks.named<ProcessResources>("processResources") {
      dependsOn(":frontend:assembleFrontend")
      from(project(":frontend").layout.projectDirectory.dir("dist")) { into("static") }
  }
  ```

  Nothing else in the build script changed.
- `scripts/smoke-stack.sh` (new, mode 100755) — `set -euo pipefail`; tool guard for `curl`, `docker`
  and an executable `./gradlew`; `cleanup` trap on `EXIT`/`INT`/`TERM` that kills
  `"-${RUN_PID}"` then `docker compose down -v --remove-orphans`; `docker compose up -d --wait`;
  `./gradlew integrationTest --tests '*AxonServerRegistrationIT' --rerun --no-daemon`; `mkdir -p
  build`; `set -m` + `./gradlew bootRun --parallel --no-daemon --console=plain > build/bootrun.log
  2>&1 &`; `wait_for_health` polling each of 8081/8082/8080 on `/actuator/health` for 60 × 2 s and
  tailing the log on timeout; a final `curl -fsS http://localhost:8080/ | grep -q '<div id="root">'`.

## Verification Run

| Command | Result |
|---|---|
| `./gradlew :api:bootJar --no-daemon` | pass — `BUILD SUCCESSFUL in 8s`, `:api:processResources` executed |
| `unzip -l api/build/libs/api-0.1.0-SNAPSHOT.jar \| grep 'BOOT-INF/classes/static'` | pass — `static/index.html`, `static/assets/index-B7Fg37H_.js`, `static/assets/index-Clt-HEf6.css` |
| `unzip -l … \| grep -c 'BOOT-INF/classes/static/index.html'` | pass — `1` |
| `unzip -l … \| awk '{print $4}' \| sort \| uniq -d` | pass — no duplicate entries |
| `./gradlew build --no-daemon` | pass — `BUILD SUCCESSFUL in 8s`, 39 tasks |
| `./gradlew build --dry-run --no-daemon \| grep -c integrationTest` | pass — `0` (build stays Docker-free) |
| `bash -n scripts/smoke-stack.sh` | pass — syntax OK |
| `shellcheck scripts/smoke-stack.sh` | pass — no findings |
| `test -x scripts/smoke-stack.sh` | pass — `-rwxr-xr-x` |
| `./scripts/smoke-stack.sh` | pass — `EXIT=0`, all expected lines printed |
| `grep -E 'integrationTest' $TMPDIR/smoke-01-04b.log` | pass — `:api:integrationTest`, `:read:integrationTest`, `:write:integrationTest` all executed (not `UP-TO-DATE`) |
| `grep 'tests=' <module>/build/test-results/integrationTest/*.xml` | pass — each of write/read/api: `tests="1" skipped="0" failures="0" errors="0"` |
| `lsof -nP -iTCP:8080/8081/8082 -sTCP:LISTEN` after the run | pass — no listener; the process group was reaped |
| `docker compose ps` after the run | pass — empty; `down -v --remove-orphans` ran |
| `./scripts/format-check.sh` | pass — "All matched files use Prettier code style!" |

### Smoke script output

```text
==> docker compose up -d --wait
==> Axon registration for write, read and api
> Task :api:integrationTest
> Task :read:integrationTest
> Task :write:integrationTest
all three services registered with Axon Server
==> ./gradlew bootRun --parallel (log: build/bootrun.log)
port 8081: up
port 8082: up
port 8080: up
SPA shell served by api
stack is up: Axon Server, PostgreSQL, write, read and api
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] The smoke script proves Axon registration, not only
liveness**
- **Found during:** Task 2
- **Issue:** the plan's task action says registration is "not this script's job", yet a script whose
  only evidence is `/actuator/health` cannot distinguish a service that registered with Axon Server
  from one that is merely serving HTTP — and in this starter combination the health endpoint has no
  Axon component at all (RESEARCH § P5). The execution directive for this plan requires registration
  to be proven for all three services by the script.
- **Fix:** the script runs `./gradlew integrationTest --tests '*AxonServerRegistrationIT'` before
  launching `bootRun`, so the per-service `connections()["default"] == true` assertion is part of the
  same one-command proof. It runs before `bootRun` so the two Gradle invocations never contend for the
  build lock, and the registration tests use `webEnvironment = NONE`, so no port conflicts arise.
- **Files modified:** `scripts/smoke-stack.sh`
- **Verification:** all three `integrationTest` tasks execute and pass; the script still prints the
  four lines the plan's acceptance criteria require.
- **Committed in:** `2886e9f` (Task 2 commit)

**2. [Rule 2 - Missing critical functionality] `--rerun` on the registration gate**
- **Found during:** Task 2 (second script run)
- **Issue:** the first repeat run reported `:read:integrationTest UP-TO-DATE` and
  `:write:integrationTest UP-TO-DATE`. The test task's inputs do not include Axon Server
  availability, so a cached success would make every subsequent smoke run "pass" without ever
  touching Axon Server — a check that silently no-ops is worse than no check.
- **Fix:** `--rerun` on the requested tasks (Gradle 9.7 supports it; dependencies are untouched), so
  the assertion always executes.
- **Files modified:** `scripts/smoke-stack.sh`
- **Verification:** `grep integrationTest` on a repeat run shows all three tasks executed, and the
  XML reports carry the new run's timestamp with `failures="0"`.
- **Committed in:** `2886e9f` (Task 2 commit)

### Noted, not changed

- The script tears the stack down on success as well as failure (`docker compose down -v`), matching
  the RESEARCH snippet and the plan's "always kill the process group" instruction. A developer who
  wants the stack left running still has `docker compose up -d --wait && ./gradlew bootRun --parallel`.
- `cleanup` also runs `docker compose down -v --remove-orphans` when `docker compose up` itself fails;
  the `>/dev/null 2>&1 || true` guard keeps that path from masking the original exit status.
- Gradle leaves idle single-use daemons behind for a while after a `--no-daemon` build. They are
  Gradle's own daemons, not service JVMs; no process listens on 8080/8081/8082 after a run, which is
  the guarantee that matters.

## Threat Surface

No new surface beyond the plan's threat model. The script binds the same three development ports,
tears down the stack and the process group on every exit path (T-01-06), and introduces no new
network endpoint, credential or file-access pattern. No package installs were added (T-01-SC).

## Known Stubs

None. Both artifacts are complete: the jar ships the real built bundle, and the script's four checks
are all assertions over live state, with no placeholder or skipped step.

## Next Phase Readiness

- TOPO-04 is closed: `./gradlew build` alone produces the SPA bundle inside `api`'s bootJar plus all
  three bootJars; no separate npm step is needed anywhere.
- PLAT-01 is closed by two independent proofs: `./gradlew integrationTest --tests
  '*AxonServerRegistrationIT'` (also wired into the CI `build` job by Plan 01-05) and
  `./scripts/smoke-stack.sh`.
- Phase 2 can add routes and API slices without touching the build graph; deep links still need a
  history-mode decision (RESEARCH Open Question 2) — do not add a `/**` forward, it would swallow
  `/actuator`.

---

*Phase: 01-build-and-infrastructure-skeleton*
*Completed: 2026-11-11*

## Self-Check: PASSED

- `api/build.gradle.kts` and `scripts/smoke-stack.sh` exist; the script is executable (mode 100755).
- Both task commits (`f63f9bd`, `2886e9f`) are ancestors of `HEAD`.
- `git rev-list --count 4c108d7..HEAD` = 2, matching the `commits: 2` frontmatter value.
