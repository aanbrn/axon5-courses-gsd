---
phase: 261112-mry-write-a-commit-guard-plugin-commits-only
plan: 01
status: complete
subsystem: .opencode/plugins/commit-guard
tags: [opencode, plugin, git, guard, tooling, ci]
requires:
  - the allow entry for `git commit *` in opencode.jsonc, removed at bce778b
  - @opencode/plugin 2.2.7 as installed for phase-session-title
provides:
  - a local plugin that refuses `git commit` outside a GSD command and names the command to type
  - a token-walk commit detector with a 13-test table covering allow, deny and fail-open
  - a documented `COMMIT_GUARD=off` escape hatch and a CI gate on the detection table
affects:
  - .gitignore and .prettierignore now unignore a second plugin directory
  - AGENTS.md § Tooling, through the regenerated CONVENTIONS.md
tech-stack:
  added: []
  patterns:
    - pure decision module + a thin hook-registration module that only wires events to it
    - token-walk detection per shell statement, never a substring match
    - fail open on the guard's own errors, log once, fail closed only on a real commit
key-files:
  created:
    - .opencode/plugins/commit-guard/package.json
    - .opencode/plugins/commit-guard/package-lock.json
    - .opencode/plugins/commit-guard/tsconfig.json
    - .opencode/plugins/commit-guard/guard.ts
    - .opencode/plugins/commit-guard/index.ts
    - .opencode/plugins/commit-guard/guard.test.ts
    - .opencode/plugins/commit-guard/verify.ts
  modified:
    - .gitignore
    - .prettierignore
    - .github/workflows/ci.yml
    - docs/tooling.md
    - .planning/codebase/CONVENTIONS.md
    - AGENTS.md
decisions:
  - "The guard is pure logic in guard.ts; index.ts registers two hooks and holds no rule of its own"
  - "A commit option's value is skipped in the flag walk, so `git commit -m \"--dry-run\"` is still a commit"
  - "An absent or empty `agent` counts as no subagent; only a `gsd-` prefix allows a commit"
  - "`COMMIT_GUARD=off` returns from setup before either hook is registered, not only the tool hook"
commits:
  - 46e07e6 feat(commit-guard): refuse a commit outside a GSD command
  - 0275d2d feat(commit-guard): detect commits by token walk and fail open
  - 7d7586d chore(commit-guard): document the guard and gate it in CI
metrics:
  duration: "single pass"
  completed: 2026-11-12
actuals:
  tokens: 7671
  tasks: 3
  commits: 3
---

# Quick 261112-mry Plan 01: A commit guard that refuses commits outside a GSD command

`git commit` now has to happen inside a GSD command: a prompt that starts with `/gsd-*` records the
command for its session, a `gsd-*` subagent is trusted by name, and every other commit-shaped shell
call is refused with a message naming `/gsd-quick`, `/gsd-fast` and `/gsd-execute-phase`. The
detection is a token walk — `-C`, env prefixes, a full git path and chained statements are commits,
while `--dry-run`, `--help`, `commit-tree`, a `--grep commit` and an `echo` are not — and the guard's
own defects are logged once and allowed through instead of wedging the tool call.

## What changed

### Task 1 — the end-to-end refusal (commit 46e07e6)

`guard.ts` holds the pure half: the GSD-prompt regex, the per-session record, a shell-like tokenizer
and the decision returning `allow` or `deny` with the message to throw. `index.ts` registers two
hooks — `session.hook('prompt')` records or clears the command, `tool.hook('execute.before')` throws
the refusal — and accepts both the `bash` and the `shell` tool ids.

The four headline cases passed at this point: active command, `gsd-executor` agent, neither, and a
non-commit bash command in both states.

### Task 2 — real detection and the failure policy (commit 0275d2d)

Statements are split at `&&`, `||`, `;`, `|`, a newline and a spaced bare `&`, outside quotes; the
token walk then skips `NAME=value` prefixes, accepts `basename(token) === 'git'` and consumes git's
argument-taking global options, including the glued `-cfoo.bar=1` form. `git commit`'s own arguments
get a second walk so a message that looks like a flag is not read as one, and `--` ends the option
region.

`decideSafely` / `recordPromptSafely` swallow an internal error, log `commit-guard: …` once per plugin
instance and allow the call through; only a genuine commit with no GSD signal denies.

The table grew to 13 tests: 12 commit forms, 11 near-misses, the `shell` id, four non-shell tools,
five inputs without a command string, the empty-agent case, prompt clearing, and an injected throw
that yields allow-with-one-log.

### Task 3 — off switch, runbook, CI (commit 7d7586d)

