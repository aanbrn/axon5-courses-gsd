---
phase: 261112-mry-write-a-commit-guard-plugin-commits-only
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - .opencode/plugins/commit-guard/package.json
  - .opencode/plugins/commit-guard/package-lock.json
  - .opencode/plugins/commit-guard/tsconfig.json
  - .opencode/plugins/commit-guard/index.ts
  - .opencode/plugins/commit-guard/guard.ts
  - .opencode/plugins/commit-guard/guard.test.ts
  - .opencode/plugins/commit-guard/verify.ts
  - .gitignore
  - .prettierignore
  - .github/workflows/ci.yml
  - docs/tooling.md
  - .planning/codebase/CONVENTIONS.md
  - AGENTS.md
autonomous: true
requirements:
  - QUICK-261112-MRY

estimate:
  tokens: 55000
  raw_tokens: 45000
  tasks: 3
  confidence: low

must_haves:
  truths:
    - "A `git commit` from an agent is refused unless the session's latest prompt is a GSD command or the payload came from a `gsd-*` subagent."
    - "A refusal names the GSD command to type instead of only reporting that the call was denied."
    - "The guard never widens its block beyond commits — every other bash command, and every other tool, runs unchanged."
    - "A defect inside the guard is logged once and then skipped, so the session keeps working."
    - "The owner can turn the guard off without editing code, and the switch is documented."
  artifacts:
    - ".opencode/plugins/commit-guard/guard.ts — pure decision logic: statement split, git token walk, prompt state"
    - ".opencode/plugins/commit-guard/index.ts — the two hook registrations"
    - ".opencode/plugins/commit-guard/guard.test.ts — node:test table covering allow, deny, and fail-open"
    - ".opencode/plugins/commit-guard/verify.ts — prints the four verdicts"
    - "docs/tooling.md § Commit guard — runbook: behaviour, block cases, off switch"
  key_links:
    - "prompt hook → session state map → bash hook decision (the only signal a GSD command started)"
    - "tool payload `agent` → `gsd-` prefix check (subagents never receive a prompt)"
    - "`.gitignore` + `.prettierignore` unignore lines → the plugin being tracked and linted at all"
---

<objective>
Ship a local OpenCode plugin that refuses `git commit` unless the work is running inside a GSD
command, and says which command to use instead.

Purpose: the per-commit permission prompt was removed because it fired on every task commit and
taught approving without reading (see the comment in `opencode.jsonc`). Removing it also removed the
only distinction the owner cares about — commits made by the process versus commits made arbitrarily.
This plugin restores that distinction without a prompt.

Output: a testable plugin under `.opencode/plugins/commit-guard/`, a documented off switch, and the
companion tracking/formatting/CI wiring.
</objective>

<execution_context>
@/Users/afanasyev/Development/axon5-courses-gsd/.opencode/gsd-core/workflows/execute-plan.md
@/Users/afanasyev/Development/axon5-courses-gsd/.opencode/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/STATE.md
@AGENTS.md

# The plugin this one mirrors: package pins, tsconfig, hook registration style, node:test layout.
@.opencode/plugins/phase-session-title/index.ts
@.opencode/plugins/phase-session-title/package.json
@.opencode/plugins/phase-session-title/tsconfig.json
@.opencode/plugins/phase-session-title/phase.test.ts

# Token-walk reference. Read for the algorithm only — do not import it (gitignored generated tree)
# and do not modify it.
@.opencode/gsd-core/bin/lib/token-scanner.cjs
@.opencode/hooks/lib/git-cmd.js

# Where the allow entry being replaced lives, and the doc/CI spots that must carry the new plugin.
@opencode.jsonc
@docs/tooling.md
@.github/workflows/ci.yml
</context>

<tasks>

<task type="tracer">
  <name>End-to-end refusal: prompt state → bash hook → throw</name>
  <files>.opencode/plugins/commit-guard/package.json, .opencode/plugins/commit-guard/tsconfig.json, .opencode/plugins/commit-guard/guard.ts, .opencode/plugins/commit-guard/index.ts, .opencode/plugins/commit-guard/guard.test.ts</files>
  <action>
Create the plugin directory with `package.json` (`"private": true`, `"type": "module"`, scripts `test` = `node --test guard.test.ts`, `typecheck` = `tsc --noEmit`, `verify` = `node verify.ts`), devDependencies pinned exactly as `phase-session-title`: `@opencode/plugin` `2.2.7`, `@types/node` `^24.0.0`, `typescript` `~7.0.2`. Copy `phase-session-title/tsconfig.json` verbatim — Node 24 strips types, so `guard.ts` must use erasable syntax only (no enums, no parameter properties, no namespaces) and import siblings as `./guard.ts`.

Write `guard.ts` as the pure half — no `@opencode/plugin` import, so `node:test` runs it without the runtime. It exports one decision function taking a plain input object (session id, prompt text, tool name, agent, tool input) plus the recorded-session state, returning a discriminated result: allow, or deny with the message to throw. In this task implement only the ONE path: `prompt` text matched with `/^\s*\/(gsd-[a-z0-9-]+)/` records the command per session, a non-matching prompt clears it, and a commit-shaped bash command is denied unless that record exists or `agent` starts with `gsd-`. Keep the commit test deliberately simple here (`git` then `commit` in the token walk); the forms that need real parsing come in Task 2.

