import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createState, decide, decideSafely, recordPrompt } from './guard.ts';
import type { GuardInput, Verdict } from './guard.ts';

const SESSION = 'session-1';

/**
 * Captured from a live session on OpenCode 2.2.7: this is what the prompt hook receives for
 * `/gsd-fast probe the prompt payload`. The command name survives only as the workflow path.
 */
const EXPANDED_GSD_PROMPT = `<arguments>probe the prompt payload</arguments>

The text inside \`<arguments>\` is exactly what the user typed after the command name.

<objective>
Execute a trivial task directly in the current context without spawning subagents.
</objective>

<execution_context>
@/Users/someone/project/.opencode/gsd-core/workflows/fast.md
</execution_context>

<process>
Execute end-to-end.
</process>`;

/** The same shape with the workflow reference removed: a long request, not a command. */
const LOOKS_EXPANDED_BUT_IS_NOT = EXPANDED_GSD_PROMPT.replace(
  '@/Users/someone/project/.opencode/gsd-core/workflows/fast.md',
  '',
);

/** Every form the token walk must recognise as a real commit. */
const COMMITS = [
  'git commit -m "feat: the thing"',
  'git commit -am "fix: the thing"',
  'git commit',
  'git -C /repo commit -m "feat: the thing"',
  'git -c user.name=someone commit -m "feat: the thing"',
  'GIT_AUTHOR_NAME=someone git commit -m "feat: the thing"',
  '/usr/bin/git commit -m "feat: the thing"',
  'true && git commit -m "feat: the thing"',
  'git add . ; git commit -m "feat: the thing"',
  'git status --short || git commit -m "feat: the thing"',
  'git commit -m "--dry-run"',
  'git -C /repo commit --amend --no-edit',
];

/** Commands that only look like commits, plus the two forms that mean "do not commit". */
const NOT_COMMITS = [
  'git commit --dry-run',
  'git commit --dry-run --short',
  'git commit --help',
  'git commit -h',
  'git status',
  'git commit-tree abc123',
  'git log --grep commit',
  'echo "git commit"',
  'echo git commit -m x',
  'git -c user.name=someone log --oneline',
  'npm test',
];

interface Overrides {
  readonly tool?: string;
  readonly agent?: string;
  readonly input?: unknown;
}

/** Decides one call, optionally under a prompt the session just received. */
function verdictFor(promptText: string | undefined, overrides: Overrides = {}): Verdict {
  const state = createState();
  // `undefined` stands for an ordinary message from the owner: the session is known and no GSD
  // command is active, which is the case the guard refuses. An unknown session is exercised
  // separately, because it is the one case the prompt hook may never report.
  recordPrompt(state, SESSION, promptText ?? 'an ordinary request');

  const event: GuardInput = {
    sessionID: SESSION,
    tool: overrides.tool ?? 'bash',
    agent: overrides.agent,
    input: 'input' in overrides ? overrides.input : { command: COMMITS[0] },
  };
  return decide(state, event);
}

function denyReason(verdict: Verdict): string {
  if (verdict.decision !== 'deny') assert.fail('expected the commit to be refused');
  return verdict.message;
}

/** Asserts the refusal names every GSD command to type instead. */
function assertRefusal(verdict: Verdict): void {
  const message = denyReason(verdict);
  for (const command of ['/gsd-quick', '/gsd-fast', '/gsd-execute-phase']) {
    assert.match(message, new RegExp(command));
  }
}

// --- The four headline cases -------------------------------------------------

test('allows a commit while a GSD command is active', () => {
  assert.equal(verdictFor('/gsd-quick fix the flaky test').decision, 'allow');
});

test('allows a commit under a command the runtime expanded before the hook saw it', () => {
  assert.equal(verdictFor(EXPANDED_GSD_PROMPT).decision, 'allow');
});

test('refuses a long request that carries the tags but no workflow reference', () => {
  assertRefusal(verdictFor(LOOKS_EXPANDED_BUT_IS_NOT));
});

test('allows a commit from a gsd- subagent, which never receives a prompt', () => {
  assert.equal(verdictFor(undefined, { agent: 'gsd-executor' }).decision, 'allow');
});

test('refuses a commit with no GSD command and no gsd- agent', () => {
  assertRefusal(verdictFor(undefined, { agent: 'build' }));
});

