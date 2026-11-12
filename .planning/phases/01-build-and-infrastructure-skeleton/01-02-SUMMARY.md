---
phase: 01-build-and-infrastructure-skeleton
plan: 02
subsystem: infra
tags: [spring-boot, axon-server, kotlin, gradle, integration-test, awaitility, topology]

requires:
  - phase: 01-build-and-infrastructure-skeleton
    provides: "Gradle build graph, :courses-platform BOM re-export, the :courses-write service pattern, and the compose stack with Axon Server 2026.1.7"

provides:
  - "read service module: Boot app on :8082 with its own bootJar and no datasource/JPA/Flyway"
  - "api service module: Boot app on :8080 with its own bootJar, depending only on :courses-domain plus the Axon starter"
  - "AxonServerRegistrationIT in read and api: connections()[\"default\"] == true via Awaitility (no Thread.sleep)"
  - "PLAT-01 registration proof now covers all three services; TOPO-01 trio of independent bootJars is complete"

affects: [01-04, phase-2, phase-5, phase-6, phase-8]

actuals:
  tokens: 1808
  tasks: 2
  commits: 2

tech-stack:
  added:
    - "org.awaitility:awaitility (versionless, Boot BOM-managed) in read and api test configurations"
  patterns:
    - "Every service module mirrors courses-write/build.gradle.kts: same plugins, jvmToolchain(21), platform(project(\":courses-platform\")), identical test/integrationTest tag split"
    - "Registration gate is an app-internal assertion on AxonServerConnectionManager.connections(), never an /actuator/health scrape"
    - "Service application.yml: name + fixed port + axon.axonserver.servers + management exposure limited to health,info"

key-files:
  created:
    - courses-read/build.gradle.kts
    - courses-read/src/main/kotlin/io/github/aanbrn/courses/ReadApplication.kt
    - courses-read/src/main/resources/application.yml
    - courses-read/src/test/kotlin/io/github/aanbrn/courses/read/AxonServerRegistrationIT.kt
    - courses-api/build.gradle.kts
    - courses-api/src/main/kotlin/io/github/aanbrn/courses/ApiApplication.kt
    - courses-api/src/main/resources/application.yml
    - courses-api/src/test/kotlin/io/github/aanbrn/courses/api/AxonServerRegistrationIT.kt
  modified: []
  deleted:
    - courses-read/.gitkeep
    - courses-api/.gitkeep

key-decisions:
  - "read stays a stub Boot app: no JPA, Flyway or datasource until Phase 5, so the module is a pure Axon client in Phase 1"
  - "api has no compile dependency on write or read — only :courses-domain plus the Axon starter — keeping the gateway unable to reach another service's internals"
  - "The SPA processResources copy into api is deliberately absent; Plan 04 adds it once :courses-frontend:assembleFrontend is wired"

patterns-established:
  - "Per-service build file is a copy of courses-write/build.gradle.kts with only name, port, app class and test fixture changed, so the three scripts cannot drift"
  - "Integration tests carry @Tag(\"integration\") and are excluded from `test`; `./gradlew build` therefore stays Docker-free"

requirements-completed: [TOPO-01, PLAT-01]

coverage:
  - id: D1
    description: "read is an independent Spring Boot application producing its own bootJar, with no datasource/JPA/Flyway"
    requirement: "TOPO-01"
    verification:
      - kind: other
        ref: "./gradlew :courses-read:bootJar#BUILD SUCCESSFUL + courses-read/build/libs/read-0.1.0-SNAPSHOT.jar"
        status: pass
      - kind: other
        ref: "grep -cE 'spring-boot-starter-data-jpa|spring-boot-starter-jdbc|spring\\.datasource' courses-read/build.gradle.kts#0"
        status: pass
    human_judgment: false
  - id: D2
    description: "read registers with Axon Server (connections()[\"default\"] == true)"
    requirement: "PLAT-01"
    verification:
      - kind: integration
        ref: "courses-read/src/test/kotlin/io/github/aanbrn/courses/read/AxonServerRegistrationIT.kt#read service registers with Axon Server"
        status: pass
    human_judgment: false
  - id: D3
    description: "api is an independent Spring Boot application producing its own bootJar, with no courses-write/courses-read dependency"
    requirement: "TOPO-01"
    verification:
      - kind: other
        ref: "./gradlew :courses-api:bootJar#BUILD SUCCESSFUL + courses-api/build/libs/api-0.1.0-SNAPSHOT.jar"
        status: pass
      - kind: other
        ref: "grep -E 'project\\(\":(write|read)\"\\)' courses-api/build.gradle.kts#no match"
        status: pass
    human_judgment: false
  - id: D4
    description: "api registers with Axon Server (connections()[\"default\"] == true)"
    requirement: "PLAT-01"
    verification:
      - kind: integration
        ref: "courses-api/src/test/kotlin/io/github/aanbrn/courses/api/AxonServerRegistrationIT.kt#api service registers with Axon Server"
        status: pass
    human_judgment: false
  - id: D5
    description: "One Docker-free Gradle invocation builds three independent bootJars and exactly three bootRun tasks exist"
    requirement: "TOPO-01"
    verification:
      - kind: other
        ref: "./gradlew :courses-write:bootJar :courses-read:bootJar :courses-api:bootJar#three jars in build/libs"
        status: pass
      - kind: other
        ref: "./gradlew bootRun --parallel --dry-run#:courses-api:bootRun, :courses-read:bootRun, :courses-write:bootRun"
        status: pass
    human_judgment: false