Write `index.ts` with `Plugin.define({ id: 'commit-guard', async setup(ctx) {...} })`: register `ctx.session.hook('prompt')` to record/clear the active command, and `ctx.tool.hook('execute.before')` to deny. The tool hook must return early for every non-bash tool and for any input object without a string `command`. Denial is `throw new Error(message)` from the callback — the pattern at `.opencode/plugins/gsd-core.js:361-365`. Message text: commits run only inside a GSD command, and it names `/gsd-quick`, `/gsd-fast` and `/gsd-execute-phase` as the alternatives to type. Match on tool name `bash` AND `shell` — `gsd-core.js:136-148` maps both, so the OpenCode 2.x id may be either.

Write `guard.test.ts` with the four headline cases: active GSD command allows a commit, `gsd-executor` agent allows a commit, neither denies it, and a non-commit bash command is allowed in both states. Each test asserts the verdict and, for the deny cases, that the message names a GSD command.

Then install and check: `npm install` inside the directory (writes `package-lock.json`; commit it, as `phase-session-title` does).
  </action>
  <verify>
    <automated>npm --prefix .opencode/plugins/commit-guard test && npm --prefix .opencode/plugins/commit-guard run typecheck</automated>
  </verify>
  <done>Both hooks are registered, the four headline cases pass, and `tsc --noEmit` is clean against `@opencode/plugin` 2.2.7.</done>
</task>

<task type="auto">
  <name>Harden commit detection and fail open on our own errors</name>
  <files>.opencode/plugins/commit-guard/guard.ts, .opencode/plugins/commit-guard/guard.test.ts, .opencode/plugins/commit-guard/index.ts</files>
  <action>
Extend `guard.ts` with a real detector. Split the command into statements on `&&`, `||`, `;`, `|` and newline, outside quotes — the shared scanner (`token-scanner.cjs`) does NOT split on control operators (`git commit && echo` yields one token `&&`), so the split is ours and the token walk runs per segment. Then, per segment, walk tokens the way `skipToSubcommand` does: skip leading `NAME=value` env assignments, accept the executable when `basename(token) === 'git'` (covers `/usr/bin/git`), consume git global options that take an argument (`-C`, `-c`, `--git-dir`, `--work-tree`, `--namespace`, `--exec-path`, …) as pairs plus glued `-cK=V`, and stop at the subcommand.

A segment is a guarded commit when that walk lands on `commit` and its own flags do not include `--dry-run` or `--help` (nor `-h`). `git commit --dry-run`, `git commit --help`, `git log`, `git commit-tree` and a `-m` message containing the words are all not commits. Quote handling comes from the tokenization, never a substring test — a naive `includes('git commit')` inside a message or an `echo` must not match.

Wire the failure policy: wrap the whole decision in a `try`/`catch` in the tool hook. On an unexpected throw, log once with a `commit-guard:` prefix and return — fail OPEN, so a broken guard cannot wedge any tool call, bash included. Fail CLOSED only for the intended case: a commit-shaped command with no active GSD command and an `agent` that does not start with `gsd-`. An `agent` that is absent or empty is treated as "not a subagent", never as an unknown that skips the check.

Extend `guard.test.ts` into a table: `git commit -m x`, `git -C /repo commit -m x`, `GIT_AUTHOR_NAME=x git commit`, `/usr/bin/git commit`, `true && git commit -m x`, `git add . ; git commit -m x` — all denied with no active command; `git commit --dry-run`, `git commit --help`, `git status`, `git commit-tree`, `echo "git commit"`, `git log --grep commit` — all allowed; and the same commit forms allowed with an active command or a `gsd-executor` agent. Add one test that a decision function which throws internally yields allow-with-log rather than a block.
  </action>
  <verify>
    <automated>npm --prefix .opencode/plugins/commit-guard test && npm --prefix .opencode/plugins/commit-guard run typecheck</automated>
  </verify>
  <done>Every form in the table gets the documented verdict; the only path that throws is a real commit with no GSD context; an injected internal error is logged once and allowed through.</done>
</task>

<task type="auto">
  <name>Track, document, and expose the guard</name>
  <files>.gitignore, .prettierignore, .github/workflows/ci.yml, docs/tooling.md, .planning/codebase/CONVENTIONS.md, .opencode/plugins/commit-guard/verify.ts, AGENTS.md</files>
  <action>
Add the off switch: read `process.env.COMMIT_GUARD` once in `setup` and skip registering the tool hook when it is `off`. Document it as the escape hatch (`COMMIT_GUARD=off opencode`) alongside removing the directory, which stays the durable one.

Track the plugin. In `.gitignore`, mirror the three `phase-session-title` lines exactly, next to them: unignore the directory, its contents, and re-ignore `node_modules/`. In `.prettierignore` mirror the matching pair there too — without it the new files are never checked, which would make `format-check` green for the wrong reason.

