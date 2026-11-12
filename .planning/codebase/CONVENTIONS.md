# Conventions

## Language policy

- **Repository artifacts are English.** Every file written into this repo — source code, comments, KDoc, commit messages, README, design notes, and everything under `.planning/` — is written in English.
- **Responses follow the participant's language, defaulting to English.** No language preference is committed to this repo: participants may work in different languages, so GSD's default is English and the assistant mirrors the language a participant writes in.
- **Never translate identifiers.** Class, function, event, command, tag-key and endpoint names stay in English regardless of the conversation language.
- Subagent prompts stay in English; subagent summaries follow the invoking conversation's language.

## Kotlin

- Kotlin on JVM 21, Gradle Kotlin DSL (`build.gradle.kts`).
- Events and commands are immutable `data class`es; no mutable state in the event contract.
- Prefer `val`, explicit non-null types at boundaries, and no `!!` on Axon/Spring return values.
- Keep Axon decision-state classes small and side-effect free; they are decision inputs, not aggregates.
- Annotate external/API boundaries, not obvious internals.

## Event sourcing

- Events are the only shared *state* contract; commands and queries are shared message types in `courses-domain`, and no service depends on another service's implementation.
- Tag keys are constants in one shared tag-keys object used by both `@EventTag` and `@EventCriteriaBuilder`.
- Never read a read model inside a command handler — the decision sources events.
- Live updates are whole read-model snapshots over SSE; only the service advertising the watch query emits, and the gateway never forwards domain events.
- The read service emits from `@TransactionalEventListener(AFTER_COMMIT)`, never directly inside an `@EventHandler`; a rolled-back write must not reach subscribers.
- `courses-read` owns both halves in one process: JPA + Flyway for the write path, Spring Data JDBC into immutable rows for the read path. Do not introduce a second read process, and keep the two persistence styles in separate packages.

The rules that keep the topology honest:

- **`courses-write` has no datasource and no JPA.** One classpath mistake installs an aggregate-based event store and silently voids DCB. A guard test (TOPO-03, Phase 2) asserts no `EntityManagerFactory` and a present connector.
- **`courses-read` never appends.** It consumes events and answers queries; only `courses-write` calls `EventAppender`.
- **Flyway migrations and the event processor live in `courses-read` only.** A guard test (TOPO-03, Phase 2) fails when another module gains either.
- **The gateway never touches SQL or the event store.** It only sends messages.
- **`courses-domain` has no Spring dependency.** It is the compile-time contract; a `@Component` there would let services depend on each other's implementations.
- **Never put a routing key on `EnrollStudent`.** Command sequencing defaults to `RoutingKeySequencingPolicy`; a shared routing key serializes the last-seat race away and the demo proves nothing.

## Testing

- Test classes carry their suite suffix: `*Tests` in `src/test/kotlin` and `*IT` in `src/integrationTest/kotlin`. `*CT` (`src/componentTest/kotlin`), `*E2E` (`src/e2eTest/kotlin`) and `*NC` (`src/negativeControl/kotlin`) are reserved for suites that do not exist yet; create the suite when the first class lands. Each suite runs only matching classes, and `checkTestNaming` (part of `check`) fails on a mismatch. Browser E2E is Playwright under `courses-frontend/e2e/`, outside Gradle's suites.
- Shared test infrastructure lives in the `courses-test-support` module and is consumed with `implementation(project(":courses-test-support"))` in the integration suite: the Axon Server registration base, the Testcontainers image lookup and the Docker image properties. The module is on test classpaths only; never put it on a production classpath.
- Unit tests live in `src/test/kotlin`; tests that need Axon Server or PostgreSQL live in `src/integrationTest/kotlin`. The Gradle `jvm-test-suite` plugin defines both suites, so the directory decides which task runs a test — never a tag or a hand-registered `Test` task.
- `./gradlew build` must stay Docker-free: it runs the unit tests and the lint/format gates, but never `integrationTest`. Integration tests run through `./gradlew integrationTest` and own their infrastructure through Testcontainers — one container per test class, random host ports, removed after the run. Never require a pre-started Compose stack or a fixed port, and never let a test depend on state left by an earlier run.
- Slice tests use `AxonTestFixture.with(application.configurer())` against the production configurer; contention tests never do — the fixture dispatches one action per `when` phase and cannot overlap.
- One fixture per test, unique ids per test, no `Thread.sleep`.
- Contention tests are barrier-driven against the live Axon Server and assert on the rejection mechanism. The negative control will live in its own source set (`src/negativeControl/kotlin`, suffix `*NC`) with a `negativeControlTest` task, added to the suite list when the first class lands (Phase 8); it must never run inside `integrationTest`, because a control that deliberately produces two winners would turn CI red on every pull request.
- Test names read as sentences describing the invariant.

