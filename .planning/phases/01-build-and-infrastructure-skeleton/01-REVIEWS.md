---
phase: 1
reviewers: [codex, opencode]
reviewed_at: 2026-11-12T02:20:07Z
plans_reviewed: [.planning/phases/01-build-and-infrastructure-skeleton/01-01-PLAN.md, .planning/phases/01-build-and-infrastructure-skeleton/01-02-PLAN.md, .planning/phases/01-build-and-infrastructure-skeleton/01-03-PLAN.md, .planning/phases/01-build-and-infrastructure-skeleton/01-04-PLAN.md, .planning/phases/01-build-and-infrastructure-skeleton/01-05-PLAN.md, .planning/phases/01-build-and-infrastructure-skeleton/01-06-PLAN.md]
models:
  codex: "gpt-6.1-sol (reasoning=high)"
  opencode: "opencode-go/glm-5.3 (reasoning=high)"
model_sources:
  codex: "config"
  opencode: "config"
---

# Cross-AI Plan Review — Phase 1

Two reviewers read the same prompt: project context, the phase section of the roadmap, the eight
requirements the phase addresses, the phase research, all six plans, and a coverage manifest. Both
verified the plans against the working tree rather than against the plan text alone.

## Consensus Summary

| Plan | Codex | GLM-5.3 | Shared finding | Where they differ |
|------|-------|---------|----------------|-------------------|
| `01-01` | HIGH | MEDIUM | `settings.gradle.kts` includes `platform`/`domain`/`write` while every other artifact and verify command uses `courses-*`; the tag-based `integrationTest` over `src/test` is the architecture ADR-0003 later replaced | Codex reads it as "cannot be executed at all"; GLM keeps the risk MEDIUM because the architectural spine (BOM platform, tracer slice, registration from inside the app) is what HEAD proves |
| `01-02` | HIGH | LOW | Inherits the naming contradiction; jar names `read-…`/`api-…` do not match `:courses-read`/`:courses-api` task paths | Codex adds that the dependency guards cannot enforce what they claim; GLM calls the plan mechanically sound and already resolved at HEAD |
| `01-03` | HIGH | LOW | Scaffold and verify commands target `frontend` while the module is `courses-frontend`, and expect `dist/` where Vite writes `build/dist` | Codex rates execution risk HIGH on the path defect alone; GLM calls it the cleanest of the six |
| `01-04` | HIGH | MEDIUM | Embedding the SPA in `courses-api` was reversed by ADR-0004 | Codex adds that the plan's `docker compose down -v` erases the local PostgreSQL and Axon volumes, and that the risk survives in `scripts/smoke-stack.sh` |
| `01-05` | HIGH | MEDIUM | The Compose-backed CI step was replaced by Testcontainers (ADR-0003); the plan's own acceptance grep fails against HEAD | Codex adds that the replacement drops the actionlint install the build now requires; GLM notes the plan never weighed the alternative that won |
| `01-06` | HIGH | LOW | The startup recipe combines a full Compose stack with host-run services on an already-published port, then opens `:8080` where the SPA is no longer served | Codex rates the port conflict and wrong surface HIGH; GLM finds only ADR-0004 fallout |

Both reviewers agree on the shape of the answer: the six plans are historically honest but not
re-executable, and every HIGH they found has already been corrected at HEAD by ADRs 0002, 0003, 0004
and 0006. Neither asks for code changes; both ask for the plans to carry a `superseded` marker so a
future executor does not reintroduce a rejected architecture.

## Codex

Review basis: the current repository, including implementation files and accepted ADRs. Severity below describes the risk of executing the supplied plans today; several historical defects have already been corrected in code.

## 01-01

**Summary:** The first-service tracer is a sound approach, but the plan contains a build-breaking naming inconsistency and now conflicts with the repository’s test and wrapper conventions.

**Strengths**

- The BOM platform is correctly designed: `allowDependencies()` permits importing both BOMs, and `api(platform(...))` exposes their constraints to consumers. The implementation preserves this mechanism in [courses-platform/build.gradle.kts:3](/Users/afanasyev/Development/axon5-courses-gsd/courses-platform/build.gradle.kts:3).
- Registration checks the actual connector state using bounded Awaitility polling. The current shared assertion checks `connections()["default"] == true`, avoiding a misleading HTTP-health pass. See [AxonServerRegistrationSupport.kt:30](/Users/afanasyev/Development/axon5-courses-gsd/courses-test-support/src/main/kotlin/io/github/aanbrn/courses/testing/AxonServerRegistrationSupport.kt:30).

**Concerns**

- **HIGH — Module names contradict project references.** [01-01-PLAN.md:102](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-01-PLAN.md:102) includes `platform`, `domain`, and `write`, while line 107 references `:courses-platform`. Without explicit directory/project mappings, these are different Gradle projects. The current settings correctly include the prefixed names at [settings.gradle.kts:21](/Users/afanasyev/Development/axon5-courses-gsd/settings.gradle.kts:21).
- **HIGH — The proposed test placement is incompatible with current suite enforcement.** The plan puts `*IT` and `InfrastructureReachabilityTest` under `src/test`; current rules require `*Tests` there and `*IT` under `src/integrationTest`. The guard actually scans filenames and throws on mismatches: [kotlin-conventions.gradle.kts:56](/Users/afanasyev/Development/axon5-courses-gsd/build-logic/src/main/kotlin/kotlin-conventions.gradle.kts:56). Compose-dependent tests also conflict with [AGENTS.md:248](/Users/afanasyev/Development/axon5-courses-gsd/AGENTS.md:248).
- **MEDIUM — Wrapper instructions would reverse the current decision.** The plan mandates 9.7.0 without specifying a distribution checksum. The repository uses checksum-pinned 9.9.1, consistent with its recorded stack decision: [gradle-wrapper.properties:3](/Users/afanasyev/Development/axon5-courses-gsd/gradle/wrapper/gradle-wrapper.properties:3), [AGENTS.md:37](/Users/afanasyev/Development/axon5-courses-gsd/AGENTS.md:37).

