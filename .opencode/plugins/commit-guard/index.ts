import { Plugin } from '@opencode/plugin';
import { createState, decideSafely, recordPromptSafely } from './guard.ts';

// The loader resolves this module through its default export, which no TypeScript file in the
// project references, so the IDE reports it as an unused global symbol.
// noinspection JSUnusedGlobalSymbols
export default Plugin.define({
  id: 'commit-guard',
  async setup(ctx) {
    // The escape hatch belongs to the owner and needs no code change: one environment variable
    // skips the guard for the session. Removing the directory stays the durable switch.
    if (process.env.COMMIT_GUARD === 'off') return;

    // One record per session: a GSD prompt sets it, any other prompt clears it.
    const state = createState();

    // The only signal that a GSD command started is the prompt text. Recording it here is what
    // lets the tool hook below tell a workflow commit from an arbitrary one.
    await ctx.session.hook('prompt', (event) => {
      recordPromptSafely(state, event.sessionID, event.prompt.text);
    });

    // Everything that is not a shell tool, or carries no command string, returns before the
    // decision runs — the guard never widens beyond commits.
    await ctx.tool.hook('execute.before', (event) => {
      const verdict = decideSafely(state, event);
      if (verdict.decision === 'deny') throw new Error(verdict.message);
    });
  },
});
