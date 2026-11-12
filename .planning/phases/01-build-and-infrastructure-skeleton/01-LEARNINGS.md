---
phase: 01
phase_name: "Build and infrastructure skeleton"
project: "Axon 5 DCB Courses"
generated: "2026-11-12"
counts:
  decisions: 8
  lessons: 8
  patterns: 7
  surprises: 6
missing_artifacts:
  - "UAT.md"
---

# Phase 01 Learnings: Build and infrastructure skeleton

## Decisions

### Three services behind Axon Server, with one read service
`courses-write`, `courses-read` and `courses-api` are independent Spring Boot applications.
`courses-read` both consumes events and answers queries, so the read side has one owner.

**Rationale:** a process split is the point of the phase — the boundary must survive it. Splitting
projection from query was rejected because live updates would then need a cross-service notification.
**Source:** 01-02-SUMMARY.md

### Tracer slice first: `courses-write` before `read` and `api`
The first plan brought up one service that registers with Axon Server end to end; the other two
mirrored it mechanically.

**Rationale:** the commercial starter on Boot 4.1.2 was the phase's largest unknown, and it is settled
by one working service, not by three half-working ones.
**Source:** 01-01-SUMMARY.md

### Registration is proven inside the application
`AxonServerRegistrationIT` asserts `AxonServerConnectionManager.connections()["default"] == true` with
a bounded Awaitility poll. `/actuator/health` is never used as evidence of registration.

**Rationale:** the `axonServer` health indicator does exist, but it reports `UP` while its connections
map is still empty — the state before the first connection attempt — and `/actuator/health` hides the
per-context detail unless health details are shown. Asserting `connections()["default"] == true` states
the claim exactly.
**Source:** 01-02-SUMMARY.md, STATE.md

### A platform module re-exports the BOMs
`:courses-platform` is a `java-platform` with `allowDependencies()`, importing the Boot and Axoniq BOMs
through `api(platform(...))`.

**Rationale:** a `java-platform` cannot take BOMs inside `constraints {}`; one module keeps every
version out of the individual builds.
**Source:** 01-01-SUMMARY.md

### Test suites are decided by directory, and own their infrastructure
`src/test`, `src/integrationTest` (and later `componentTest`/`e2eTest`) map to suites by
`jvm-test-suite`; each integration suite starts its own Testcontainers.

**Rationale:** the earlier tag filter was silent and fragile; a directory is visible in every path and
cannot be forgotten.
**Source:** STATE.md, docs/adr/0003-test-suites-and-testcontainers.md

### The frontend ships as a standalone nginx image
`courses-api` serves no static assets; the SPA is built by `courses-frontend` and deployed on its own.

**Rationale:** release cycles and static-asset serving stop being the gateway's problem; CORS becomes an
explicit, typed contract instead of an implicit same-origin assumption.
**Source:** docs/adr/0004-frontend-as-a-standalone-deployable.md

### Every deployable is a Paketo image, with no Dockerfile
The four images come from `bootBuildImage` and a `PackBuildImageTask`; base images and buildpacks are
catalog pins.

**Rationale:** OS patches come from the run image, and a builder bump is a two-line catalog change
rather than a Dockerfile edit.
**Source:** docs/adr/0005-paketo-images-without-dockerfiles.md

### Compose runs the whole stack
`docker compose up -d --wait` starts Axon Server, PostgreSQL, the three services and the frontend;
only the gateway and the frontend publish host ports.

**Rationale:** one command proves the deployed wiring, and the development loop (infrastructure in
Compose, services from Gradle) stays a separate concern.
**Source:** docs/adr/0006-compose-runs-the-whole-stack.md

---

## Lessons

### A green Axon Server healthcheck does not mean the context exists
The server answered its health endpoint while the services failed with
`[AXONIQ-1400] No active AxonServers found for context "default"`. Stale runner volumes were the cause;
`docker compose down -v` is the reset.

**Context:** surfaced while bringing up the second and third service in the same Compose project.
**Source:** 01-02-SUMMARY.md, STATE.md

### A tag filter hides mistakes instead of failing
Integration tests were selected by a JUnit tag inside `src/test`. A mis-tagged test silently moved
between the fast build and the Docker-backed run, and nothing failed.

**Context:** replaced wholesale by the directory-based suites and a `checkTestNaming` gate.
**Source:** STATE.md, docs/adr/0003-test-suites-and-testcontainers.md

### The shared Compose stack in CI was the phase's biggest rework
Starting one Compose stack for all three suites bound fixed ports, shared mutable state between tests
and made the job depend on an out-of-band `up`. Self-contained containers replaced it.

**Context:** the plan's acceptance grep for the Compose step now fails against the repository.
**Source:** 01-REVIEWS.md (both reviewers), docs/adr/0003-test-suites-and-testcontainers.md

### A "tested ceiling" is an assumption until it is verified
The wrapper was pinned to Gradle 9.7.0 because that was believed to be Kotlin 2.4.21's ceiling, and the
plan forbade 9.7.1. The project runs 9.9.1 today, verified.

**Context:** an `[ASSUMED]` research entry became a hard "do not" in the plan.
**Source:** 01-01-SUMMARY.md, .planning/research/STACK.md

### `setsid` does not exist on macOS
Reaping the three `bootRun` JVMs needed `set -m` job control plus a process-group kill; the earlier
`setsid` approach was unavailable on darwin.

