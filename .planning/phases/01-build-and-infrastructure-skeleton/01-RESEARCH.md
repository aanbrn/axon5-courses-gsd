# Phase 1: Build and infrastructure skeleton — Research

**Researched:** 2026-11-11
**Domain:** Multi-module Gradle/Kotlin build, Spring Boot 4 process topology, Axon Server + PostgreSQL local infrastructure, FSD frontend scaffold, CI gates
**Confidence:** HIGH (every version, build error and runtime claim for the local path was reproduced on this machine; the handful of items that were not are listed in the Assumptions Log)

## Summary

Phase 1 is plumbing, but three of its details fail loudly and late if guessed: the Gradle `java-platform` wiring, the Axon Server "no `default` context" trap, and the PostgreSQL 18 volume path. All three were reproduced here and are quoted verbatim below.

The build shape is proven end to end in a scratch project: `:platform` importing the Spring Boot and Axoniq BOMs with `javaPlatform { allowDependencies() }` + `api(platform(...))`, consumers declaring `implementation(platform(project(":platform")))`, and `./gradlew :api:bootJar` producing a jar whose `BOOT-INF/classes/static/index.html` is the Vite bundle. The `frontend` module drives `npm` through plain `Exec` tasks under the `base` plugin, so `assemble`/`check` exist and `./gradlew build` builds the SPA with no separate npm invocation (TOPO-04).

Runtime is proven too. A Kotlin/Spring Boot 4.1.2 app with `io.axoniq.framework:axoniq-spring-boot-starter:5.4.1` boots on JDK 21 and connects to Axon Server 2026.1.7 — but only after the server is initialised with `axoniq_axonserver_standalone=true`; without it every client loops on `[AXONIQ-1302] default: not found in any replication group`. The Axon actuator health indicator is **not** registered in this combination, so the "services registered" gate must assert `AxonServerConnectionManager.connections()` from inside the app, not scrape `/actuator/health`.

Frontend is proven too: a five-file FSD tree (`app`, `pages/home`, `shared/ui`) passes `steiger ./src --fail-on-warnings`, fails on an upward import, and fails on an alias deep import into a slice. Two surprises: TypeScript 7.0 removed `baseUrl` (the alias must be declared with `paths` alone), and steiger does **not** police deep imports inside `shared/*` segments.

**Primary recommendation:** build in four waves — Gradle skeleton, infrastructure + boot proof, frontend + FSD gate, CI + docs — and never let `./gradlew build` require Docker; the integration proof is a separate `integrationTest` task that CI runs after `docker compose up -d --wait` inside the same `build` job.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|---|---|---|---|
| Module graph, dependency versions, bootJar assembly | Build (Gradle) | — | One `./gradlew build` must produce every artifact; no module may own version truth outside `gradle/libs.versions.toml` + `:platform` |
| Infrastructure lifecycle (Axon Server, PostgreSQL) | Local container tier (Docker Compose) | CI runner | Same pinned images locally and in CI; no Testcontainers in Phase 1 |
| Process hosting, ports, Axon connection | JVM process tier (Spring Boot app) | — | Each service owns its own `server.port` and Axon client; Gradle only launches |
| SPA build | Frontend module (npm/Vite inside Gradle) | — | `:frontend` runs the toolchain; the output is a build artifact, not a deployment step |
| SPA delivery | API tier (`api` static resources) | CDN in production (out of scope) | Only `api` serves HTTP to the browser; Vite runs only in dev (Phase 2+) |
| FSD structure enforcement | Frontend toolchain (`steiger` via npm script) | Build (`:frontend:check`) | The linter must run where the source lives; Gradle only invokes it |
| Formatting enforcement | Build (Spotless) + repo scripts (Prettier) | CI | Spotless for Kotlin, Prettier for TS and repo files; CI verifies, never rewrites |
| CI gating | CI (GitHub Actions) | — | Job ids are the required-check names and must not change |
| Agent-driven browser check | Agent tooling (Playwright MCP client config) | — | Client-spawned MCP server; the repo only carries `opencode.jsonc` |

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PLAT-01 | `docker compose up -d` plus one Gradle run starts Axon Server, PostgreSQL and all three services | Compose file verified `--wait`-clean with both healthchecks; `axoniq_axonserver_standalone` required (§ Pitfall P1); `./gradlew bootRun --parallel` verified to run tasks concurrently (§ Pitfall P8) |
| PLAT-05 | CI builds the whole project and runs unit and distributed integration tests on every PR, starting Axon Server and PostgreSQL | `build` job keeps its id, gains `docker compose up -d --wait` + `./gradlew integrationTest`; integration test design in § Validation Architecture |
| QUAL-01 | Spotless, frontend Prettier and repo Prettier run in CI | Spotless `spotlessCheck` verified wired into `check`; `:frontend:check` hosts `format:check`; `format` job already runs `scripts/format-check.sh` |
| E2E-01 | Playwright MCP configured in tracked config and can drive the running SPA headless | `opencode.jsonc` `mcp.servers.playwright` validated against the OpenCode schema; headless navigation of the api-served SPA reproduced (§ Code Examples) |
| TOPO-01 | Three independent Spring Boot apps, each with its own bootJar | Boot plugin auto-detects the Kotlin top-level `main` (`Start-Class: demo.ApiApplicationKt`); three-module build produced three jars |
| TOPO-04 | `./gradlew build` produces the SPA bundle and all three bootJars, no separate npm step | Verified: `bootJar` contained `BOOT-INF/classes/static/index.html` + hashed assets after one Gradle invocation |
| FE-01 | SPA follows FSD with one-way layers and a public `index.ts` per slice | Minimal passing tree reproduced; steiger rules quoted (§ Pattern 4) |
| FE-02 | `npm run lint:fsd` passes in CI and fails on a layer violation or deep import | Reproduced both failure modes; one documented gap inside `shared/*` |
</phase_requirements>

## User Constraints

> No `CONTEXT.md` exists for this phase (no `/gsd-discuss-phase` run). The constraints below are locked decisions from `PROJECT.md`, `AGENTS.md`, `.planning/research/STACK.md` and `ROADMAP.md`, plus the phase brief. Treat them exactly as discuss-phase decisions: research them, do not reopen them.

### Project Constraints (from AGENTS.md)

These have the same authority as locked decisions. Every one of them is actionable in this phase.

| Directive | Action for Phase 1 |
|---|---|
| Kotlin on JVM 21, Gradle Kotlin DSL (`build.gradle.kts`) | `jvmToolchain(21)` in every Kotlin module; no Groovy DSL |
| Kotlin 2.4.21, Spring Boot 4.1.2, Axon 5.4.1 via `io.axoniq.framework:axoniq-framework-bom`, Axon Server 2026.1.7, PostgreSQL 18 | Pin exactly these in `gradle/libs.versions.toml` and `compose.yaml` |
| Six modules, `:platform` is `java-platform` with `allowDependencies()` and `api(platform(...))` | Exact snippet in § Pattern 2; both failure modes reproduced |
| Root package `io.github.aanbrn.showcase`; `group = "io.github.aanbrn.showcase"` | Root `build.gradle.kts` `allprojects { group = ... }`; package dirs under `src/main/kotlin/io/github/aanbrn/showcase` |
| Ports `api`:8080, `write`:8081, `read`:8082, Axon 8124/8024, PostgreSQL 5432, Vite 5173 | `server.port` per module; `server.port: 5173` + `/api` proxy in `vite.config.ts` |
| Line length 100, 4-space Kotlin, 2-space TS, LF, final newline | Root `.editorconfig` already correct — do not duplicate settings per module |
| `./gradlew spotlessApply` and `npm run format` are the only sanctioned normalizers | Document both; CI runs `spotlessCheck` / `format:check` only |
| No pre-commit hook; enforcement is CI only | Do not add `.git/hooks`, Husky, or Lefthook |
| Branch per phase `gsd/phase-1-...`; PR title conventional ≤72 chars | Phase branch and PR, not code |
| `.opencode/` is generated and gitignored; project MCP servers live in tracked `opencode.jsonc` | Only edit `opencode.jsonc`, never the generated `.opencode/opencode.json` |
| Commits: conventional prefixes, imperative, English, ≤72 chars subject | Applies to every plan's commit step |
| No automatic command retries; `CreateCourse` idempotent by key | Not exercised in Phase 1 — do not build retry plumbing |
| E2E lives in `frontend/e2e/`, outside `src`, `data-testid` selectors, no `waitForTimeout` | Phase 8 owns the suite; Phase 1 only reserves the directory shape and the MCP proof |
| Read-side caching deferred; one `read` service; no second read process | Phase 1 creates exactly three app modules |