**Suggestions:** Use canonical `courses-*` names throughout; create every included module directory before wrapper generation; reuse the existing convention plugins and Testcontainers support. The missing-directory failure was already encountered during execution: [01-01-SUMMARY.md:186](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-01-SUMMARY.md:186).

**Risk assessment: HIGH.** Literal execution can fail during build configuration or regress established build and test behavior.

## 01-02

**Summary:** The service separation is appropriate and implemented cleanly. The principal weaknesses are duplicated, superseded test setup and verification that cannot enforce the dependency boundaries it claims to check.

**Strengths**

- Each service declares its own application and integration suite. The API’s production dependencies include the domain contract without another service implementation: [courses-api/build.gradle.kts:9](/Users/afanasyev/Development/axon5-courses-gsd/courses-api/build.gradle.kts:9).
- Explicit application selection prevents ambiguous Spring Boot discovery. The current write-service fixture demonstrates this at [AxonServerRegistrationIT.kt:6](/Users/afanasyev/Development/axon5-courses-gsd/courses-write/src/integrationTest/kotlin/io/github/aanbrn/courses/AxonServerRegistrationIT.kt:6).

**Concerns**

- **HIGH — Mirroring the old tagged tests would regress suite isolation.** Both tasks prescribe tests under `src/test` and hand-registered `Test` tasks. The current services instead declare `JvmTestSuite` integration suites consuming shared support: [courses-read/build.gradle.kts:24](/Users/afanasyev/Development/axon5-courses-gsd/courses-read/build.gradle.kts:24). Reintroducing the proposed classes would trigger the naming failure described in 01-01.
- **MEDIUM — The API dependency guard matches the wrong names.** [01-02-PLAN.md:103](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-02-PLAN.md:103) searches for `:(write|read)`, so it misses forbidden `:courses-write` and `:courses-read` dependencies. Its automated verification at line 108 does not run that guard at all. Likewise, building a jar does not prove the absence of transitive persistence dependencies.

**Suggestions:** Reuse the shared registration base. Check resolved production dependencies for forbidden service and persistence modules. Use `bootRun --parallel --dry-run` when inspecting task selection, followed by a separate runtime check.

**Risk assessment: MEDIUM.** The architecture is sound, but the planned checks provide weaker protection than their descriptions suggest.

## 01-03

**Summary:** The minimal FSD scaffold and negative controls are well chosen. Directory inconsistencies make the commands unreliable, and the architecture and incremental-build checks have remaining gaps.

**Strengths**

- The source follows the intended dependency direction: the app imports the page’s public API, and the page imports shared UI’s public API. See [main.tsx:3](/Users/afanasyev/Development/axon5-courses-gsd/courses-frontend/src/app/main.tsx:3) and [HomePage.tsx:1](/Users/afanasyev/Development/axon5-courses-gsd/courses-frontend/src/pages/home/ui/HomePage.tsx:1).
- The combined lint script runs steiger with warning failures and then oxlint; the restricted import rule is an error. See [package.json:13](/Users/afanasyev/Development/axon5-courses-gsd/courses-frontend/package.json:13) and [.oxlintrc.json:7](/Users/afanasyev/Development/axon5-courses-gsd/courses-frontend/.oxlintrc.json:7).

**Concerns**

- **HIGH — Scaffold and verification commands target `frontend`, while artifacts target `courses-frontend`.** This appears in [01-03-PLAN.md:90](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-03-PLAN.md:90), including `npm --prefix frontend` and `git -C frontend`. The actual module is `courses-frontend`; there is no `frontend` directory in this checkout. The plan also expects `dist`, while Vite currently writes `build/dist`: [vite.config.ts:17](/Users/afanasyev/Development/axon5-courses-gsd/courses-frontend/vite.config.ts:17).
- **MEDIUM — The deep-import rule covers alias spelling only.** The regex begins with `^@/`, so a relative import such as `../../../shared/ui/AppShell` bypasses it. Steiger’s installed implementation exempts direct files in `shared/ui` from the relevant diagnostic branch: [steiger-plugin/dist/index.js:802](/Users/afanasyev/Development/axon5-courses-gsd/courses-frontend/node_modules/@feature-sliced/steiger-plugin/dist/index.js:802). The single alias negative control therefore does not establish complete enforcement.
- **MEDIUM — Frontend build inputs are incomplete.** The referenced task declares only `src` and `index.html`, allowing configuration or dependency changes to leave an old bundle considered current: [01-RESEARCH.md:272](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-RESEARCH.md:272). The implementation now declares configurations, but still omits `package-lock.json` from `assembleFrontend` inputs: [courses-frontend/build.gradle.kts:29](/Users/afanasyev/Development/axon5-courses-gsd/courses-frontend/build.gradle.kts:29).

**Suggestions:** Normalize directory and output names; add a relative-import negative control; declare the lockfile and all effective build inputs. Compare negative-control files with their pre-probe contents rather than assuming they were already committed.

**Risk assessment: HIGH.** Command paths block literal execution, while the remaining gaps can produce false confidence in architecture enforcement and bundle freshness.