**Context:** the smoke script must leave no orphaned JVMs on either platform.
**Source:** 01-04-SUMMARY.md

### A buildpack patch release can be unusable
`paketobuildpacks/nginx` 1.3.6 is published but cannot be extracted by `pack` 0.40.10
(`could not find label 'io.buildpacks.buildpackage.metadata'`). The pin is held back with a marker.

**Context:** found while updating the Paketo pins after the phase; `./gradlew build` does not see it,
only an image build does.
**Source:** gradle/libs.versions.toml, docs/tooling.md

### Verify a dependency's behaviour in its source, not at one endpoint
The documentation claimed "this starter combination registers no Axon health indicator", inferred from
a green `/actuator/health` whose body showed no component. The dependency does ship
`AxonServerHealthIndicator`, registered by `AxonServerActuatorAutoConfiguration`; it reports `UP` while
its connections map is empty, and health hides component details unless `show-details` is set. One
observation became a stated fact and then travelled from the plan into the tooling page.

**Context:** caught while reviewing these learnings; corrected in `docs/tooling.md` and in this file.
**Source:** axoniq-spring-boot-autoconfigure 5.4.1 (class and auto-configuration), docs/tooling.md

---

### Verification evidence goes stale within the same phase
`01-VERIFICATION.md` records CI building the frontend image with `setup-pack`; the rework removed that
step, so a criterion that passed at the time no longer describes the repository.

**Context:** a phase can be complete and its plans still be reworked afterwards.
**Source:** 01-VERIFICATION.md, 01-REVIEWS.md

---

## Patterns

### Tracer slice, then mirror
Build one slice end to end, prove the risky integration, then copy the shape to the siblings.

**When to use:** when one unknown (here, a commercial starter on a new Boot version) dominates the
phase's risk.
**Source:** 01-01-SUMMARY.md, 01-02-SUMMARY.md

### Guard tests that assert absence
Tests assert what must *not* exist: no `EntityManagerFactory` in `courses-write`, no JPA in the read
stub, no dependency from `courses-api` to the other services.

**When to use:** architectural boundaries that a single classpath mistake can silently erase.
**Source:** 01-02-SUMMARY.md, .planning/REQUIREMENTS.md (TOPO-03)

### Prove the integration from inside the process
A bounded Awaitility poll on the connector state is stronger evidence than an HTTP endpoint that may be
green for unrelated reasons.

**When to use:** when a health check does not actually observe the dependency under test.
**Source:** 01-02-SUMMARY.md

### One command proves the deployed stack
`scripts/smoke-stack.sh` builds the images, starts Compose, asserts registrations from the server log and
the SPA from the browser surface, then tears everything down on every exit path.

**When to use:** whenever "it works in tests" is not the claim being made — the claim is about the
assembled deployables.
**Source:** 01-04-SUMMARY.md

### Fail-closed typed configuration
`@ConfigurationProperties` with JSR-380 constraints: an empty list means no access, and an invalid origin
fails startup instead of surfacing as a browser error.

**When to use:** security-relevant settings whose permissive default would be invisible in tests.
**Source:** docs/adr/0007-fail-closed-cors-configuration.md

### Reproducible metadata
`bootBuildInfo` drops the timestamp and the SPA mirrors the same fields in `build-info.json`.

**When to use:** image layers or artifacts compared across rebuilds.
**Source:** docs/adr/0008-reproducible-build-info.md

### Mark executed plans as superseded
Once a phase's architecture changes, the plans say so in their header, naming the ADR that replaced them.

**When to use:** a phase is complete but its plans would rebuild a rejected design if re-run.
**Source:** 01-REVIEWS.md, .planning/ROADMAP.md

---

## Surprises

### The Axon Server's own healthcheck does not prove the `default` context exists
The server container can be healthy while the services report
`[AXONIQ-1400] No active AxonServers found for context "default"` — the context, not the process, is what
the services need.

**Impact:** a green Compose status sent the investigation in the wrong direction until the server log
was read.
**Source:** 01-02-SUMMARY.md

### Tag-filtered tests fail silently
A wrong tag does not error; it moves a test out of the run.

**Impact:** the suite could pass while its distributed half never executed.
**Source:** docs/adr/0003-test-suites-and-testcontainers.md

### The biggest rework was infrastructure, not code
The build graph, the three services and the frontend survived review largely intact; the Compose-backed
CI stack did not.

**Impact:** the phase's "done" evidence had to be rewritten, and an ADR was needed to record why.
**Source:** 01-REVIEWS.md

### The Gradle ceiling was lower than reality
9.7.0 was pinned as a supported maximum; 9.9.1 is what the project runs.

**Impact:** a hard "do not upgrade" instruction in a plan that later had to be reversed.
**Source:** 01-01-SUMMARY.md, .planning/research/STACK.md

### An upstream release can be published and broken
nginx 1.3.6 exists in the registry but no `pack` can extract it.

**Impact:** an image build fails where `./gradlew build` is green, so pins need a hold-back mechanism and
an image build as proof.
**Source:** gradle/libs.versions.toml, docs/tooling.md

### A completed phase can still be refactored
The merge of the phase's own PR was followed by a rename, a build-logic extraction and a frontend split.

**Impact:** every document written during the phase needed a consistency pass; ADRs and the review of the
plans are what made the drift visible.
**Source:** 01-REVIEWS.md, .planning/PLANNING-REVIEW.md
