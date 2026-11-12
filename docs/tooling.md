# Local tooling

The repository builds and tests without any _agent_ tooling. It does need a small set of external
tools, listed below; GSD, its commands and the MCP servers are optional and only add the `/gsd-*`
workflows.

## Prerequisites

Check the whole set with one command — it prints the version it found for each row, installs
nothing, and exits non-zero when something is missing:

```bash
./scripts/setup-tooling.sh --check
```

| Tool              | Version                                                                                       | Needed by                                                                           |
| ----------------- | --------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| JDK               | any JDK that runs Gradle 9.9.1; the build's JDK 21 is auto-provisioned by the foojay resolver | every Gradle task                                                                   |
| Node.js           | 24.x (CI uses 24.20.0)                                                                        | `:courses-frontend` — `npm ci` and `npm run build`                                  |
| npm               | the one bundled with Node                                                                     | `:courses-frontend`                                                                 |
| actionlint        | 1.7.12 in CI; any recent release locally                                                      | `./gradlew build` — `lintWorkflows` is wired into `check`                           |
| Docker Engine     | any recent                                                                                    | `./gradlew integrationTest`, `bootBuildImage`, `:courses-frontend:dockerBuildImage` |
| Docker Compose v2 | the `docker compose` plugin                                                                   | `docker compose up`, `scripts/smoke-stack.sh`                                       |
| pack              | any recent                                                                                    | `:courses-frontend:dockerBuildImage`                                                |

The rows are cumulative: `./gradlew build` needs the first four, integration tests add Docker,
images add `pack`, and the full stack adds Compose. `./gradlew build` stays Docker-free on purpose,
which is why Docker is not a build prerequisite.

```bash
brew install actionlint
brew install buildpacks/tap/pack
brew install --cask docker    # or: brew install colima docker docker-compose
```

`actionlint` is the only tool whose absence fails `./gradlew build` with a named, explicit error:
`lintWorkflows` refuses to run instead of silently skipping the gate. A missing Node or npm fails
the build too, but through the failing `npm ci`, not through a message that names the tool. A
missing `pack` fails only the frontend image task, and a missing Docker daemon fails only the tasks
that start containers.

## GSD Core (agent workflows)

Uses the Node.js from the table above — version 24 is the installer's own engine floor.

`.opencode/` is generated, not committed wholesale: `gsd-core`, the agents and the generated
`opencode.json` are ignored because they are an 18 MB install with absolute machine paths that the
installer rewrites. Only hand-written files under `.opencode/commands/` are tracked — currently
`/setup-tooling`.

Install or verify it from OpenCode with the project command:

```text
/setup-tooling          # install if missing, verify if present
/setup-tooling --force  # reinstall the pinned version
```

The command runs the same script and reports its output. Without an agent session, run the script
directly:

```bash
./scripts/setup-tooling.sh
./scripts/setup-tooling.sh --force
```

The script is idempotent: it compares `.opencode/gsd-core/VERSION` with the pinned version, runs the
non-interactive installer (`--opencode --local`) only when needed, then checks the planning config
is readable.

Commands and skills are invoked differently, and the difference matters when asking an agent for
something:

- a **slash command** runs only when the user types its name — `/setup-tooling`. An agent cannot
  invoke one, so a phrase like "set up the tooling" does not trigger the command;
- a **skill** is matched by its description, so a phrase does trigger one. "Connect the MCP servers"
  loads `connect-mcp-servers` without naming it.

A phrase therefore reaches the script directly, or leads the agent to read the command file and
follow it — the same work, without the command's fixed argument contract.

The project commands, and when to point at them instead of acting:

| Command                  | Offer it when                                                                        |
| ------------------------ | ------------------------------------------------------------------------------------ |
| `/setup-tooling`         | the tooling is missing, or the question is whether the machine can build the project |
| `/setup-tooling --check` | a diagnosis is wanted and nothing should be installed                                |
| `/setup-tooling --force` | the pinned GSD Core has to be reinstalled                                            |
| `/setup-tooling --mcp`   | connecting the GitHub or IntelliJ MCP servers                                        |
| `/gsd-*`                 | any phase work — those commands orchestrate their own subagents                      |

