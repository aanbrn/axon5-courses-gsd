# 0002 — Comment for #6054: the hooks bundle ships an unrewritten command form

|            |                                                                      |
| ---------- | -------------------------------------------------------------------- |
| Type       | Comment on an open maintenance task, not a new issue                 |
| Target     | [#6054](https://github.com/open-gsd/gsd-core/issues/6054)            |
| Dependency | `@opengsd/gsd-core`                                                  |
| Version    | 1.20.1                                                               |
| Ecosystem  | npm                                                                  |
| Upstream   | https://github.com/open-gsd/gsd-core                                 |
| Status     | `not filed`                                                          |
| Disclosure | Drafted with assistance from an AI agent and reviewed before filing. |
| Found      | 2026-11-12                                                           |

## Why a comment here, not a new issue

The defect class is already tracked twice: #376 (closed, COMPLETED 2026-05-28) names this hook and
this literal, and #4859 swept `bin/lib`. #6054 is the open phase of #4909 that makes the install
rewrite checkable — "no unrewritten command form may survive into any shipped artifact, compiled
`bin/lib` included". What it does not name is the hooks bundle, and that is exactly the artifact
observed here.

A new issue would add a fourth entry for the same class. The missing information belongs on the task
that will implement the check.

## What the comment asserts

| #   | Claim                                                                                            | Evidence                                                            |
| --- | ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------- |
| C1  | The installed hook carries the colon form at the advisory.                                       | `.opencode/hooks/gsd-workflow-guard.js:369`                         |
| C2  | The header comment repeats the colon form.                                                       | `.opencode/hooks/gsd-workflow-guard.js:8`                           |
| C3  | This install carries the hyphenated command files.                                               | `.opencode/commands/gsd-fast.md`, `.opencode/commands/gsd-quick.md` |
| C4  | The advisory fires on a write-like call that is not exempt, and emits it as `WORKFLOW_ADVISORY`. | `hooks/gsd-workflow-guard.js:341`, `:346-352`, `:365-372`           |
| C5  | A runtime-aware helper exists and the instruction generator already uses it.                     | `bin/lib/runtime-slash.cjs:228`; `bin/lib/profile-output.cjs:207`   |
| C6  | #6054 asks for an artifact-level check and names compiled `bin/lib`.                             | the issue body, "What is the maintenance task?" and "Done when"     |

The comment claims nothing about command registration, routing failure, or other runtimes. It
reports what one installed tree contains and where the check described in #6054 would catch it.

## Draft

```text
Checking a project-scope OpenCode install of 1.20.1: the hooks bundle ships an unrewritten colon form,
and it does not look like an artifact the compiled bin/lib sweep would catch.

Evidence from the installed tree:

- .opencode/hooks/gsd-workflow-guard.js:369 -
  'Consider using /gsd:fast for trivial fixes or /gsd:quick for larger changes '
- .opencode/hooks/gsd-workflow-guard.js:8 - the header comment repeats the colon form
- the installed commands are commands/gsd-fast.md and commands/gsd-quick.md

The advisory fires on a write-like tool call that is not exempt (.planning/ paths and a short allow-list
of config/doc filenames), so on this runtime a user is told to run a command form the install does not
carry.

Relevance to this phase: "Done when" says no unrewritten command form survives into any shipped
artifact. The hooks bundle appears to be outside the manifest - the phase text names compiled bin/lib,
not hooks/. If so, adding hooks/*.js catches this class and needs no runtime-specific logic: the check
would go red on the file above.

Two fixes at the emission site, either is fine by me:

- call formatGsdSlashFor(cwd, 'fast') / 'quick' - bin/lib/runtime-slash.cjs:228, the helper the
  instruction generator already uses (bin/lib/profile-output.cjs:207);
- or take the names from the same rendered source as the instruction file, so the advisory and the
  instructions cannot disagree.

Scope: one project-scope OpenCode install at 1.20.1. I did not test other runtimes, or a fresh install
from the published package, so I cannot say whether the file is rewritten at install time for some
runtimes and not this one.

Found while reading the guard for another question. No production incident - a hobby project using GSD
for a learning exercise.

Drafted with assistance from an AI agent and reviewed before filing.
```

## Review

| Pass                                     | Verdict                                                                               | Date       |
| ---------------------------------------- | ------------------------------------------------------------------------------------- | ---------- |
| Evidence (agent)                         | passed — C1-C6 re-read at the cited line or file on 2026-11-12                        | 2026-11-12 |
| Independent (codex, `gpt-6.1-sol`, high) | pending — this text has not been reviewed; it is shorter than the retired issue draft | —          |
| Owner                                    | pending                                                                               | —          |

## History

This file began as a draft issue. Four independent passes held it, every time on the same point: it
could not establish, without install-level evidence, that the hyphenated names are what the runtime
registers, and without that it read as a duplicate of #376. Retargeting as a comment removes the
claim that needed the evidence — #6054 already asks for the check, and the comment only adds the
artifact class it does not name.