`setup` returns before registering anything when `process.env.COMMIT_GUARD === 'off'`. `verify.ts`
prints the four verdicts and sets a non-zero exit code on a mismatch, needing no session or runtime.
`docs/tooling.md` gains `## Commit guard` after `## Session titles` — behaviour, both allow
conditions, what is refused, the two off switches, the three verification commands and the honest
limit. CI runs the detection table next to the session-title test, and the plugin rule reaches
`AGENTS.md` through `CONVENTIONS.md` regeneration.

## Verification

```
$ npm --prefix .opencode/plugins/commit-guard test
ℹ tests 13
ℹ pass 13
ℹ fail 0

$ npm --prefix .opencode/plugins/commit-guard run typecheck
> tsc --noEmit          (no output — clean against @opencode/plugin 2.2.7)

$ npm --prefix .opencode/plugins/commit-guard run verify
ok   active GSD command: expected allow, got allow
ok   gsd-executor subagent: expected allow, got allow
ok   neither a command nor a gsd- agent: expected deny, got deny
ok   non-commit bash command: expected allow, got allow

$ ./scripts/format-check.sh
All matched files use Prettier code style!

$ git check-ignore -q .opencode/plugins/commit-guard/{guard.ts,verify.ts,index.ts}   # exit 1 each
tracked  guard.ts / verify.ts / index.ts
ignored  node_modules
```

`node .opencode/gsd-core/bin/gsd-tools.cjs query generate-claude-md --output AGENTS.md --force`
reported `Generated 6/6 sections`, and `AGENTS.md:350` carries the plugin rule.

An ad-hoc probe confirmed the documented residual too: `sh -c "git commit"` and `\git commit` are
allowed — the guard is an authoring discipline, not a security control.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] The unignore lines moved from Task 3 into Task 1**
- **Found during:** Task 1, before the first commit.
- **Issue:** `.gitignore` ignored `.opencode/plugins/*` with a negation only for `phase-session-title`,
  so `git add` of the new plugin directory failed. Task 1 could not commit without them.
- **Fix:** the three `.gitignore` lines and the two `.prettierignore` lines were added in Task 1,
  mirroring the existing plugin exactly; Task 3 did not repeat them.
- **Files modified:** `.gitignore`, `.prettierignore`
- **Commit:** 46e07e6

**2. [Rule 2 - Correctness] A commit option's value is skipped in the flag walk**
- **Found during:** Task 2.
- **Issue:** checking `git commit`'s arguments for `--dry-run` verbatim reads the message in
  `git commit -m "--dry-run"` as a flag and allows a real commit.
- **Fix:** the walk skips the value of every commit option that takes one, and stops at `--`; the
  case is a table row.
- **Files modified:** `.opencode/plugins/commit-guard/guard.ts`, `guard.test.ts`
- **Commit:** 0275d2d

**3. [Plan wording] `COMMIT_GUARD=off` skips both hooks**
- **Found during:** Task 3.
- **Issue:** the plan says "skip registering the tool hook".
- **Fix:** `setup` returns before registering either hook. The prompt record is read only by the tool
  hook, so recording it with the guard off would be dead work; the behaviour the plan asks for is
  unchanged.
- **Files modified:** `.opencode/plugins/commit-guard/index.ts`
- **Commit:** 7d7586d

## Known Stubs

None. The guard is wired end to end: both hooks are registered, the decision is reached on every
shell call, and `verify.ts` exercises the real function.

## Threat Flags

None beyond the plan's register. The detector matches T-261112-01 (token walk, not substring), the
failure policy matches T-261112-02 (log once, allow through), and no package was added — the three
devDependencies are the pins already used by `phase-session-title`, and `package-lock.json` was
generated from them.

## Deliberate exclusions

- `.planning/quick/**` and `.planning/STATE.md` are left uncommitted: the orchestrator owns the docs
  commit.
- The one manual check the plan lists — `/reload`, a plain prompt must be refused, `/gsd-quick` must
  commit — needs a live session and is not run here.

## Self-Check: PASSED

- All seven plugin files and the SUMMARY exist on disk.
- All three commits (`46e07e6`, `0275d2d`, `7d7586d`) are ancestors of `HEAD`.
- The working tree is clean except the untracked SUMMARY, which the orchestrator commits.

## After execution: the guard refused its own docs commit

The manual check above did not have to wait. The plugin loaded mid-session, so the prompt that started
this quick task arrived before the hook was registered, and the session had no record. The guard then
refused the orchestrator's docs commit:

```
commit-guard: git commit runs only inside a GSD command. Type /gsd-quick, /gsd-fast or
/gsd-execute-phase and let that workflow make the commit.
```

That is the blocker working as designed on a case it could not see: a late-loaded plugin and a resumed
session look exactly like an arbitrary one. Fixed in `fb6b0ee` — the state now records which sessions
have reported a prompt and refuses only those whose latest prompt is a known non-GSD message. An
unknown session is allowed, because the alternative is blocking the workflow that is running.

The detection table grew to 15 tests and `verify.ts` to five verdicts; both cover the new case.