### Locked decisions carried from planning artifacts

- Three service modules named `write`, `read`, `api`; `domain` is the shared contract library; `platform` re-exports BOMs and contains no code; `frontend` is a Gradle module.
- JPA/Flyway/datasource belong to `read` **only**, and only from Phase 5. `write` never gains a datasource.
- Every service is a real Spring Boot application; no shared runtime.
- CI required checks are `build`, `format`, `title` (verified job ids in `.github/workflows/ci.yml:21`, `:30`, `.github/workflows/pr-title.yml:15`).

## Standard Stack

### Core

| Library / tool | Version | Purpose | Why Standard / Evidence |
|---|---|---|---|
| Kotlin Gradle plugin | **2.4.21** | Language + compiler | `[VERIFIED: Maven Central — kotlin-gradle-plugin-2.4.21.pom HTTP 200]`; KGP 2.4.20–2.4.21 fully supports Gradle 7.6.3–**9.7.0** `[CITED: kotlinlang.org/docs/gradle-configure-project.html]` |
| Spring Boot Gradle plugin + BOM | **4.1.2** | App runtime, dependency management | `[VERIFIED: Maven Central metadata last stable 4.1.2; 4.2.0-RC2 is pre-release]`; `[CITED: docs.spring.io/spring-boot/4.1/system-requirements.html — "requires at least Java 17 … Gradle 8.x (8.14 or later) and 9.x"]` |
| Axoniq Framework BOM | **5.4.1** (`io.axoniq.framework:axoniq-framework-bom`) | Version alignment incl. commercial modules | `[VERIFIED: Maven Central POM 200]`; the BOM imports `org.axonframework:axon-framework-bom` and manages `axon-server-connector`, `axoniq-postgresql` and the other commercial modules |
| Axoniq Spring Boot starter | **5.4.1** (`io.axoniq.framework:axoniq-spring-boot-starter`) | Auto-config + connector | `[VERIFIED: starter POM]` depends on `axoniq-spring-boot-autoconfigure` and `axon-server-connector` — declare the starter alone; AF `5.0.3+` supports Spring Boot 3 **and 4** `[CITED: docs.axoniq.io/axon-framework-reference/5.4/spring-boot-integration/]` |
| Axon Server | **2026.1.7** (`axoniq/axonserver:2026.1.7`) | Event store, routing, token store | `[VERIFIED: Docker Hub tag exists, published 2026-10-15, amd64+arm64]` |
| PostgreSQL | **18.6** (`postgres:18.6`) | Read model (Phase 5+) | `[VERIFIED: Docker Hub tag exists, published 2026-11-06]`; `18` also exists and floats — pin the patch |
| Gradle wrapper | **9.7.0** | Build tool | `[VERIFIED: KGP 2.4.21 fully supported ceiling is 9.7.0; Boot 4.1.2 requires >= 8.14]` |
| JDK toolchain | **21** | Target JVM | Local `/Library/Java/JavaVirtualMachines/temurin-21.jdk` present; CI uses `actions/setup-java` temurin 21 |

### Supporting