duration: 6min
completed: 2026-11-11
status: complete
plan_head_before: 91e832fb985d023ed0b33ac26310a7e5a5133122
plan_head_after: f6fcfc6d0151fd7b42783eb537f1ddcfed6c7be6
---

# Phase 1 Plan 02: Build and infrastructure skeleton Summary

**`courses-read` (8082) and `courses-api` (8080) added as independent Boot applications, completing the three-service
topology: three bootJars from one Gradle run, three `bootRun` tasks, and each service proven to
register with Axon Server through its own Awaitility-backed integration test.**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-11-11T00:19:34Z
- **Completed:** 2026-11-11T00:25Z
- **Tasks:** 2/2
- **Files created:** 8; deleted: 2 placeholder `.gitkeep` files
- **Diff size:** 7,232 characters ≈ 1,808 estimateTokens (both modules are near-copies of `courses-write`)

## Accomplishments

- `./gradlew :courses-read:bootJar` and `./gradlew :courses-api:bootJar` each produce an 82 MB executable jar
  (`read-0.1.0-SNAPSHOT.jar`, `api-0.1.0-SNAPSHOT.jar`), and a single
  `./gradlew :courses-write:bootJar :courses-read:bootJar :courses-api:bootJar` assembles all three with no Docker.
- `./gradlew bootRun --parallel --dry-run` resolves to exactly `:courses-api:bootRun`, `:courses-read:bootRun`,
  `:courses-write:bootRun` — no fourth application in the graph.
- `./gradlew integrationTest --tests '*AxonServerRegistrationIT'` passes for all three services:
  `connections()["default"] == true` inside `courses-read` and `courses-api`, observed by Awaitility with a 30 s
  ceiling and no `Thread.sleep`.
- `courses-read` declares no JPA/JDBC/datasource dependency (grep count `0`); `courses-api` declares no
  `project(":courses-write")` or `project(":courses-read")` dependency (grep finds nothing) — its only project
  dependencies are `:courses-platform` (BOM) and `:courses-domain`.
- `./gradlew build --dry-run` contains zero `integrationTest` tasks, so the Docker-free build
  guarantee survives the new modules.

## Task Commits

1. **Task 1: Create the `courses-read` service module** — `a11b565` (feat)
2. **Task 2: Create the `courses-api` service module** — `f6fcfc6` (feat)

**Plan metadata:** the SUMMARY commit that follows (docs). Measured commits in this plan: 2.

## Files Created/Modified

- `courses-read/build.gradle.kts` — copy of `courses-write/build.gradle.kts`: kotlin-jvm + kotlin-spring + Boot
  plugins, `jvmToolchain(21)`, `platform(project(":courses-platform"))`, `:courses-domain`, Axoniq starter, webmvc,
  actuator, kotlin-reflect; `test` excludes `integration`, `integrationTest` includes it; `jar`
  disabled.
- `courses-read/src/main/kotlin/io/github/aanbrn/courses/ReadApplication.kt` — `class ReadApplication` +
  `runApplication`.
- `courses-read/src/main/resources/application.yml` — `spring.application.name: read`, `server.port: 8082`,
  `axon.axonserver.servers: ${AXON_SERVER:localhost:8124}`,
  `management.endpoints.web.exposure.include: health,info`.
- `courses-read/src/test/kotlin/io/github/aanbrn/courses/read/AxonServerRegistrationIT.kt` —
  `@Tag("integration")`, `@SpringBootTest(classes = [ReadApplication::class],
  webEnvironment = NONE)`, autowires `AxonServerConnectionManager`, asserts with
  `await().atMost(Duration.ofSeconds(30))`.
- `courses-api/build.gradle.kts` — same shape as `courses-read`, no `processResources`/`assembleFrontend` wiring
  (Plan 04 owns it).
- `courses-api/src/main/kotlin/io/github/aanbrn/courses/ApiApplication.kt` — `class ApiApplication`.
- `courses-api/src/main/resources/application.yml` — `name: api`, `server.port: 8080`, same Axon/management
  block.
- `courses-api/src/test/kotlin/io/github/aanbrn/courses/api/AxonServerRegistrationIT.kt` — the api
  registration gate, identical in shape to `courses-read`'s.
- Deleted: `courses-read/.gitkeep`, `courses-api/.gitkeep` — placeholders from Plan 01-01, no longer needed now that
  both modules carry real sources (same cleanup Plan 01-03 did for `courses-frontend/.gitkeep`).

