# 0002 — Comment for #6054: installed hook scripts as an artifact class

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

| #   | Claim                                                                                        | Evidence                                                                                                                         |
| --- | -------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| C1  | The installed hook contains colon-form command names.                                        | `.opencode/hooks/gsd-workflow-guard.js:369`                                                                                      |
| C2  | The header comment repeats them.                                                             | `.opencode/hooks/gsd-workflow-guard.js:8`                                                                                        |
| C3  | The install's file manifest tracks that hook with its checksum.                              | `.opencode/gsd-file-manifest.json` — `files["hooks/gsd-workflow-guard.js"]`; `version` 1.20.1, `runtime` opencode, `scope` local |
| C4  | The OpenCode plugin invokes it for write-like tools and Bash.                                | `.opencode/plugins/gsd-core.js:756-758`                                                                                          |
| C5  | #376 reports this hook and this literal; #4859 is the compiled `bin/lib` precedent.          | both issues                                                                                                                      |
| C6  | `runtime-slash.cjs` calls the colon form non-routable for Claude Code installs specifically. | `bin/lib/runtime-slash.cjs:12-13`                                                                                                |
| C7  | #6054 asks for an artifact-level check per runtime.                                          | the issue body, "Proposed work"                                                                                                  |

Nothing here claims that the checker behaves a certain way, that `hooks/` is outside any manifest,
or that the hyphenated form is what the runtime registers.

## Draft

```text
In a project-scope OpenCode install of 1.20.1, the install's file manifest lists the hook scripts, and
one of them contains colon-form command names:

- gsd-file-manifest.json (version 1.20.1, runtime opencode, scope local) tracks
  "hooks/gsd-workflow-guard.js" with its SHA-256
- hooks/gsd-workflow-guard.js:369 -
  'Consider using /gsd:fast for trivial fixes or /gsd:quick for larger changes '
- hooks/gsd-workflow-guard.js:8 - the header comment repeats /gsd:quick and /gsd:fast
- the plugin invokes this hook for write-like tools and Bash (plugins/gsd-core.js:756-758)

If this phase's coverage is meant to include installed hook JavaScript, that is a candidate input, and
the class is already known: #376 reports this hook and this literal, #4859 is the compiled bin/lib
precedent this phase absorbs.

I am not claiming the colon form is wrong for this runtime. bin/lib/runtime-slash.cjs:12-13 calls it
no longer routable specifically for Claude Code skill installs, and I have not verified what OpenCode
accepts. The observation is only that a manifest-tracked artifact contains the tokens, so a per-runtime
check would have something concrete to decide on.

Scope: one project-scope OpenCode install at 1.20.1; not verified against a fresh install from the
published package.

Drafted with assistance from an AI agent and reviewed before filing.
```

## Review

| Pass                     | Verdict                                                                                                                                                                                        | Date       |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| Evidence (comment)       | passed — every numbered claim re-read on 2026-11-12                                                                                                                                            | 2026-11-12 |
| Independent (comment #1) | HOLD — claimed the hooks bundle was outside the manifest, which `gsd-file-manifest.json` contradicts; implied a registration claim; promised what the checker would do; named the wrong helper | 2026-11-12 |
| Independent (comment #2) | HOLD — "ready-made fixture" and "unrewritten token" assumed a required conversion the draft cannot prove; the discovery narrative is unverifiable                                              | 2026-11-12 |
| Independent (comment #3) | pending — must pass on this exact text before posting                                                                                                                                          | —          |
| Owner                    | pending                                                                                                                                                                                        | —          |

## History

This file began as a draft issue. Four independent passes held it on one point: without
install-level evidence that the hyphenated names are what the runtime registers, it read as a
duplicate of #376. Retargeting as a comment removed the claim that needed that evidence.

The first comment draft was held as well, and for a better reason: it asserted that the hooks bundle
falls outside the install manifest, which the manifest itself contradicts. The pivot to "reuse the
artifact as the positive control" is what the evidence actually supports.