| Library / tool | Version | Purpose | When to Use |
|---|---|---|---|
| Spotless | **8.11.0** | Kotlin/ktlint formatting gate | Root build, applied to every Kotlin module |
| ktlint (via Spotless) | **1.8.0** | Rule engine; reads root `.editorconfig` | Pin explicitly so the gate does not drift with the plugin |
| Prettier | **3.9.10** | TS/TSX + repo-file formatting | `frontend` devDependency (`format`, `format:check`) and `scripts/format*.sh` |
| steiger | **0.10.0** | FSD structure linter | `frontend` devDependency; `lint:fsd` npm script |
| `@feature-sliced/steiger-plugin` | **0.8.2** | FSD rule set (`fsd.configs.recommended`) | Imported by `steiger.config.js`; also a transitive dep of steiger 0.10.0 |
| create-vite | **9.3.4** (`--template react-ts`) | SPA scaffold | One-time generator: React ^19.3.1, Vite ^8.3.6 (resolves 8.3.7), TypeScript ~7.0.2, `@vitejs/plugin-react` ^6.1.2, oxlint ^1.90.0 |
| `@playwright/mcp` | **0.0.85** | Headless browser MCP server | Already pinned in `opencode.jsonc:15`; `--headless` verified |
| Node.js | **24.20.0** (local; CI pins the same) | npm toolchain | `scripts/setup-tooling.sh` already demands Node ≥ 24 |
| npm | 11.19.0 (local) | Package manager | `npm ci` requires a committed `frontend/package-lock.json` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|---|---|---|
| Gradle wrapper 9.7.0 | 8.14.6 | Boot supports it and it is below every ceiling, but it is two majors behind KGP's supported line and misses Gradle 9 fixes; choose 8.14.6 only if a plugin misbehaves on 9.x |
| Plain `Exec` tasks for `:frontend` | `com.github.node-gradle.node` 7.1.0 | node-gradle 7.1.0 applies and runs on Gradle 9.9 (reproduced), but its last release is Sep 2024 and there are open Gradle-9/configuration-cache issues (#350, #351). It also downloads Node, which duplicates `setup-node` in CI |
| Named Docker volumes | Bind mounts `./.axonserver`, `./pgdata` | Bind mounts make the data visible and match the existing `.gitignore` entries; named volumes keep the working tree clean. Either is fine — never both |
| `docker compose up -d --wait` | GitHub Actions `services:` | Compose reuses the exact local file (no duplicated image tags/env). Use `services:` only if compose proves flaky on the runner |

## Package Legitimacy Audit

Ran `gsd-tools query package-legitimacy check --ecosystem npm steiger @feature-sliced/steiger-plugin @playwright/mcp prettier`.

| Package | Registry | Latest publish | Downloads | Source Repo | Postinstall | Verdict | Disposition |
|---|---|---|---|---|---|---|---|
| `steiger` | npm | 2026-11-09 | 193k/wk | github.com/feature-sliced/steiger | none | SUS (`too-new`) | Approved — the signal is the *latest version's* publish date, not package age; official repo, high adoption, no postinstall. Pre-approved in project stack research |
| `@feature-sliced/steiger-plugin` | npm | 2026-11-01 | 226k/wk | github.com/feature-sliced/steiger | none | SUS (`too-new`) | Approved — same reasoning; same monorepo |
| `@playwright/mcp` | npm | 2026-11-09 | 10.1M/wk | github.com/microsoft/playwright-mcp | none | SUS (`too-new`) | Approved — Microsoft repo; already pinned in the tracked `opencode.jsonc` |
| `prettier` | npm | 2026-10-10 | 182M/wk | github.com/prettier/prettier | none | OK | Approved |
| `react`, `react-dom`, `vite`, `typescript`, `@vitejs/plugin-react`, `oxlint` | npm | — | — | official | none | Not flagged (installed by the official `create-vite` template) | Approved — no explicit install step required |
| `org.springframework.boot:*` 4.1.2 | Maven Central | — | — | github.com/spring-projects/spring-boot | — | Verified present (`spring-boot-starter-webmvc`, `-actuator`, `-data-jdbc`, `-security` all HTTP 200) | Approved |
| `io.axoniq.framework:*` 5.4.1 | Maven Central | — | — | axoniq.io | — | Verified present (BOM + starter + connector POMs HTTP 200) | Approved — commercial license, evaluation use |
| `com.diffplug.spotless` 8.11.0 | Gradle Plugin Portal / Maven Central | — | — | github.com/diffplug/spotless | — | Verified present | Approved |
| `com.pinterest.ktlint:ktlint-cli` 1.8.0 | Maven Central | — | — | github.com/pinterest/ktlint | — | Verified present | Approved |

**Packages removed due to SLOP verdict:** none.
**Packages flagged as suspicious:** the three npm tooling packages above. The recency signal is a known false positive for actively released projects; no `checkpoint:human-verify` task is warranted because each has an official repository, six-figure weekly downloads, and no install scripts.

## Architecture Patterns

### Pattern 1: Scaffolding order (and what breaks first)

The order is not cosmetic; four of the steps produce hard failures if skipped. All four messages below were reproduced in `/tmp/scaffold` and `/tmp/topo` on Gradle 9.9.0.

1. `settings.gradle.kts` — `rootProject.name`, `include(...)`, `dependencyResolutionManagement.repositories`, foojay resolver.
2. `gradle/libs.versions.toml` — every version and plugin alias.
3. Root `build.gradle.kts` — `group`/`version`, plugin declarations with `apply false`, Spotless convention, no Kotlin plugin applied to the root.
4. `:platform` — `java-platform` + `allowDependencies()` + BOM imports.
5. `:domain` — Kotlin library consuming `platform(project(":platform"))`.
6. `:write`, `:read`, `:api` — Kotlin + Spring + Boot plugins, app class, `application.yml`.
7. `:frontend` — `base` plugin + `npm` Exec tasks + the Vite/FSD tree.
8. Wrapper: `gradle wrapper --gradle-version 9.7.0 --distribution-type bin`.
9. `compose.yaml`, then `scripts/`, then CI.

Failure modes, in the order they bite:

| Wrong order / wrong construct | Exact failure |
|---|---|
| No `repositories` before resolving | `Cannot resolve external dependency org.jetbrains.kotlin:kotlin-stdlib:2.4.21 because no repositories are defined.` |
| `java-platform` without `allowDependencies()` | `Adding dependencies to platforms is not allowed by default. Most likely you want to add constraints instead. … Found dependencies in the 'api' configuration.` |
| `implementation(project(":platform"))` | `No matching variant of project ':platform' was found. … Incompatible because this component declares a platform and the consumer needed a library` |
| `:frontend` without `base` plugin | `Task with name 'assemble' not found in project ':frontend'.` |
| Boot plugin applied to `platform`/`domain` | Produces an empty `bootJar` and a second "application" in the graph — `./gradlew bootRun` then launches a module that has no `main` |
| Starter declared without the Axoniq BOM | Unversioned dependency cannot resolve (`Could not find io.axoniq.framework:axoniq-spring-boot-starter:`) |

### Pattern 2: The `platform` module (verified)

`platform/build.gradle.kts`:

```kotlin
plugins { `java-platform` }

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(platform(libs.axoniq.bom))
}
```

Consumers:

```kotlin
dependencies {
    implementation(platform(project(":platform")))
    // …
}
```

Reproduced resolution graph:

```text
+--- org.jetbrains.kotlin:kotlin-stdlib:2.4.21
|    +--- org.springframework.boot:spring-boot-dependencies:4.1.2
|    |    +--- org.jetbrains.kotlin:kotlin-stdlib:2.3.21 -> 2.4.21 (c)
|         +--- org.axonframework:axon-messaging:5.4.1 (c)
\--- org.axonframework:axon-messaging -> 5.4.1
```

Two consequences the plan must state:

- BOM propagation works through `platform(project(...))`; `axon-messaging` resolves to 5.4.1 with no version at the call site.
- Spring Boot 4.1.2's BOM imports `kotlin-bom` **2.3.21** (`[VERIFIED: spring-boot-dependencies-4.1.2.pom] <artifactId>kotlin-bom</artifactId> … <version>${kotlin.version}</version>`, `kotlin.version = 2.3.21`). The Kotlin plugin's 2.4.21 stdlib wins by conflict resolution, but a **versionless** `kotlin-reflect` would resolve to 2.3.21 and skew against the 2.4.21 compiler. Always declare `kotlin-reflect` with `version.ref = "kotlin"` (the STACK catalog already does).

### Pattern 3: Runtime topology and readiness

```text
docker compose up -d --wait
  ├─ axonserver (8024 HTTP/console, 8124 gRPC)  ── healthy: curl /actuator/info
  └─ postgres   (5432)                          ── healthy: pg_isready -U enrollment

./gradlew bootRun --parallel --console=plain
  ├─ :write:bootRun  server.port=8081  axon client → localhost:8124
  ├─ :read:bootRun   server.port=8082  axon client → localhost:8124
  └─ :api:bootRun    server.port=8080  axon client → localhost:8124, serves SPA from classpath:/static
```

- The Axon connector connects during startup and **retries** if the server is absent (`Failed to get connection to AxonServer. Scheduling a reconnect in 2000ms`), so the three services do not need `depends_on`.
- PostgreSQL is not touched by any Phase 1 service (no datasource yet), so its readiness only matters to the integration test.
- Spring Boot has no `depends_on` awareness and does not wait for the stack. Do **not** add `spring-boot-docker-compose`: it is a dev-time module, and three parallel `bootRun` tasks would each try to own the same Compose project.

### Pattern 4: FSD tree that passes `steiger --fail-on-warnings`

Reproduced tree (five source files, zero violations):

```text
frontend/src/
├── app/
│   ├── index.css
│   └── main.tsx                 # imports @/pages/home
├── pages/
│   └── home/
│       ├── index.ts             # public API: export { HomePage } from './ui/HomePage'
│       └── ui/HomePage.tsx      # imports @/shared/ui
└── shared/
    └── ui/
        ├── index.ts             # public API: export { AppShell } from './AppShell'
        └── AppShell.tsx         # data-testid="app-shell"
```

Why not more layers: `fsd/insignificant-slice` flags slices with zero or exactly one reference — "Note that pages are allowed to only have one reference … Another exception is when slices are only used on the App layer" `[CITED: github.com/feature-sliced/steiger — packages/steiger-plugin-fsd/src/insignificant-slice/README.md]`. A `widgets/` or `entities/` slice with a single use would therefore fail the gate that Phase 1 is supposed to make green. Create those layers with their first real slice (Phase 2 login, Phase 5 course), and record that intent in `docs/fsd.md` or the frontend README so a reviewer does not read the folder listing as compliance.

`steiger.config.js`:

```javascript
import { defineConfig } from 'steiger'
import fsd from '@feature-sliced/steiger-plugin'

export default defineConfig([...fsd.configs.recommended])
```

Verified behaviours:

- `npx steiger ./src --fail-on-warnings` → `✔ No problems found!`, exit 0.
- upward import (`shared/ui/AppShell.tsx` → `@/pages/home`) → `✘ Forbidden import from higher layer "pages".` / `fsd/forbidden-imports`, exit 1.
- alias deep import into a slice (`pages/admin` → `@/pages/home/ui/HomePage`) → two errors: `fsd/forbidden-imports` (cross-import) and `fsd/no-public-api-sidestep` (`Forbidden sidestep of public API when importing from "@/pages/home/ui/HomePage"`), exit 1.
- **Gap:** a deep import into a `shared` segment (`@/shared/ui/AppShell`) is **not** flagged, relative or aliased. `no-public-api-sidestep` covers slices; `public-api` only requires the `index.ts` to exist. Cover this by review, or extend the config later.

### Pattern 5: `:frontend` as a Gradle module (verified)

```kotlin
plugins { base }

val npmInstall = tasks.register<Exec>("npmInstall") {
    workingDir = projectDir
    commandLine("npm", "ci")
    inputs.file("package.json")
    inputs.file("package-lock.json")
    outputs.dir("node_modules")
}

val assembleFrontend = tasks.register<Exec>("assembleFrontend") {
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine("npm", "run", "build")
    inputs.dir("src")
    inputs.file("index.html")
    outputs.dir("dist")
}

val lintFsd = tasks.register<Exec>("lintFsd") {
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine("npm", "run", "lint:fsd")
}

val formatCheck = tasks.register<Exec>("formatCheck") {
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine("npm", "run", "format:check")
}

tasks.named("assemble") { dependsOn(assembleFrontend) }
tasks.named("check") { dependsOn(lintFsd, formatCheck) }
```

- `base` is required: without it `assemble` does not exist (reproduced failure).
- Use `tasks.register<Exec>("name")`, **not** `val x by tasks.registering(Exec::class)`: Gradle 9.9 emits `The 'val name by registering(Type::class) { }' property delegate syntax has been deprecated. This is scheduled to be removed in Gradle 10.`
- `processResources` in `api`:

```kotlin
tasks.named<ProcessResources>("processResources") {
    dependsOn(":frontend:assembleFrontend")
    from(project(":frontend").layout.projectDirectory.dir("dist")) { into("static") }
}
```

Verified jar contents after a single `./gradlew :api:bootJar`:

```text
BOOT-INF/classes/static/index.html
BOOT-INF/classes/static/assets/index--TAMJsRt.js
BOOT-INF/classes/static/assets/index-D01ZHKqt.css
```

and `GET http://localhost:8080/` returned the generated `index.html` with `<script type="module" crossorigin src="/assets/index--TAMJsRt.js">`.

### Recommended Project Structure

```text
.
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/{gradle-wrapper.jar,gradle-wrapper.properties}
├── gradlew, gradlew.bat
├── compose.yaml
├── .github/workflows/{ci.yml,pr-title.yml}
├── scripts/{format.sh,format-check.sh,setup-tooling.sh,smoke-stack.sh}
├── docs/tooling.md
├── platform/build.gradle.kts
├── domain/build.gradle.kts
├── write/
│   ├── build.gradle.kts
│   └── src/main/kotlin/io/github/aanbrn/showcase/WriteApplication.kt
│   └── src/main/resources/application.yml
│   └── src/test/kotlin/... (unit + @Tag("integration"))
├── read/  … ReadApplication.kt
├── api/   … ApiApplication.kt, static resources from :frontend
└── frontend/
    ├── build.gradle.kts
    ├── package.json, package-lock.json, tsconfig*.json, vite.config.ts
    ├── steiger.config.js, .prettierignore, index.html
    └── src/{app,pages,shared}
```

### Anti-Patterns to Avoid

- **Applying the Spring Boot plugin to `platform`, `domain` or `frontend`.** Only the three services may have `bootRun`/`bootJar`; otherwise `./gradlew bootRun --parallel` launches non-apps.
- **`implementation(project(":platform"))`.** It is a platform, not a library — variant resolution fails.
- **Duplicating versions at call sites.** Every dependency version lives in the catalog or, better, nowhere (BOM-managed).
- **Running `npm install` inside Gradle.** Use `npm ci`; `install` rewrites the lockfile and makes builds non-reproducible.
- **Creating empty FSD layers to "look complete".** steiger's `insignificant-slice`/`no-segmentless-slices` will flag placeholders, and the fix will be disabling rules rather than fixing architecture.
- **Health-checking Axon Server on `/actuator/health`.** It returns non-200 when any component (including `license`) is not UP; `/actuator/info` is the documented liveness/readiness endpoint and returned 200 throughout.
- **Using the app's `/actuator/health` to prove Axon registration.** The Axon health indicator was absent from the response in the reproduced boot (§ Pitfall P5).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Dependency version alignment across 6 modules | Hand-written `versions` blocks per module | Version catalog + `:platform` `java-platform` | Gradle's platform semantics propagate `(c)` constraints transitively; manual pinning drifts silently |
| Running npm from Gradle | A custom Node downloader / shell script | `Exec` tasks (`npm ci`, `npm run build`) + `setup-node` in CI | Two lines of configuration; Node is already a documented prerequisite |
| FSD boundary checking | Hand-rolled import-graph script | `steiger` + `@feature-sliced/steiger-plugin` | 20 rules maintained upstream, including alias-aware sidestep detection |
| Kotlin formatting | Manual `.editorconfig` enforcement in CI | Spotless `ktlint()` | `spotlessCheck` is already wired into `check`; ktlint reads `.editorconfig` |
| Waiting for infra readiness in app code | Retry loops in Spring beans | Compose healthchecks + `docker compose up -d --wait` | Readiness is an infrastructure concern; the Axon connector already retries |
| Proving services registered | Scraping the Axon Server console | `AxonServerConnectionManager.connections()` inside the app | The console API changed (`/v1/components` is 404 in 2026.1.7); the connection manager is the authoritative client-side truth |
| Process orchestration for three services | A bash supervisor or `concurrently` | `./gradlew bootRun --parallel` | Verified: Gradle runs same-named tasks in different projects concurrently |

## Runtime State Inventory

Omitted — this is a greenfield phase; no rename/refactor/migration is involved and no runtime state exists yet.
## Exact file contents

Everything in this section was either reproduced on this machine or is a direct transcription of a verified document. Where a value is unproven it is marked.

### `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Auto-provision the JDK 21 toolchain on machines that only ship a newer JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "axon5-dcb-showcase"
include("platform", "domain", "write", "read", "api", "frontend")
```

`[VERIFIED: foojay-resolver-convention 1.0.0 exists on plugins.gradle.org]`; the Kotlin docs prescribe exactly `version "1.0.0"` for Gradle 8.0.2+ `[CITED: kotlinlang.org/docs/gradle-configure-project.html]`.

### `gradle/libs.versions.toml`

```toml
[versions]
kotlin = "2.4.21"
springBoot = "4.1.2"
axon = "5.4.1"
spotless = "8.11.0"
ktlint = "1.8.0"
node = "24.20.0"
prettier = "3.9.10"
steiger = "0.10.0"
steigerPlugin = "0.8.2"

[libraries]
spring-boot-bom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "springBoot" }
axoniq-bom = { module = "io.axoniq.framework:axoniq-framework-bom", version.ref = "axon" }
axoniq-spring-boot-starter = { module = "io.axoniq.framework:axoniq-spring-boot-starter" }
kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "kotlin" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "springBoot" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```

Boot-managed artifacts (`spring-boot-starter-webmvc`, `-actuator`, `-test`, `reactor-core`, `postgresql`, `flyway-*`, …) are deliberately **not** in the catalog — they take their version from `:platform`. Catalog entries only exist where the BOM does not manage the version.

The frontend tool versions live in `frontend/package.json` (npm owns them), not in the catalog; `node`/`prettier`/`steiger` entries above are documentation of the pin, not consumed by Gradle.

### `build.gradle.kts` (root)

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = "io.github.aanbrn.showcase"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "com.diffplug.spotless")

        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                ktlint(libs.versions.ktlint.get())
                trimTrailingWhitespace()
                endWithNewline()
            }
            kotlinGradle {
                ktlint(libs.versions.ktlint.get())
            }
        }
    }
}
```

