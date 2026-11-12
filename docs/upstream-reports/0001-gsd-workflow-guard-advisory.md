# 0001 — The workflow guard advises; nothing requires a GSD command

|                |                                                                      |
| -------------- | -------------------------------------------------------------------- |
| Dependency     | `@opengsd/gsd-core`                                                  |
| Version        | 1.20.1                                                               |
| Ecosystem      | npm                                                                  |
| Upstream       | https://github.com/open-gsd/gsd-core                                 |
| Status         | `not filed`                                                          |
| Disclosure     | Drafted with assistance from an AI agent and reviewed before filing. |
| Found          | 2026-11-12                                                           |
| Local decision | `.planning/codebase/CONVENTIONS.md` § Upstream reports               |

## Summary

`AGENTS.md` asks the agent not to edit files outside a GSD workflow. `hooks.workflow_guard` is off
by default and, when enabled, emits an advisory for a top-level edit and lets the edit proceed. The
config schema names no key that enforces workflow entry. The report asks either for an opt-in
blocking mode or for the option to be labelled advisory-only.

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

| #   | Claim                                                                                                                                                                                                                                                                                                              | Evidence                                                                                                                                                                                                                                                |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| C1  | `hooks.workflow_guard` is opt-in and off by default.                                                                                                                                                                                                                                                               | `bin/shared/config-defaults.manifest.json:82` (`"workflow_guard": false`); documented at `workflows/settings.md:509` (`"workflow_guard": true/false`, no default statement)                                                                             |
| C2  | With the guard enabled, a top-level `Edit` of a file that is neither under `.planning/` nor one of the exempt config/doc filenames returns an advisory and no block decision.                                                                                                                                      | `hooks/gsd-workflow-guard.js:7` ("SOFT guard for edits — it advises, not blocks"), `:326` (subagent exemption), `:346-355` (`allowedPatterns`), `:365-372` (the advisory payload, `code: 'WORKFLOW_ADVISORY'`, no `decision`); reproduction A           |
| C3  | The hook's one hard block is `git add -f` / `git add --force` on a branch matching `/^(worktree-)?agent-/`, emitted as `decision: "block"` with exit code 2. An internal error on such a branch also blocks, failing closed with a reason saying the command was not analysed and not confirmed to be a force-add. | `hooks/gsd-workflow-guard.js:11`, `:112` (the predicate, used at `:311`), `:128-136` (the block payload and both reasons), `:153` (`failClosedBlockContext`), `:385-386` (the crash path calls it); reproduction B                                      |
| C4  | The OpenCode adapter runs this hook, so the advisory is delivered on OpenCode as well.                                                                                                                                                                                                                             | `plugins/gsd-core.js:753-757`                                                                                                                                                                                                                           |
| C5  | The adapter blocks only on `decision: "block"` or exit code 2; an advisory is injected and the call proceeds.                                                                                                                                                                                                      | `plugins/gsd-core.js:360-366` (`isBlock`), `:368-389` (advisory to `_gsdAdvisory` and `deliverAdvisory`)                                                                                                                                                |
| C6  | The generated instruction file states the rule as a directive with a user-bypass carve-out.                                                                                                                                                                                                                        | `AGENTS.md:434` ("... unless the user explicitly asks to bypass it"), generated by `bin/lib/profile-output.cjs:202-217`; the same block renders the entry points at `AGENTS.md:429-432`                                                                 |
| C7  | The config schema names no key that enforces workflow entry; the `gates.*` keys are conditions inside workflows.                                                                                                                                                                                                   | `bin/shared/config-schema.manifest.json:2` (the file describes itself as a list of valid config key paths) and `:5-7` (the three `gates.*` names, no descriptions); `workflows/execute-plan.md:84`, where `gates.execute_next_plan` is a step condition |

The evidence for C7 is the schema's own description plus one place a gate is read: a workflow
transition test, not a tool-layer guard.

## Reproduction

Run from the repository root of a checkout with `@opengsd/gsd-core` 1.20.1 installed. `H` is the
absolute, quoted path to the hook as installed here; the hook sits beside `gsd-core/`, not inside
it.

### Block A — advisory, with the exemption probe

```bash
H="$PWD/.opencode/hooks/gsd-workflow-guard.js"
P=$(mktemp -d) && mkdir -p "$P/.planning" "$P/src"
printf '{"hooks":{"workflow_guard":true}}' > "$P/.planning/config.json"
echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/src/App.kt\"}}" \
  | node "$H"; echo " exit=$?"
```

