# Local tooling

The repository builds and tests without any agent tooling. This page describes the optional setup
that makes `/gsd-*` commands and MCP servers available.

## GSD Core (agent workflows)

Requires **Node.js >= 24** (the installer's own engine floor).

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
- `.agents/skills/` — gh-cli, github-actions-workflows, pull-request-review
- `.opencode/commands/setup-tooling.md` — the tracked project command
- `.planning/` — project context, requirements, roadmap, research, review record
- `.github/workflows/` — CI

## MCP servers

Project configuration lives in the tracked root `opencode.jsonc`. Servers go under `mcp.servers` (V2
shape):

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

Add one from the CLI so unrelated settings are preserved:

```bash
opencode mcp add context7 --url https://mcp.context7.com/mcp
opencode mcp list
```

Rules:

- `.opencode/opencode.json` is generated and machine-specific; never commit it and never edit it by
  hand.
- `.opencode` configuration takes precedence over the root file, so do not reuse the name `gsd` for
  your own server.
- Secrets use `{env:NAME}` substitution; `.env` is gitignored.
- OpenCode reloads configuration on save; a restart is not needed. Use `/reload` only if a server
  looks stale.

The tracked `playwright` entry runs `npx -y @playwright/mcp@0.0.85 --headless`, so an agent session
can drive the running SPA without a visible browser: navigate to http://localhost:8080/, snapshot
the accessibility tree and assert test ids with an evaluate call. Its snapshots and console logs
land in `.playwright-mcp/`, which is gitignored.

## Running the tests

Each service module uses the Gradle `jvm-test-suite` plugin, so the source directory decides which
task runs a test:

| Directory                    | Task                        | Needs Docker |
| ---------------------------- | --------------------------- | ------------ |
| `src/test/kotlin`            | `./gradlew test`            | no           |
| `src/integrationTest/kotlin` | `./gradlew integrationTest` | yes          |

`./gradlew build` runs `test` only, which is what keeps a local build Docker-free. The integration
suites in `write`, `read` and `api` talk to the live Axon Server and PostgreSQL, so start Compose
first. There are no tag filters: move a test between directories instead of tagging it.

## Running the stack locally

Two commands from the repository root, in this order:

```bash
docker compose up -d --wait   # Axon Server + PostgreSQL
./gradlew bootRun --parallel  # write, read, api
```

`docker compose up -d --wait` starts the infrastructure and returns only once both containers are
healthy:

| Container    | Ports      | Health check                               |
| ------------ | ---------- | ------------------------------------------ |
| `axonserver` | 8024, 8124 | `curl http://localhost:8024/actuator/info` |
| `postgres`   | 5432       | `pg_isready -U enrollment`                 |

8024 is Axon Server's HTTP console and REST API; 8124 is the gRPC port the three services connect
to. PostgreSQL carries the `enrollment` database and the `enrollment` user, with `enrollment` as the
password. That credential is a local development convenience, not a secret and not a production
default — this project has no production deployment.

`./gradlew bootRun --parallel` starts all three services in one Gradle run:

| Module  | Port | Role                                                   |
| ------- | ---- | ------------------------------------------------------ |
| `write` | 8081 | command side — the only service that appends events    |
| `read`  | 8082 | read side — projections and queries over PostgreSQL    |
| `api`   | 8080 | HTTP gateway, serves the SPA at http://localhost:8080/ |

For frontend-only work, `./gradlew :frontend:dev` runs Vite on 5173 and proxies `/api` to the
gateway.

`./scripts/smoke-stack.sh` runs the whole loop as one command: it brings Compose up, asserts that
`write`, `read` and `api` each register with Axon Server, starts the three services, checks their
health endpoints and the SPA shell, then tears everything down.

Registration is asserted inside the applications, never by scraping `/actuator/health`: this starter
combination registers no Axon health indicator, so a green health endpoint says nothing about the
Axon connection. To prove registration against a running stack, run

```bash
./gradlew integrationTest --tests '*AxonServerRegistrationIT'
```

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

1. checkout, plus JDK 21 (Temurin) and Node 24.20.0 with the Gradle and npm caches;
2. `docker compose up -d --wait` — Compose rather than `services:`, so the image tags have one
   source of truth;
3. `./gradlew build --no-daemon` — builds and unit-tests every module, including the SPA through
   `:frontend`. It is Docker-free by construction: unit tests live in `src/test/kotlin`, and the
   `integrationTest` suites are separate source sets that are not part of `check`;
4. `./gradlew integrationTest --no-daemon` — the `integrationTest` suites in `write`, `read` and
   `api`, whose sources live in `src/integrationTest/kotlin`, running against the live Axon Server
   and PostgreSQL started above;
5. `docker compose logs` when a step fails, and `docker compose down -v --remove-orphans` always.

The `format` job runs the same `./scripts/format-check.sh` used locally — Prettier over docs, the
README, workflows and root JSON; `.planning/`, `AGENTS.md` and `*.jsonc` are excluded because the
planning files are parsed by GSD and generated files would drift.

The `title` check lives in `.github/workflows/pr-title.yml` and enforces the conventional-commit
format on pull request titles, because the squash commit in `main` takes the PR title.
