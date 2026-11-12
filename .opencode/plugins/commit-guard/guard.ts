/**
 * commit-guard — the pure half of the plugin.
 *
 * The plugin exists because the per-commit permission prompt in `opencode.jsonc` was removed: it
 * fired on every task commit and taught approving without reading. Removing it also removed the one
 * distinction worth keeping — a commit made by a GSD workflow versus a commit made arbitrarily.
 * This module restores that distinction without a prompt.
 *
 * Nothing here imports `@opencode/plugin` or touches the filesystem, so `node:test` runs it without
 * the OpenCode runtime. `index.ts` only registers hooks and hands their events to these functions.
 */

/** A runtime that hands the hook the raw input: the text starts with the command. */
const GSD_COMMAND = /^\s*\/(gsd-[a-z0-9-]+)/;

/**
 * OpenCode expands a slash command before the prompt hook sees it. What arrives is the command
 * template body — no `/gsd-` anywhere — and the only stable trace of which command ran is the
 * workflow file it references inside `<execution_context>`. Measured on 2.2.7 with a captured
 * payload; see the fixture in `guard.test.ts`.
 */
const EXPANDED_COMMAND = /<execution_context>[\s\S]*?gsd-core\/workflows\/([a-z0-9-]+)\.md/;

/** Matches the OpenCode 2.x shell tool id as well as the older `bash` one. */
const SHELL_TOOL_IDS = new Set(['bash', 'shell']);

/** Parallel to the refusal requirement: a denied call must name the command to type instead. */
export const REFUSAL =
  'commit-guard: git commit runs only inside a GSD command. Type /gsd-quick, /gsd-fast or ' +
  '/gsd-execute-phase and let that workflow make the commit. ' +
  'To disable the guard, start opencode with COMMIT_GUARD=off.';

/** Everything a tool hook can tell the decision apart from session state. */
export interface GuardInput {
  readonly sessionID: string;
  readonly tool: string;
  readonly agent?: string;
  readonly input: unknown;
}

/** Allow, or refuse with the message to throw. */
export type Verdict =
  { readonly decision: 'allow' } | { readonly decision: 'deny'; readonly message: string };

export const ALLOW: Verdict = { decision: 'allow' };

/**
 * The plugin instance's memory: the GSD command each session is under, plus whether an internal
 * failure has already been reported — the "log once" half of failing open.
 */
export interface GuardState {
  readonly active: Map<string, string>;
  /**
   * Sessions whose prompt this plugin has seen. A session missing here is unknown rather than
   * arbitrary: the plugin may have loaded after the session's prompt arrived, and a subagent session
   * never receives one. Unknown sessions are allowed — see `decide`.
   */
  readonly seen: Set<string>;
  reported: boolean;
}

export function createState(): GuardState {
  return { active: new Map(), seen: new Set(), reported: false };
}

/**
 * A GSD prompt records the command — raw or expanded — and any other prompt clears it. Either way
 * the session is now known.
 */
export function recordPrompt(state: GuardState, sessionID: string, promptText: string): void {
  const raw = GSD_COMMAND.exec(promptText);
  const expanded = raw ? null : EXPANDED_COMMAND.exec(promptText);
  state.seen.add(sessionID);

  if (raw) state.active.set(sessionID, raw[1]);
  else if (expanded) state.active.set(sessionID, expanded[1]);
  else state.active.delete(sessionID);
}

/** The command string of a shell tool call, or undefined for any other input shape. */
function commandOf(input: unknown): string | undefined {
  if (!input || typeof input !== 'object') return undefined;
  const command = (input as { command?: unknown }).command;
  return typeof command === 'string' ? command : undefined;
}

/**
 * Split a command string the way the shared GSD scanner does: whitespace-separated tokens, with
 * single- and double-quoted spans taken as one token each. No escape or variable expansion.
 */
export function tokenize(command: string): string[] {
  const tokens: string[] = [];
  let index = 0;
  while (index < command.length) {
    while (index < command.length && /\s/.test(command[index])) index++;
    if (index >= command.length) break;
    let token = '';
    while (index < command.length && !/\s/.test(command[index])) {
      const character = command[index];
      if (character === "'" || character === '"') {
        index++;
        while (index < command.length && command[index] !== character) token += command[index++];
        if (index < command.length) index++;
      } else {
        token += command[index++];
      }
    }
    if (token) tokens.push(token);
  }
  return tokens;
}