`spotlessCheck` is added to `check` automatically once the plugin is applied — verified by `gradle :lib:check --dry-run`, which lists `:lib:spotlessKotlinCheck`, `:lib:spotlessKotlinGradleCheck`, `:lib:spotlessCheck`, and by a real failure:

```text
> Task :lib:spotlessKotlinCheck FAILED
Execution failed for task ':lib:spotlessKotlinCheck'
  Run './gradlew spotlessApply' to fix all violations.
```

The `subprojects { plugins.withId(...) }` form is required: applying Spotless unconditionally to `platform` (a `java-platform`) and `frontend` (no Kotlin) creates no-op tasks and a misleading failure surface.

### `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.caching=true
kotlin.code.style=official
```

Do **not** enable `org.gradle.configuration-cache` in Phase 1 — three long-running `bootRun` tasks are already the most fragile part of the developer loop, and a toolchain change that fails late is not worth the speed.

### `platform/build.gradle.kts`

Shown in § Pattern 2.

### `domain/build.gradle.kts`

```kotlin
plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(21) }

dependencies {
    implementation(platform(project(":platform")))
    // Phase 3 adds axon-messaging/annotations and the message contract.
}
```

An empty Kotlin library compiles cleanly and keeps Phase 3's "freeze the contract" honest.

### `write/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(platform(project(":platform")))
    implementation(project(":domain"))
    implementation(libs.axoniq.spring.boot.starter)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(libs.kotlin.reflect)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform { excludeTags("integration") } }

tasks.register<Test>("integrationTest") {
    description = "Runs tests tagged 'integration'. Requires docker compose up -d."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.named("test"))
}
```

No datasource, no JPA, no Flyway — and none of them even exist in another module yet. `spring-boot-starter-webmvc` is present only so the actuator ports (8081/8082) exist and can be polled.

`read/build.gradle.kts` and `api/build.gradle.kts` are identical except `server.port`, the app class, and `api` adding the `processResources` copy from `:frontend`.

### `application.yml` (one per service)

`write` (port 8081; `read` identical with name/port swapped):

```yaml
spring:
  application:
    name: write

server:
  port: 8081

axon:
  axonserver:
    servers: ${AXON_SERVER:localhost:8124}

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

`api` (port 8080) adds nothing else in Phase 1 — the SPA arrives as classpath `static/`.

