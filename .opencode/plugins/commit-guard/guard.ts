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

/** A typed slash command is the only signal that a GSD workflow started. */
const GSD_COMMAND = /^\s*\/(gsd-[a-z0-9-]+)/;

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
  reported: boolean;
}

export function createState(): GuardState {
  return { active: new Map(), reported: false };
}

/** A GSD prompt records the command; any other prompt clears it. */
export function recordPrompt(state: GuardState, sessionID: string, promptText: string): void {
  const match = GSD_COMMAND.exec(promptText);
  if (match) state.active.set(sessionID, match[1]);
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
 * Task 1 detector: the first `git` token followed by a `commit` token. The forms that need a real
 * walk — `-C`, env prefixes, chained statements — arrive with the hardened detector.
 */
function isCommitCommand(command: string): boolean {
  const tokens = tokenize(command);
  for (let index = 0; index < tokens.length - 1; index++) {
    if (basename(tokens[index]) === 'git' && tokens[index + 1] === 'commit') return true;
  }
  return false;
}

/**
 * The decision. Pure: same input and state, same verdict, and it never writes or logs.
 *
 * A call is a guarded commit only when the shell tool runs a commit-shaped command. That command is
 * then allowed when the session's latest prompt typed a GSD command, or when the call comes from a
 * `gsd-*` subagent — subagents never receive a prompt, so their name is the signal.
 */
export function decide(state: GuardState, event: GuardInput): Verdict {
  if (!SHELL_TOOL_IDS.has(event.tool.toLowerCase())) return ALLOW;

  const command = commandOf(event.input);
  if (command === undefined || !isCommitCommand(command)) return ALLOW;

  if (state.active.has(event.sessionID)) return ALLOW;
  if (event.agent && event.agent.startsWith('gsd-')) return ALLOW;

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