function basename(token: string): string {
  return token.slice(token.lastIndexOf('/') + 1);
}

/**
 * Statement separators the guard splits on before walking tokens. The shared GSD scanner does not
 * split on control operators — `git commit && echo` yields `&&` as an ordinary token — so the split
 * is ours and the token walk runs per statement.
 */
const STATEMENT_PAIRS = new Set(['&&', '||']);

const STATEMENT_SINGLES = new Set([';', '|', '\n']);

/**
 * Git global options that consume the following token as their value. `-c` and its glued
 * `-cfoo.bar=1` spelling are handled separately.
 */
const GIT_ARGUMENT_OPTIONS = new Set([
  '-C',
  '-c',
  '--git-dir',
  '--work-tree',
  '--namespace',
  '--super-prefix',
  '--exec-path',
  '--html-path',
  '--man-path',
  '--info-path',
  '--list-cmds',
]);

/** Git global flags that take no value. */
const GIT_BOOLEAN_OPTIONS = new Set([
  '-p',
  '--paginate',
  '--no-pager',
  '--no-replace-objects',
  '--bare',
  '--literal-pathspecs',
  '--glob-pathspecs',
  '--noglob-pathspecs',
  '--icase-pathspecs',
  '--no-optional-locks',
  '-P',
  '--no-lazy-fetch',
  '--version',
  '--help',
]);

/**
 * `git commit` options that consume the following token. Without this walk, `git commit -m
 * "--dry-run"` would read the message as a flag and be allowed.
 */
const COMMIT_ARGUMENT_OPTIONS = new Set([
  '-m',
  '--message',
  '-F',
  '--file',
  '-C',
  '--reuse-message',
  '-c',
  '--reedit-message',
  '-t',
  '--template',
  '--author',
  '--date',
  '--fixup',
  '--squash',
  '--cleanup',
  '-S',
  '--gpg-sign',
  '--pathspec-from-file',
]);

/** `git commit` flags that mean the invocation does not create a commit. */
const COMMIT_NO_COMMIT_FLAGS = new Set(['--dry-run', '--help', '-h']);

interface Subcommand {
  readonly name: string;
  readonly args: readonly string[];
}

/**
 * Split a command line into statements at `&&`, `||`, `;`, `|`, a newline and a spaced bare `&`,
 * never inside quotes. Quotes stay in the returned text so the tokenizer still sees them.
 */
export function splitStatements(command: string): string[] {
  const statements: string[] = [];
  let current = '';
  let quote: string | null = null;

  for (let index = 0; index < command.length; index++) {
    const character = command[index];

    if (quote) {
      current += character;
      if (character === quote) quote = null;
      continue;
    }

    if (character === '"' || character === "'") {
      quote = character;
      current += character;
      continue;
    }

    const pair = command.slice(index, index + 2);
    if (STATEMENT_PAIRS.has(pair)) {
      statements.push(current);
      current = '';
      index++;
      continue;
    }

    if (STATEMENT_SINGLES.has(character)) {
      statements.push(current);
      current = '';
      continue;
    }

    // A bare `&` separates only when it stands alone; `2>&1` is a redirect, not a statement break.
    const previous = command[index - 1] ?? ' ';
    const next = command[index + 1] ?? ' ';
    if (character === '&' && /\s/.test(previous) && /\s/.test(next)) {
      statements.push(current);
      current = '';
      continue;
    }

    current += character;
  }

  statements.push(current);
  return statements
    .map((statement) => statement.trim())
    .filter((statement) => statement.length > 0);
}

/**
 * Walk one statement's tokens the way `skipToSubcommand` does: past leading `NAME=value` env
 * assignments, past the git executable (a full path is accepted), and past the global options git
 * takes before a subcommand.
 */