Verified: `axon.axonserver.servers` is the documented property key `[CITED: docs.axoniq.io — axon.axonserver.servers=localhost:8124]`; the default when omitted is `localhost:8124`. `axon.axonserver.enabled=false` disables the connector `[CITED: same page]`. The starter's auto-configuration is gated on that property `[VERIFIED: string constant axon.axonserver.enabled present in AxonServerAutoConfiguration and AxonServerActuatorAutoConfiguration]`.

### `compose.yaml`

```yaml
services:
  axonserver:
    image: axoniq/axonserver:2026.1.7
    ports:
      - "8024:8024"
      - "8124:8124"
    environment:
      axoniq_axonserver_hostname: localhost
      axoniq_axonserver_standalone: "true"
    volumes:
      - axonserver-data:/axonserver/data
      - axonserver-events:/axonserver/events
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:8024/actuator/info >/dev/null || exit 1"]
      interval: 5s
      timeout: 3s
      retries: 30
      start_period: 30s

  postgres:
    image: postgres:18.6
    environment:
      POSTGRES_DB: enrollment
      POSTGRES_USER: enrollment
      POSTGRES_PASSWORD: enrollment
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U enrollment -d enrollment"]
      interval: 5s
      timeout: 3s
      retries: 20

volumes:
  axonserver-data:
  axonserver-events:
  pgdata:
```

Verified on Compose v2.32.1: `docker compose up -d --wait` reports both containers `Healthy`. Proven details:

- `axoniq_axonserver_standalone: "true"` is **mandatory on a fresh volume** (§ Pitfall P1).
- The Axon Server image contains `curl`, `wget`, `bash` and Java 21 (`/opt/java/openjdk/bin/java`), so the healthcheck needs no extra tooling.
- PostgreSQL 18's `PGDATA` is `/var/lib/postgresql/18/docker`; mounting at `/var/lib/postgresql` is the documented layout (§ Pitfall P2).
- `POSTGRES_PASSWORD: enrollment` is a local-only credential. Never reuse it; note in `docs/` that it is not a secret and not a production default.

### `.github/workflows/ci.yml` — build job

Keep the job id `build`. Add Node, Compose, and the integration task; keep `docker compose down -v` unconditional.

```yaml
  build:
    runs-on: ubuntu-24.04
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1

      - uses: actions/setup-java@77dd5b2ce225ea11469928ac317850245a74ae78 # v6.2.0
        with:
          distribution: temurin
          java-version: '21'
          cache: gradle

      - uses: actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1 # v7.1.0
        with:
          node-version: '24.20.0'
          cache: npm
          cache-dependency-path: frontend/package-lock.json

      - name: Start infrastructure
        run: docker compose up -d --wait

      - name: Build and unit test
        run: ./gradlew build --no-daemon

      - name: Integration tests (Axon Server + PostgreSQL)
        run: ./gradlew integrationTest --no-daemon

      - name: Infrastructure logs
        if: failure()
        run: docker compose logs --no-color

      - name: Stop infrastructure
        if: always()
        run: docker compose down -v --remove-orphans
```

Action SHAs verified from the GitHub API (`v7.0.1` checkout / `v6.2.0` setup-java match the pins already in the file; `v7.1.0` setup-node and `v7.0.2` upload-artifact resolved live). The `format` job is unchanged; `pr-title` is unchanged.

Two decisions the plan must state explicitly:

- **Compose, not `services:`** — one source of truth for image tags. If the runner proves flaky, `services:` is the fallback and the required check stays `build` either way.
- **`./gradlew build` must stay Docker-free.** If the integration test leaks into `test`, every local `./gradlew build` starts needing a server, and the loop dies. The tag split is the guard.

### `scripts/smoke-stack.sh` (new, Phase 1 verification aid)

Purpose: prove criterion 1 and 3 mechanically without a test framework.

```bash
#!/usr/bin/env bash
# Start the compose stack and the three services, prove liveness, tear down.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

cleanup() {
  [ -n "${RUN_PID:-}" ] && kill -- "-${RUN_PID}" 2>/dev/null || true
  docker compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker compose up -d --wait
mkdir -p build
setsid ./gradlew bootRun --parallel --console=plain > build/bootrun.log 2>&1 &
RUN_PID=$!

for port in 8081 8082 8080; do
  for _ in $(seq 1 60); do
    curl -fsS "http://localhost:${port}/actuator/health" >/dev/null 2>&1 && break
    sleep 2
  done
  curl -fsS "http://localhost:${port}/actuator/health" >/dev/null
  echo "port ${port}: up"
done

curl -fsS http://localhost:8080/ | grep -q '<div id="root">'
echo "SPA shell served by api"
```

`setsid` + `kill -- -$RUN_PID` kills the whole process group — killing the Gradle process alone can orphan three JVMs. Note that `build/` may not exist before the redirect; create it or write the log to `build/` only after a build has run.

## Common Pitfalls

All items below were observed, not guessed. Each lists how to detect it early.

### P1: Axon Server has no `default` context on a fresh volume

**What goes wrong:** the container is healthy, the gRPC port accepts connections, and every client loops forever.
**Evidence:**

```text
WARN  i.a.a.c.impl.AxonServerManagedChannel : Connecting to AxonServer node [localhost:8124] failed.
io.grpc.StatusRuntimeException: NOT_FOUND: [AXONIQ-1302] default: not found in any replication group
INFO  i.a.a.c.impl.AxonServerManagedChannel : Failed to get connection to AxonServer. Scheduling a reconnect in 2000ms
```

**Fix:** `axoniq_axonserver_standalone: "true"` in the compose environment (documented as `AXONIQ_AXONSERVER_STANDALONE=true`). After that:

```text
INFO  i.a.a.c.impl.AxonServerManagedChannel : Successfully connected to localhost:8124
INFO  i.a.a.connector.impl.ControlChannelImpl : Connected instruction stream for context 'default'. Sending client identification with clientId 15018@…
```

**Warning sign:** `connections()` returns `{"default": false}` and the log repeats every 2 s.

### P2: PostgreSQL 18 refuses the pre-18 volume path

**What goes wrong:** `postgres` exits with code 1 and the compose stack never becomes ready.
**Evidence:** mounting at `/var/lib/postgresql/data` produced

```text
Counter to that, there appears to be PostgreSQL data in:
  /var/lib/postgresql/data (unused mount/volume)
…
The suggested container configuration for 18+ is to place a single mount
at /var/lib/postgresql …
```

and `docker run --rm postgres:18 printenv PGDATA` → `/var/lib/postgresql/18/docker`.
**Fix:** mount the volume at `/var/lib/postgresql`.

### P3: `java-platform` cannot hold dependencies without opting in

**Evidence:** `Adding dependencies to platforms is not allowed by default. Most likely you want to add constraints instead. … Found dependencies in the 'api' configuration.`
**Fix:** `javaPlatform { allowDependencies() }` + `api(platform(bom))` inside `dependencies`.

### P4: `project(":platform")` is not a library

**Evidence:** `No matching variant of project ':platform' was found … Incompatible because this component declares a platform and the consumer needed a library`.
**Fix:** `implementation(platform(project(":platform")))`.

### P5: Axon actuator health is not a registration signal

**What goes wrong:** a gate scrapes `/actuator/health` for an `axon` component and either finds nothing or gets a false UP.
**Evidence:** with `management.endpoint.health.show-details: always`, the response contained `diskSpace`, `livenessState`, `ping`, `readinessState`, `ssl` — **no Axon component** — while `AxonServerConnectionManager.connections()` returned `{"default":true}`. The indicator itself calls `builder.up()` first and only marks DOWN when the connection map is non-empty and every value is false, so an empty map reads UP.
**Fix:** assert `connectionManager.connections()["default"] == true` (or `isConnected("default")`) in an integration test; do not use health for registration.

### P6: Kotlin version skew from Boot's BOM

**Evidence:** `spring-boot-dependencies:4.1.2` imports `kotlin-bom` with `kotlin.version = 2.3.21`; the resolved graph shows `kotlin-stdlib:2.3.21 -> 2.4.21 (c)` while a versionless `kotlin-reflect` would stay at 2.3.21.
**Fix:** `kotlin-reflect` declares `version.ref = "kotlin"`; if any other Kotlin artifact appears, pin it in `:platform` constraints.