## Changes go through GSD

- Every change to this repository starts from a GSD command: `/gsd-fast` for a trivial one, `/gsd-quick` for a small tracked task, `/gsd-debug` for a defect, `/gsd-discuss-phase`/`/gsd-plan-phase`/`/gsd-execute-phase` for phase work, `/gsd-docs-update`, `/gsd-code-review`, `/gsd-audit-*`, `/gsd-validate-phase`, `/gsd-secure-phase`, `/gsd-capture`, `/gsd-extract-learnings`, `/gsd-ship` and `/gsd-undo` for the rest.
- Outside a GSD command the agent does not edit, create or delete files, commit, push, open a pull request or merge one. When a change is requested, it names the command that owns it and waits, instead of doing the work directly because that is faster.
- Reading, searching, running tests and answering questions need no command: investigation is not a change.
- Operations outside the repository — GitHub settings, the user's global configuration, the OpenCode session — are named as such and are taken only on an explicit request.
- `/gsd-ship` ships a phase: it requires that phase's verification and its ROADMAP record. A non-phase change made by `/gsd-fast` or `/gsd-quick` therefore lands through the branch-and-PR rules in § Branching — push the branch, open the PR, wait for the three checks, squash-merge — because the branch protection, not a GSD command, is what makes that path honest.

## Git

- Commit messages: conventional prefixes (`feat`, `fix`, `docs`, `chore`, `test`, `refactor`, `perf`, `ci`, `build`, `style`, `revert`), optional scope (`feat(command): ...`), imperative, English, ≤72 characters on the subject line.
- The PR title becomes the squash commit in `main`, so it must follow the same format; CI enforces it (`pr-title` workflow).
- Pull requests use `.github/pull_request_template.md`: summary, change type, decisions and trade-offs, deliberate exclusions, the local checks and a test plan.
- One logical change per commit; planning artifacts commit alongside the work they describe.
- The agent commits, pushes, opens a pull request or merges one only when the current message asks for that action. An earlier agreement covers the action it approved, not the next one, so ask again before each.

## Review

Review runs on GSD's rails, not on a bespoke loop:

| Artifact | Command |
|----------|---------|
| Phase plan (`PLAN.md`) | `/gsd-review <phase>` — cross-AI peer review through `codex` and `claude`, written to `REVIEWS.md`, fed back with `--reviews` |
| Plan convergence | `/gsd-plan-review-convergence <phase>` — plan → review → replan until no HIGH concerns remain, three cycles by default; enabled through `workflow.plan_review_convergence` |
| Phase code | `/gsd-code-review <phase>` — findings land in `REVIEW.md`, `--fix` applies them |
| Documentation claims | `/gsd-docs-update` — its `gsd-doc-verifier` checks claims against the codebase |
| Milestone | `/gsd-audit-milestone` — audits completion against the original intent |

There is no review gate on ordinary edits: run the command that matches the artifact, and do not
dispatch hand-rolled reviewers or keep a parallel round ledger. `.planning/PLANNING-REVIEW.md` holds
the findings of two manual cycles run before this rule existed; it is history, not a procedure.

A reviewer bar still applies: report only defects that would change implementation or mislead a
reader; style preferences are not findings.

## Branching

- One branch per change: `gsd/phase-{phase}-{slug}` for a phase (config template), a conventional name such as `chore/dependabot-title-and-node-types` for tooling work. Every branch reaches `main` through a pull request.
- `main` is protected by five GitHub rulesets, not by a file in the repository: `main-require-pr-on-merge` (PR required, squash only, no approval needed, conversations resolved), `main-required-checks` (`build`, `format` and `title`, strict — the branch must be up to date with `main`), `main-require-linear-history`, `main-restrict-deletions` and `main-block-force-pushes`.
- The owner may bypass `main-block-force-pushes` only. The PR and checks rules carry no bypass, so a direct push to `main` is rejected for the owner too; the force-push escape hatch exists to repair history and still has to pass the checks anew.
- PR titles follow the same conventional-commit format as commits — the squash commit in `main` takes the PR title and body.
- Only squash merges are allowed; the branch is deleted after merge.

