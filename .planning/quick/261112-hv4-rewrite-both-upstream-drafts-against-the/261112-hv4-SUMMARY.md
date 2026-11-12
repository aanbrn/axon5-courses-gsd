---
phase: 261112-hv4-rewrite-both-upstream-drafts-against-the
plan: 01
status: complete
subsystem: docs/upstream-reports
tags: [upstream-reports, documentation, review-gate]
requires:
  - 0001 and 0002 drafts as committed at e95542e
provides:
  - publication-ready 0001 and 0002 drafts that answer every blocking review reason
  - a recorded HOLD with re-run and owner still pending
affects:
  - docs/upstream-reports/README.md
tech-stack:
  added: []
  patterns: [claim-to-file:line evidence, self-contained Draft fence]
key-files:
  created: []
  modified:
    - docs/upstream-reports/0001-gsd-workflow-guard-advisory.md
    - docs/upstream-reports/0002-gsd-advisory-command-form.md
    - docs/upstream-reports/README.md
decisions:
  - "0001 C7 rests on the schema's self-description plus workflows/execute-plan.md:84, not on a bare key list"
  - "0002 answers the duplicate risk with a C5 row for #376 (CLOSED/COMPLETED 2026-05-28) and names the OpenCode residual"
  - "runtime-slash.cjs citations start at :11, not :12 — line 11 carries the hyphen-unification statement"
commits:
  - 33c6ff5 docs(upstream-reports): rewrite 0001 against the review verdicts
  - 615ecfd docs(upstream-reports): rewrite 0002 against the review verdicts
  - 3015d15 docs(upstream-reports): correct both register symptoms
metrics:
  duration: "not measured (single pass)"
  completed: 2026-11-12
actuals:
  tokens: 6859
  tasks: 4
  commits: 3
---

# Quick 261112-hv4 Plan 01: Rewrite both upstream drafts against the review verdicts

Both held drafts now answer the independent review at the claim level: 0001's C2/C3/C7 match what the
cited lines show, all intent attribution is gone, and both reproduction blocks run as pasted and sit
inside the `## Draft` fence; 0002 drops the false Claude Code command-name claim, links #376 as prior
art with its state and date, and makes its probe runnable. Neither report claims a pass that has not
happened.

## What changed

### 0001 — workflow guard advisory (commit 33c6ff5)

- **Summary** rebuilt from facts: guard off by default, advisory on enable, no schema key that
  enforces workflow entry, an either/or request. `unbacked`, "the only option that looks like it
  enforces" and "a capability gap, not a bug" are gone.
- **C1** spells out both citations — `config-defaults.manifest.json:82` (`"workflow_guard": false`)
  and `settings.md:509` (documented, no default statement).
- **C2** narrowed to a top-level `Edit` that is neither under `.planning/` nor an exempt filename,
  with `:326` (subagent), `:346-355` (`allowedPatterns`) and `:365-372` (payload) named.
- **C3** rewritten to the source: `/^(worktree-)?agent-/` at `:112` used at `:311`,
  `decision: "block"` with exit 2 at `:131-136`, and the fail-closed path at `:128-130`, `:153`,
  `:385-386`.
- **C4** scoped to "delivered on OpenCode as well"; **C6** calls the rule a directive with a
  user-bypass carve-out and cites `AGENTS.md:434` plus `profile-output.cjs:202-217`.
- **C7** rebuilt on `config-schema.manifest.json:2` (self-description) and `:5-7` (names only) plus
  `execute-plan.md:84`.
- **Reproduction** holds canonical blocks A and B verbatim, with the exemption probe and the
  adapter-run caveat (`plugins/gsd-core.js:753-757`, `:360-366`, `:368-389`), and both blocks are
  repeated inside the `## Draft` fence.

### 0002 — advisory command form (commit 615ecfd)

- The "Claude Code command names" claim is deleted, not reworded; the colon form is now described as
  the legacy namespace, sourced to `runtime-slash.cjs:11-15`.
- **C2** carries line numbers: `commands/gsd-fast.md:1-9` (no `name:` field) and `:24`,
  `commands/gsd-quick.md:130`.
- **C3** cites `runtime-slash.cjs:11-17`, `:43`, `:228`, `profile-output.cjs:202-217` and
  `runtime-artifact-conversion.cjs:492`, `:3207`; the `#3584`/`#2808` parenthetical quotes the comment
  and does not link either.
- **C4** is the observation that the hook does not reference the helper (`grep` returns no match,
  exit 1) and that the two outputs in one install disagree.
- **C5** is new: `#376`, CLOSED / COMPLETED on 2026-05-28, plus the OpenCode residual.
- The Draft gains the "what is new relative to #376" paragraph, the reproduction and observed
  output, the adapter caveat, the suggested fix and the neutral "not visible from the installed
  tree" sentence.

### Register (commit 3015d15)

- 0001 symptom → "no configuration key enforces that a change starts from a GSD command".
- 0002 symptom → the hardcoded legacy `/gsd:<cmd>` form against the hyphen form
  `bin/lib/runtime-slash.cjs` declares canonical, with #376 named as the Claude-only closure.
- Both rows stay `not filed`; both links resolve.

## Review state recorded

| Report | Evidence (agent) | Independent (codex, `gpt-6.1-sol`, high) | Re-run | Owner |
| ------ | ---------------- | ---------------------------------------- | ------ | ----- |
| 0001   | passed 2026-11-12 | HOLD 2026-11-12 (five reasons)           | pending | pending |
| 0002   | passed 2026-11-12 | HOLD 2026-11-12 (four reasons)           | pending | pending |