### P7: TypeScript 7 removed `baseUrl`

**Evidence:** `tsconfig.app.json(18,5): error TS5102: Option 'baseUrl' has been removed. Please remove it from your configuration.`
**Fix:** declare only `"paths": { "@/*": ["./src/*"] }` in `tsconfig.app.json`. Verified: `tsc -b && vite build` succeeds and both resolve the alias. Vite's own alias is `resolve.alias` with `fileURLToPath(new URL('./src', import.meta.url))`.

### P8: `--parallel` semantics and process lifetime

**What goes wrong:** assumptions about ordering or cleanup.
**Evidence:** two projects' tasks both printed their start timestamp as `1794351445347` and `--parallel` ran them concurrently with no deprecation warning on Gradle 9.9.0.
**Facts to encode:** `--parallel` parallelises across **projects**, not within one; only `write`/`read`/`api` have `bootRun`, so `./gradlew bootRun --parallel` launches exactly three; Ctrl-C stops the Gradle run, but a script that kills only the Gradle PID can orphan the JVMs (use a process group).

### P9: Port collisions are loud but late

**Evidence:** a second app on the same port exits with code 1 and `Web server failed to start. Port 8091 was already in use.`
**Fix:** fixed ports in `application.yml`; document `lsof -nP -iTCP:8080 -sTCP:LISTEN`; let the Gradle run fail rather than adding a retry.

### P10: steiger flags placeholder slices, and ignores `shared` deep imports

**Evidence:** a clean five-file tree passes; an upward import fails; an alias deep import into a page slice fails **twice**; a deep import into `shared/ui` passes silently.
**Fix:** keep the tree minimal and real; treat `shared` sidesteps as a review concern; optionally add a later rule/CI grep.

### P11: `:frontend` has no lifecycle tasks by default

**Evidence:** `Task with name 'assemble' not found in project ':frontend'.`
**Fix:** `plugins { base }`.

### P12: Gradle 9.9 deprecates the Kotlin-DSL delegated registrar

**Evidence:** `The 'val name by registering(Type::class) { }' property delegate syntax has been deprecated. This is scheduled to be removed in Gradle 10.`
**Fix:** `tasks.register<Exec>("name") { … }`.

### P13: Health-checking Axon Server on `/actuator/health` couples readiness to licensing

**Evidence:** `/actuator/health` exposes a `license` component — observed as `license: UP` during the trial — and Axon documents that a license in its grace period reports WARN and an expired one DOWN, with any WARN making the overall status WARN `[CITED: docs.axoniq.io/axon-server-reference/v2026.1 — actuator endpoints]`. `/actuator/info` returned 200 with `{"app":{"name":"Axon Server","version":"2026.1.7"}}` throughout.
**Fix:** use `/actuator/info` in the healthcheck.

### P14: The 12-hour unlicensed window is real and visible

**Evidence:**

```text
WARN  i.a.a.licensing.LicenseValidityEnforcer : [LICENSE] No license detected. Running in unregistered mode.
Trial access is limited to 12 hours. Register at platform.axoniq.io to continue.
Shutdown scheduled at 2026-11-11T11:09:17.178Z.
```

**Implication:** a long demo day dies mid-session. Document the restart; never present the stack as production-ready. Phase 1 proves only that the commercial artifacts resolve and boot without a license — not that they are licensed.

### P15: App-class placement and test discovery

**What to do:** put each app class in the module's root package (`io.github.aanbrn.showcase.WriteApplication`, `ReadApplication`, `ApiApplication`) so component scanning covers the module's own feature packages, and annotate the integration test with `@SpringBootTest(classes = [WriteApplication::class])` rather than relying on package-walk discovery.
**Why:** each module is a separate jar, so two `@SpringBootApplication` classes in the same package only coexist because they never share a classpath — an accidental inter-module dependency makes discovery ambiguous. `[ASSUMED]` — not reproduced, because no module currently depends on another; the explicit `classes =` keeps it true by construction.

### P16: `npm ci` without a lockfile

**What goes wrong:** the first CI build fails because `npm ci` requires `package-lock.json`.
**Fix:** generate and commit the lockfile during scaffolding (`npm install` once), and never let Gradle run `npm install`.
## Code Examples

### Integration test that proves registration (the PLAT-01 gate)

```kotlin
package io.github.aanbrn.showcase.write

import io.axoniq.framework.axonserver.connector.api.AxonServerConnectionManager
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import kotlin.test.assertTrue

@Tag("integration")
@SpringBootTest(classes = [WriteApplication::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AxonServerRegistrationIT {

    @Autowired
    lateinit var connectionManager: AxonServerConnectionManager

    @Test
    fun `write service registers with Axon Server`() {
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline) {
            if (connectionManager.connections()["default"] == true) return
            Thread.sleep(250)
        }
        assertTrue(false, "write never registered with Axon Server: ${connectionManager.connections()}")
    }
}
```

`AxonServerConnectionManager` is `io.axoniq.framework.axonserver.connector.api.AxonServerConnectionManager` with `connections(): Map<String, Boolean>`, `isConnected(String)`, `getDefaultContext()` (`[VERIFIED: javap of axon-server-connector-5.4.1.jar]`). Do not autowire by the older `org.axonframework.axonserver.connector` package — that class moved and will not compile.

### Driver-free infrastructure reachability test

```kotlin
package io.github.aanbrn.showcase.write

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
class InfrastructureReachabilityTest {

    @Test
    fun `Axon Server answers its info endpoint`() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:8024/actuator/info")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode())
    }

    @Test
    fun `PostgreSQL accepts TCP connections`() {
        Socket("localhost", 5432).use { assertTrue(it.isConnected) }
    }
}
```

Deliberately no JDBC driver in Phase 1 — the PostgreSQL driver belongs to `read` and lands with the datasource in Phase 5. TCP reachability is enough to prove the container is up.

### Playwright MCP headless verification (E2E-01)

Preconditions: `docker compose up -d --wait` and the three services running (`./gradlew bootRun --parallel`); the SPA is served by `api` on `:8080`.

**No change to `opencode.jsonc` is required.** The tracked entry at `opencode.jsonc:13-16` — `"mcp" → "servers" → "playwright" → {type: "local", command: ["npx","-y","@playwright/mcp@0.0.85","--headless"]}` — validates against the published OpenCode schema (`mcp.servers.*` accepts `Mcp.LocalConfigEncoded`, which requires `type` and `command`), and `0.0.85` is the registry's current `latest`. The Phase 1 deliverable is the recorded proof, not an edit.

Agent steps (tools exposed by the `playwright` MCP server configured in `opencode.jsonc`):

1. `browser_navigate` → `http://localhost:8080/`
2. `browser_snapshot`

Reproduced result on this machine:

```yaml
- generic [ref=e3]:
  - banner [ref=e4]: Axon 5 DCB Showcase
  - main [ref=e5]:
    - heading "Course catalog" [level=1] [ref=e6]
```

with `Page Title: Axon 5 DCB Showcase`. The snapshot does **not** surface `data-testid` (it is an accessibility tree); to assert a test id, use `browser_evaluate` with `document.querySelector('[data-testid="app-shell"]') !== null`. For Phase 1 the heading assertion is sufficient and is the one to record in the verification note.

**`.gitignore` gap found by running it:** the MCP server wrote its snapshots to `.playwright-mcp/` in the repository root, which is not ignored (`.gitignore` has `.playwright/` but not `.playwright-mcp/`). Phase 1 must add `.playwright-mcp/` to `.gitignore`, otherwise the first verification run leaves untracked noise in `git status`.

If the first navigation fails with a missing-browser error, run `npx playwright install chromium` once (`[ASSUMED]` — browsers were already present here).

### Frontend package scripts

```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "format": "prettier --write .",
    "format:check": "prettier --check .",
    "lint": "oxlint",
    "lint:fsd": "steiger ./src --fail-on-warnings"
  }
}
```

`frontend/.prettierignore` must list `dist/`, `node_modules/`, `playwright-report/`, `test-results/`. Prettier resolves the root `.prettierrc.json` from `frontend/` (printWidth 100, singleQuote, trailingComma all), and the root `.prettierignore` deliberately excludes `frontend/` so the repo-level script never double-formats it.

### Vite config with the alias and the dev proxy