An agent should name the command with its flags rather than reproduce its steps, and act itself only
when the user asks it to. The `gsd-*` commands in particular expect to be the entry point of a
phase: running their workflow by hand skips the state they maintain.

Manual equivalent, if you prefer to run the installer yourself:

```bash
npx --yes @opengsd/gsd-core@1.20.1 --opencode --local
```

The version is pinned deliberately: `@latest` can change command and workflow shapes between
releases.

Check an existing install:

```bash
cat .opencode/gsd-core/VERSION    # expected: 1.20.1
node .opencode/gsd-core/bin/gsd-tools.cjs query init.new-project >/dev/null && echo ok
```

What is committed instead, so the project still carries its rules and context without GSD:

- `AGENTS.md` — project rules for any agent
- `.agents/skills/` — gh-cli, github-actions-workflows, gradle-best-practices,
  gradle-wrapper-upgrade, pull-request-review, connect-mcp-servers
- `.opencode/commands/setup-tooling.md` — the tracked project command
- `.planning/` — project context, requirements, roadmap, research, review record
- `.github/workflows/` — CI

## Model selection

GSD decides which model each subagent runs on; OpenCode just executes the resolved id. The layers,
highest precedence first:

1. `model_overrides.<agent>` — one agent, a full model id.
2. `models.<phase-type>` — `planning`, `research`, `execution`, `verification`, `discuss`.
3. `model_profile` — a per-agent tier table (`quality`, `balanced`, `budget`, `adaptive`,
   `inherit`).
4. The runtime default.

Steps 2–4 pick a **tier** (`opus`, `sonnet`, `haiku`); `model_profile_overrides.opencode` maps that
tier to a concrete `opencode-go` model. This project runs `balanced`, so planning gets the strongest
tier and everything else the working tier:

| Tier     | Model                             | Used for                                                                      |
| -------- | --------------------------------- | ----------------------------------------------------------------------------- |
| `opus`   | `opencode-go/deepseek-v4-pro`     | `gsd-planner`                                                                 |
| `sonnet` | `opencode-go/deepseek-v4.1-flash` | executor, researchers, document agents, `gsd-verifier` and the other checkers |
| `haiku`  | `opencode-go/claude-haiku-5-5`    | `gsd-codebase-mapper` and other fan-out work                                  |

`resolve_model_ids` is `true`, so a tier becomes the full id above; the global
`~/.gsd/defaults.json` also sets it, but the project config wins and GSD says so on every
`resolve-model` call.

### Cost

Two kinds of spending meet here, and they are not interchangeable:

- **subscription-backed** — the `codex` CLI and the `openai/*` provider both run under the ChatGPT
  subscription (the provider authenticates through OAuth, not an API key), and `opencode-go` covers
  the DeepSeek and GLM models. Using them adds no per-token charge.
- **metered** — `opencode/*` bills per token. Claude and Gemini live only there, with no
  subscription behind them, which is why they are not in the defaults.

Defaults therefore stay on the subscription side: `codex` for review, `opencode-go/glm-5.3` for the
second reviewer, `openai/gpt-6.1-sol` for the two gates that must not share a family with what they
check (`gsd-code-reviewer` and `gsd-plan-checker`), DeepSeek for planning, execution and
verification.

GPT therefore appears twice on an artifact — once as a gate and once as the `codex` review lane —
while GLM carries only the second lane. The gate is the place where strength matters more than the
symmetry of the family count, so the assignment is deliberate. If the ChatGPT quota becomes the
binding constraint, both overrides move back to `opencode-go/glm-5.3` at no cost.

A lane is a CLI, not a model, so two reviewers need two binaries: `codex` and `opencode`. If the
Codex CLI is unavailable or hits its subscription limit, the fallback is the same GPT model through
the other binary — `review.models.opencode: openai/gpt-6.1-sol` — not a metered provider. GLM then
drops out for that invocation, since one lane carries one model.

