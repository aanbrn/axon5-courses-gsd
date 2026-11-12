# Upstream reports

Findings about a dependency are recorded here and, when they qualify, reported to the dependency's
own repository. A local workaround without a report is how the same defect gets rediscovered by
every project that walks this path.

The rules live in `.planning/codebase/CONVENTIONS.md` § Tooling; this directory is where they are
executed.

## When a finding qualifies

| Finding                                                | Qualifies                  |
| ------------------------------------------------------ | -------------------------- |
| A dependency's behaviour contradicts its documentation | yes                        |
| A published release is broken or regresses             | yes                        |
| The tooling lacks a capability this project needs      | yes — as a feature request |
| A typo in a log line, an unclear error message         | no                         |

The test is whether it changed how this project worked, or would change it for anyone walking the
same path.

## The gate

Nothing is filed until three passes succeed **on the exact text that will be sent**. The draft is
committed first, so it can be diffed, reviewed, and later compared with what was actually said.

| Pass        | Who                                      | Checks                                                                                                                                                       |
| ----------- | ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Evidence    | the agent                                | every behavioural claim maps to a `file:line` at the reported version, re-read at review time rather than recalled, with the command and its output recorded |
| Independent | the codex lane configured under `review` | supported / unsupported / speculative per claim, plus tone and duplicate risk                                                                                |
| Owner       | the repository owner                     | reads the committed file in full and approves that text, not a summary of it                                                                                 |

Any one of these blocks publication: an unverified claim; a missing or untested version; a local
path, hostname, username, token, internal URL or unpublished design in the text; speculation about a
maintainer's intent; an unchecked duplicate; an approval given to a paraphrase rather than to the
file.

The report states in one line that it was drafted with AI assistance and reviewed before filing,
unless the owner records a different decision in that report's header.

## Files

One file per report, `NNNN-<slug>.md`, numbered in order. Each carries:

- a header table — dependency, version, ecosystem, upstream repository, status, disclosure, date;
- `## Claims and evidence` — one row per claim, with the file and line that support it;
- `## Draft` — the exact text that will be sent, in a `text` fence so formatting cannot rewrite it;
- `## Review` — one row per pass, with its verdict and date.

Statuses: `not filed`, `filed`, `fixed in <version>`, `closed (wontfix)`.

## Register

| #    | Date       | Dependency                 | Symptom                                                                                                       | Status      | File                                        |
| ---- | ---------- | -------------------------- | ------------------------------------------------------------------------------------------------------------- | ----------- | ------------------------------------------- |
| 0001 | 2026-11-12 | `@opengsd/gsd-core` 1.20.1 | Enabling `hooks.workflow_guard` still lets a direct edit through, while the generated instructions forbid one | `not filed` | [0001](0001-gsd-workflow-guard-advisory.md) |
| 0002 | 2026-11-12 | `@opengsd/gsd-core` 1.20.1 | The hooks bundle ships `/gsd:fast` and `/gsd:quick`; flagged for the artifact check in #6054                  | `not filed` | [0002](0002-gsd-advisory-command-form.md)   |