```typescript
import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  build: { outDir: 'dist' },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
})
```

## State of the Art

| Old approach | Current approach | When changed | Impact |
|---|---|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` | Spring Boot 4.0 | `-web` is deprecated; both artifacts exist at 4.1.2, use `-webmvc` |
| Jackson 2 (`com.fasterxml`) | Jackson 3 (`tools.jackson`) | Spring Boot 4 / AF 5 | Boot 4.1.2 manages `jackson-bom` 3.1.7; the Axon starter's `ObjectMapperAutoConfiguration` already uses `tools.jackson` (verified in the startup log) |
| Boot-managed Kotlin | Explicit Kotlin 2.4.21 with pinned `kotlin-reflect` | Kotlin 2.4 | Boot's BOM still carries 2.3.21; skew is silent unless reflect is pinned |
| `baseUrl` in `tsconfig` | `paths` relative to the tsconfig | TypeScript 7.0 | The scaffold must not emit `baseUrl` |
| `val t by tasks.registering(...)` | `tasks.register<T>("name")` | Gradle 9.9 deprecation | Avoids a Gradle 10 blocker |
| AF4 `@Aggregate`/`@AggregateIdentifier` | `@EventSourced` + `@EventCriteriaBuilder` + `@InjectEntity` | AF 5 | Do not add any aggregate API in Phase 1; there are no handlers yet |
| Axon Server `/v1/components` for clients | No equivalent public REST route found (404 in 2026.1.7) | 2026.1 line | Prove registration from the client side |
| `postgres:17` volume at `/var/lib/postgresql/data` | `postgres:18` volume at `/var/lib/postgresql` | PostgreSQL 18 image | Wrong path exits with code 1 |

**Deprecated/outdated in this project's context:** the OSS `axon-spring-boot-starter` (no Axon Server connector), `InMemoryEventStorageEngine` for anything shared, `spring-boot-docker-compose` for this topology, and `com.github.node-gradle.node` (usable, but behind and unnecessary).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| A1 | Playwright MCP browsers are installed on this machine and on CI; a clean machine may need `npx playwright install chromium` | Code Examples | E2E-01 verification blocked until one install command runs |
| A2 | Gradle 9.7.0 is KGP 2.4.21's fully supported ceiling | KGP compatibility table | No fallback needed; 9.9.x is outside the tested range |
| A3 | GitHub's `ubuntu-24.04` runner ships Compose v2 with `--wait` and a reachable Docker daemon | CI | The `build` job fails at the first step; fall back to `services:` or a manual wait loop |
| A4 | A five-file FSD tree stays adequate when Phase 2 adds a login page | Pattern 4 | Adding a second page is fine (pages may have one reference); if not, add the missing layer with its first real slice |
| A5 | Named volumes survive `docker compose down` but not `down -v` | compose.yaml | Read models lost between runs if someone uses `-v`; documented behaviour of Compose, not this project |
| A6 | The lockfile generated at scaffold time is committed and stable | Pattern 5 | `npm ci` fails in CI; regenerate with `npm install` once |
| A7 | Naming each app class `io.github.aanbrn.showcase.{Write,Read,Api}Application` avoids test-discovery ambiguity without further configuration | Pitfall P15 | Explicit `@SpringBootTest(classes = [...])` makes it moot; adjust package names if the verifier prefers `…showcase.write` |

**If this table is short, that is the point:** everything else was executed and its output pasted above.

## Open Questions (RESOLVED)

1. **Does Axon Server 2026.1 need `axoniq.axonserver.standalone-dcb: true` for DCB?**
   - What we know: Axon's own `examples/docker-compose.yaml` sets it; Phase 1 does not need DCB, only a `default` context.
   - What's unclear: whether DCB is on by default in 2026.1.7 standalone mode.
   - Resolution: leave it out of Phase 1; Phase 4 adds it if the spike fails without it, and records the answer there.

2. **History-mode routing for the SPA.**
   - What we know: `/` serves `index.html`; hashed assets resolve; deep links are not yet used.
   - Resolution: Phase 2 introduces either `HashRouter` or a small forward controller once the login route exists. Do not add a `/**` forward in Phase 1 — it would swallow `/actuator`.

3. **Where the shared `integrationTest` convention lives once a second module needs it.**
   - Resolution: Phase 1 keeps the single tagged test in `write`; Phase 2 promotes it to a `buildSrc`/convention plugin if `read` or `api` needs its own.

4. **Empty FSD layer directories.**
   - Resolution: do not create them. Record in the plan and the frontend README that `widgets`/`features`/`entities` appear with their first slice, so the verifier checks the lint gate rather than `ls`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|---|---|---|---|---|
| JDK 21 | Kotlin/Boot toolchain | ✓ | Temurin 21.0.13 (default `java` is 25.0.5) | Toolchain auto-detect; foojay plugin for other machines |
| Gradle | Wrapper generation | ✓ | 9.9.0 local | Wrapper 9.7.0 downloads on first run |
| Node.js | `:frontend`, steiger, MCP | ✓ | 24.20.0 | — |
| npm | frontend install/build | ✓ | 11.19.0 | — |
| Docker Engine | Compose stack | ✓ | 29.9.3 | — |
| Docker Compose | `up -d --wait` | ✓ | v2.32.1 | `up -d` + manual wait loop |
| curl | healthchecks, smoke script | ✓ | system | — |
| Maven Central / npm registry | dependency resolution | ✓ | HTTP 200 | — |
| `axoniq/axonserver:2026.1.7` | event store | ✓ (pulled and run) | 2026.1.7, Java 21.0.12.1 inside | — |
| `postgres:18.6` | read model | ✓ (pulled and run) | PostgreSQL 18.6 | `postgres:18` (floats) |
| Playwright browser | MCP headless check | ✓ (used this session) | Chromium via `@playwright/mcp@0.0.85` | `npx playwright install chromium` |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

## Validation Architecture

### Test Framework

| Property | Value |
|---|---|
| Framework | JUnit 5 (Jupiter) via `spring-boot-starter-test`; `kotlin("test")` for assertions |
| Config file | none — Gradle `Test` tasks with `useJUnitPlatform()`; tag split `integration` |
| Quick run command | `./gradlew spotlessCheck test --no-daemon` |
| Full suite command | `./gradlew build --no-daemon` |
| Infra-dependent command | `./gradlew integrationTest --no-daemon` (requires `docker compose up -d --wait`) |
| Full-stack proof | `./scripts/smoke-stack.sh` |

### Phase Requirements → Test Map

| Criterion | Behavior | Test Type | Automated Command | Exists? |
|---|---|---|---|---|
| 1 (PLAT-01) | Compose stack up; three services start; all three register | smoke | `./scripts/smoke-stack.sh` + `./gradlew integrationTest` | ❌ Wave 0 |
| 1 (PLAT-01) | `write` registers with Axon Server | integration | `./gradlew :write:integrationTest --tests '*AxonServerRegistrationIT'` | ❌ Wave 0 |
| 1 (PLAT-01) | Axon Server and PostgreSQL are reachable | integration | `./gradlew :write:integrationTest --tests '*InfrastructureReachabilityTest'` | ❌ Wave 0 |
| 2 (TOPO-01) | Three independent bootJars | build | `./gradlew build` then assert `write/build/libs/*.jar`, `read/…`, `api/…` | ❌ Wave 0 (assertion step) |
| 3 (TOPO-04) | One build produces SPA + jars | build | `./gradlew build`; assert `frontend/dist/index.html` and `unzip -l api/build/libs/*.jar \| grep BOOT-INF/classes/static/index.html` | ❌ Wave 0 (assertion step) |
| 4 (PLAT-05) | CI starts infra and runs integration tests in job `build` | CI | workflow step names visible in the run; `git grep -n "integrationTest" .github/workflows/ci.yml` | ❌ Wave 0 |
| 5 (QUAL-01) | Kotlin formatting gate | unit/build | `./gradlew spotlessCheck` (also inside `build`) | ✅ after Spotless lands |
| 5 (QUAL-01) | Frontend formatting | build | `./gradlew :frontend:check` → `npm run format:check` | ❌ Wave 0 |
| 5 (QUAL-01) | Repo formatting | CI | `./scripts/format-check.sh` | ✅ exists |
| 6 (FE-01/FE-02) | FSD passes on the scaffold | build | `./gradlew :frontend:lintFsd` → `npm run lint:fsd` | ❌ Wave 0 |
| 6 (FE-01/FE-02) | Gate fails on a violation | manual negative control | add `import { HomePage } from '@/pages/home'` to `shared/ui/AppShell.tsx`, run `npm run lint:fsd`, expect exit 1, revert | ❌ one-off |
| 7 (E2E-01) | MCP opens the SPA headless | manual/agent | `browser_navigate http://localhost:8080/` + `browser_snapshot`, expect heading "Course catalog" | ❌ manual by design |

### Sampling Rate

- **Per task commit:** `./gradlew spotlessCheck test --no-daemon` (no Docker, under a minute once dependencies are cached).
- **Per wave merge:** `./gradlew build --no-daemon`.
- **Phase gate:** `docker compose up -d --wait && ./gradlew build integrationTest && ./scripts/smoke-stack.sh` — all green before `/gsd-verify-work`.

### Wave 0 Gaps

- [ ] `frontend/package.json`, `package-lock.json`, `tsconfig*.json`, `vite.config.ts`, `steiger.config.js`, `.prettierignore`, `index.html`, `src/app`, `src/pages/home`, `src/shared/ui`
- [ ] `frontend/build.gradle.kts` with `base` + `npmInstall` / `assembleFrontend` / `lintFsd` / `formatCheck`
- [ ] `write/src/test/kotlin/.../AxonServerRegistrationIT.kt` and `InfrastructureReachabilityTest.kt`
- [ ] `integrationTest` task + `excludeTags("integration")` on `test`
- [ ] `compose.yaml` with the standalone flag, the PG18 volume path and both healthchecks
- [ ] `platform/`, `domain/`, `write/`, `read/`, `api/` build files and app classes
- [ ] `gradle/wrapper/gradle-wrapper.properties` pinned to 9.7.0
- [ ] `scripts/smoke-stack.sh`
- [ ] CI `build` job steps for Node, Compose and `integrationTest`

### CI now vs. later

| Check | In CI now | Why |
|---|---|---|
| `./gradlew build` (incl. `spotlessCheck`, `:frontend:check`) | Yes | QUAL-01, TOPO-01, TOPO-04, FE-02 |
| `./gradlew integrationTest` after compose up | Yes | PLAT-05 |
| `scripts/format-check.sh` | Yes (existing `format` job) | QUAL-01 |
| `scripts/smoke-stack.sh` | Optional — add once stable | It duplicates `integrationTest` for one criterion and adds minutes; keep it local until it proves non-flaky |
| FSD negative control | No | A deliberately broken build cannot be a required check; record the one-off output in the phase verification |
| Playwright MCP check | No | By definition agent-driven, not a CI gate; E2E-02 brings the real suite in Phase 8 |

## Security Domain

`security_enforcement: true`, `security_asvs_level: 1`.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---|---|---|
| V2 Authentication | No | No login in Phase 1 (Phase 2) |
| V3 Session Management | No | No sessions (Phase 2) |
| V4 Access Control | Partial | Actuator exposure limited to `health,info`; no management endpoints on a public interface; `write`/`read` are developer-facing only |
| V5 Input Validation | No | No user input; the SPA shell has no forms |
| V6 Cryptography | No | Nothing to encrypt; no secrets generated |
| V14 Configuration / Supply chain (L1 hygiene) | Yes | Pinned image tags, SHA-pinned actions, no credentials in the repo, `.env.example` only |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---|---|---|
| Secret committed in compose or `application.yml` | Information disclosure | Local-only default credentials, documented as non-production; real secrets via env/`{env:NAME}`; `.env*` gitignored |
| Unauthenticated actuator exposes internals (`env`, `heapdump`) | Information disclosure / Elevation | `management.endpoints.web.exposure.include: health,info` only; add security in Phase 2 |
| Moving container tag silently changes the runtime | Tampering | Pin `axoniq/axonserver:2026.1.7` and `postgres:18.6` |
| Mutable third-party action | Tampering | Full-commit SHAs with a version comment (existing convention) |
| Build runs arbitrary install scripts from npm | Tampering | `npm ci` against a committed lockfile; no `postinstall` in the audited packages |
| Broad host exposure of the dev stack | Information disclosure | Ports bound for local dev only; do not deploy this compose file |

## Sources

### Primary (HIGH confidence — reproduced in this session)

- Local Gradle 9.9.0 probes: `java-platform` with/without `allowDependencies()`, `platform(project(...))` vs `project(...)`, BOM propagation graph, `--parallel` timings, Spotless wiring, `bootJar` contents with `BOOT-INF/classes/static/index.html`, `base` plugin requirement.
- Local Spring Boot 4.1.2 + `io.axoniq.framework:axoniq-spring-boot-starter:5.4.1` boot on Temurin 21 against Axon Server 2026.1.7: startup logs, `connections()`, `/actuator/health` component list, port-collision exit message.
- Local Compose v2.32.1 run: `axoniq/axonserver:2026.1.7` + `postgres:18.6`, healthchecks, `--wait`, `AXONIQ-1302` failure and the `standalone` fix, PG18 volume failure, license log line.
- Local `create-vite@9.3.4` scaffold + steiger 0.10.0 runs: clean pass, upward-import failure, deep-import failure, `shared` gap, TS 7 `baseUrl` error, Vite 8.3.7 build output.
- Local Playwright MCP 0.0.85 headless navigation of the api-served SPA.

### Secondary (CITED — official documentation)

- `https://docs.spring.io/spring-boot/4.1/system-requirements.html` — Java 17+, Gradle 8.14+/9.x.
- `https://docs.spring.io/spring-boot/4.1/gradle-plugin/index.html` — plugin requirements, configuration-cache compatibility.
- `https://docs.axoniq.io/axon-framework-reference/5.4/spring-boot-integration/` — Boot 3/4 support from AF 5.0.3+, `axon.axonserver.*`, `axon.axonserver.enabled`, processor properties, default `localhost:8124`.
- `https://docs.axoniq.io/axon-server-reference/v2026.1/axon-server/installation/docker-k8s/` and `…/administration/configuration/` — image volumes, environment variables, `/actuator/info` as the liveness probe.
- `https://kotlinlang.org/docs/gradle-configure-project.html` — KGP 2.4.20–2.4.21 supports Gradle 7.6.3–9.7.0; foojay resolver `1.0.0`.
- `https://github.com/feature-sliced/steiger` — `defineConfig`, `fsd.configs.recommended`, rule table, `insignificant-slice`/`public-api`/`no-segmentless-slices` semantics.
- `https://github.com/microsoft/playwright-mcp` — `--headless`, `--isolated`, config shape.
- `https://opencode.ai/config.json` — `mcp.servers.<name>` with `{type:"local", command:[…]}` (the tracked `opencode.jsonc` matches).

### Tertiary (registry / API lookups)

- Maven Central metadata and POMs: `spring-boot-dependencies:4.1.2`, `axoniq-framework-bom:5.4.1`, `axoniq-spring-boot-starter:5.4.1`, `axon-server-connector:5.4.1`, `axoniq-spring-boot-autoconfigure:5.4.1`, `kotlin-gradle-plugin:2.4.21`, `spotless-plugin-gradle:8.11.0`, `ktlint-cli:1.8.0`.
- npm registry: `steiger@0.10.0`, `@feature-sliced/steiger-plugin@0.8.2`, `@playwright/mcp@0.0.85`, `create-vite@9.3.4`.
- Docker Hub API: `axoniq/axonserver:2026.1.7`, `postgres:18.6`.
- `services.gradle.org/versions/all`: Gradle 9.7.1, 9.9.0, 8.14.6.
- GitHub API: `actions/setup-node@v7.1.0` SHA, `actions/setup-java@v6.2.0` SHA, `actions/checkout@v7.0.1` SHA.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every artifact resolved and executed; the only judgement call is the wrapper patch version.
- Architecture: HIGH — the module graph, the bootJar contents and the runtime connection were reproduced.
- Pitfalls: HIGH — each is an error message or log line captured here, not a warning from memory.

**Research date:** 2026-11-11
**Valid until:** 2026-12-11 (fast-moving: Axon Server patch line, Boot 4.1.x, Gradle 9.x and TypeScript 7 all move monthly; re-check the image tags and the KGP support matrix at scaffold time).