## 01-04

**Summary:** This plan’s central deployment model has been superseded. Its smoke-test lifecycle also poses a concrete data-loss risk.

**Strengths**

- The plan correctly distinguishes HTTP liveness from Axon registration. The current smoke script checks registration separately for all three services: [smoke-stack.sh:47](/Users/afanasyev/Development/axon5-courses-gsd/scripts/smoke-stack.sh:47).

**Concerns**

- **HIGH — Embedding the SPA contradicts an accepted architecture decision.** The task adds static resources to the API at [01-04-PLAN.md:60](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-04-PLAN.md:60). ADR-0004 explicitly requires a standalone nginx frontend and no API static assets: [ADR-0004:16](/Users/afanasyev/Development/axon5-courses-gsd/docs/adr/0004-frontend-as-a-standalone-deployable.md:16). The proposed copy also reads `dist`, whereas the current output is `build/dist`.
- **HIGH — Cleanup deletes existing development data.** The plan mandates `docker compose down -v` against the ordinary Compose project at [01-04-PLAN.md:78](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-04-PLAN.md:78). Those named volumes hold PostgreSQL data and Axon events: [compose.yaml:10](/Users/afanasyev/Development/axon5-courses-gsd/compose.yaml:10), [compose.yaml:28](/Users/afanasyev/Development/axon5-courses-gsd/compose.yaml:28). This risk remains in the current script at [smoke-stack.sh:21](/Users/afanasyev/Development/axon5-courses-gsd/scripts/smoke-stack.sh:21).
- **MEDIUM — Separate registration tests do not prove the launched processes registered.** The planned script polls HTTP endpoints while registration tests create different application instances. Current integration tests also own separate Axon containers, so their success cannot establish the Compose instances’ connections: [AxonServerRegistrationSupport.kt:39](/Users/afanasyev/Development/axon5-courses-gsd/courses-test-support/src/main/kotlin/io/github/aanbrn/courses/testing/AxonServerRegistrationSupport.kt:39).

**Suggestions:** Replace the embedding task with standalone-image verification. Run smoke tests under a unique Compose project and isolated ports, and remove only resources created by that run. Check registration for the actual launched services.

**Risk assessment: HIGH.** Execution would reverse deployment decisions and can erase persistent local state.

## 01-05

**Summary:** Preserving required-check identities is sensible, but wholesale workflow replacement is unsafe against the current repository. The original dependency ordering and verification are also incomplete.

**Strengths**

- The current workflow preserves `build` and `format`, uses SHA-pinned actions, and limits token permissions to read access: [ci.yml:19](/Users/afanasyev/Development/axon5-courses-gsd/.github/workflows/ci.yml:19), [ci.yml:45](/Users/afanasyev/Development/axon5-courses-gsd/.github/workflows/ci.yml:45).
- The ignore change is present in the correct block: [.gitignore:46](/Users/afanasyev/Development/axon5-courses-gsd/.gitignore:46).

**Concerns**

- **HIGH — Replacing the steps removes a required tool installation.** The current build runs `lintWorkflows` through root `check`, and fails when actionlint is unavailable: [build.gradle.kts:12](/Users/afanasyev/Development/axon5-courses-gsd/build.gradle.kts:12), [build.gradle.kts:31](/Users/afanasyev/Development/axon5-courses-gsd/build.gradle.kts:31). The current workflow installs it at [ci.yml:72](/Users/afanasyev/Development/axon5-courses-gsd/.github/workflows/ci.yml:72); the replacement at [01-05-PLAN.md:66](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-05-PLAN.md:66) does not.
- **MEDIUM — The dependency graph omits the frontend lockfile producer.** Plan 05 depends only on 01-01, yet configures npm caching using the lockfile created by 01-03. See [01-05-PLAN.md:6](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-05-PLAN.md:6) and line 66. A standalone intermediate commit can therefore fail before building.
- **MEDIUM — Text searches do not prove workflow execution.** The verification searches the entire YAML for strings, accepting comments, misplaced steps, or skipped integration work: [01-05-PLAN.md:74](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-05-PLAN.md:74). The current integration step is conditional for draft PRs, demonstrating why job success alone is insufficient: [ci.yml:94](/Users/afanasyev/Development/axon5-courses-gsd/.github/workflows/ci.yml:94).

**Suggestions:** Patch the existing workflow incrementally, retain actionlint installation, use Testcontainers without starting Compose, and depend on 01-03. Require a hosted run showing the integration step executed and produced test results.

**Risk assessment: HIGH.** The replacement can break CI and unnecessarily start application containers that current integration tests do not use.

## 01-06

**Summary:** The browser proof is appropriately concrete, but its launch instructions and documentation targets belong to the superseded embedded-SPA topology.

**Strengths**

- The configured MCP command is version-pinned and headless: [opencode.jsonc:13](/Users/afanasyev/Development/axon5-courses-gsd/opencode.jsonc:13). The plan requires rendered title, heading, and test-id assertions, which are substantially stronger than grepping HTML: [01-06-PLAN.md:60](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-06-PLAN.md:60).

**Concerns**

