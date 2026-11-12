import { Plugin } from '@opencode/plugin';
import { currentPhase, titleOf } from './phase.ts';

// The loader resolves this module through its default export, which no TypeScript file in the
// project references, so the IDE reports it as an unused global symbol.
// noinspection JSUnusedGlobalSymbols
export default Plugin.define({
  id: 'phase-session-title',
  async setup(ctx) {
    const root = ctx.location.project.canonical ?? ctx.location.directory;
    // One entry per session: a phase change renames once, not on every prompt.
    const applied = new Map<string, string>();

    // A new session is titled by the harness after its first exchange. Answer with the phase
    // instead, so the generated title never has to be corrected afterwards.
    await ctx.session.hook('title', async (event) => {
      const phase = await currentPhase(root);
      if (phase) event.result = titleOf(phase);
    });

    // A phase can change while a session lives on: rename on the next prompt, before the work it
    // introduces. Failures are logged and never block the prompt.
    await ctx.session.hook('prompt', async (event) => {
      const phase = await currentPhase(root);
      if (!phase) return;

      const title = titleOf(phase);
      if (applied.get(event.sessionID) === title) return;

      try {
        const session = await ctx.session.get({ sessionID: event.sessionID });
        // A title the owner set by hand is not ours to overwrite: only an empty title or one this
        // plugin already wrote is renamed.
        if (session.title && !session.title.startsWith('Phase ')) return;
        if (session.title !== title) {
          await ctx.session.update({ sessionID: event.sessionID, title });
        }
        applied.set(event.sessionID, title);
      } catch (error) {
        console.error(`phase-session-title: could not rename the session: ${String(error)}`);
      }
    });
  },
});
