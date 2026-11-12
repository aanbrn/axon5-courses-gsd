# 0002 — Comment for #6054: a manifest-listed artifact for the positive control

|            |                                                                      |
| ---------- | -------------------------------------------------------------------- |
| Type       | Comment on an open maintenance task, not a new issue                 |
| Target     | [#6054](https://github.com/open-gsd/gsd-core/issues/6054)            |
| Role       | A ready-made red case for the positive control that task describes   |
| Dependency | `@opengsd/gsd-core`                                                  |
| Version    | 1.20.1                                                               |
| Ecosystem  | npm                                                                  |
| Upstream   | https://github.com/open-gsd/gsd-core                                 |
| Status     | `not filed`                                                          |
| Disclosure | Drafted with assistance from an AI agent and reviewed before filing. |
| Found      | 2026-11-12                                                           |

## Why a comment here, not a new issue

The defect is already reported: #376 names this hook and this literal, and #4859 is the compiled
`bin/lib` precedent that #6054 absorbs. A new issue would be the fourth entry for one class.

#6054 asks for a positive control — "an artifact carrying an unrewritten token drives the check
red". The install already contains one, and the install manifest already tracks it. That is the
whole contribution, and it needs no claim about command registration or about the other runtimes.

## What the comment asserts

| #   | Claim                                                          | Evidence                                                                                                                                  |
| --- | -------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| C1  | The installed hook carries the colon form at the advisory.     | `.opencode/hooks/gsd-workflow-guard.js:369`                                                                                               |
| C2  | The header comment repeats the colon form.                     | `.opencode/hooks/gsd-workflow-guard.js:8`                                                                                                 |
| C3  | The install's file manifest lists that hook with its checksum. | `.opencode/gsd-file-manifest.json` — `files["hooks/gsd-workflow-guard.js"]`; manifest `version` 1.20.1, `runtime` opencode, `scope` local |
| C4  | The OpenCode plugin invokes it, so it is a live artifact.      | `.opencode/plugins/gsd-core.js:756-758`                                                                                                   |
| C5  | #6054 asks for a positive control of exactly that shape.       | the issue body, "Proposed work"                                                                                                           |

Nothing here claims that the checker behaves a certain way, that `hooks/` is outside any manifest,
or that the hyphenated form is what the runtime registers.

## Draft

```text
Context only - this is a ready-made fixture for the positive control, not a new defect report.

In a project-scope OpenCode install of 1.20.1, the install's own file manifest lists the hooks bundle,
and that bundle still carries the unrewritten form:

- gsd-file-manifest.json (version 1.20.1, runtime opencode, scope local) tracks
  "hooks/gsd-workflow-guard.js" with its SHA-256
- the file as installed contains:
    hooks/gsd-workflow-guard.js:369 -
      'Consider using /gsd:fast for trivial fixes or /gsd:quick for larger changes '
    hooks/gsd-workflow-guard.js:8 - the header comment repeats /gsd:quick and /gsd:fast
- the plugin invokes this hook for write-like tools and Bash (plugins/gsd-core.js:756-758), so it is
  reachable at runtime

That artifact is already in the manifest and still carries an unrewritten token - the shape the
positive control is described as needing. It could be used as the red case without authoring a
separate fixture.

The class is already reported: #376 names this hook and this literal, and #4859 is the compiled
bin/lib precedent that this phase absorbs. The only new information here is that the artifact is
already tracked by the manifest and can be reused.

Scope: one project-scope OpenCode install at 1.20.1. I did not verify a fresh install from the
published package, so I cannot say whether this is rewrite-at-install being skipped for this runtime
or a stale tree.

Found while reading the guard for an unrelated question. No production incident - a hobby project
using GSD for a learning exercise.

Drafted with assistance from an AI agent and reviewed before filing.
```

## Review

| Pass                     | Verdict                                                                                                                                                                                                                                                                 | Date       |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| Evidence (comment)       | passed — C1-C5 re-read or re-run on 2026-11-12 after the pivot                                                                                                                                                                                                          | 2026-11-12 |
| Independent (comment #1) | HOLD — the first draft claimed the hooks bundle was outside the manifest, but `gsd-file-manifest.json` already lists `hooks/gsd-workflow-guard.js`; it implied a registration claim it did not have; it guaranteed what the checker would do; C5 named the wrong helper | 2026-11-12 |
| Owner                    | pending                                                                                                                                                                                                                                                                 | —          |

## History

This file began as a draft issue. Four independent passes held it on one point: without
install-level evidence that the hyphenated names are what the runtime registers, it read as a
duplicate of #376. Retargeting as a comment removed the claim that needed that evidence.

The first comment draft was held as well, and for a better reason: it asserted that the hooks bundle
falls outside the install manifest, which the manifest itself contradicts. The pivot to "reuse the
artifact as the positive control" is what the evidence actually supports.