- **HIGH — The prescribed startup combines two launch modes.** It starts the complete Compose stack and then launches host services using the API’s already-published port. It subsequently navigates to `:8080`, where the frontend is no longer served. Compare [01-06-PLAN.md:57](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-06-PLAN.md:57) with [compose.yaml:73](/Users/afanasyev/Development/axon5-courses-gsd/compose.yaml:73) and [compose.yaml:91](/Users/afanasyev/Development/axon5-courses-gsd/compose.yaml:91).
- **MEDIUM — Background-process cleanup is unspecified.** The task records `RUN_PID` but defines no teardown for success, browser failure, or interruption: [01-06-PLAN.md:57](/Users/afanasyev/Development/axon5-courses-gsd/.planning/phases/01-build-and-infrastructure-skeleton/01-06-PLAN.md:57). This can leave processes and port conflicts behind.
- **MEDIUM — Documentation instructions would overwrite correct current guidance.** The plan describes infrastructure-only Compose and API-served UI; current documentation correctly separates full deployment from the Vite development loop: [tooling.md:336](/Users/afanasyev/Development/axon5-courses-gsd/docs/tooling.md:336), [tooling.md:368](/Users/afanasyev/Development/axon5-courses-gsd/docs/tooling.md:368).

**Suggestions:** Choose one mode: full Compose with browser verification on `:3000`, or infrastructure-only Compose plus host services and Vite on `:5173`. Add guaranteed cleanup and record which mode the evidence exercises.

**Risk assessment: HIGH.** The browser assertions are useful, but the prescribed environment now produces port conflicts and targets the wrong HTTP surface.

## Overall assessment

**Request changes before treating these as executable plans.** Their decomposition and requirement traceability are useful, but they need explicit supersession markers or revised instructions aligned with the accepted ADRs.

Prioritize canonical paths, suite isolation, standalone frontend deployment, smoke-test resource isolation, and CI execution evidence. Keep subsequent enrollment, security, and contention behavior outside this skeleton phase.

Validation was read-only. Oxlint passed; the combined FSD lint attempt failed with `EMFILE` while initializing file watchers, so I could not independently confirm steiger’s current pass. Gradle builds, container tests, and browser navigation were not rerun; historical verification records were treated as historical evidence.

## OpenCode (GLM-5.3)

The repo has existing state that differs from what the plans describe — I need to verify each plan's claims against it. Let me read the key files.
The repo has clearly evolved beyond the plans' original shape. Let me check the remaining referenced artifacts to pin down exactly where plans and reality diverge.
Phase 1 is executed and the repo has evolved. Let me check the remaining plan claims: the ADRs, frontend module, the registration IT content, docs, and the summaries that recorded execution deviations.
One last check — the shared registration support and the Testcontainers base, to confirm how the plan-era tests were reworked.
# Cross-AI Plan Review — Phase 1: Build and infrastructure skeleton

**Review basis.** I read the six `01-0x-PLAN.md` files and verified their claims against the working tree at `/Users/afanasyev/Development/axon5-courses-gsd` (HEAD ≈ `cd02165`, Phase 1 executed 2026-11-11, project now in Phase 2 planning per `.planning/STATE.md`). Important context for every finding below: **the repo at HEAD is not the repo the plans describe** — Phase 1 was executed as planned (see `01-05-SUMMARY.md`, `01-04-SUMMARY.md`), then substantially reworked under ADR-0002…0008 (`docs/adr/0002-build-logic-conventions.md`, `0003-test-suites-and-testcontainers.md`, `0004-frontend-as-a-standalone-deployable.md`, `0005`, `0006`, `0007`, `0008`). So each plan now contains a mix of (a) defects it had at writing time and (b) accurate-at-the-time content that is now stale. I separate the two, because a reader following any of these plans today would produce code that conflicts with HEAD and fails the project's own conventions.

---

## 01-01

**Summary.** A well-researched, atomically-justified scaffold plan that correctly front-loads the phase's one real risk (commercial Axon starter on Boot 4 resolving and registering). Its biggest flaw was present at writing time: the module-naming internals contradict each other (`include("platform", ...)` vs `courses-*` file paths and `:courses-*:` task commands), and its test architecture (tag-based `integrationTest`, fixed-port reachability tests against a pre-started Compose stack) was institutionalized here and had to be deleted by ADR-0003.

**Strengths.**
- The file-count exemption argument (settings + catalog + root build + wrapper + platform + domain + compose as one atomic unit, citing the P3/P4 broken-intermediate failures) is correct and well-defended — splitting those files produces a graph that cannot resolve between tasks.
- The plan self-corrects its source: RESEARCH's `Thread.sleep`-poll registration example is replaced with Awaitility (`await().atMost(...).until { connectionManager.connections()["default"] == true }`), explicitly citing the AGENTS.md `Thread.sleep` ban and the 100-column ktlint limit. The final repo kept exactly that assertion shape — see `courses-test-support/.../AxonServerRegistrationSupport.kt:39-43`.
- The Docker-free `build` vs tag-gated `integrationTest` split is stated as a plan invariant and survives to this day (`courses-api/build.gradle.kts:20-39`; `docs/tooling.md:315-320`).
- The compose pitfalls that actually bite are encoded, not guessed: `axoniq_axonserver_standalone: "true"` (P1, AXONIQ-1302) and `/actuator/info` rather than `/actuator/health` for the server healthcheck (P13) — both present in `compose.yaml:9,14` at HEAD.