Observed — one line of JSON, no `decision` field, `exit=0`:

```text
{"hookSpecificOutput":{"hookEventName":"PreToolUse","additionalContext":"⚠️ WORKFLOW ADVISORY: You're editing App.kt directly without a GSD command. This edit will not be tracked in STATE.md or produce a SUMMARY.md. Consider using /gsd:fast for trivial fixes or /gsd:quick for larger changes to maintain project state tracking. If this is intentional (e.g., user explicitly asked for a direct edit), proceed normally.","code":"WORKFLOW_ADVISORY"}} exit=0
```

Exemption probe — the same payload against exempt paths, and once as a subagent session:

```bash
echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/.planning/STATE.md\"}}" | node "$H"; echo " exit=$?"
echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/AGENTS.md\"}}" | node "$H"; echo " exit=$?"
echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"session_type\":\"task\",\"tool_input\":{\"file_path\":\"$P/src/App.kt\"}}" | node "$H"; echo " exit=$?"
```

Observed — no advisory, only the ` exit=0` marker, in all three cases.

### Block B — force-add, both legs of the branch test

```bash
H="$PWD/.opencode/hooks/gsd-workflow-guard.js"
P=$(mktemp -d) && mkdir -p "$P/.planning"
printf '{"hooks":{"workflow_guard":true}}' > "$P/.planning/config.json"
git -C "$P" init -q -b agent-probe && git -C "$P" commit -q --allow-empty -m probe
echo "{\"tool_name\":\"Bash\",\"cwd\":\"$P\",\"tool_input\":{\"command\":\"git add -f x\"}}" | node "$H"; echo " exit=$?"
git -C "$P" checkout -q -b feature-probe
echo "{\"tool_name\":\"Bash\",\"cwd\":\"$P\",\"tool_input\":{\"command\":\"git add -f x\"}}" | node "$H"; echo " exit=$?"
```

Observed on `agent-probe` — the block payload on stdout, the same reason on stderr, `exit=2`:

```text
stdout: {"decision":"block","code":"WORKTREE_AGENT_FORCE_ADD_FORBIDDEN","origin":"force-add-detected","reason":"agent/worktree-agent branches must not run git add -f or git add --force. Respect the SDK skipped_gitignored/skipped_commit_docs_false contract and leave gitignored files untracked."}
stderr: agent/worktree-agent branches must not run git add -f or git add --force. Respect the SDK skipped_gitignored/skipped_commit_docs_false contract and leave gitignored files untracked.
exit=2
```

Observed on `feature-probe` — no output, `exit=0`.

Note: this calls the hook script directly; it does not run the OpenCode adapter end to end. The
adapter invokes the hook for write-like tools and Bash (`plugins/gsd-core.js:753-757`), blocks only
on `decision: "block"` or exit code 2 (`:360-366`), and injects `additionalContext` as an advisory
(`:368-389`).

## Draft