### Review lanes

Cross-AI review runs through external CLIs, not through the subagents above. Two lanes, from two
different model families:

| Lane       | Binary     | Model                 | Effort | Family                                     |
| ---------- | ---------- | --------------------- | ------ | ------------------------------------------ |
| `codex`    | `codex`    | `gpt-6.1-sol`         | `high` | OpenAI                                     |
| `opencode` | `opencode` | `opencode-go/glm-5.3` | `high` | Zhipu (through the `opencode-go` provider) |

Both values are pinned in `.planning/config.json` (`review.models.<lane>`, `review.effort.<lane>`),
so the review does not drift with whatever the CLIs' own configurations hold. The catch: a renamed
or retired model id is not tracked by anything — the lane fails loudly when the CLI rejects it.

The families matter more than the model names. Planning runs on `deepseek-v4-pro` and execution on
`deepseek-v4.1-flash`, so a reviewer on either DeepSeek model would be reviewing its own work; GLM
and GPT are both outside that lineage, which is what makes the pair adversarial. On OpenCode 2.x the
`opencode` lane rejects `--variant`, so the effort rides on the model reference (`…/glm-5.3#high`)
and the prompt is fed with `--standalone` — GSD applies that translation itself.

Adding a third lane for one invocation is a flag (`/gsd-review <phase> --claude`, `--all`); an
unusable lane named explicitly fails the review instead of being skipped.

## MCP servers

There are two levels, and the choice is not stylistic — it follows from what the server needs:

| Level   | File                                                    | Belongs there when                                                  |
| ------- | ------------------------------------------------------- | ------------------------------------------------------------------- |
| Project | tracked `opencode.jsonc` under `mcp.servers`            | the server needs no credentials, no IDE and no local state          |
| User    | `~/.config/opencode/opencode.jsonc` under `mcp.servers` | the server uses your credentials, your running IDE, or your machine |

A server that reads your GitHub token or talks to your IDE cannot work for anyone else, so
committing it would document a configuration that fails on the next clone. The project config
therefore carries only credential-free servers.