**Concerns.**
- **HIGH (internal defect, independent of later drift):** the plan's `settings.gradle.kts` says `include("platform", "domain", "write", "read", "api", "frontend")`, but every other artifact in the same plan uses `courses-*` paths (`files_modified`, `courses-platform/build.gradle.kts`), and the verify commands use `./gradlew :courses-domain:build`. `include("platform")` maps to a `platform/` directory, which never existed. In Gradle, that project would silently resolve to an empty, mislocated project and the `:courses-domain:build` verify would fail. The executed repo resolved this by deviating from the plan: `settings.gradle.kts:21` reads `include("courses-platform", "courses-domain", "courses-write", "courses-read", "courses-api", "courses-frontend", "courses-test-support")`. Whatever the executor did to reconcile this, the plan as written cannot be re-executed.
- **HIGH (design, later paid for):** the plan's test architecture — a hand-registered `tasks.register<Test>("integrationTest")` over `sourceSets["test"]` with `@Tag("integration")`, plus `InfrastructureReachabilityTest` hardcoding `localhost:8024` and `localhost:5432` against a pre-started Compose stack — contradicts both the project's locked conventions ("`src/integrationTest/kotlin` … the Gradle jvm-test-suite plugin defines both suites … Never require a pre-started Compose stack or a fixed port", CONVENTIONS) and what a multi-service CI needs. This was reverted wholesale by ADR-0003: HEAD uses `jvm-test-suite` (`courses-write/build.gradle.kts:20-39`), the test lives at `courses-write/src/integrationTest/kotlin/io/github/aanbrn/courses/AxonServerRegistrationIT.kt` (not the plan's `src/test/kotlin/.../write/` path) as a thin subclass of the shared `AxonServerRegistrationSupport` base, and `InfrastructureReachabilityTest` does not exist anywhere at HEAD (verified by grep across all modules).
- **MEDIUM (instruction built on an assumption):** "pin 9.7.0 exactly … do NOT bump to 9.7.1 (outside the tested range)" converts RESEARCH's `[ASSUMED]` A2 into a hard prohibition. HEAD runs `gradle-9.9.1-all.zip` with a SHA-256 pin (`gradle/wrapper/gradle-wrapper.properties:4-5`) because KGP 2.4.21 was in fact verified on 9.9.1 (STACK.md). The instruction's confident "do NOT" was wrong; plans should carry assumption uncertainty forward, not launder it into hard do-nots. (Same pattern: `-distribution-type bin` vs HEAD's `-all`.)
- **LOW (drift):** `rootProject.name = "axon5-dcb-courses"` is claimed as a must-have artifact but appears nowhere at HEAD (`settings.gradle.kts` has no `rootProject.name`; `grep` across `*.kts` finds none) — Gradle falls back to the directory name. Also `allprojects { version = "0.1.0-SNAPSHOT" }` moved to `gradle.properties:7`, and the root-build Spotless convention moved entirely into `build-logic` (ADR-0002), so the root `build.gradle.kts` from Task 1/4 is unrecognizable at HEAD (`build.gradle.kts:1-133` is now actionlint/compose-image checks).

**Suggestions.**
- Add a `superseded-by: ADR-0002/0003` banner to the plan header so a future executor does not re-import the tag-based test task or the `include("platform")` names.
- If a plan instruction derives from an `[ASSUMED]` research entry, prefix the instruction with the assumption rather than asserting it as settled.
- The `InfrastructureReachabilityTest` idea (infra liveness without a driver) was not bad — its *binding to fixed host ports* was. The Testcontainers base kept the useful half (config from mapped ports, `AxonServerRegistrationSupport.kt:52-58`).

**Risk Assessment.** **MEDIUM.** The architectural spine (BOM platform, tracer-slice approach, registration proven from inside the app) was right and is exactly what HEAD proves today. But the plan's concrete build script cannot be re-executed against this repo (naming contradiction), and its test design was reverted at documented rework cost. As a historical artifact it is honest; as a reusable plan it is dangerous.

---

## 01-02

**Summary.** A tight, mechanical mirror of the tracer slice that correctly completes the three-service topology with real topology guards (no JPA/datasource in read; api must not depend on write/read). It inherits 01-01's module-naming contradiction, and its per-service copies of the registration test were correctly judged duplication and later consolidated into `courses-test-support`.

**Strengths.**
- Topology is enforced with greppable acceptance criteria, not prose: `grep -cE "spring-boot-starter-data-jpa|spring-boot-starter-jdbc|spring\.datasource" courses-read/build.gradle.kts` equals 0, and `grep -E 'project\(":(write|read)"\)'` on api's build file matches nothing. Both invariants still hold at HEAD (`courses-read/build.gradle.kts`, `courses-api/build.gradle.kts` declare only `project(":courses-domain")`).
- Correct deferral discipline: read gets no persistence until Phase 5; api does *not* get the `processResources` copy yet (Task 2 action explicitly explains why — it would break `./gradlew build` before `:courses-frontend:assembleFrontend` exists). That sequencing note is exactly right.
- The "three bootRun tasks and no fourth" check is a nice guard against accidental Spring Boot plugin application to non-app modules (P-anti-pattern in RESEARCH).

**Concerns.**
- **HIGH (inherited):** same self-contradiction as 01-01 — the plan's own settings include `read`/`api`, but every verify command is `./gradlew :courses-read:bootJar`, `:courses-api:bootJar`, and the must-have names jars `read-0.1.0-SNAPSHOT.jar` / `api-0.1.0-SNAPSHOT.jar`. Under the plan's own settings those task paths don't exist; under the executed settings the jar names are `courses-read-0.1.0-SNAPSHOT.jar` etc. Either way, must_have truths are false in one direction or the other.
- **MEDIUM (later consolidated):** three near-identical `AxonServerRegistrationIT` copies (one per module) embed the Awaitility/assert boilerplate three times. The project later extracted the whole thing into `AxonServerRegistrationSupport` (`courses-test-support/.../AxonServerRegistrationSupport.kt`, doc comment: "the three services cannot drift"). The plan created the drift risk it was later forced to fix; a shared base should arguably have been in scope from wave 2.
- **LOW (drift):** file paths `courses-read/src/test/kotlin/.../read/AxonServerRegistrationIT.kt`; HEAD has them in `src/integrationTest/kotlin/io/github/aanbrn/courses/` (no `.read`/`.write` subpackages). Harmless, but again unreexecutable.

**Suggestions.**
- When a plan's verify commands and its own settings.gradle snippet disagree, the verify command is the one that gets run against reality — normalize names in one place, or better, derive them from a single list.
- The test-consolidation lesson is worth a line in `.planning/codebase/CONVENTIONS.md`: "a test class copied into ≥2 modules moves to `courses-test-support` at the second copy."

**Risk Assessment.** **LOW.** The design content (mirroring, guards, deferrals) is correct and cheap; the flaws are mechanical naming and duplication, both already resolved at HEAD. The phase goal (TOPO-01) was verifiably achieved.

---

## 01-03

**Summary.** The strongest plan of the six: nearly every claim is verifiable against HEAD byte-for-byte, the two genuinely novel ideas (oxlint regex to close steiger's `shared` deep-import gap; the non-committed negative controls with a cleanliness check) were executed and survive unchanged. Its only real defect is an internal directory-name inconsistency in the scaffold command.

**Strengths.**
- The oxlint `no-restricted-imports` rule with `regex: "^@/[^/]+/[^/]+/.+"` is a genuinely clever fix for the verified steiger 0.10.0 gap (RESEARCH P10: deep imports into `shared/*` segments pass silently), and it is present at HEAD verbatim — `courses-frontend/.oxlintrc.json:9-17`. The negative control for it (Task 3, control 2) proves the gate actually fails on the case FE-02 names.
- Negative-control hygiene is done right: both controls are temporary edits with the revert enforced, not narrated — `test -z "$(git status --porcelain -- <files>)"` in the verify block means a forgotten violation fails the plan.
- `tasks.register<Exec>(...)` instead of the Gradle 9.9-deprecated delegate (P12), `npm ci` + committed lockfile (P16), TS7 `baseUrl` removal (P7), and the explicit "do NOT create empty FSD layers" — all match HEAD (`courses-frontend/package.json` scripts, `tsconfig.app.json` paths, no `widgets/`/`features/`/`entities/` dirs).
- Expectation management in must_haves is precise about *what proves what* (steiger + oxlint both exit 0; Gradle drives npm with no separate invocation).

**Concerns.**
- **MEDIUM (internal inconsistency):** Task 1 says "Scaffold with `npm create vite@9.3.4 frontend -- --template react-ts`" — that creates `./frontend/`, while every file path, the Gradle module (`courses-frontend/build.gradle.kts`), `.gitignore:23` (`courses-frontend/dist/`), and plan 01-05's CI cache key (`courses-frontend/package-lock.json`) say `courses-frontend/`. Same story for the verify commands `npm --prefix frontend ...` / `git -C frontend ...`. The executor clearly adapted (HEAD dir is `courses-frontend`), but as written the scaffold lands in the wrong directory.
- **LOW (latent rule breadth):** the regex bans *every* import with more than two segments. That is correct today, but conventions later place SSE in `shared/api/sse` — any future `import { x } from '@/shared/api/sse'` will be flagged even if it is a legitimate segment re-export. The escape (`@/shared/api` public API) is FSD-correct, but the plan should have stated the rule's stance on segment-level imports, since this will surface in a later phase as a confusing lint failure.
- **LOW (acknowledged gap left open):** the oxlint rule only guards `@/`-aliased deep imports; a *relative* sidestep into `shared` internals (`../../shared/ui/AppShell`) is still unguarded (steiger covers cross-slice, not the shared gap). The plan claims the gap is "closed" — it is narrowed, not closed.

**Suggestions.**
- Fix the scaffold command to `npm create vite@9.3.4 courses-frontend -- --template react-ts` (or scaffold-then-rename, stated explicitly).
- Add one sentence documenting the rule's position on segment files (`shared/api/sse`), so Phase 10's SSE work doesn't rediscover it.

**Risk Assessment.** **LOW.** Verified against HEAD almost line-for-line; the inconsistencies are cosmetic relative to the goal, and FE-01/FE-02 demonstrably hold.

---

## 01-04

**Summary.** The plan contains the best systems-thinking of the wave (liveness vs registration as separate proofs; the portable `set -m` process-group launch replacing macOS-absent `setsid`), but it is the plan the project itself has flagged as superseded: its headline deliverable — the SPA embedded in the `courses-api` bootJar — was reverted by ADR-0004, and the smoke script was rewritten from scratch.

**Strengths.**
- The registration-vs-liveness distinction is stated with correct mechanism: `/actuator/health` proves nothing about Axon connection in this starter combination (RESEARCH P5), so the script's health polls prove only boot, and registration stays with the `AxonServerRegistrationIT` tests. That separation survives conceptually at HEAD — `scripts/smoke-stack.sh:3-8` still refuses to scrape health and proves registration differently (via "Application connected" lines in the Axon Server log).
- The process-group handling is genuinely good: `set -m`, `--no-daemon` with an explicit *reason* ("without it the daemon forks the JVMs outside the group and the 'no orphan' guarantee is false"), and the `mkdir -p build` note for the log redirect. This is the kind of detail reviewers usually have to add; here the plan added it.
- Correct dependency shape: wave 3, `depends_on: [01-02, 01-03]` — the copy step needs both the frontend's dist and the api module.

**Concerns.**
- **HIGH (superseded, acknowledged):** Task 1's entire deliverable — `processResources` embedding `:courses-frontend/dist` into `courses-api`'s bootJar — does not exist at HEAD. `courses-api/build.gradle.kts` contains no `processResources`/static copy (verified); the frontend ships as a standalone nginx image on `:3000` (`compose.yaml:86-102`, ADR-0004), the browser talks to the gateway under a CORS allow-list (ADR-0007), and `opencode.jsonc:12` even documents "the agent drives the running SPA on :3000". The roadmap already annotates 01-04 as "superseded by ADR-0004", which is the right call — but executing this plan today would reintroduce an architecture the project deliberately abandoned. Plans in an executed state should carry that marker *in the plan file*, not only in the roadmap.
- **MEDIUM (drift):** the smoke script as specified (compose infra + `bootRun --parallel` + port polls + `<div id="root">` grep) was replaced wholesale: HEAD's `scripts/smoke-stack.sh` builds four Paketo images, brings up the *entire* stack via Compose (ADR-0005/0006), and asserts registration from server logs. Also, the plan's `bootRun`-based run loop is no longer the documented loop — `docs/tooling.md:338-342` now runs everything through Compose. Same lesson, different surface: the plan encodes the shortest-lived layer of the stack.
- **LOW:** the `<div id="root">` grep is marker-coupled to Vite's generated `index.html`; fine for a smoke script, worth a comment when copied.

**Suggestions.**
- Stamp the plan with the ADR-0004 supersession at the top (the objective paragraph still reads as current truth).
- The `--rerun` point recorded in `01-04-SUMMARY.md` ("the test task's inputs do not include Axon Server availability, so a cached result would make a repeat run prove nothing") should have been in the *plan's* verify block — that is a real gap the executor had to discover.

**Risk Assessment.** **MEDIUM.** Mechanically sound and thoughtfully detailed, but its central deliverable was reverted within the same phase, and the plan does not warn about that itself.

---

## 01-05

**Summary.** A CI plan with the right invariants (job id is load-bearing; build stays Docker-free; teardown unconditional; SHA-pinned actions) and one significant design decision that the project explicitly reversed: starting a shared Compose stack for integration tests. Its own acceptance criteria now fail against HEAD's `ci.yml`.

**Strengths.**
- The "job ids are required-check names and must not change" invariant is enforced, not just stated — the verify block counts `^  build:` and `^  format:` occurrences (`-eq 1`) and checks `pr-title.yml`'s `title:` job. That is exactly how a rename-getting-past-review failure mode is prevented.
- The Docker-free `./gradlew build` / Docker-backed `./gradlew integrationTest` split carried into CI is still the project's rule (`docs/tooling.md:315-320`), and the plan's refusal to fold `integrationTest` into `build` is documented with its reason.
- Versioning hygiene: full commit SHAs for `setup-node`, logs on `if: failure()`, unconditional `down -v`, and the unheralded-but-real `.playwright-mcp/` ignore fix (a gap the researcher actually hit — confirmed present at `.gitignore:51`).

**Concerns.**
- **HIGH (design, later reverted):** the plan's central step — "Start infrastructure: `docker compose up -d --wait`" — is absent from HEAD's CI, deliberately: `.github/workflows/ci.yml:91-93` states "The suites own their infrastructure: Testcontainers starts Axon Server and PostgreSQL per run on random ports and removes them afterwards, so no Compose stack is started here." The plan's acceptance criterion `grep -c "docker compose up -d --wait" .github/workflows/ci.yml` ≥ 1 **fails against HEAD**. This is not just drift — the shared-stack design had real defects the plan didn't see: a single long-lived stack shared by all three suites is shared mutable state across tests, it binds the fixed ports (8024/8124/5432) that conventions later forbade, and it made `integrationTest` order-dependent on an out-of-band `up`. Its own summary (`01-05-SUMMARY.md`, key-decisions) shows it shipped the compose step; ADR-0003 removed it. The plan's "Compose, not `services:` — one source of truth for image tags" argument was reasonable and *still lost* to test self-containment, which suggests the plan never weighed the alternative.
- **MEDIUM (dependency ordering):** the plan declares `depends_on: [01-01]` but is in the same wave-2 batch as 01-03, and its CI uses `actions/setup-node` with `cache-dependency-path: courses-frontend/package-lock.json`. If 01-05 merges before 01-03, that file does not exist and `setup-node`'s cache resolution fails — CI is red on the intermediate commit. The plan needed `depends_on: [01-02, 01-03]` (or to run in the wave after them). At HEAD the step exists with that exact key (`ci.yml:66-70`), so the ordering hazard was real in the plan's own graph.
- **LOW (drift):** the plan's snippet uses `setup-java` with `cache: gradle`; HEAD instead owns the Gradle cache via `gradle/actions/setup-gradle@…# v6.4.1` with a reasoned comment (`ci.yml:57-63`), and added an actionlint install step with checksum verification (`ci.yml:72-84`) plus draft-PR gating (`ci.yml:95-96`) the plan never anticipated. Not defects — additions that show the plan's CI shape was a floor, not a ceiling.

**Suggestions.**
- When a plan's verify criteria are green-on-day-zero and red at HEAD, the plan should be annotated as superseded (here: "CI infra model superseded by ADR-0003") so later phases' readers don't cite it as precedent — the *Testcontainers* rule is the precedent now.
- CI-touching plans must depend on every plan whose artifacts the CI references (lockfile paths, module names, task names).

**Risk Assessment.** **MEDIUM.** The invariant enforcement and security hygiene are genuinely good; but the plan's distinctive contribution (compose-backed CI infra) was the single largest rework item of the phase, and the plan did not consider the alternative that won.

---

## 01-06

**Summary.** A clean, honest closing plan whose main weakness is a conceptual one it admits to: the automated verify can only confirm the *configured* half of E2E-01 — proving the *driven* half is an agent's recorded action in the SUMMARY, not a reproducible command. The documentation requirements it set are all verifiably met at HEAD, though its URL claims (`:8080`) are stale after ADR-0004/0006.

**Strengths.**
- The verify block is candid about its own limits: "the automated check proves only the 'configured' half of E2E-01", and the note that the accessibility-tree snapshot does not surface `data-testid` — requiring `browser_evaluate` with `document.querySelector(...)` — matches the actual behavior of Playwright MCP, meaning the agent verification steps as written would work the first time.
- `opencode.jsonc` is confirmed untouched at HEAD (`opencode.jsonc:13-16`: `@playwright/mcp@0.0.85`, `--headless`) — and the plan *not* proposing to edit a file that already validates is the correct restraint.
- The document requirements all verify, then and now: `docs/tooling.md:372` (`./gradlew bootRun --parallel` loop), `docs/tooling.md:392` (`./gradlew integrationTest --tests '*AxonServerRegistrationIT'`, with the health-endpoint-is-not-registration caveat carried over), `docs/tooling.md:398-403` (the 12-hour unlicensed window, including the restart path via `docker compose restart axonserver`), and the stale "once `gradlew` exists" paragraph is gone.
- The portable launch procedure reuses 01-04's `set -m` pattern rather than repeating the `setsid` mistake.

**Concerns.**
- **MEDIUM (verification asymmetry):** E2E-01's "success criteria" is an observation made once, by an agent, recorded in a summary file. Nothing mechanical prevents regression (e.g., a broken SPA route, a broken proxy target). The plan handled this honestly and the roadmap deliberately defers the real suite to Phase 8 — acceptable, but worth stating that until Phase 8, E2E-01's proven half is a historical record, not a gate.
- **LOW (drift):** the plan's navigation target `http://localhost:8080/` is no longer where the SPA lives — the standalone frontend serves on `:3000` and the API serves only JSON+CORS (ADR-0004/0007; `compose.yaml:86-92`; `opencode.jsonc:12` already says ":3000"). Anyone replaying the plan's "recorded proof" steps verbatim would hit the gateway, not the app.
- **LOW:** the acceptance criteria say `x` succeeds because the api serves the SPA — true at the time, false now, same root cause.

**Suggestions.**
- One-line supersession note (URLs: `:8080` → `:3000`) referencing ADR-0004.
- In the Phase 8 plan, link back to this summary as the pre-history of the real Playwright suite so the "proven once by agent" status is closed out.

**Risk Assessment.** **LOW.** Lowest-risk plan of the six; its stated deliverables (docs + a recorded proof) hold at HEAD, and its only stalenesses are consequences of ADR-0004, not of its own design.

---

## Cross-plan comparison

The six plans follow a clear escalation: 01-01 (tracer, highest research density) → 01-02 (mechanical mirror) → 01-03 (frontend, most self-consistent) → 01-05 (CI) → 01-04 (integration glue) → 01-06 (records). Wave/dependency structure is sound overall (`01-04` correctly needs both `01-02` and `01-03`), with the one ordering flaw in `01-05` vs `01-03` (npm-lockfile cache key). Disposable-verification discipline is a consistent strength: *every* plan's verify block is a real command with a real `fails_when`, and plans repeatedly improved on their own inputs (Awaitility over RESEARCH's `Thread.sleep`; `set -m` over RESEARCH's `setsid`; oxlint over steiger's verified gap).

Two systemic weaknesses recur:

1. **Plans freeze the most volatile layer.** Build wiring, CI infra shape, and SPA delivery were the three areas the ADR series (0002, 0003, 0004, 0005, 0006) rewrote immediately after execution. A phase-1 plan cannot know Phase-3 decisions, but it *can* mark its opinions as provisional where it's guessing at architecture rather than reproducing verified research — the same way it marks assumptions elsewhere.
2. **Supersession lives outside the plan files.** The roadmap annotates 01-04; nothing annotates 01-01 (tag-based tests, wrapper pin), 01-02 (jar names), 01-05 (compose-in-CI), or 01-06 (`:8080`). Since GSD plans are the artifact future phases (and their subagents) re-read as precedent, stale "must be TRUE" claims are an active hazard, not just trivia.

## Overall risk assessment: **MEDIUM**

The phase goal was verifiably achieved — STATE records Phase 1 "verified 7/7", and every validated requirement (compose stack, three bootJars + SPA in one build, CI on every PR, FSD gates, MCP-configured headless drive) is demonstrably true at HEAD. The research-backed plans got the hard, durable things right (BOM platform mechanics, Axon Server standalone trap, TS7 `baseUrl`, steiger semantics, process-group cleanup). What costs the "LOW" rating is concentration of rework: the two most opinionated plans (01-01's test architecture, 01-05's CI infrastructure) encoded designs that ADR-0003 explicitly reversed, 01-01/01-02 carry an internal module-naming contradiction that makes them unreexecutable as written, and none of the six plan files warns a future reader about any of it. Recommendation: keep them as history, but stamp each wave-1 plan with its superseding ADR — or accept that anyone re-running these plans verbatim will produce code that fails today's conventions and today's CI.
