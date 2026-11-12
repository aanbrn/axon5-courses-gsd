# 0001 — settings.md omits a description the configuration guide already gives

|            |                                                                                                                                                                  |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Type       | Enhancement issue                                                                                                                                                |
| Template   | `.github/ISSUE_TEMPLATE/enhancement.yml`                                                                                                                         |
| Dependency | `@opengsd/gsd-core`                                                                                                                                              |
| Version    | 1.20.1                                                                                                                                                           |
| Ecosystem  | npm                                                                                                                                                              |
| Upstream   | https://github.com/open-gsd/gsd-core                                                                                                                             |
| Status     | `closed (wontfix)` — not filed                                                                                                                                   |
| Resolution | The published configuration guide already documents the option at `docs/CONFIGURATION.md:854` (v1.20.1); only the internal `workflows/settings.md` line omits it |
| Disclosure | Drafted with assistance from an AI agent and reviewed before filing.                                                                                             |
| Found      | 2026-11-12                                                                                                                                                       |

## Outcome

**Not filed.** The premise of this draft was that a user configuring `hooks.workflow_guard` cannot
learn what it does. That is false: `docs/CONFIGURATION.md:854` in v1.20.1 documents the option, its
advisory behaviour, the `agent-*`/`worktree-agent-*` force-add exception and its fail-closed rule.

What remains is that `workflows/settings.md:509` lists the key as `true/false` without repeating
that description. A single reference line that duplicates a published table is below this project's
own threshold for an upstream report - "it changed how this project worked, or would change it for
anyone walking the same path". Nothing here changed how the project worked, and the published guide
already answers the question a user would ask.

The draft below is retained as the record of what was considered and why it stopped.

## Summary

The hook documents its own behaviour precisely: it "advises, not blocks". The configuration
reference does not — `workflows/settings.md` lists `"workflow_guard": true/false` and nothing else.
The draft asks for one sentence there, naming what the switch actually does. Nothing else changes.

## Why an enhancement, not a feature request

`CONTRIBUTING.md` defines an enhancement as an improvement to existing behaviour that adds no new
command, workflow or concept. This draft edits one documentation line about one existing option. It
proposes no behaviour change, so the question of a new concept does not arise.

## Claims and evidence

Paths are relative to an installed `@opengsd/gsd-core` 1.20.1 tree: `hooks/` and `plugins/` sit
under `.opencode/`, `bin/` and `workflows/` under `.opencode/gsd-core/`.