Project entries use the V2 shape:

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "servers": {
      "context7": { "type": "remote", "url": "https://mcp.context7.com/mcp" },
    },
  },
}
```

User entries go in `~/.config/opencode/opencode.jsonc`, which is local and machine-specific. Only
the 2.x tools on this machine read it, so nothing constrains its shape, and it uses the same v2
shape as the project file:

```jsonc
// ~/.config/opencode/opencode.jsonc
"mcp": {
  "servers": {
    "github": {
      "type": "local",
      "command": ["gh", "mcp"],
      "environment": { "GH_TOKEN": "{env:GH_TOKEN}" }
    },
    "intellij": { "type": "remote", "url": "http://127.0.0.1:64342/stream" }
  }
}
```

The version constraint lands on the **project** file instead, and only if a 1.x consumer ever reads
the repository: a 1.x agent reads only the flat `mcp` map, so a checked-in `mcp.servers` would show
it no servers at all, with no error anywhere. Nothing in this project runs on 1.x, so the project
config keeps the v2 shape; the `connect-mcp-servers` skill carries the conditional rule for an
environment where that changes.

Wiring is the agent's job, not the reader's: `/setup-tooling --mcp` runs the prerequisite script and
then the `connect-mcp-servers` skill, which detects what the machine has, installs `gh` and the
`gh-mcp` extension when they are missing, writes the global entry and verifies the result. It hands
back only the steps a human must perform — `gh auth login`, exporting `GH_TOKEN`, enabling the IDE's
MCP server, a restart on versions that do not hot-reload. Without `--mcp`, the command only reports
and never touches the global config.

Add a server from the CLI so unrelated settings are preserved:

```bash
opencode mcp add context7 --url https://mcp.context7.com/mcp
opencode mcp list
```

Rules:

- `.opencode/opencode.json` is generated and machine-specific; never commit it and never edit it by
  hand.
- `.opencode` configuration takes precedence over the root file, so do not reuse the name `gsd` for
  your own server.
- Secrets use `{env:NAME}` substitution; `.env` is gitignored. A literal token in a config is a
  defect.
- OpenCode reloads configuration on save; a restart is not needed. Use `/reload` only if a server
  looks stale, and restart if the server is still listed as unavailable.

The tracked `playwright` entry runs `npx -y @playwright/mcp@0.0.85 --headless`, so an agent session
can drive the running SPA without a visible browser: navigate to http://localhost:3000/, snapshot
the accessibility tree and assert test ids with an evaluate call. Its snapshots and console logs
land in `.playwright-mcp/`, which is gitignored. It is the project-level example precisely because
it needs nothing but `npx`.

## Session titles

A session is named after the phase GSD is on: `Phase 2 — Session and message path`. The
`phase-session-title` plugin under `.opencode/plugins/` does it, with no command to remember:

- it reads `current_phase` and `current_phase_name` from `.planning/STATE.md`;
- on title generation it supplies that string through the `title` session hook, so a new session is
  never named after its first prompt;
- on prompt admission it compares the session's current title and renames once when the phase moved,
  so a long session follows `/gsd-progress --next`.

The title follows the phase, not the branch: a session resumed on an older branch still names the
phase it is on. A missing `STATE.md` or absent fields leave the harness title untouched, and a
failed rename is logged without blocking the prompt. Plugins reload when the configuration changes;
`/reload` forces it.

Verify it at two levels:

```bash
npm --prefix .opencode/plugins/phase-session-title test        # parsing: node:test, no dependencies
npm --prefix .opencode/plugins/phase-session-title run typecheck # the plugin against @opencode/plugin
```

CI runs the first after the formatting gate. The hooks need the OpenCode server and a model call, so
after editing `index.ts` check one session by hand:

```bash
opencode run "reply with ok" >/dev/null
opencode session list | head -1     # expect: Phase N — <name>
opencode session delete <id>        # remove the throwaway session
```

`@opencode/plugin` is a devDependency pinned to the running OpenCode version (`2.2.7`). It exists so
the type check and the IDE's TypeScript service can see the hook API; the runtime loads the plugin
with its own copy. Bump the pin together with OpenCode.

## Build logic

Shared build configuration lives in the `build-logic` included build, not in the module scripts:

| Convention plugin         | Applies                                                            | Adds                                                                 |
| ------------------------- | ------------------------------------------------------------------ | -------------------------------------------------------------------- |
| `kotlin-conventions`      | Kotlin JVM, JDK 21 toolchain, Spotless + ktlint                    | the `:courses-platform` BOM                                          |
| `spring-boot-conventions` | `kotlin-conventions`, Kotlin Spring, Spring Boot, `jvm-test-suite` | service dependencies, the common `withType<JvmTestSuite>` test setup |
| `frontend-conventions`    | the SPA module                                                     | `stageImageFiles` and the `dockerBuildImage` `PackBuildImageTask`    |

`courses-domain` applies only `kotlin-conventions`; `courses-write`, `courses-read` and
`courses-api` apply `spring-boot-conventions`. Each service keeps its own `:courses-domain`
dependency, `integrationTest` suite and test dependencies, so nothing service-specific hides behind
a convention.

`build-logic` imports the same `gradle/libs.versions.toml` and uses its generated accessors
(`LibrariesForLibs`), so plugin and dependency versions stay in one file. It is formatted by
Spotless too, and its `spotlessCheck` runs from the root `check`, so CI covers the build sources.

`check` also lints `.github/workflows/*.yml` with `actionlint`; install it locally
(`brew install actionlint`) or the task fails. CI downloads a pinned, checksum-verified release
before the build.

## Running the tests

Each service module uses the Gradle `jvm-test-suite` plugin, so the source directory decides which
task runs a test:

| Directory                    | Task                        | Suffix   | Needs Docker |
| ---------------------------- | --------------------------- | -------- | ------------ |
| `src/test/kotlin`            | `./gradlew test`            | `*Tests` | no           |
| `src/integrationTest/kotlin` | `./gradlew integrationTest` | `*IT`    | yes          |

A `componentTest` suite uses `*CT`, an `e2eTest` suite `*E2E` and the negative control `*NC`, when
those suites are added. Each suite runs only classes with its suffix, and `checkTestNaming` (part of
`check`) fails when a test file does not carry it.

Shared test infrastructure lives in the `courses-test-support` module and is consumed with
`implementation(project(":courses-test-support"))` in the integration suite: the Axon Server
registration base and the Docker image lookup are written once, not in every service.

`./gradlew build` runs the unit tests plus the lint and format gates, but no `integrationTest` —
that is what keeps a local build Docker-free. The integration suites in `courses-write`,
`courses-read` and `courses-api` own their infrastructure: Testcontainers starts a fresh Axon Server
(and, where a test needs it, PostgreSQL) for the run on random host ports and removes it afterwards.
Only the read-side suite needs PostgreSQL; the other suites need Axon Server alone. Nothing has to
be started first, ports never clash with the Compose stack, and no volume or event store survives a
run. Only the Docker daemon is required.

There are no tag filters: move a test between directories instead of tagging it.

## Architecture decisions

Decisions with real alternatives are recorded as ADRs in `docs/adr/` (Nygard format,
`NNNN-slug.md`); `docs/adr/README.md` is the index. GSD can ingest them with
`/gsd-plan-phase --ingest docs/adr`. `.planning/PROJECT.md` keeps the short Key Decisions index.

## Running the stack locally

Every deployable is a Paketo image. `pack` must be on `PATH` for the frontend image; the service
images are built by `bootBuildImage` through Docker. From the repository root:

```bash
./gradlew bootBuildImage :courses-frontend:dockerBuildImage
docker compose up -d --wait
# frontend: http://localhost:3000, gateway: http://localhost:8080
```

`docker compose up -d --wait` starts the whole stack and returns once every container is healthy:

| Container    | Ports       | Health check                                                                |
| ------------ | ----------- | --------------------------------------------------------------------------- |
| `axonserver` | 8024, 8124  | `curl http://localhost:8024/actuator/info`                                  |
| `postgres`   | 5432        | `pg_isready -U courses`                                                     |
| `write`      | —           | `/workspace/health-check` (`/actuator/health` on 8080; `bootRun` uses 8081) |
| `read`       | —           | `/workspace/health-check` (`/actuator/health` on 8080; `bootRun` uses 8082) |
| `api`        | 8080        | `/workspace/health-check` (`/actuator/health` on 8080)                      |
| `frontend`   | 3000 → 8080 | `bash -c "exec 3<>/dev/tcp/127.0.0.1/8080"`                                 |

8024 is Axon Server's HTTP console and REST API; 8124 is the gRPC port the services connect to.
PostgreSQL carries the `courses` database and the `courses` user, with `courses` as the password.
That credential is a local development convenience, not a secret and not a production default — this
project has no production deployment. Only the gateway and the frontend are exposed, on
`${API_PORT:-8080}` and `${FRONTEND_PORT:-3000}`; `write` and `read` stay on the Compose network.

The service images default `AXON_SERVER` to `axonserver:8124` (`BPE_DEFAULT_AXON_SERVER` in each
service's `bootBuildImage`); a deployment overrides it with the `AXON_SERVER` environment variable.

The frontend is a separate origin. nginx serves the Vite bundle, and `start.sh` renders `config.js`
from `COURSES_API_BASE_URL` at container start, so one image works in every environment. The gateway
accepts cross-origin calls only from `COURSES_CORS_ALLOWED_ORIGINS` and is fail-closed when it is
unset. The bundle also serves `/build-info.json` (name, group, version; no timestamp), mirroring
Spring Boot's build-info.

For development with hot reload, run the infrastructure in Compose and the services from Gradle:

```bash
docker compose up -d axonserver postgres
./gradlew bootRun --parallel
./gradlew :courses-frontend:dev   # Vite on 5173, proxies /courses, /me and /session
```

`bootRun` uses `AXON_SERVER=localhost:8124` by default; the dev server serves `public/config.js`.

`./scripts/smoke-stack.sh` runs the whole loop as one command: it builds all four images, starts the
stack, checks the Axon registration for each service, asserts the SPA shell and the rendered
`config.js` on :3000, then tears everything down. Volumes are kept, so a local PostgreSQL or event
store survives the run; `--wipe-volumes` starts from a clean slate.

Compose tags are guarded by `checkComposeImages`, which runs as part of `check`: project images must
use `${PROJECT_VERSION:-<project.version>}`; infrastructure tags may be pinned or use
`${AXONSERVER_VERSION:-...}` / `${POSTGRES_VERSION:-...}`, and the effective tag must match the
version catalog.

Registration is asserted inside the applications, never by scraping `/actuator/health`: this starter
combination registers no Axon health indicator, so a green health endpoint says nothing about the
Axon connection. To prove registration, run

```bash
./gradlew integrationTest --tests '*AxonServerRegistrationIT'
```

The test starts its own Axon Server container, so it proves the application registers without
depending on the Compose stack.

### The 12-hour unlicensed window

Axon Server runs unlicensed here. On startup it logs
`No license detected. Running in unregistered mode.` and `Trial access is limited to 12 hours`, then
schedules a shutdown twelve hours later — a long demo day dies mid-session even though the
containers stay up. `docker compose restart axonserver` starts a fresh window; a license from
[platform.axoniq.io](https://platform.axoniq.io) removes the limit. Nothing in this setup is
production-ready: Phase 1 proves only that the commercial artifacts resolve and boot without a
license.

## CI

Three checks are required on `main`: `build`, `format` and `title`.

`.github/workflows/ci.yml` runs the `build` job on Ubuntu 24.04:

1. checkout, JDK 21 (Temurin), Node 24.20.0 with the npm cache, and `gradle/actions/setup-gradle`
   for the Gradle dependency, wrapper and build caches;
2. `./gradlew build` — builds and unit-tests every module, including the SPA through
   `:courses-frontend`. It is Docker-free by construction: unit tests live in `src/test/kotlin`, and
   the `integrationTest` suites are separate source sets that are not part of `check`;
3. `./gradlew integrationTest` — the `integrationTest` suites in `courses-write`, `courses-read` and
   `courses-api`, whose sources live in `src/integrationTest/kotlin`. Each suite starts its own Axon
   Server through Testcontainers — plus PostgreSQL in the read-side suite — so the job starts no
   Compose stack and tears nothing down. Draft pull requests skip this step; marking a draft ready
   (`ready_for_review`) re-runs CI so the suites execute before merge.

Images are built locally, and in CI only by the Playwright e2e job (Phase 8), which needs the real
stack: `./gradlew bootBuildImage` and `./gradlew :courses-frontend:dockerBuildImage`. They are never
pushed to a registry.

The `format` job sets Node up with the npm cache before running the same `./scripts/format-check.sh`
used locally — Prettier over docs, the README, workflows and root JSON; `.planning/`, `AGENTS.md`
and `*.jsonc` are excluded because the planning files are parsed by GSD and generated files would
drift.

The `title` check lives in `.github/workflows/pr-title.yml` and enforces the conventional-commit
format on pull request titles, because the squash commit in `main` takes the PR title.

### Branch protection

`main` is protected by five repository rulesets rather than by a file in the tree, so the policy
lives in Settings → Rules and changes through the API:

| Ruleset                       | Rule                                                                              |
| ----------------------------- | --------------------------------------------------------------------------------- |
| `main-require-pr-on-merge`    | PR required, squash only, no approval needed, conversations must be resolved      |
| `main-required-checks`        | `build`, `format` and `title`, strict — the branch must be up to date with `main` |
| `main-require-linear-history` | no merge commits                                                                  |
| `main-restrict-deletions`     | `main` cannot be deleted                                                          |
| `main-block-force-pushes`     | no force-push, except the owner's explicit bypass                                 |

A direct push to `main` is rejected even for the owner: the PR and checks rules have no bypass. The
force-push bypass remains the single escape hatch for repairing history, and required checks still
apply to the rewritten commits.

Change a rule with `gh api -X PUT repos/{owner}/{repo}/rulesets/{id} --input ruleset.json`. Classic
branch protection is not used; the two mechanisms must not run side by side.

Pull requests start from `.github/pull_request_template.md`: summary, change type, decisions and
trade-offs, deliberate exclusions, the local checks and a test plan.

## Dependency updates

`.github/dependabot.yml` watches three ecosystems on a Monday schedule. Actions updates of any size
land in one PR; the Gradle group and npm's catch-all group carry minor and patch updates only, and
stack majors are ignored entirely — except `fsd-linters`, which moves `steiger` with its plugin in
one PR whatever the size:

| Ecosystem        | Directory           | Covers                                                      |
| ---------------- | ------------------- | ----------------------------------------------------------- |
| `github-actions` | `/`                 | every `uses:` pin in `.github/workflows`                    |
| `gradle`         | `/`                 | `gradle/libs.versions.toml` — modules **and** `build-logic` |
| `npm`            | `/courses-frontend` | `package.json` and its lockfile                             |

The Gradle entry is one entry because `build-logic` imports the root catalog: a version is bumped
once, in one file. `fsd-linters` groups `steiger` with `@feature-sliced/steiger-plugin`, because
they share one FSD spec version and a major of one without the other breaks the lint.

Major upgrades of the stack — Kotlin, Spring Boot, Axon and, on the frontend, React, Vite and
TypeScript — are ignored on purpose. They are migrations that invalidate assumptions recorded in
`docs/adr/` and `.planning/research/STACK.md`, so they start from a decision, not from a bot PR.
Everything else may arrive as a PR; CI builds it and runs the integration suites.

The commit-message prefix is `build`, so the generated title passes the `title` check.

### Pins no ecosystem can see

Four kinds of version are read by the build rather than declared as a dependency, so Dependabot
leaves them alone and they are updated by hand:

| Pin                                              | Where                                       | Guard                                                                     |
| ------------------------------------------------ | ------------------------------------------- | ------------------------------------------------------------------------- |
| Gradle wrapper (`distributionUrl`, checksum)     | `gradle/wrapper/gradle-wrapper.properties`  | `./gradlew wrapper --gradle-version X --gradle-distribution-sha256-sum Y` |
| `axonserver`, `postgres`                         | `[versions]` in `gradle/libs.versions.toml` | `checkComposeImages` compares the catalog with `compose.yaml`             |
| `paketo-*` buildpack, builder and nginx versions | `[versions]` in `gradle/libs.versions.toml` | images fail to build on a bad pin                                         |
| `@playwright/mcp` in `opencode.jsonc`            | root config                                 | manual, agent-side only                                                   |

A `[versions]` entry that no `[libraries]` or `[plugins]` entry references is invisible to
Dependabot as well: it is a constant the build reads, not a declared dependency. That is exactly why
`axonserver`, `postgres` and the Paketo pins are listed above.

Bumping one of these is a two-line change plus `./gradlew build`:

- **Gradle wrapper** — update `distributionUrl` and `distributionSha256Sum` together and verify the
  new distribution with `./gradlew build`; never bump the URL without re-pinning the checksum.
- **`axonserver`** — must stay compatible with Axon Framework 5.4.x. Check the Axon Server release
  notes, bump the catalog, and `checkComposeImages` propagates the expectation to `compose.yaml`.
- **`postgres`** — a major bump changes the data directory layout. Bump it together with the
  `docker compose down -v` step in the local loop; nothing migrates an existing volume.
- **Paketo versions** — a builder or buildpack bump changes the run image; rebuild all four images
  with `./scripts/smoke-stack.sh` and check the health checks still pass.

The Paketo pins are the one category here that has a reminder:
`.github/workflows/buildpack-updates.yml` runs weekly, compares the catalog with Docker Hub and
keeps one open "Buildpack updates" issue. The bump stays manual because an image build is the only
proof that a new buildpack works. GitHub disables scheduled workflows after 60 days without
repository activity, so a schedule that stopped looks exactly like a clean report — run the workflow
by hand (`workflow_dispatch`) after a long pause.

Buildpack bumps fail more often than a version number suggests, and none of the failures shows up in
`./gradlew build`. The modes worth knowing, each of which costs an afternoon the first time:

1. **The buildpack is not in the builder.** `bootBuildImage` and `pack` accept a buildpack by id, by
   a version the builder bundles, or by an image reference. `id@version` for something the builder
   does not bundle fails with `Invalid buildpack reference`; a buildpack published as an image is
   pinned as `docker://…`, which is why the health checker is written that way.
2. **The builder and the buildpacks it bundles drift apart.** A buildpackage the builder lacks is
   pulled from the registry, and the slice `pack` resolves can disagree with the builder's platform:
   the image builds, then dies at runtime with `exec: nginx: not found`. Bump the builder and its
   bundled buildpacks together, and do not trust a pair that was never run.
3. **An unversioned reference becomes ambiguous.** Once a builder bundles two versions of the same
   buildpack, an unversioned id fails with "multiple versions … must specify an explicit version".
   That is why every buildpack here carries an explicit version.
4. **A floating builder flips the platform.** Without an explicit `imagePlatform`, Spring Boot
   derives the build platform from the builder image, so a `latest` retagged to another architecture
   kills the build with `exec /cnb/lifecycle/creator: exec format error`. Builders stay pinned.
5. **A published release can be unusable.** `paketo-buildpacks/nginx` 1.3.6 is served by the
   registry but `pack` 0.40.10 cannot extract it
   (`could not find label 'io.buildpacks.buildpackage.metadata'`). Hold the pin back and let the
   weekly workflow stay quiet: `paketo-nginx = "1.3.5" # hold-back: 1.3.6`. The workflow skips
   exactly the held version and reports the next one; drop the marker when a later release or a
   newer `pack` fixes it.
6. **Do not freeze the run image.** Base-OS patches arrive through the run image the builder names.
   Pinning it would turn the security pipeline into a frozen one — rebuild instead.

Two things that look like defects and are not: `pack`'s warnings about containerd storage and about
the deprecated `stacks` key are informational, and CNB layer timestamps read `Jan 1 1980` by design,
so an image's date says nothing about when it was built.

When an image build does break on a bump: put the previous pin back, record the version, the error
and the date in the buildpack issue, and retry on a later release rather than on the same broken
one.

None of these pins is the security schedule — the run image is. CVE fixes for OpenSSL, glibc and
`ca-certificates` arrive in the run image that the builder references, so an image picks them up
only when it is rebuilt. Images are rebuilt before a demo and at least monthly, and they are neither
built in CI nor pushed to a registry, which is why nothing scans them yet (backlog item 999.2).

### Vulnerability scanning

These are repository settings, not files in the tree — see
`docs/adr/0010-github-native-vulnerability-scanning.md` for why:

| Mechanism                         | State | What it catches                                          |
| --------------------------------- | ----- | -------------------------------------------------------- |
| Secret scanning + push protection | on    | committed credentials, blocked before the push lands     |
| Dependabot alerts                 | on    | published advisories against resolved versions           |
| Dependabot security updates       | on    | an advisory with a fixed version, as a PR that CI checks |
| Snyk, `npm audit` gate, CodeQL    | none  | —                                                        |

`ignore` in `dependabot.yml` suppresses **version** updates only. A security update still opens a
PR, including for a major that the ignore list would otherwise hold back: an unfixed CVE outranks a
migration preference, and the PR still needs a human decision.

A fork, or a repository created from this one, starts with alerts off — the setting lives on the
repository, not in the tree.
