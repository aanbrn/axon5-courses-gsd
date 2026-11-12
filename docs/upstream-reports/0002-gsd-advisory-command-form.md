# 0002 — The workflow advisory names a command form the runtime does not have

|            |                                                                      |
| ---------- | -------------------------------------------------------------------- |
| Dependency | `@opengsd/gsd-core`                                                  |
| Version    | 1.20.1                                                               |
| Ecosystem  | npm                                                                  |
| Upstream   | https://github.com/open-gsd/gsd-core                                 |
| Status     | `not filed`                                                          |
| Disclosure | Drafted with assistance from an AI agent and reviewed before filing. |
| Found      | 2026-11-12                                                           |

## Summary

The workflow advisory tells the agent to use `/gsd:fast` or `/gsd:quick`. Those are the Claude Code
forms; on OpenCode the commands are `/gsd-fast` and `/gsd-quick`, so the advice cannot be followed
by the runtime that received it. The core already renders the correct form through `formatGsdSlash`,
which the generated instruction file uses and this hook does not.

## Claims and evidence

| #   | Claim                                                                       | Evidence                                                                                                                      |
| --- | --------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| C1  | The advisory hardcodes the colon form.                                      | `hooks/gsd-workflow-guard.js:369`; the same form in the header comment at `:8`                                                |
| C2  | On OpenCode the commands are flat and hyphenated.                           | `.opencode/commands/gsd-fast.md`, `.opencode/commands/gsd-quick.md`                                                           |
| C3  | The core renders the runtime-correct form and the instruction file uses it. | `bin/lib/runtime-slash.cjs:43` (`formatGsdSlash`), `:228` (`formatGsdSlashFor`), used at `bin/lib/profile-output.cjs:202-217` |
| C4  | The hook therefore bypasses an existing mechanism rather than lacking one.  | C1 versus C3                                                                                                                  |

## Reproduction

```bash
P=$(mktemp -d) && mkdir -p "$P/.planning"
printf '{"hooks":{"workflow_guard":true}}' > "$P/.planning/config.json"
echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/src/App.kt\"}}" \
  | node <gsd-core>/hooks/gsd-workflow-guard.js
```

The `additionalContext` contains:

```text
Consider using /gsd:fast for trivial fixes or /gsd:quick for larger changes
```

## Draft

```text
Title: The workflow advisory suggests /gsd:fast and /gsd:quick, which OpenCode does not have

On OpenCode the workflow advisory ends with:

  "Consider using /gsd:fast for trivial fixes or /gsd:quick for larger changes"
  (hooks/gsd-workflow-guard.js:369)

Those are the Claude Code command names. OpenCode installs the flat, hyphenated forms
(commands/gsd-fast, commands/gsd-quick), so the advice points at commands the runtime does not have.

The core already knows how to render this per runtime: formatGsdSlash() in
bin/lib/runtime-slash.cjs:43, wrapped for a project by formatGsdSlashFor() at :228, and used by the
instruction-file generator (bin/lib/profile-output.cjs:202-217) for exactly this reason. The hook
predates or misses that helper and hardcodes the colon form.

Suggested fix: call formatGsdSlashFor(cwd, 'fast') and formatGsdSlashFor(cwd, 'quick'), or take the
already-rendered names from the same source the instruction file uses, so the advisory cannot drift
from the installed command layout.

Drafted with assistance from an AI agent and reviewed before filing.
```

## Review

| Pass                                     | Verdict                                                           | Date       |
| ---------------------------------------- | ----------------------------------------------------------------- | ---------- |
| Evidence (agent)                         | passed — claims re-read at the cited lines; reproduction executed | 2026-11-12 |
| Independent (codex, `gpt-6.1-sol`, high) | pending                                                           | —          |
| Owner                                    | pending                                                           | —          |
