import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { currentPhase, titleOf } from './phase.ts';

/** Runs the given body against a throwaway project root, with `.planning/STATE.md` when contents are given. */
async function withState(contents: string | undefined, run: (root: string) => Promise<void>) {
  const root = await mkdtemp(join(tmpdir(), 'phase-session-title-'));
  try {
    if (contents !== undefined) {
      await mkdir(join(root, '.planning'), { recursive: true });
      await writeFile(join(root, '.planning', 'STATE.md'), contents);
    }
    await run(root);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

test('reads the phase and its roadmap name', async () => {
  const state =
    '---\ncurrent_phase: 2\ncurrent_phase_name: Session and message path\nstatus: planning\n---\n';
  await withState(state, async (root) => {
    assert.deepEqual(await currentPhase(root), {
      number: '2',
      name: 'Session and message path',
    });
  });
});

test('strips quotes the state file may carry', async () => {
  const state = '---\ncurrent_phase: "3"\ncurrent_phase_name: \'Message and tag contract\'\n---\n';
  await withState(state, async (root) => {
    assert.deepEqual(await currentPhase(root), {
      number: '3',
      name: 'Message and tag contract',
    });
  });
});

test('accepts CRLF line endings', async () => {
  const state =
    '---\r\ncurrent_phase: 4\r\ncurrent_phase_name: DCB append-condition spike\r\n---\r\n';
  await withState(state, async (root) => {
    assert.deepEqual(await currentPhase(root), {
      number: '4',
      name: 'DCB append-condition spike',
    });
  });
});

test('returns nothing when a field is absent', async () => {
  await withState('---\ncurrent_phase: 2\n---\n', async (root) => {
    assert.equal(await currentPhase(root), undefined);
  });
});

test('returns nothing when there is no frontmatter', async () => {
  await withState('# State\n\ncurrent_phase: 2\n', async (root) => {
    assert.equal(await currentPhase(root), undefined);
  });
});

test('returns nothing when the state file is missing', async () => {
  await withState(undefined, async (root) => {
    assert.equal(await currentPhase(root), undefined);
  });
});

test('formats the title as Phase N — name', () => {
  assert.equal(
    titleOf({ number: '2', name: 'Session and message path' }),
    'Phase 2 — Session and message path',
  );
});