## Formatting

- Kotlin: Spotless + ktlint. Normalize with `./gradlew spotlessApply`; CI verifies via `spotlessCheck`.
- Frontend: Prettier. Normalize with `npm run format` in `courses-frontend/`; CI verifies via `npm run format:check`.
- `.editorconfig` in the root is the shared baseline; do not override it per editor.
- Line length is **100** for Kotlin (via `.editorconfig` `max_line_length`) and for every Prettier target (`printWidth`), including Markdown prose in `docs/` and `README.md`; `.planning/` is excluded from Prettier.
- Repo-level files (docs, README, workflows, root JSON): `scripts/format.sh` to write, `scripts/format-check.sh` to verify.
- `.planning/`, `AGENTS.md` and `*.jsonc` are never Prettier-formatted: they are machine-read or generated.
- CI never rewrites files — a red formatting check is fixed locally and pushed.

## Frontend (Feature-Sliced Design)

- Layers: `app → pages → widgets → features → entities → shared`; imports only point downward.
- No cross-imports inside a layer — compose in the layer above.
- Every slice exposes `index.ts`; deep imports are forbidden.
- Segments: `ui`, `model`, `api`, `lib`, `config` — only what a slice uses.
- `shared` contains no domain knowledge; `CourseCard` belongs to `entities/course`, not `shared/ui`.
- Pages compose; data fetching lives in entity/feature `api` segments.
- SSE transport lives in `shared/api/sse`; entity `api` segments type it; features subscribe via public APIs.
- `npm run lint:fsd` (steiger) runs as part of `build` once the frontend module exists.

## Retries and idempotency

- No automatic command retries in the gateway; 409 and 422 are terminal for a request.
- `CreateCourse` carries a client-supplied `courseId` and is safe to retry; a repeat returns the existing course.
- Enroll, waitlist join and drop are idempotent by effect: a repeat appends nothing and returns a named business rejection.
- Never retry a command you cannot key.

## End-to-end tests

- Playwright lives in `courses-frontend/e2e/`, outside `src`, so FSD layer rules do not apply to it.
- Tests run against the real Compose stack; never mock the backend — the distributed path is the subject.
- Select by `data-testid`; never by text or DOM structure.
- No `waitForTimeout`; wait for the observable value (Playwright retries assertions).
- Each test creates its own course with a UUIDv7 id; demo seeding is disabled in the E2E profile.
- The Playwright MCP server is for agent-driven exploration; it does not replace the suite.

## Identifiers

- UUIDv7 everywhere (`uuid-creator` server-side, `uuid` npm `v7()` in the SPA). UUIDv4 is rejected: no chronological order, worse index locality.
- Wrap ids in `@JvmInline value class` (`CourseId`, `StudentId`); never pass raw `UUID` through domain code.
- The canonical UUID string is the only string form — it is what `@EventTag` stores and what URLs carry.
- Unit and slice tests use fixed id constants. Integration, contention and E2E tests generate UUIDv7 ids, because each run needs its own course without colliding with a previous one.

## Tags

- Tag keys are domain-neutral and type-implied: `courseId`, `studentId`. Do not repeat the type in the value.
- `either(courseId, studentId)` is the sanctioned widening for a rule that genuinely spans two subjects (v2: per-student seat limit).

## Build

