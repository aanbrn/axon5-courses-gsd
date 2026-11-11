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

## CI

`.github/workflows/ci.yml` builds and tests the project once `gradlew` exists. Until then the build
step is skipped and the job stays green, so the required `build` check is never red for a missing
wrapper.

`./scripts/format-check.sh` runs Prettier over docs, the README, workflows and root JSON.
`.planning/`, `AGENTS.md` and `*.jsonc` are excluded: the planning files are parsed by GSD and
generated files would drift.

`.github/workflows/pr-title.yml` enforces the conventional-commit format on pull request titles,
because the squash commit in `main` takes the PR title.
