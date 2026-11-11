---
phase: 01-build-and-infrastructure-skeleton
plan: 01
subsystem: infra
tags: [gradle, kotlin, spring-boot, axon, docker-compose, spotless]

requires: []

provides:
  - "Gradle build graph: six-module settings, version catalog, root Spotless convention, wrapper 9.7.0"
  - "platform java-platform re-exporting the Spring Boot 4.1.2 and Axoniq 5.4.1 BOMs"
  - "Empty domain Kotlin library compiled against :platform"
  - "write Spring Boot service (port 8081) with the commercial Axoniq starter and no datasource"
  - "compose.yaml with Axon Server 2026.1.7 (standalone) and PostgreSQL 18.6, both healthchecked"
  - "PLAT-01 registration proof from inside the app (connections()[\"default\"] == true)"

affects: [01-02, 01-03, 01-04, 01-05, 01-06, phase-2, phase-3]

actuals:
  tokens: 5300
  tasks: 4
  commits: 3

tech-stack:
  added:
    - "Gradle wrapper 9.7.0 (KGP 2.4.21 supported ceiling)"
    - "Kotlin 2.4.21 / JVM toolchain 21"
    - "Spring Boot 4.1.2 via :platform"
    - "Axoniq Framework BOM + starter 5.4.1"
    - "Spotless 8.11.0 with ktlint 1.8.0"
    - "Axon Server 2026.1.7 and PostgreSQL 18.6 in Compose"
    - "Awaitility (Boot-BOM managed) for the registration assertion"
  patterns:
    - "BOM propagation through implementation(platform(project(\":platform\")))"
    - "Spotless convention applied only to subprojects with the Kotlin JVM plugin"
    - "Integration tests tag-gated into a separate integrationTest task so ./gradlew build stays Docker-free"
    - "Axon registration asserted from AxonServerConnectionManager, never from /actuator/health"

key-files:
  created:
    - settings.gradle.kts
    - gradle/libs.versions.toml
    - gradle.properties
    - build.gradle.kts
    - gradle/wrapper/gradle-wrapper.properties
    - platform/build.gradle.kts
    - domain/build.gradle.kts
    - compose.yaml
    - write/build.gradle.kts
    - write/src/main/kotlin/io/github/aanbrn/showcase/WriteApplication.kt
    - write/src/main/resources/application.yml
    - write/src/test/kotlin/io/github/aanbrn/showcase/write/AxonServerRegistrationIT.kt
    - write/src/test/kotlin/io/github/aanbrn/showcase/write/InfrastructureReachabilityTest.kt
  modified: []

key-decisions:
  - "Wrapper pinned to Gradle 9.7.0 exactly: inside KGP 2.4.21's tested ceiling and above Boot 4.1.2's floor"
  - "Plain jar disabled in write so bootJar is the only artifact in build/libs"
  - "Placeholder .gitkeep files in read/api/frontend because Gradle 9 refuses to configure included projects without an existing directory"

patterns-established:
  - "Shared BOM platform: api(platform(libs.spring.boot.bom)) + api(platform(libs.axoniq.bom)) with allowDependencies()"
  - "Kotlin formatting: ktlint reads the root .editorconfig; Spotless attached to check"
  - "Registration gate: @Tag(\"integration\") + Awaitility on connections()[\"default\"]"

requirements-completed: [PLAT-01, TOPO-01, QUAL-01]

coverage:
  - id: D1
    description: "Gradle build graph (catalog, root build, wrapper 9.7.0) resolves Boot 4.1.2 and Axon 5.4.1 through :platform"
    requirement: "TOPO-01"
    verification:
      - kind: other
        ref: "./gradlew :domain:build#BUILD SUCCESSFUL"
        status: pass
    human_judgment: false
  - id: D2
    description: "compose.yaml brings Axon Server 2026.1.7 and PostgreSQL 18.6 up healthy with the standalone flag and PG18 volume path"
    requirement: "PLAT-01"
    verification:
      - kind: integration
        ref: "docker compose up -d --wait#both containers Healthy"
        status: pass
    human_judgment: false
  - id: D3
    description: "write service boots on Boot 4.1.2 and registers with Axon Server"
    requirement: "PLAT-01"
    verification:
      - kind: integration
        ref: "write/src/test/kotlin/io/github/aanbrn/showcase/write/AxonServerRegistrationIT.kt#write service registers with Axon Server"
        status: pass
    human_judgment: false
  - id: D4
    description: "Infrastructure reachability proven without a JDBC driver (Axon info endpoint 200, PostgreSQL TCP accept)"
    requirement: "PLAT-01"
    verification:
      - kind: integration
        ref: "write/src/test/kotlin/io/github/aanbrn/showcase/write/InfrastructureReachabilityTest.kt#both tests"
        status: pass
    human_judgment: false
  - id: D5
    description: "write bootJar is the single artifact at write/build/libs/write-0.1.0-SNAPSHOT.jar"
    requirement: "TOPO-01"
    verification:
      - kind: other
        ref: "./gradlew :write:bootJar && ls write/build/libs#write-0.1.0-SNAPSHOT.jar"
        status: pass
    human_judgment: false
  - id: D6
    description: "Spotless ktlint gate is green and attached to check"
    requirement: "QUAL-01"
    verification:
      - kind: other
        ref: "./gradlew spotlessApply spotlessCheck#BUILD SUCCESSFUL, no diff after apply"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-11-11
