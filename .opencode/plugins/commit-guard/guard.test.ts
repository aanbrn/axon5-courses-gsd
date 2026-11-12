import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createState, decide, recordPrompt } from './guard.ts';
import type { GuardInput, Verdict } from './guard.ts';

const SESSION = 'session-1';
const COMMIT_INPUT = { command: 'git commit -m "feat: the thing"' };

interface Overrides {
  readonly tool?: string;
  readonly agent?: string;
  readonly input?: unknown;
}

/** Decides one call, optionally under a prompt the session just received. */
function verdictFor(promptText: string | undefined, overrides: Overrides = {}): Verdict {
  const state = createState();
  if (promptText !== undefined) recordPrompt(state, SESSION, promptText);

  const event: GuardInput = {
    sessionID: SESSION,
    tool: overrides.tool ?? 'bash',
    agent: overrides.agent,
    input: 'input' in overrides ? overrides.input : COMMIT_INPUT,
  };
  return decide(state, event);
}

/** Asserts the verdict is a refusal that names every GSD command to type instead. */
function assertRefusal(verdict: Verdict): void {
  if (verdict.decision !== 'deny') assert.fail('expected the commit to be refused');
  for (const command of ['/gsd-quick', '/gsd-fast', '/gsd-execute-phase']) {
    assert.match(verdict.message, new RegExp(command));
  }
}

test('allows a commit while a GSD command is active', () => {
  assert.equal(verdictFor('/gsd-quick fix the flaky test').decision, 'allow');
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