- Shared build configuration lives in the `build-logic` included build as three convention plugins: `kotlin-conventions` (Kotlin JVM, JDK 21 toolchain, Spotless + ktlint, the `:courses-platform` BOM), `spring-boot-conventions` (Kotlin Spring, Spring Boot, `jvm-test-suite`, service dependencies, the common `withType<JvmTestSuite>` block) and `frontend-conventions` (the `stageImageFiles` step and the `dockerBuildImage` `PackBuildImageTask`). `courses-domain` applies the first, the three services the second, `courses-frontend` the third; `courses-test-support` is a plain Kotlin library.
- A module declares its own project dependencies, `integrationTest` suite and suite-specific test libraries. A convention carries only what every consumer needs.
- `build-logic` imports the root `gradle/libs.versions.toml` and uses its generated `LibrariesForLibs` accessors. Never add a second catalog or inline versions in build logic.
- `build-logic` is formatted by Spotless; the root `check` depends on its `spotlessCheck`, so CI covers the build sources as well as the modules.
- `bootBuildInfo` excludes the timestamp: `META-INF/build-info.properties` carries version, group, artifact and name only, so rebuilding an image does not change that layer. The SPA mirrors it with `/build-info.json`, generated into `build/dist` without a timestamp.
- `gradle.properties` enables the build cache, parallel execution and the configuration cache. Tasks must capture configuration-time values (paths, versions, names) instead of touching `project` in a `doLast` action.
- The project `version` lives in `gradle.properties` and is overridden with `-Pversion=...`; `group` stays in the root `build.gradle.kts`.
- `actionlint` lints `.github/workflows/*.yml` from `check`. It is a required local tool (`brew install actionlint`) and the only one whose absence fails with a named, explicit error; CI installs a pinned, checksum-verified release before the build. A missing Node or npm fails the same build through the failing `npm ci`, not through a message that names the tool.
- Dependabot watches three ecosystems weekly — `github-actions`, `gradle` (the catalog, which `build-logic` shares) and `npm` in `courses-frontend` — grouping updates into one PR per ecosystem with a `build:` title (no scope, so long single updates still fit the 72-character `title` check). Gradle and npm group minor and patch only (`fsd-linters` moves `steiger` with its plugin whatever the size); actions group every size. Kotlin, Spring Boot, Axon, React, Vite, TypeScript and `@types/node` majors are ignored: framework majors are migrations, and the Node types must match the Node runtime rather than a new major.
- The Gradle wrapper, `axonserver`, `postgres` and the Paketo pins are invisible to Dependabot and are bumped by hand, each with a guard: `checkComposeImages` compares the catalog with `compose.yaml`, and a wrong buildpack pin fails the image build. `.github/workflows/buildpack-updates.yml` reports the Paketo pins weekly as one issue; the bump stays manual because only an image build proves a new buildpack, and a bad release is held back in the catalog with `# hold-back: <version>`, which the workflow skips. See `docs/adr/0009-dependency-update-tracking.md`.
- OS patches come from the Paketo run image, not from a buildpack version, so images are rebuilt before a demo and at least monthly (`./scripts/smoke-stack.sh`). CI builds them only for the Playwright e2e job and never pushes them to a registry, so nothing scans them yet — backlog item 999.2.
- Docker image versions used by tests live in the catalog (`[versions] axonserver`, `postgres`) and reach the suites as system properties.
- Vulnerability scanning is GitHub-native and lives on the repository, not in a file: Dependabot alerts and security updates are on, secret scanning and push protection are on, and there is no Snyk, `npm audit` gate or CodeQL. The `ignore` list in `dependabot.yml` suppresses version updates only — a security update still opens a PR, even for an ignored major. See `docs/adr/0010-github-native-vulnerability-scanning.md`.

## Tooling

