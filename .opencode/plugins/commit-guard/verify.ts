/**
 * Prints the four verdicts the guard must reach, and exits non-zero when one differs.
 *
 * It needs no session, no server and no plugin runtime — the decision function is pure, so this is
 * the same check the tests make, in a form a human can read in one glance.
 */
import { createState, decide, recordPrompt } from './guard.ts';

const SESSION = 'verify-session';

interface Case {
  readonly name: string;
  readonly expected: 'allow' | 'deny';
  readonly prompt?: string;
  readonly agent?: string;
  readonly input?: unknown;
}

const CASES: readonly Case[] = [
  {
    name: 'active GSD command',
    expected: 'allow',
    prompt: '/gsd-quick verify the guard',
  },
  {
    name: 'gsd-executor subagent',
    expected: 'allow',
    agent: 'gsd-executor',
  },
  {
    name: 'neither a command nor a gsd- agent',
    expected: 'deny',
    agent: 'build',
  },
  {
    name: 'non-commit bash command',
    expected: 'allow',
    agent: 'build',
    input: { command: 'git status --short' },
  },
];

let failures = 0;

for (const testCase of CASES) {
  const state = createState();
  if (testCase.prompt !== undefined) recordPrompt(state, SESSION, testCase.prompt);

  const verdict = decide(state, {
    sessionID: SESSION,
    tool: 'bash',
    agent: testCase.agent,
    input: 'input' in testCase ? testCase.input : { command: 'git commit -m "feat: verify"' },
  });

  const matches = verdict.decision === testCase.expected;
  if (!matches) failures++;
  console.log(
    `${matches ? 'ok  ' : 'FAIL'} ${testCase.name}: expected ${testCase.expected}, got ${verdict.decision}`,
  );
}

if (failures > 0) {
  console.error(
    `commit-guard: ${failures} of ${CASES.length} verdicts differ from the expectation`,
  );
  process.exitCode = 1;
}
