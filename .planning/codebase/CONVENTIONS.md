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

- Events are the only shared *state* contract; commands and queries are shared message types in `domain`, and no service depends on another service's implementation.
- Tag keys are constants in one shared tag-keys object used by both `@EventTag` and `@EventCriteriaBuilder`.
- Never read a read model inside a command handler — the decision sources events.
- Live updates are whole read-model snapshots over SSE; only the service advertising the watch query emits, and the gateway never forwards domain events.
- The read service emits from `@TransactionalEventListener(AFTER_COMMIT)`, never directly inside an `@EventHandler`; a rolled-back write must not reach subscribers.
- `read` owns both halves in one process: JPA + Flyway for the write path, Spring Data JDBC into immutable rows for the read path. Do not introduce a second read process, and keep the two persistence styles in separate packages.

## Testing

- Unit tests live in `src/test/kotlin`; tests that need Axon Server or PostgreSQL live in `src/integrationTest/kotlin`. The Gradle `jvm-test-suite` plugin defines both suites, so the directory decides which task runs a test — never a tag or a hand-registered `Test` task.
- `./gradlew build` must stay Docker-free: it runs `test` only. Integration tests run through `./gradlew integrationTest` after `docker compose up -d --wait`.
- Slice tests use `AxonTestFixture.with(application.configurer())` against the production configurer; contention tests never do — the fixture dispatches one action per `when` phase and cannot overlap.
- One fixture per test, unique ids per test, no `Thread.sleep`.
- Contention tests are barrier-driven against the live Axon Server, assert on the rejection mechanism, and carry a negative control that lives in `src/integrationTest` and is excluded from the default run by that placement.
- Test names read as sentences describing the invariant.

## Git

- Commit messages: conventional prefixes (`feat`, `fix`, `docs`, `chore`, `test`, `refactor`, `perf`, `ci`, `build`, `style`, `revert`), optional scope (`feat(command): ...`), imperative, English, ≤72 characters on the subject line.
- The PR title becomes the squash commit in `main`, so it must follow the same format; CI enforces it (`pr-title` workflow).
- One logical change per commit; planning artifacts commit alongside the work they describe.

## Review loop (planning artifacts)

### When it runs

- Any substantial change to `PROJECT.md`, `REQUIREMENTS.md`, `ROADMAP.md`, `research/*`, or this file.
- The loop completes before a human is asked to read the artifact.

### Rounds

- **Round 1:** three reviewers in parallel, read-only — consistency, requirements/roadmap quality, technical fact-check.
- **Round 2+:** one consistency reviewer plus only the specialist whose domain still has open findings.
- Later rounds review the diff since the previous round and its cross-file consequences; only round 1 re-reads the whole corpus.
- Findings are classified `blocker`, `major`, `minor`.

### Exit criteria

All three must hold:

- zero open `blocker` and `major`;
- the round's finding count is not greater than the previous round's;
- no finding closed in an earlier round is reopened.

### Convergence control

- **Hard cap: 3 rounds.** A round 3 with open blockers or majors freezes the documents and escalates to the human with the open list; a fourth round needs an explicit human decision.
- **Oscillation stop:** a reopened finding, or two findings prescribing opposite fixes, ends the loop and becomes a decision recorded in `PROJECT.md` Key Decisions.
- **Diminishing-returns stop:** a round producing only minor findings ends the loop; minors are recorded, not fixed by default.
- **Reviewer bar:** reviewers report only defects that would change implementation or mislead a reader; style preferences are not findings.

### Recording

- Every round appends to `.planning/PLANNING-REVIEW.md`: round number, reviewers, counts by severity, status per finding.
- Acceptance marks the artifact reviewed; a later change touching more than one file or any constraint starts a new round 1.

## Branching

- One branch per phase: `gsd/phase-{phase}-{slug}` (config template), merged into `main` through a pull request.
- `main` is protected: no direct pushes, no force-push, no deletion, `build`, `format` and `title` checks required, conversation resolution required.
- PR titles follow the same conventional-commit format as commits — the squash commit in `main` takes the PR title and body.
- Only squash merges are allowed; the branch is deleted after merge.

## Formatting

- Kotlin: Spotless + ktlint. Normalize with `./gradlew spotlessApply`; CI verifies via `spotlessCheck`.
- Frontend: Prettier. Normalize with `npm run format` in `frontend/`; CI verifies via `npm run format:check`.
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

- Playwright lives in `frontend/e2e/`, outside `src`, so FSD layer rules do not apply to it.
- Tests run against the real Compose stack; never mock the backend — the distributed path is the subject.
- Select by `data-testid`; never by text or DOM structure.
- No `waitForTimeout`; wait for the observable value (Playwright retries assertions).
- Each test creates its own course with a UUIDv7 id; demo seeding is disabled in the E2E profile.
- The Playwright MCP server is for agent-driven exploration; it does not replace the suite.

## Identifiers

- UUIDv7 everywhere (`uuid-creator` server-side, `uuid` npm `v7()` in the SPA). UUIDv4 is rejected: no chronological order, worse index locality.
- Wrap ids in `@JvmInline value class` (`CourseId`, `StudentId`); never pass raw `UUID` through domain code.
- The canonical UUID string is the only string form — it is what `@EventTag` stores and what URLs carry.
- Tests use fixed id constants, never generated ones.

## Tags

- Tag keys are domain-neutral and type-implied: `courseId`, `studentId`. Do not repeat the type in the value.
- `either(courseId, studentId)` is the sanctioned widening for a rule that genuinely spans two subjects (v2: per-student seat limit).

## Tooling

- After cloning, run `/setup-tooling` in OpenCode (or `./scripts/setup-tooling.sh` without an agent) — it installs the pinned GSD Core (`1.20.1`, OpenCode, project scope) or verifies an existing install.
- `.opencode/` is generated and gitignored. Only hand-written project commands under `.opencode/commands/` are tracked, each with an explicit unignore line in `.gitignore`; never hand-edit the generated `opencode.json`.
- Project MCP servers belong in the tracked root `opencode.jsonc` under `mcp.servers`; secrets use `{env:NAME}`.
- `docs/tooling.md` is the human-facing version of these rules.