- **GSD is project-scoped.** The installer runs with `--opencode --local`; a global install writes the full runtime into `~/.config/opencode/`, shadows this project's `.opencode/gsd-core` for every session on the machine, appends a generated profile block to the user's `AGENTS.md`, and adds `mcp.gsd` plus two permission entries to the user's `opencode.jsonc`. Never pass a global flag. If one is found, remove it by the file manifest rather than by glob — `docs/tooling.md` carries the procedure.
- After cloning, run `/setup-tooling` in OpenCode (or `./scripts/setup-tooling.sh` without an agent) — it installs the pinned GSD Core (`1.20.1`, OpenCode, project scope) or verifies an existing install, then reports the build prerequisites. `./scripts/setup-tooling.sh --check` diagnoses without installing and exits non-zero on a missing tool. `/setup-tooling --mcp` additionally runs the `connect-mcp-servers` skill.
- Slash commands run only when the user types their name; an agent cannot invoke one. A phrase triggers a skill (matched by description), then the script or the command's file — never the command itself. Never claim a command was invoked when the work came from the script.
- When a request matches a slash command in `.opencode/commands/`, do not carry out the command's procedure: name the command with its flags and let the user run it. This applies to the commands that own a contract — `setup-tooling` (`--check`, `--force`, `--mcp`) and the `gsd-*` workflows, which orchestrate their own subagents. Proceed only when the user asks the agent to do it instead.
- A skill is the opposite case: it is meant to fire on intent, so a matching phrase loads it without redirection.
- The build prerequisites are JDK, Node with npm, `actionlint`, Docker, Compose v2 and `pack`; `actionlint` is the only one whose absence fails `./gradlew build`, because `lintWorkflows` is part of `check`. The table and install hints live in `docs/tooling.md`.
- `.opencode/` is generated and gitignored. Only hand-written project commands under `.opencode/commands/` are tracked, each with an explicit unignore line in `.gitignore`; never hand-edit the generated `opencode.json`.
- Hand-written plugins live under `.opencode/plugins/` and are gitignored except for an explicit unignore block per plugin — the directory, its contents, and a re-ignore for `node_modules/`; `git commit` is gated by the `commit-guard` plugin, whose escape hatch is `COMMIT_GUARD=off`.
- Project MCP servers belong in the tracked root `opencode.jsonc` under `mcp.servers`; secrets use `{env:NAME}`. Auth- or IDE-bound servers (GitHub via `gh mcp`, IntelliJ) belong in the user's global config instead — the `connect-mcp-servers` skill detects, installs and wires them, handing back only the steps a human must do.
- `docs/tooling.md` is the human-facing version of these rules.

## Upstream reports

- **The register, the gate and the file format live in `docs/upstream-reports/`.** The rules below say what may be published; that directory says where it is recorded and how it is reviewed.
- **A deficiency in a dependency is reported upstream, not only worked around.** When a dependency's behaviour contradicts its documentation, a published release is unusable, or the tooling lacks a capability this project needs, the finding is reported to the dependency's own repository — resolved from its package metadata, never guessed. The threshold is "it changed how this project worked, or would change it for anyone walking the same path"; a typo in a log line is not a report, a wrong claim about a health indicator is.
- **The report is evidence, not a complaint.** Exact version, the file and line in the dependency's source, observed versus expected behaviour, and the smallest reproduction available. A maintainer cannot fix what they cannot reproduce.
- **Check the dependency's own documentation before drafting.** The installed tree shows what the code does; the published docs show what the project says it does, and that is where a "this is undocumented" claim usually dies. Read `docs/` (or the generated reference) at the exact version being reported — for a GitHub project, `gh api repos/<owner>/<repo>/contents/docs/<file>?ref=v<version>` — and put the relevant line in the claims table. 0001 was held after eight passes because its premise, that a configuration option was undocumented, was refuted by `docs/CONFIGURATION.md` in the same release. A defect in the code is still worth reporting; an omission in one reference line is not.
- **Record before reporting.** The finding lands in `docs/upstream-reports/` — one file per report plus a row in its register — inside the same task that found it, so the knowledge survives even if the report is never filed.
- **Three passes, on the exact text that will be sent.** The draft is committed before it is filed, so it can be diffed, reviewed, and later compared with what was actually said. Evidence: every behavioural claim maps to a `file:line` at the reported version and is re-read at review time, never recalled. Independent: the draft and the cited sources go to the codex lane configured under `review`, which returns supported / unsupported / speculative per claim, plus tone and duplicate risk. Owner: the owner reads the committed file in full and approves that text, not a summary of it.
- **Publication blockers.** An unverified claim, a missing or untested version, a local path, hostname, username, token, internal URL or unpublished design in the text, speculation about a maintainer's intent, an unchecked duplicate, or an approval given to a paraphrase rather than to the file.
- **The voice is the owner's; the facts are the project's.** No first-hand experience the project cannot support and no invented production usage. AI assistance is disclosed in one line, unless the owner records a different decision in the report's header.
- **Filing is an operation outside this repository.** The draft is prepared inside the task; the report is opened only on an explicit request, like a push or a merge.
- **Re-check the register when a dependency is bumped.** A row closes when the fixed version is adopted; until then a local workaround carries a pointer to it.