| #   | Claim                                                                                                                           | Evidence                                                                                                                                                                                                                                                                                        |
| --- | ------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| C1  | `hooks.workflow_guard` is opt-in and off by default.                                                                            | `bin/shared/config-defaults.manifest.json:82` (`"workflow_guard": false`); the hook repeats it at `hooks/gsd-workflow-guard.js:17`                                                                                                                                                              |
| C2  | With the guard enabled, a top-level `Edit` of a non-exempt path returns an advisory and no block decision.                      | `hooks/gsd-workflow-guard.js:7-8` ("SOFT guard for edits — it advises, not blocks"), `:326` (subagent/task exemption), `:341` (both `.planning/` and `.planning\`), `:346-352` (exempt patterns, `/\.env/` unanchored), `:365-372` (`code: 'WORKFLOW_ADVISORY'`, no `decision`); reproduction A |
| C3  | The hook's one hard block is force-add on `agent-`/`worktree-agent-` branches, and its fail-closed path needs three conditions. | `hooks/gsd-workflow-guard.js:11`, `:112` (predicate; `:311` repeats it), `:132`, `:140`, `:153-175`, call at `:385`, block at `:386`; reproduction B                                                                                                                                            |
| C4  | A runtime adapter runs the hook and translates its result; that is not evidence of model receipt.                               | `plugins/gsd-core.js:756-758`, `:360-366`, `:368-389`                                                                                                                                                                                                                                           |
| C5  | The generated instruction states the rule that invites this expectation.                                                        | `AGENTS.md:434`, generated by `bin/lib/profile-output.cjs:202-217`                                                                                                                                                                                                                              |
| C6  | The schema's `validKeys` carries no per-key descriptions: 126 bare strings, three `gates.*`, none containing `enforc`.          | `bin/shared/config-schema.manifest.json:2`, `:4-129`, `:5-7`                                                                                                                                                                                                                                    |
| C7  | All three `gates.*` keys are read as workflow step conditions, not as tool-layer guards.                                        | `workflows/execute-plan.md:84`, `workflows/transition.md:145`, `workflows/complete-milestone.md:169`                                                                                                                                                                                            |
| C8  | The hook header already documents the advisory behaviour; the configuration reference does not.                                 | `hooks/gsd-workflow-guard.js:7-11` versus `workflows/settings.md:509`, which reads `"workflow_guard": true/false` and stops there                                                                                                                                                               |

C6 and C7 say what the schema lists and where the gate keys are read. They do not claim that no
enforcement mechanism exists anywhere in the framework; the draft makes no such claim.

### Design statements, not evidence claims

- **documentation only** — the proposed change edits text, not behaviour;
- **no breaking changes** — a consequence of that, not an inspection;
- **no new command, workflow or concept** — the submitter's reading of `CONTRIBUTING.md`.

## Draft (retained for the record — not posted)

````text
### Pre-submission checklist

- [x] I have confirmed this improves existing behavior - it does not add a new command, workflow, or concept
- [x] I have searched existing issues and this enhancement has not already been proposed
- [x] I have read CONTRIBUTING.md and understand I must wait for `approved-enhancement` before writing any code
- [x] I can clearly describe the concrete benefit

### What existing feature or behavior does this improve?

`hooks.workflow_guard` - the opt-in guard that watches for edits made outside a GSD workflow - and the
one place its behaviour is documented for users.

### Current behavior

Checked against an installed 1.20.1 tree.

`AGENTS.md:434` tells the agent not to edit files outside a GSD workflow:

> Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.

`hooks.workflow_guard` ships off by default (`bin/shared/config-defaults.manifest.json:82`). The hook
header describes exactly what it does when enabled (`hooks/gsd-workflow-guard.js:7-11`):

> This is a SOFT guard for edits - it advises, not blocks. The edit still proceeds.
> ...
> ONE hard block lives here: `git add -f` on an agent/worktree-agent branch

The header is accurate. A top-level edit whose path contains neither `.planning/` nor `.planning\` and
matches none of the exempt patterns returns an advisory with `code: 'WORKFLOW_ADVISORY'` and no
`decision` field (`:365-372`); subagents and task sessions are exempt (`:326`), as are a short list of
config and documentation filenames (`:346-352`).

The configuration reference says only this (`workflows/settings.md:509`):

```json
"hooks": {
  "context_warnings": true/false,
  "workflow_guard": true/false
}
```

So the code documents the semantics and the reference does not. Nothing states, where a user configures
the option, that turning it on buys advice rather than refusal. `AGENTS.md` carries a directive that
reads as if something enforced it, and the option's name reads the same way.

The config schema does not fill the gap: `validKeys` is a list of 126 bare key-path strings with no
per-key descriptions, three of them start with `gates.`, and none contains "enforc"
(`bin/shared/config-schema.manifest.json:2`, `:4-129`). Those three gate keys are read as workflow step
conditions (`workflows/execute-plan.md:84`, `workflows/transition.md:145`,
`workflows/complete-milestone.md:169`), not as tool-layer guards.

Reproduction of the advisory, against an installed tree:

```bash
H="$PWD/.opencode/hooks/gsd-workflow-guard.js"
P=$(mktemp -d) && mkdir -p "$P/.planning" "$P/src"
printf '{"hooks":{"workflow_guard":true}}' > "$P/.planning/config.json"
echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/src/App.kt\"}}" \
  | node "$H"; echo " exit=$?"
```

Observed: one line of JSON, no `decision` field, `exit=0`, whose `additionalContext` contains
`Consider using /gsd:fast ... or /gsd:quick ...`.

The exemptions, same shell:

```bash
echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/.planning/STATE.md\"}}" | node "$H"; echo " exit=$?"
echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"tool_input\":{\"file_path\":\"$P/AGENTS.md\"}}" | node "$H"; echo " exit=$?"
echo "{\"tool_name\":\"Edit\",\"cwd\":\"$P\",\"session_type\":\"task\",\"tool_input\":{\"file_path\":\"$P/src/App.kt\"}}" | node "$H"; echo " exit=$?"
```

Observed: nothing printed, `exit=0`, in all three cases.

The force-add block, both legs:

```bash
H="$PWD/.opencode/hooks/gsd-workflow-guard.js"
P=$(mktemp -d) && mkdir -p "$P/.planning"
printf '{"hooks":{"workflow_guard":true}}' > "$P/.planning/config.json"
git -C "$P" init -q -b agent-probe
git -C "$P" -c user.email=probe@example.invalid -c user.name=probe commit -q --no-verify \
  --allow-empty -m probe
echo "{\"tool_name\":\"Bash\",\"cwd\":\"$P\",\"tool_input\":{\"command\":\"git add -f x\"}}" | node "$H"; echo " exit=$?"
git -C "$P" checkout -q -b feature-probe
echo "{\"tool_name\":\"Bash\",\"cwd\":\"$P\",\"tool_input\":{\"command\":\"git add -f x\"}}" | node "$H"; echo " exit=$?"
```

Observed: on `agent-probe`, `{"decision":"block","code":"WORKTREE_AGENT_FORCE_ADD_FORBIDDEN",...}` on
stdout, the same reason on stderr, `exit=2`; on `feature-probe`, nothing, `exit=0`.

These call the hook script directly. They do not run a runtime adapter end to end: the OpenCode plugin
invokes the hook for write-like tools and Bash (`plugins/gsd-core.js:756-758`), blocks only on
`decision: "block"` or exit 2 (`:360-366`), and otherwise collects the advisory (`:368-389`).

### Proposed behavior

Add the semantics to the configuration reference, next to the switch it describes:

```json
"hooks": {
  "context_warnings": true/false,
  "workflow_guard": true/false  // advisory only: warns on a direct edit, never blocks it
}
```

and a sentence in `workflows/settings.md:509` saying the same thing in prose, including what it does
not do: it does not refuse an edit, and it does not check whether a GSD command is active.

There is no per-key description in the config schema to update - `validKeys` holds bare strings - so
the reference and the hook header are the only two places a user can read this.

### Reason and benefit

**Why the current behavior is a problem:** the hook's header is honest and the configuration reference
is silent. A user reads `workflow_guard: true/false` under "hooks", next to `context_warnings`, with
`AGENTS.md` telling the agent not to edit outside a workflow, and reasonably expects the switch to
enforce something. It does not, and nothing says so where the decision is made.

**Concrete benefit:** one line closes the gap between what the code documents and what a user reads
when configuring it. A project can decide knowingly whether to enable the option, and nobody has to
read the hook's header comment to learn that it only advises.

### Scope of changes

- `workflows/settings.md:509` - one commented line plus a short prose sentence
- `CHANGELOG.md` - a user-facing entry, if the project requires one for documentation changes

No code paths change, no defaults change, no new files.

### Breaking changes

None. The change is documentation about behaviour that already ships.

### Alternatives considered

- **Make the option blocking.** This is the behaviour the name suggests, and I would use it. It needs a
  signal for "a GSD command is active", and the hook does not have one: it distinguishes subagents and
  task sessions (`hooks/gsd-workflow-guard.js:326`), while `/gsd-fast` runs inline without a subagent
  (`workflows/fast.md:4` and `:112`). A blocking predicate over the current inputs would refuse
  legitimate tracked edits. That is a larger change than an enhancement to one option, so it is out of
  scope here.
- **Document nothing and leave it to the header.** A user configuring the option has no reason to open
  the hook's source.
- **Rename the option.** Renaming a shipped config key breaks existing configuration for a cosmetic fix.

### Area affected

Documentation

### Additional context

Related: #5036 changed the generated instruction's wording; it did not touch this option's
documentation, and it is not the same request.

Found while reading the guard for an unrelated question, in a personal hobby project that uses GSD for
a learning exercise. No production incident, and the finding is about the documented contract rather
than about a failure.

Drafted with assistance from an AI agent and reviewed before filing.
````

## Attestation

The preflight boxes are the submitter's statements:

- "searched existing issues" — searched for `workflow guard`, `workflow_guard`, `direct edit`,
  `WORKFLOW ADVISORY`, `enforce workflow`, `advises, not blocks`, `gsd:fast` and `colon form`;
  #5036, #3504, #1577, #2255, #376, #4859 and #6054 are adjacent and none requests this;
- "read CONTRIBUTING.md" — **the owner confirms this before posting**;
- "improves existing behavior" — documentation of an existing option;
- "can describe the concrete benefit" — the "Reason and benefit" field does.

## Review

| Pass                              | Verdict                                                                                                                                                                                                                       | Date       |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| Evidence (agent)                  | passed — C1-C8 re-read on 2026-11-12                                                                                                                                                                                          | 2026-11-12 |
| Independent #1-#6 (codex)         | HOLD, six times — claim scope, citation ranges, tone, and the public text drifting from the claim table; each round's reasons are in the commits that answered them                                                           | 2026-11-12 |
| Independent #7 (codex, templated) | HOLD — the blocking ask was an incomplete specification; the scope named a configuration-schema description that does not exist; the reproductions would not render in an issue body; all-three-gates evidence was incomplete | 2026-11-12 |
| Independent #8 (codex)            | HOLD — `docs/CONFIGURATION.md:854` in v1.20.1 already documents the option, which refutes the draft's premise; the remaining ask is too thin to file                                                                          | 2026-11-12 |
| Decision                          | closed, not filed — the finding is real but already documented upstream                                                                                                                                                       | 2026-11-12 |

## History

Nine independent passes. The request narrowed at each one, and the last narrowing is the important
one: the draft asked for a blocking mode it could not specify without inventing a workflow-active
signal, and pointed at a config-schema description that does not exist. What remains is one
documentation line, which is what the evidence supports without speculation.

Pass #8 is the one that decided the outcome: the reviewer looked outside the files this report had
been citing, found the published configuration table, and the draft's premise collapsed. That is the
gate working as intended - a report that survives citation checking can still be wrong about the
world.