status: complete
---

# Phase 1 Plan 01: Build and infrastructure skeleton Summary

**Gradle 9.7.0 + Kotlin 2.4.21 build graph that compiles an empty `domain` library against a BOM
platform, plus a `write` Boot 4.1.2 service that boots and proves Axon Server 2026.1.7 registration
from inside the JVM.**

## Performance

- **Duration:** ~25 min (includes the one-time 9.7.0 distribution download)
- **Started:** 2026-11-11T00:00:00Z
- **Completed:** 2026-11-11T00:12:15Z
- **Tasks:** 4/4
- **Files modified:** 19 (13 hand-written, 6 wrapper/launcher artifacts, 3 placeholder `.gitkeep`)

## Accomplishments

- Version catalog + `java-platform` resolve Spring Boot 4.1.2 and the commercial Axoniq 5.4.1 BOM
  from one place; `:domain:build` compiles with no repositories error and no variant error.
- `compose.yaml` brings Axon Server 2026.1.7 (with `axoniq_axonserver_standalone: "true"`) and
  PostgreSQL 18.6 (volume at `/var/lib/postgresql`) to `Healthy`, so the `AXONIQ-1302` trap and the
  PG18 volume trap are both closed.
- `write` connects to the server and `AxonServerRegistrationIT` asserts
  `connections()["default"] == true` with Awaitility (no `Thread.sleep`).
- `./gradlew build` succeeds with Docker stopped: integration tests are tag-gated out of `test`.

## Task Commits

1. **Task 1: Scaffold the Gradle build graph, wrapper, and platform/domain** — `ed1201d` (chore)
2. **Task 2: Wire the write service through to Axon Server registration** — `2f305e3` (feat)
3. **Task 3: Add infrastructure reachability tests and prove the bootJar** — `500e151` (test)
4. **Task 4: Lock in Kotlin formatting with Spotless** — no commit (verification only; the Task 1
   convention already satisfied the gate)

**Plan metadata:** the SUMMARY commit that follows (docs).

## Files Created/Modified

