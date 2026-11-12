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

The workflow advisory ends with "Consider using /gsd:fast for trivial fixes or /gsd:quick for larger
changes" (`hooks/gsd-workflow-guard.js:369`; the same literal sits in the header comment at `:8`).
`bin/lib/runtime-slash.cjs:11-17` states that installs register under the hyphen form, that the
colon form is no longer routable by Claude Code skill installs, and that this module is the only
place the runtime should decide which shape to emit. The core follows that: `formatGsdSlash` renders
the name (`:43`), the instruction generator uses it (`bin/lib/profile-output.cjs:202-217`, producing
`AGENTS.md:429-432`), and the body rewriter normalises `/gsd:<cmd>`
(`bin/lib/runtime-artifact-conversion.cjs:492`, reused by the OpenCode skill writer at `:3207`). The
hook still carries the literal on an OpenCode install, where the commands are registered as
`/gsd-fast` and `/gsd-quick`. The ask is to emit the registered name.

## Claims and evidence

The claims cite `@opengsd/gsd-core` package paths. In this install they resolve as:

| Upstream path   | Resolves here as                                                     |
| --------------- | -------------------------------------------------------------------- |
| `hooks/*.js`    | `.opencode/hooks/*.js` (installed beside `gsd-core/`, not inside it) |
| `plugins/*.js`  | `.opencode/plugins/*.js`                                             |
| `bin/**`        | `.opencode/gsd-core/bin/**`                                          |
| `workflows/**`  | `.opencode/gsd-core/workflows/**`                                    |
| `commands/*.md` | `.opencode/commands/*.md`                                            |

Every line below was re-read at the cited line on 2026-11-12.

| #   | Claim                                                                                                              | Evidence                                                                                                                                                                                                                                  |
| --- | ------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| C1  | The advisory hardcodes the colon form.                                                                             | `hooks/gsd-workflow-guard.js:369` ("Consider using /gsd:fast ... or /gsd:quick ..."); the same form in the header comment at `:8` ("nudges Claude to use /gsd:quick or /gsd:fast")                                                        |
| C2  | An OpenCode install registers the hyphenated names; the command files carry no `name:` field in their frontmatter. | `commands/gsd-fast.md:1-9` (frontmatter, no `name:` field) and `:24` (the body refers to itself as `/gsd-fast`); `commands/gsd-quick.md:130` ("Resume with: /gsd-quick resume {slug}")                                                    |
| C3  | The core renders and reuses the runtime-correct form.                                                              | `bin/lib/runtime-slash.cjs:11-17`, `:43` (`formatGsdSlash`), `:228` (`formatGsdSlashFor`); `bin/lib/profile-output.cjs:202-217`; `bin/lib/runtime-artifact-conversion.cjs:492`, `:3207`                                                   |
| C4  | The hook does not use that helper; its literal and the names this install registers disagree.                      | `grep -n 'formatGsdSlash\|runtime-slash' .opencode/hooks/gsd-workflow-guard.js` returns no match (exit 1), while `:8` and `:369` carry the literal                                                                                        |
| C5  | Prior art: #376 closed this class of the problem for Claude Code installs; the OpenCode path remains.              | #376, CLOSED / COMPLETED on 2026-05-28 ("Hook source files (.js) skipped by /gsd: → /gsd- rewriter — colon namespace leaks into ... workflow-guard messages on Claude Code installs"); the literal is still present in 1.20.1's hook (C1) |

The `bin/lib/runtime-slash.cjs` comment attributes the `bin/lib` cleanup to `#3584` and the hyphen
unification to `#2808`. Both numbers show different titles in the tracker today, so this report
quotes the comment and does not rely on either.

## Reproduction

Run from the repository root of a checkout with `@opengsd/gsd-core` 1.20.1 installed. `H` is the
absolute, quoted path to the hook as installed here; the hook sits beside `gsd-core/`, not inside
it.

### Block A — the advisory text

```bash
H="$PWD/.opencode/hooks/gsd-workflow-guard.js"
P=$(mktemp -d) && mkdir -p "$P/.planning" "$P/src"
printf '{"hooks":{"workflow_guard":true}}' > "$P/.planning/config.json"
echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/src/App.kt\"}}" \
  | node "$H"; echo " exit=$?"
```