Add `.opencode/plugins/commit-guard/verify.ts`: import the decision function, feed it the four cases (active command, `gsd-executor` agent, neither, non-commit bash), print one line per case with the verdict, and exit non-zero if any verdict differs from the expectation. It must not need a session, a server, or the plugin runtime.

Add a `## Commit guard` section to `docs/tooling.md` after `## Session titles`, matching that section's voice: what it does and why it exists (the removed permission prompt drew no distinction, `opencode.jsonc` records that decision); the two allow conditions; what is refused; that the refusal names the command to use; the `COMMIT_GUARD=off` switch and directory removal; the verification commands `npm --prefix .opencode/plugins/commit-guard test`, `run typecheck` and `run verify`; and the honest limit — the only signal a command started is prompt text, so a session that never received the command prompt cannot commit, and a subagent's commits are trusted by its `gsd-` name.

Add one line to `.planning/codebase/CONVENTIONS.md` § Tooling, next to the `.opencode/` bullet, stating that hand-written plugins live under `.opencode/plugins/`, are gitignored except for explicit unignore lines, and that `git commit` is gated by the `commit-guard` plugin with `COMMIT_GUARD=off` as the escape hatch. This is the line `AGENTS.md` inherits on regeneration.

Add a CI step in the `format` job of `.github/workflows/ci.yml`, directly after the session-title step at `:46-47`: `node --test .opencode/plugins/commit-guard/guard.test.ts`, with a comment matching the existing one.

Finish with the gates: `npm --prefix .opencode/plugins/commit-guard run verify`, then `./scripts/format.sh` and `./scripts/format-check.sh` at the root, then `node .opencode/gsd-core/bin/gsd-tools.cjs query generate-claude-md --output AGENTS.md --force`.
  </action>
  <verify>
    <automated>npm --prefix .opencode/plugins/commit-guard run verify && ./scripts/format-check.sh</automated>
    <human-check>AGENTS.md § Tooling carries the plugin line after regeneration, and `git status` shows the plugin files as tracked (not ignored).</human-check>
  </verify>
  <done>`verify.ts` prints four verdicts and exits 0; `format-check.sh` is green at the root; `git check-ignore` reports the plugin sources as tracked while `node_modules/` stays ignored; `AGENTS.md` mentions the guard.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| agent → bash tool | An agent-authored command string reaches the shell; the guard sits between the two |
| prompt text → session state | The only signal that a GSD command started; it is inferred, not attested |

## STRIDE Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation Plan |
|-----------|----------|-----------|----------|-------------|-----------------|
| T-261112-01 | Tampering | `guard.ts` commit detector | medium | mitigate | Token-walk per statement, not substring matching; table tests for `-C`, env prefixes, full paths, chained forms, and the `--dry-run`/`--help` exclusions. Residual: an obfuscated invocation (`sh -c`, an alias, a script) is out of reach and is accepted as such — the guard is an authoring discipline, not a security control. |
| T-261112-02 | Denial of service | `index.ts` tool hook | high | mitigate | Any unexpected error inside the decision is caught, logged once, and allowed through; non-bash tools and inputs without a string `command` return before the decision runs. |
| T-261112-03 | Elevation of privilege | `COMMIT_GUARD=off` | low | accept | The off switch belongs to the owner and is documented; removing a guard on your own machine is not a threat this plugin can police. |
| T-261112-04 | Repudiation | refusal message | low | mitigate | The message names the GSD commands to type, so a refused agent has an action, not just a denial. |
| T-261112-SC | Tampering | `npm install` in the plugin directory | high | mitigate | No new packages: the three devDependencies are the exact pins already installed for `phase-session-title`, and the plugin adds no runtime dependency at all. |
</threat_model>

<verification>
- `npm --prefix .opencode/plugins/commit-guard test` — the detection table and the four headline cases.
- `npm --prefix .opencode/plugins/commit-guard run typecheck` — the hook signatures against `@opencode/plugin` 2.2.7.
- `npm --prefix .opencode/plugins/commit-guard run verify` — the four verdicts, printed.
- `./scripts/format-check.sh` — repository formatting, including the newly unignored plugin.
- `git check-ignore -v .opencode/plugins/commit-guard/guard.ts` — no output (tracked); the same for `node_modules/` — ignored.
- Manual, once, by the owner: `/reload` in OpenCode, then a plain prompt asking the agent to commit — expect a refusal naming `/gsd-quick`; then `/gsd-quick` on a trivial change — expect the commit to land.
</verification>

<success_criteria>
- A commit is allowed only under an active GSD command prompt or from a `gsd-*` subagent.
- The refusal is actionable and names the command to type.
- No other tool call, and no other bash command, changes behaviour.
- The guard's own failures are logged once and never block.
- The off switch is documented in `docs/tooling.md`, and the rule survives into `AGENTS.md`.
- The plugin is tracked, formatted, and tested in CI like `phase-session-title`.
</success_criteria>

<output>
Create `.planning/quick/261112-mry-write-a-commit-guard-plugin-commits-only/261112-mry-SUMMARY.md` when done
</output>