- `settings.gradle.kts` — root name, six includes, foojay resolver, mavenCentral
- `gradle/libs.versions.toml` — Kotlin 2.4.21, Boot 4.1.2, Axon 5.4.1, Spotless 8.11.0, ktlint 1.8.0
- `gradle.properties` — 2g heap, build cache, official Kotlin style (no configuration cache)
- `build.gradle.kts` — plugins `apply false`, group/version, Spotless convention per Kotlin subproject
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat` — wrapper pinned to 9.7.0
- `platform/build.gradle.kts` — `java-platform`, `allowDependencies()`, both BOMs
- `domain/build.gradle.kts` — Kotlin library on toolchain 21 consuming `platform(project(":platform"))`
- `compose.yaml` — Axon Server + PostgreSQL with healthchecks
- `write/build.gradle.kts` — Boot app, Axoniq starter, `integrationTest` task, plain jar disabled
- `write/src/main/kotlin/io/github/aanbrn/showcase/WriteApplication.kt` — entry point
- `write/src/main/resources/application.yml` — name `write`, port 8081, `axon.axonserver.servers`
- `write/src/test/.../AxonServerRegistrationIT.kt` — PLAT-01 registration gate
- `write/src/test/.../InfrastructureReachabilityTest.kt` — Axon info endpoint + PostgreSQL TCP
- `read/.gitkeep`, `api/.gitkeep`, `frontend/.gitkeep` — module directory placeholders

## Decisions Made

- Wrapper pinned to 9.7.0 exactly (KGP 2.4.21 tested ceiling, Boot 4.1.2 needs >= 8.14).
- Registration is asserted through `AxonServerConnectionManager`, since the Axon health indicator is
  absent from `/actuator/health` in this starter combination.
- The plain `jar` artifact is disabled so the acceptance criterion "exactly one jar" holds.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Gradle 9 refuses included projects without a directory**
- **Found during:** Task 1 (wrapper generation)
- **Issue:** `Configuring project ':frontend' without an existing directory is not allowed`, then the
  same for `:write`, because `settings.gradle.kts` includes six modules while this plan owns only four
  of them.
- **Fix:** created `read/`, `api/`, `frontend/` and `write/` directories with `.gitkeep` placeholders
  (`write/.gitkeep` removed in Task 2 once real files landed). Plans 01-02 and 01-03 populate the
  remaining placeholders.
- **Files modified:** `read/.gitkeep`, `api/.gitkeep`, `frontend/.gitkeep`, `write/.gitkeep`
- **Verification:** `./gradlew :domain:build` and the wrapper task both succeed.
- **Committed in:** `ed1201d` (Task 1 commit)

**2. [Rule 1 - Bug] bootJar produced a second plain jar**
- **Found during:** Task 3 (bootJar proof)
- **Issue:** `write/build/libs` contained `write-0.1.0-SNAPSHOT.jar` **and**
  `write-0.1.0-SNAPSHOT-plain.jar`, violating the acceptance criterion of a single artifact.
- **Fix:** `tasks.jar { enabled = false }` in `write/build.gradle.kts`; `:write:jar` now reports
  `SKIPPED`.
- **Files modified:** `write/build.gradle.kts`
- **Verification:** removed `write/build/libs`, ran `:write:assemble`, only
  `write-0.1.0-SNAPSHOT.jar` remained.
- **Committed in:** `500e151` (Task 3 commit)

**3. [Rule 3 - Blocking] IDE-created `.java-version` noise**
- **Found during:** Task 3
- **Issue:** an untracked `.java-version` (content `21`) appeared in the repo root while Gradle ran.
- **Fix:** deleted it; it did not recur across later Gradle runs, so no `.gitignore` change was made.
- **Committed in:** not applicable (no repository change)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug) + 1 no-op cleanup
**Impact on plan:** Both fixes were required for the plan's own acceptance criteria; no scope creep.

## Issues Encountered

- The wrapper distribution download made the first `:domain:build` take 2.5 minutes; subsequent builds
  are seconds.
- `spotlessApply` removed one blank line after the class opening brace in the registration IT — that
  normalisation is part of commit `500e151`.

## Known Stubs

| File | Reason |
|---|---|
| `read/.gitkeep`, `api/.gitkeep`, `frontend/.gitkeep` | Module directories must exist for Gradle 9 to configure the included projects; plan 01-02 creates `read`/`api` build files and plan 01-03 creates `frontend`. |

No stub flows into runtime behaviour: the three placeholder modules contribute no classes, no tasks
and no artifacts.

## Verification Run

| Command | Result |
|---|---|
| `./gradlew :domain:build --no-daemon` | pass — `BUILD SUCCESSFUL`, no repositories/variant error |
| `grep -q '9\.7\.0' gradle/wrapper/gradle-wrapper.properties` | pass |
| `docker compose config -q` | pass |
| `docker compose up -d --wait` | pass — both containers `Healthy` |
| `./gradlew :write:integrationTest --tests '*AxonServerRegistrationIT'` | pass — 1 test, 0 failures |
| `./gradlew :write:integrationTest --tests '*InfrastructureReachabilityTest'` | pass — 2 tests, 0 failures |
| `./gradlew :write:bootJar` then `test -f write/build/libs/write-0.1.0-SNAPSHOT.jar` | pass — single jar |
| `./gradlew spotlessApply spotlessCheck --no-daemon` | pass — no diff after apply |
| `./gradlew build --no-daemon` with Compose stopped | pass — integration tests excluded from `test` |

## Next Phase Readiness

- `read` and `api` can copy the `write` build file shape; only `server.port`, app class and package
  differ (plan 01-02).
- The Compose stack is reusable as-is for the per-service registration gates.
- No blockers. The 12-hour Axon Server unlicensed window (RESEARCH P14) still applies and is
  documented in plan 01-06.

---
*Phase: 01-build-and-infrastructure-skeleton*
*Completed: 2026-11-11*

## Self-Check: PASSED