function subcommandOf(tokens: readonly string[]): Subcommand | null {
  let index = 0;
  while (index < tokens.length && /^[A-Za-z_][A-Za-z0-9_]*=/.test(tokens[index])) index++;
  if (index >= tokens.length) return null;

  const executable = tokens[index++];
  if (basename(executable) !== 'git') return null;

  while (index < tokens.length) {
    const token = tokens[index];
    const equals = token.indexOf('=');
    const name = equals === -1 ? token : token.slice(0, equals);
    if (GIT_ARGUMENT_OPTIONS.has(name)) {
      index += equals === -1 ? 2 : 1;
      continue;
    }
    // Glued config override: git accepts `-cfoo.bar=1` with its argument attached.
    if (/^-c\S*=/.test(token)) {
      index++;
      continue;
    }
    if (GIT_BOOLEAN_OPTIONS.has(token)) {
      index++;
      continue;
    }
    break;
  }

  if (index >= tokens.length) return null;
  return { name: tokens[index], args: tokens.slice(index + 1) };
}

/** True when `git commit`'s own arguments ask it not to commit. */
function meansNoCommit(args: readonly string[]): boolean {
  for (let index = 0; index < args.length; index++) {
    const arg = args[index];
    // Everything after `--` is a pathspec, never an option.
    if (arg === '--') return false;
    if (COMMIT_ARGUMENT_OPTIONS.has(arg)) {
      index++;
      continue;
    }
    if (COMMIT_NO_COMMIT_FLAGS.has(arg)) return true;
  }
  return false;
}

/**
 * A commit-shaped invocation, decided by the token walk rather than by a substring. `git commit
 * --dry-run`, `git commit --help`, `git commit-tree`, `git log --grep commit` and `echo "git
 * commit"` are therefore not commits.
 */
function isCommitCommand(command: string): boolean {
  for (const statement of splitStatements(command)) {
    const subcommand = subcommandOf(tokenize(statement));
    if (!subcommand || subcommand.name !== 'commit') continue;
    if (meansNoCommit(subcommand.args)) continue;
    return true;
  }
  return false;
}

/**
 * The decision. Pure: same input and state, same verdict, and it never writes or logs.
 *
 * A call is a guarded commit only when the shell tool runs a commit-shaped command. That command is
 * then allowed when the session's latest prompt typed a GSD command, when the call comes from a
 * `gsd-*` subagent — subagents never receive a prompt, so their name is the signal — or when the
 * session is unknown to the prompt hook. The last case is deliberate: see the `seen` field.
 */
export function decide(state: GuardState, event: GuardInput): Verdict {
  if (!SHELL_TOOL_IDS.has(event.tool.toLowerCase())) return ALLOW;

  const command = commandOf(event.input);
  if (command === undefined || !isCommitCommand(command)) return ALLOW;

  if (state.active.has(event.sessionID)) return ALLOW;
  if (event.agent && event.agent.startsWith('gsd-')) return ALLOW;
  // Unknown session: the prompt hook never reported one, so there is no evidence of an arbitrary
  // commit — only of a plugin that loaded late. Refusing here would block the workflow that is
  // running. A session that has reported a non-GSD prompt is known, and that is the case to refuse.
  if (!state.seen.has(event.sessionID)) return ALLOW;

  return { decision: 'deny', message: REFUSAL };
}

function reportOnce(state: GuardState, log: (message: string) => void, error: unknown): void {
  if (state.reported) return;
  state.reported = true;
  log(`commit-guard: internal error, the call is allowed: ${String(error)}`);
}

/**
 * `decide`, with the guard's own failures swallowed: a broken guard must never wedge a tool call.
 * The first failure is logged loudly, later ones stay silent so a persistent defect cannot flood
 * the session.
 */
export function decideSafely(
  state: GuardState,
  event: GuardInput,
  log: (message: string) => void = (message) => console.error(message),
): Verdict {
  try {
    return decide(state, event);
  } catch (error) {
    reportOnce(state, log, error);
    return ALLOW;
  }
}

/** `recordPrompt`, with the same fail-open policy — a broken guard must not block a prompt. */
export function recordPromptSafely(
  state: GuardState,
  sessionID: string,
  promptText: string,
  log: (message: string) => void = (message) => console.error(message),
): void {
  try {
    recordPrompt(state, sessionID, promptText);
  } catch (error) {
    reportOnce(state, log, error);
  }
}