Observed — one line of JSON, no `decision` field, `exit=0`; the `additionalContext` ends with:

```text
Consider using /gsd:fast for trivial fixes or /gsd:quick for larger changes to maintain project state tracking.
```

Note: this calls the hook script directly; it does not run the OpenCode adapter end to end. The
adapter invokes the hook for write-like tools and Bash (`plugins/gsd-core.js:753-757`) and injects
`additionalContext` as an advisory (`:368-389`).

## Draft

```text
Title: The workflow guard's advisory recommends /gsd:fast and /gsd:quick, which an OpenCode install
does not register

On OpenCode the workflow advisory ends with:

  "Consider using /gsd:fast for trivial fixes or /gsd:quick for larger changes"
  (hooks/gsd-workflow-guard.js:369)

That is the legacy namespace. bin/lib/runtime-slash.cjs:11-15 records that installs register under
the hyphen form and that the colon form "is no longer routable by Claude Code skill installs".

An OpenCode install registers the hyphenated names. The command files carry no name: field in their
frontmatter (commands/gsd-fast.md:1-9) and refer to themselves that way: /gsd-fast at
commands/gsd-fast.md:24, /gsd-quick at commands/gsd-quick.md:130.

The core already renders the runtime-correct form: formatGsdSlash() at bin/lib/runtime-slash.cjs:43,
wrapped for a project by formatGsdSlashFor() at :228, used by the instruction-file generator
(bin/lib/profile-output.cjs:202-217) — which is where AGENTS.md:429-432 gets its /gsd-fast,
/gsd-quick, /gsd-debug and /gsd-execute-phase names. The body rewriter normalises /gsd:<cmd> to the
hyphen form (bin/lib/runtime-artifact-conversion.cjs:492), and the OpenCode skill writer reuses that
converter (:3207). The hook carries the literal instead: grep for formatGsdSlash or runtime-slash in
hooks/gsd-workflow-guard.js returns nothing.

Reproduction — run from a checkout with the package installed; H is the absolute, quoted path to the
installed hook, which sits beside gsd-core/, not inside it:

  H="$PWD/.opencode/hooks/gsd-workflow-guard.js"
  P=$(mktemp -d) && mkdir -p "$P/.planning" "$P/src"
  printf '{"hooks":{"workflow_guard":true}}' > "$P/.planning/config.json"
  echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/src/App.kt\"}}" \
    | node "$H"; echo " exit=$?"

Observed: one line of JSON, no decision field, exit=0, and the additionalContext ends with:

  Consider using /gsd:fast for trivial fixes or /gsd:quick for larger changes to maintain project state tracking.

This calls the hook script directly; it does not run the OpenCode adapter end to end: the adapter
invokes the hook for write-like tools and Bash (plugins/gsd-core.js:753-757) and injects
additionalContext as an advisory (:368-389).

What is new relative to #376: that issue (CLOSED / COMPLETED on 2026-05-28) fixed colon literals in
hook .js files for Claude Code installs. The literal is still present in 1.20.1's workflow-guard
hook, so the OpenCode path — where the registered names are hyphenated — is what remains.

Suggested fix: use formatGsdSlash / formatGsdSlashFor(cwd, 'fast') in the advisory, or take the names
the instruction generator already renders, so the two outputs in one install cannot disagree.
Whether the hook predates that helper is not visible from the installed tree.

Filed from a personal hobby project that uses GSD for a learning exercise; the finding is about the
advisory's contract, not a production incident.

Drafted with assistance from an AI agent and reviewed before filing.
```

## Review

| Pass                                            | Verdict                                                                                                                                                                                                    | Date       |
| ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| Evidence (agent)                                | passed — claims re-read at the cited lines; reproduction executed                                                                                                                                          | 2026-11-12 |
| Independent (codex, `gpt-6.1-sol`, high)        | HOLD — the claim that the colon form is the Claude Code command name is false; duplicate risk against #376; the reproduction does not run as written; the runnable block sits outside the `## Draft` fence | 2026-11-12 |
| Independent re-run (codex, `gpt-6.1-sol`, high) | pending — must pass on this exact text before filing                                                                                                                                                       | —          |
| Owner                                           | pending                                                                                                                                                                                                    | —          |

The evidence row describes the text before this rewrite. The rewrite re-read every cited line on
2026-11-12 and re-ran the reproduction block.