The HOLD rows describe the text before this rewrite; each table carries the line that says so.

## Verification evidence

Format gates (run before the final commit):

```text
$ ./scripts/format.sh
docs/upstream-reports/0001-gsd-workflow-guard-advisory.md 11ms (unchanged)
docs/upstream-reports/0002-gsd-advisory-command-form.md 8ms (unchanged)
docs/upstream-reports/README.md 5ms (unchanged)

$ ./scripts/format-check.sh
Checking formatting...
All matched files use Prettier code style!
```

Line-resolution sweep — every `file:line` in both reports re-read with
`sed -n '<range>p' <install path>`; 37 assertions, all `OK`, `FAIL=0`. The set covers
`gsd-workflow-guard.js` 7, 8, 11, 112, 128-130, 128-136, 153, 311, 326, 346-355, 365-372, 369,
385-386; `plugins/gsd-core.js` 360-366, 368-389, 753-757; `runtime-slash.cjs` 11-17, 11-15, 12-14,
43, 228; `profile-output.cjs` 202-217; `runtime-artifact-conversion.cjs` 492, 3207;
`execute-plan.md` 84; `config-schema.manifest.json` 2 and 5-7; `config-defaults.manifest.json` 82;
`settings.md` 509; `commands/gsd-fast.md` 1-9 and 24; `commands/gsd-quick.md` 130; `AGENTS.md`
429-432 and 434; `capability-registry.cjs` 2975.

Canonical block A (advisory), run from the repo root — stdout matches the recorded line verbatim
(`grep -cF` → 2 occurrences in 0001, reproduction and Draft), `exit=0`, no `decision` field. The
exemption probe prints no advisory for `.planning/STATE.md`, `AGENTS.md` or a `session_type: "task"`
payload.

Canonical block B (force-add), both legs:

```text
stdout: {"decision":"block","code":"WORKTREE_AGENT_FORCE_ADD_FORBIDDEN","origin":"force-add-detected","reason":"agent/worktree-agent branches must not run git add -f or git add --force. ..."}
stderr: agent/worktree-agent branches must not run git add -f or git add --force. ...
exit=2
```

`feature-probe` prints nothing and exits `0`.

Leakage detector: `grep -rn '/Users/\|afanasyev\|Bearer\|token'` over the two reports returns nothing
(exit 1). Over the whole directory, `/Users/` and `afanasyev` return nothing; the only `token`
occurrence is the README's own rule sentence listing forbidden content. No generated command file is
attached.

Duplicate check: `gh issue view 376 --repo open-gsd/gsd-core` returns
`state=CLOSED`, `stateReason=COMPLETED`, `closedAt=2026-05-28T03:12:16Z` — matching the C5 row and
the Draft sentence.

`.planning/codebase/CONVENTIONS.md` is untouched (`git status --short` shows only the three report
files).

Task verify blocks from PLAN.md, run after formatting:

```text
--- T1 ---
PASS
--- T2 ---
PASS
--- T3 ---
docs/upstream-reports/0001-gsd-workflow-guard-advisory.md:2
docs/upstream-reports/0002-gsd-advisory-command-form.md:2
PASS
--- T4 automated ---
Checking formatting...
All matched files use Prettier code style!
PASS
```

## Deviations from Plan

### 1. [Rule 1 - Bug] runtime-slash.cjs citation range widened from `:12-17` to `:11-17`

- **Found during:** Task 4 line sweep
- **Issue:** `:12` begins mid-sentence ("form (`name: gsd-<cmd>`). The legacy colon form…"); the
  hyphen-unification statement the summary attributes to the module is on `:11`. A reviewer
  re-reading `:12-17` would not see "installs register under the hyphen form".
- **Fix:** Summary and C3 cite `:11-17`; the Draft's legacy-namespace sentence cites `:11-15`; the
  quoted "no longer routable" text sits at `:12-14`.
- **Files modified:** `docs/upstream-reports/0002-gsd-advisory-command-form.md`
- **Commit:** 615ecfd

### 2. [Rule 1 - Bug] block B output recorded per stream

- **Found during:** Task 4 reproduction re-run
- **Issue:** Recording the interleaved terminal line (`…untracked."}exit=2`) appends `exit=2` to the
  stdout payload, which is not what the hook does — stdout and stderr are separate writes and
  neither ends with a newline.
- **Fix:** the recorded block names `stdout:`, `stderr:` and `exit=2` separately; both payloads are
  verbatim.
- **Files modified:** `docs/upstream-reports/0001-gsd-workflow-guard-advisory.md`
- **Commit:** 33c6ff5

## Deferred / not done

- The independent re-run and the owner pass are deliberately still `pending`; nothing in this plan
  may claim them.
- Filing the issues is an operation outside this repository and needs an explicit request.
- `.planning/quick/**` and `.planning/STATE.md` are left uncommitted by design; the orchestrator owns
  that docs commit.

## Known stubs

None.

## Self-Check: PASSED

- `docs/upstream-reports/0001-gsd-workflow-guard-advisory.md` — FOUND
- `docs/upstream-reports/0002-gsd-advisory-command-form.md` — FOUND
- `docs/upstream-reports/README.md` — FOUND
- Commits 33c6ff5, 615ecfd, 3015d15 — all ancestors of HEAD

