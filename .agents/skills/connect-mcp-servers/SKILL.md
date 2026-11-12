---
name: connect-mcp-servers
description: Connect the user-level MCP servers this project works with — GitHub through the `gh mcp` extension and IntelliJ IDEA's built-in server — by detecting what the machine already has, installing what is missing and wiring the rest into the user's global OpenCode config, handing back only the steps that need a human. Use when the user asks to set up, install, connect or repair MCP servers or agent tools, when GitHub/IDE MCP tools are missing from a session, or when a server is configured but unreachable.
---

# Connect the MCP servers with the fewest manual steps

The outcome is a working tool list at the end of the session, not a list of instructions. Install
what can be installed, wire what is already there, and ask the human only for the steps a machine
cannot perform: an interactive login, an IDE setting, an app restart. Never hand back a command the
agent could have run itself.

## Scope

| Server | Shape | Needs |
| --- | --- | --- |
| GitHub | local `gh mcp` (the `gh-mcp` extension) | `gh` installed, `gh auth login` done, `GH_TOKEN` in the launching environment |
| IntelliJ IDEA | remote `http://127.0.0.1:<port>/stream` | IDEA running with its MCP server enabled; the port must match |

Out of scope: project-scoped servers that need no credentials — Playwright already lives in the
tracked `opencode.jsonc`. Tokens are never written into a config file.

## Step 1 — Know the environment before changing anything

```bash
opencode --version
printf '%s\n' "${XDG_CONFIG_HOME:-$HOME/.config}/opencode/opencode.jsonc"
command -v brew apt-get dnf pacman winget npx gh
gh auth status
gh extension list | grep -i mcp
curl -fsS -m 2 http://127.0.0.1:64342/stream
```

Read the global config too. Its existing shape decides the shape of your edit: on OpenCode 2.2.x
both the flat `mcp` map (user config) and `mcp.servers` (project config) load, so match what the file
already uses instead of migrating it.

## Step 2 — Check whether a 1.x consumer shares the file

The shape is decided by the file's consumers, not by the version you happen to be running. 2.x reads
both shapes; 1.x — today the cloud agent — reads only the flat `mcp` map, so a file with
`mcp.servers` shows it no servers at all.

| | 1.x — the cloud agent | 2.x — local CLI, TUI, desktop |
| --- | --- | --- |
| MCP shape it reads | flat `mcp` map only | flat `mcp` **and** `mcp.servers` |
| `opencode mcp` subcommands | may be absent | `list`, `add`, `auth`, `logout` |

```bash
opencode --version
opencode mcp --help >/dev/null 2>&1 && echo "mcp subcommands available"
```

Then apply the rule for the consumers the file actually has:

- **Only 2.x reads it — the normal case: nobody runs a 1.x agent unless they say so.** Use the shape
  the file already has; `mcp.servers` is correct for a v2 project.
- **A 1.x consumer reads the same file — a cloud agent, or an IDE agent on 1.x.** Flatten it to the
  `mcp` map. `mcp.servers` makes every server invisible to that agent, and no error is raised
  anywhere to tell you.
- **Do not flatten "just in case."** A shape written for a consumer nobody runs is a decision nobody
  can trace later.
- If it cannot be determined from the machine, ask one question — "does the cloud agent or any 1.x
  agent use this config?" — rather than researching it. Note the answer in the report.

## Step 3 — Install what is missing, with what the machine has

Pick the package manager from Step 1; fall back to a release binary or `npx` only when none exists.

- `gh` — `brew install gh`, `sudo apt install gh`, `winget install GitHub.cli`.
- `gh-mcp` extension — `gh extension install shuymn/gh-mcp`. Check that the repository still exists
  before running it; an extension that moved leaves a broken `gh mcp` subcommand.

If an install needs an interactive sudo prompt and cannot complete non-interactively, ask the human
for exactly that one command — not for the whole setup.

## Step 4 — Wire the global config

Prefer `opencode mcp add` when the installed version offers it — with `--global` for the user config:
it preserves unrelated settings and comments. Read the file back afterwards: it may pick
`mcp.servers` on its own, which is fine unless Step 2 found a 1.x consumer sharing the file — then
flatten it. Otherwise edit the JSONC by hand, changing only the `mcp` object and never reformatting
the rest of the file.

```jsonc
"mcp": {
  "github": {
    "type": "local",
    "command": ["gh", "mcp"],
    "environment": { "GH_TOKEN": "{env:GH_TOKEN}" }
  },
  "intellij": { "type": "remote", "url": "http://127.0.0.1:64342/stream" }
}
```

- User-level servers belong in the global config, never in the project `opencode.jsonc`: they are
  auth- or IDE-bound and would not work for anyone else.
- Secrets use `{env:NAME}` substitution. A literal token in the file is a defect, even locally.
- Do not reuse the name `gsd` — the generated `.opencode/opencode.json` owns it and takes precedence.

## Step 5 — Prove it works instead of assuming

- `opencode mcp list` when available, plus a read-back of the written config.
- GitHub: `gh auth status` exits zero and `gh mcp --help` resolves; if `GH_TOKEN` is not exported in
  the shell that launched OpenCode, the server starts without credentials.
- IntelliJ: repeat the `curl`. A refused connection means the IDE is closed or its MCP server is
  off — that is a human step, not a config bug.

Report which servers are live and which are waiting on a human.

## Step 6 — Hand back only what needs a human

| Step | Why the agent cannot do it | What to say |
| --- | --- | --- |
| `gh auth login` | Interactive device or browser flow; the agent cannot type the code | The exact command, and that it must run before the next session |
| Exporting `GH_TOKEN` | Must exist in the environment that launches OpenCode | The variable name and where it is read |
| Enabling IDEA's MCP server | A GUI setting; the port is chosen there | Settings → Tools → MCP Server, and the URL to keep in sync |
| Restarting OpenCode | Older versions do not hot-reload MCP entries | Only if `opencode mcp list` shows the server as unavailable |

## Rules

- Never add an auth-bound server to the project config, and never commit a token.
- Never invent a config shape: it follows the consumers found in Step 2; a project file uses
  `mcp.servers`, and the `$schema` decides when a file is neither.
- If a step fails, report the exact error and what was already tried instead of silently switching
  to another install method.
- This skill owns the user's machine, not the repository: it must not create, modify or commit files
  under the project except when the user asks for the documented procedure to change.