```text
Title: hooks.workflow_guard advises; no configuration key names workflow-entry enforcement

The generated instruction file tells the agent not to edit files outside a GSD workflow:

  "Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it."
  (AGENTS.md:434, generated by bin/lib/profile-output.cjs:202-217)

`hooks.workflow_guard` is off by default: "workflow_guard": false at
bin/shared/config-defaults.manifest.json:82. The key is documented at workflows/settings.md:509
without a default statement. With it enabled the edit still proceeds; the hook's own header says so:

  // This is a SOFT guard for edits — it advises, not blocks. The edit still proceeds.
  (hooks/gsd-workflow-guard.js:7)

The scope, exactly as the code shows it: a top-level Edit of a file that is neither under .planning/
nor one of the exempt config/doc filenames (.gitignore, .env, CLAUDE.md, AGENTS.md, GEMINI.md,
settings.json) returns the advisory and no block decision
(hooks/gsd-workflow-guard.js:326, :346-355, :365-372). A subagent session is exempt as well (:326).

Reproduction — run from a checkout with the package installed; H is the absolute, quoted path to the
installed hook, which sits beside gsd-core/, not inside it:

  H="$PWD/.opencode/hooks/gsd-workflow-guard.js"
  P=$(mktemp -d) && mkdir -p "$P/.planning" "$P/src"
  printf '{"hooks":{"workflow_guard":true}}' > "$P/.planning/config.json"
  echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/src/App.kt\"}}" \
    | node "$H"; echo " exit=$?"

Observed: one line of JSON, no decision field, exit=0, the additionalContext ending with the
/gsd:fast and /gsd:quick sentence:

  {"hookSpecificOutput":{"hookEventName":"PreToolUse","additionalContext":"⚠️ WORKFLOW ADVISORY: You're editing App.kt directly without a GSD command. This edit will not be tracked in STATE.md or produce a SUMMARY.md. Consider using /gsd:fast for trivial fixes or /gsd:quick for larger changes to maintain project state tracking. If this is intentional (e.g., user explicitly asked for a direct edit), proceed normally.","code":"WORKFLOW_ADVISORY"}} exit=0

Exemption probe — the same payload against an exempt path, and once as a subagent session:

  echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/.planning/STATE.md\"}}" | node "$H"; echo " exit=$?"
  echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/AGENTS.md\"}}" | node "$H"; echo " exit=$?"
  echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"session_type\":\"task\",\"tool_input\":{\"file_path\":\"$P/src/App.kt\"}}" | node "$H"; echo " exit=$?"

Observed: no advisory, exit=0, in all three cases.

The one hard block in this hook is force-add on an agent branch:

  H="$PWD/.opencode/hooks/gsd-workflow-guard.js"
  P=$(mktemp -d) && mkdir -p "$P/.planning"
  printf '{"hooks":{"workflow_guard":true}}' > "$P/.planning/config.json"
  git -C "$P" init -q -b agent-probe && git -C "$P" commit -q --allow-empty -m probe
  echo "{\"tool_name\":\"Bash\",\"cwd\":\"$P\",\"tool_input\":{\"command\":\"git add -f x\"}}" | node "$H"; echo " exit=$?"
  git -C "$P" checkout -q -b feature-probe
  echo "{\"tool_name\":\"Bash\",\"cwd\":\"$P\",\"tool_input\":{\"command\":\"git add -f x\"}}" | node "$H"; echo " exit=$?"

Observed: on agent-probe the guard prints
{"decision":"block","code":"WORKTREE_AGENT_FORCE_ADD_FORBIDDEN","origin":"force-add-detected",...}
on stdout, writes the same reason to stderr, and exits 2; on feature-probe it prints nothing and
exits 0. An internal error on such a branch also blocks, failing closed with a reason stating the
command was not analysed and not confirmed to be a force-add (:128-130, :153, :385-386).

Both probes call the hook script directly; they do not run the OpenCode adapter end to end
(plugins/gsd-core.js:753-757 invokes the hook for write-like tools and Bash; :360-366 blocks only on
decision: "block" or exit code 2; :368-389 injects additionalContext as an advisory).

The config schema names no key that enforces workflow entry. Its own description calls it a list of
valid config key paths (bin/shared/config-schema.manifest.json:2), the three gates.* names carry no
descriptions (:5-7), and gates.execute_next_plan is read as a step condition inside a workflow
(workflows/execute-plan.md:84) — a transition test, not a tool-layer guard.

Request: either add an opt-in blocking mode, or label hooks.workflow_guard advisory-only in its
description and in workflows/settings.md, so a project does not enable it expecting a gate.

Filed from a personal hobby project that uses GSD for a learning exercise; the finding is about the
option's contract, not a production incident.

Drafted with assistance from an AI agent and reviewed before filing.
```

## Review

| Pass                                            | Verdict                                                                                                                                                                                                                                                                           | Date       |
| ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| Evidence (agent)                                | passed — every claim re-read at the cited line on 2026-11-12; the reproduction was executed                                                                                                                                                                                       | 2026-11-12 |
| Independent (codex, `gpt-6.1-sol`, high)        | HOLD — C2 over-generalised (it omitted the subagent and exempt-filename carve-outs); C3 omitted `worktree-agent-*` and the fail-closed force-add exit; C7's schema key list does not show what the keys enforce; tone attributes intent; the reproduction does not run as written | 2026-11-12 |
| Independent re-run (codex, `gpt-6.1-sol`, high) | pending — must pass on this exact text before filing                                                                                                                                                                                                                              | —          |
| Owner                                           | pending                                                                                                                                                                                                                                                                           | —          |

The evidence row describes the text before this rewrite. The rewrite re-read every cited line on
2026-11-12 and re-ran both reproduction blocks.

### Note on the first draft

An earlier version of this report claimed the guard is not registered on OpenCode at all,
generalising from the `opencode` capability descriptor's "no lifecycle hook registration"
(`bin/lib/capability-registry.cjs:2975`). The evidence pass disproved it: `plugins/gsd-core.js:753`
runs the hook. The claim was removed rather than reworded. The descriptor wording is accurate about
the installer and misleading about execution, which is why the report rests on C4-C5 instead.