test('leaves a non-commit bash command alone in both states', () => {
  const status = { command: 'git status --short' };
  assert.equal(verdictFor(undefined, { input: status, agent: 'build' }).decision, 'allow');
  assert.equal(verdictFor('/gsd-quick x', { input: status }).decision, 'allow');
});

// --- The detection table -----------------------------------------------------

test('refuses every commit form when no GSD command is active', () => {
  for (const command of COMMITS) {
    const verdict = verdictFor(undefined, { agent: 'build', input: { command } });
    assert.equal(verdict.decision, 'deny', command);
  }
});

test('allows the same commit forms under an active command or a gsd- agent', () => {
  for (const command of COMMITS) {
    assert.equal(verdictFor('/gsd-quick x', { input: { command } }).decision, 'allow', command);
    assert.equal(
      verdictFor(undefined, { agent: 'gsd-executor', input: { command } }).decision,
      'allow',
      command,
    );
  }
});

test('allows every command that only looks like a commit', () => {
  for (const command of NOT_COMMITS) {
    const verdict = verdictFor(undefined, { agent: 'build', input: { command } });
    assert.equal(verdict.decision, 'allow', command);
  }
});

test('treats the OpenCode 2.x shell tool id the same as bash', () => {
  assert.equal(verdictFor(undefined, { tool: 'shell', agent: 'build' }).decision, 'deny');
  assert.equal(verdictFor('/gsd-quick x', { tool: 'shell' }).decision, 'allow');
});

// --- Scope and state -------------------------------------------------------

test('ignores every tool that is not a shell', () => {
  const input = { command: COMMITS[0] };
  for (const tool of ['read', 'write', 'edit', 'grep']) {
    assert.equal(verdictFor(undefined, { tool, agent: 'build', input }).decision, 'allow', tool);
  }
});

test('ignores a shell call whose input carries no command string', () => {
  for (const input of [undefined, null, {}, { command: 42 }, { filePath: 'x' }]) {
    assert.equal(verdictFor(undefined, { agent: 'build', input }).decision, 'allow');
  }
});

test('an absent or empty agent is not a subagent', () => {
  assertRefusal(verdictFor(undefined, { agent: undefined }));
  assertRefusal(verdictFor(undefined, { agent: '' }));
});

test('a plain prompt clears the command an earlier prompt recorded', () => {
  const state = createState();
  const event: GuardInput = {
    sessionID: SESSION,
    tool: 'bash',
    agent: 'build',
    input: { command: COMMITS[0] },
  };

  recordPrompt(state, SESSION, '/gsd-quick fix the flaky test');
  assert.equal(decide(state, event).decision, 'allow');
  recordPrompt(state, SESSION, 'thanks, carry on');
  assert.equal(decide(state, event).decision, 'deny');
});

test('allows a session the prompt hook never reported', () => {
  // The plugin can load after a session's prompt arrived, and a subagent session never has one.
  // Neither is evidence of an arbitrary commit, so both are allowed; only a session that reported a
  // non-GSD prompt is refused.
  const state = createState();
  const event: GuardInput = {
    sessionID: 'session-the-hook-never-saw',
    tool: 'bash',
    agent: 'build',
    input: { command: COMMITS[0] },
  };

  assert.equal(decide(state, event).decision, 'allow');
});

test('refuses the same session once a non-GSD prompt is reported', () => {
  const state = createState();
  const event: GuardInput = {
    sessionID: 'session-the-hook-never-saw',
    tool: 'bash',
    agent: 'build',
    input: { command: COMMITS[0] },
  };

  recordPrompt(state, event.sessionID, 'please fix the build');
  assert.equal(decide(state, event).decision, 'deny');
});

// --- Fail open ---------------------------------------------------------------

test('fails open and logs once when the decision itself breaks', () => {
  const state = createState();
  const messages: string[] = [];
  const log = (message: string) => messages.push(message);
  const broken = {
    get command(): string {
      throw new Error('the guard broke');
    },
  };
  const event: GuardInput = { sessionID: SESSION, tool: 'bash', agent: 'build', input: broken };

  assert.equal(decideSafely(state, event, log).decision, 'allow');
  assert.equal(decideSafely(state, event, log).decision, 'allow');
  assert.equal(messages.length, 1);
  assert.match(messages[0], /^commit-guard:/);
  assert.match(messages[0], /allowed/);
});
