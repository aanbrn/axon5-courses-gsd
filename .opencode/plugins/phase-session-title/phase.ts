import { readFile } from 'node:fs/promises';
import { join } from 'node:path';

export type Phase = { number: string; name: string };

const strip = (value: string) => value.trim().replace(/^["']|["']$/g, '');

/**
 * The phase GSD is on, read from the planning state. Returns undefined when the file or the two
 * fields are absent, so a project without GSD keeps whatever title the harness would pick.
 *
 * Kept free of OpenCode imports so the parsing can be tested without the plugin runtime.
 */
export async function currentPhase(root: string): Promise<Phase | undefined> {
  try {
    const text = await readFile(join(root, '.planning/STATE.md'), 'utf8');
    const frontmatter = text.match(/^---\r?\n([\s\S]*?)\r?\n---/);
    if (!frontmatter) return undefined;

    const number = frontmatter[1].match(/^current_phase:\s*(.+)$/m)?.[1];
    const name = frontmatter[1].match(/^current_phase_name:\s*(.+)$/m)?.[1];
    if (!number || !name) return undefined;

    return { number: strip(number), name: strip(name) };
  } catch {
    return undefined;
  }
}

export const titleOf = (phase: Phase) => `Phase ${phase.number} — ${phase.name}`;