## Verification Run

| Command | Result |
|---|---|
| `./gradlew :courses-read:bootJar --no-daemon` | pass — `BUILD SUCCESSFUL`; `courses-read/build/libs/read-0.1.0-SNAPSHOT.jar` |
| `grep -cE 'spring-boot-starter-data-jpa\|spring-boot-starter-jdbc\|spring\.datasource' courses-read/build.gradle.kts` | pass — `0` |
| `docker compose up -d --wait` | pass — axonserver and postgres both `Healthy` |
| `./gradlew :courses-read:integrationTest --tests '*AxonServerRegistrationIT' --no-daemon` | pass — 1 test, 0 failures |
| `./gradlew :courses-api:bootJar --no-daemon` | pass — `courses-api/build/libs/api-0.1.0-SNAPSHOT.jar` |
| `grep -E 'project\(":(write\|read)"\)' courses-api/build.gradle.kts` | pass — no match |
| `./gradlew bootRun --parallel --dry-run --no-daemon` | pass — exactly `:courses-api:bootRun`, `:courses-read:bootRun`, `:courses-write:bootRun` |
| `./gradlew :courses-api:integrationTest --tests '*AxonServerRegistrationIT' --no-daemon` | pass — 1 test, 0 failures |
| `./gradlew :courses-write:bootJar :courses-read:bootJar :courses-api:bootJar --no-daemon` | pass — three jars in `build/libs` |
| `./gradlew integrationTest --tests '*AxonServerRegistrationIT' --no-daemon` | pass — `BUILD SUCCESSFUL` across write, read and api |
| `./gradlew build --dry-run --no-daemon \| grep -c integrationTest` | pass — `0` (build stays Docker-free) |
| `./gradlew :courses-read:spotlessCheck :courses-api:spotlessCheck --no-daemon` | pass — `BUILD SUCCESSFUL` |

## Deviations from Plan

None — the plan executed as written. Two notes that are not deviations:

- `.gitkeep` placeholders were deleted in the same commits that added the real module sources.
- `courses-api/build.gradle.kts` intentionally has no `processResources` copy of `:courses-frontend/dist`; Plan 01-04
  adds it (verified against `01-04-PLAN.md`, task "Embed the SPA bundle in the `courses-api` bootJar").

## Issues Encountered

- **Stale Axon Server raft state blocked the first registration run.** The first
  `:courses-read:integrationTest` failed with `ConditionTimeoutException`; the client log showed
  `[AXONIQ-1400] No active AxonServers found for context "default"`, and the server log showed
  `default in term 1: Last applied index 4 is higher than last log index 0` /
  `Failed to start replication`. The Axon Server volume left over from the Plan 01-01 session was
  inconsistent, so the `default` context never became active even though the container healthcheck
  (`/actuator/info`) was green. Fix: `docker compose down -v --remove-orphans` followed by
  `docker compose up -d --wait`; the server then logged
  `default: context default created, my role PRIMARY` and both registration tests passed. This is a
  local-volume condition, not a code change — no files were modified.
- **Research Pitfall P13 worth restating:** the compose healthcheck passes before the `default`
  context is created, so a green container does not imply a usable Axon Server. Reset the volumes
  (`down -v`) whenever the client loops with `AXONIQ-1400`/`AXONIQ-1302`.

## User Setup Required

None — no external service configuration required. Docker Compose is already the sanctioned local
infrastructure path.

## Next Phase Readiness

- The three-service topology (TOPO-01) is complete: `courses-write` 8081, `courses-read` 8082, `courses-api` 8080, each with
  its own bootJar and its own Axon client.
- PLAT-01's registration proof now exists in every service module; Plan 01-04 can run the aggregate
  `./gradlew integrationTest --tests '*AxonServerRegistrationIT'` gate plus the SPA-in-jar and
  smoke-stack checks.
- `courses-read` remains deliberately persistence-free; Phase 5 adds JPA + Flyway + Spring Data JDBC and the
  datasource, at which point its `application.yml` gains the PostgreSQL block.

---
*Phase: 01-build-and-infrastructure-skeleton*
*Completed: 2026-11-11*

## Self-Check: PASSED

- All eight module files exist on disk (`courses-read/build.gradle.kts`, `ReadApplication.kt`,
  `courses-read/application.yml`, read `AxonServerRegistrationIT.kt`; the same four for `courses-api`).
- Both task commits (`a11b565`, `f6fcfc6`) are ancestors of `HEAD`.
- `git rev-list --count 91e832f..HEAD` = 2, matching the `commits: 2` frontmatter value.

## Later changes

- The `test`/`integrationTest` tag split was replaced by Gradle `jvm-test-suite` source sets:
  integration tests live in `src/integrationTest/kotlin` and no longer carry `@Tag("integration")`.
- Each suite now starts its own Axon Server through Testcontainers instead of using Compose.
