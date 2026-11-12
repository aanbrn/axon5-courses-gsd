---
phase: 01-build-and-infrastructure-skeleton
plan: 03
subsystem: frontend
tags: [react, vite, typescript, fsd, steiger, oxlint, prettier, gradle-exec]

requires:
  - phase: 01-build-and-infrastructure-skeleton
    provides: "Gradle 9.7.0 build graph with the frontend module already included in settings.gradle.kts and a placeholder directory"

provides:
  - "frontend Gradle module driven by npm Exec tasks (npmInstall, assembleFrontend, lintFsd, formatCheck) under the base plugin"
  - "React 19.3.1 / Vite 8.3.7 / TypeScript 7.0.2 SPA scaffolded into the minimal FSD tree (app, pages/home, shared/ui)"
  - "FSD gate: steiger 0.10.0 --fail-on-warnings plus an oxlint no-restricted-imports rule that closes steiger's shared-segment deep-import gap"
  - "Prettier format:check wired into :courses-frontend:check, lockfile committed for npm ci"
  - "Vite dev server on :5173 with /api proxied to :8080"

affects: [01-04, 01-05, 01-06, phase-2, phase-5, phase-8]

actuals:
  tokens: 1600
  tasks: 3
  commits: 2

tech-stack:
  added:
    - "React 19.3.1 + react-dom 19.3.1"
    - "Vite 8.3.7 with @vitejs/plugin-react 6.1.2"
    - "TypeScript 7.0.2 (paths-only alias; baseUrl removed)"
    - "steiger 0.10.0 + @feature-sliced/steiger-plugin 0.8.2"
    - "oxlint ^1.90.0 (pinned explicitly) with no-restricted-imports patterns"
    - "Prettier 3.9.10 as a frontend devDependency"
  patterns:
    - "FSD import direction enforced by steiger; public index.ts per slice"
    - "Deep imports rejected by an oxlint regex on @/<layer>/<slice>/<...>"
    - "Gradle Exec tasks under the base plugin run npm and attach to assemble/check"
    - "Committed package-lock.json so Gradle can run npm ci, never npm install"

key-files:
  created:
    - courses-frontend/build.gradle.kts
    - courses-frontend/package.json
    - courses-frontend/package-lock.json
    - courses-frontend/steiger.config.js
    - courses-frontend/.oxlintrc.json
    - courses-frontend/.prettierignore
    - courses-frontend/.gitignore
    - courses-frontend/index.html
    - courses-frontend/vite.config.ts
    - courses-frontend/tsconfig.json
    - courses-frontend/tsconfig.app.json
    - courses-frontend/tsconfig.node.json
    - courses-frontend/src/app/main.tsx
    - courses-frontend/src/app/index.css
    - courses-frontend/src/pages/home/index.ts
    - courses-frontend/src/pages/home/ui/HomePage.tsx
    - courses-frontend/src/shared/ui/index.ts
    - courses-frontend/src/shared/ui/AppShell.tsx
  modified: []

key-decisions:
  - "Empty FSD layers are not created: only app, pages/home and shared/ui exist so steiger's insignificant-slice rule stays green"
  - "The @/ alias is declared with tsconfig paths alone (no baseUrl) plus a matching Vite resolve.alias"
  - "steiger cannot see deep imports inside shared/*, so lint:fsd also runs oxlint with a no-restricted-imports regex"
  - "Gradle runs npm ci against the committed lockfile; npm install never runs inside the build"

patterns-established:
  - "FSD slice: public index.ts re-exports from ui/, internals are private"
  - "Negative-control proof: both an upward import and a deep import must exit non-zero before the gate is trusted"
  - "npm scripts are the single implementation of build/lint/format; Gradle only invokes them"

requirements-completed: [FE-01, FE-02, QUAL-01]

coverage:
  - id: D1
    description: "SPA builds to courses-frontend/dist with a hashed JS/CSS bundle and the courses title"
    requirement: "FE-01"
    verification:
      - kind: automated_ui
        ref: "npm --prefix frontend run build#dist/index.html + dist/assets/index-B7Fg37H_.js"
        status: pass
    human_judgment: false
  - id: D2
    description: "FSD structure is legal (one-way layers, public APIs) and the gate is green"
    requirement: "FE-01"
    verification:
      - kind: other
        ref: "npm --prefix frontend run lint:fsd#steiger: No problems found / oxlint: clean"
        status: pass
    human_judgment: false
  - id: D3
    description: "The FSD gate fails on an upward import (fsd/forbidden-imports) and on a deep import (oxlint no-restricted-imports)"
    requirement: "FE-02"
    verification:
      - kind: other
        ref: "negative control 1: @/pages/home imported from shared/ui/AppShell.tsx#exit 1, fsd/forbidden-imports"
        status: pass
      - kind: other
        ref: "negative control 2: @/shared/ui/AppShell imported from pages/home/ui/HomePage.tsx#exit 1, eslint(no-restricted-imports)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Prettier format:check passes over the SPA and is part of :courses-frontend:check"
    requirement: "QUAL-01"
    verification:
      - kind: other
        ref: "npm --prefix frontend run format:check#All matched files use Prettier code style!"
        status: pass
    human_judgment: false
  - id: D5
    description: "One Gradle invocation builds, lints and formats the SPA with no separate npm step"
    requirement: "QUAL-01"
    verification:
      - kind: other
        ref: "./gradlew build --no-daemon#:courses-frontend:assemble + :courses-frontend:check executed"
        status: pass
    human_judgment: false

duration: 20min
completed: 2026-11-11
status: complete
plan_head_before: 4ffca6486f4a2aa0bc4dc3e68f7fbee3f435e279
plan_head_after: 94fd4f80cc41592ef4d67777c0837fc24aa1c7fc
---

# Phase 1 Plan 03: Build and infrastructure skeleton Summary

**The create-vite React SPA reduced to a legal Feature-Sliced Design tree, gated by steiger plus an
oxlint deep-import rule and Prettier, and driven entirely from the Gradle graph through `npm ci`.**
Both negative controls were proven: an upward import fails on `fsd/forbidden-imports`, a deep import
fails on oxlint `no-restricted-imports` — the case steiger 0.10.0 silently allows inside `shared/*`.

## Performance

- **Duration:** ~20 min
- **Tasks:** 3/3
- **Files created:** 18 hand-written files (plus the generated `package-lock.json`)
- **Diff size:** 6,315 characters of hand-written source ≈ 1,600 estimateTokens (lockfile excluded
  from the measurement — it is generated, and counting it would swamp the hand-written signal)

## Accomplishments

- `npm --prefix frontend run build` (`tsc -b && vite build`) produces `courses-frontend/dist/index.html`,
  `dist/assets/index-B7Fg37H_.js` and `dist/assets/index-Clt-HEf6.css`.
- The source tree is exactly five FSD files across three layers — `app`, `pages/home`, `shared/ui` —
  with a public `index.ts` per slice and no placeholder `widgets`/`features`/`entities` folders.
- `npm --prefix frontend run lint:fsd` runs `steiger ./src --fail-on-warnings && oxlint` and exits 0
  (`✔ No problems found!`).
- `npm --prefix frontend run format:check` exits 0 after one sanctioned `npm run format` pass.
- `./gradlew :courses-frontend:assemble :courses-frontend:check` and the full `./gradlew build --no-daemon` both
  succeed; `npm ci` runs inside Gradle, never `npm install`.

## Task Commits

1. **Task 1: Scaffold the Vite React-TS app and reduce it to the FSD tree** — `aedc282` (feat)
2. **Task 2: Add steiger, Prettier, and the Gradle Exec task wrapper** — `94fd4f8` (chore)
3. **Task 3: Prove the FSD gate is green and both negative controls fail** — no commit (verification
   only; both temporary edits were reverted and the tree is clean at `94fd4f8`)

**Plan metadata:** the SUMMARY commit that follows (docs). Measured commits in this plan: 2.

## Files Created/Modified

- `courses-frontend/build.gradle.kts` — `base` plugin; `npmInstall` (`npm ci`), `assembleFrontend`
  (`npm run build`), `lintFsd` (`npm run lint:fsd`), `formatCheck` (`npm run format:check`);
  `assemble` → `assembleFrontend`, `check` → `lintFsd` + `formatCheck`.
- `courses-frontend/package.json` — scripts `dev`/`build`/`preview`/`format`/`format:check`/`lint`/`lint:fsd`;
  devDependencies `steiger@0.10.0`, `@feature-sliced/steiger-plugin@0.8.2`, `prettier@3.9.10`,
  `oxlint@^1.90.0`.
- `courses-frontend/package-lock.json` — committed lockfile (84 KB) so `npm ci` is reproducible.
- `courses-frontend/steiger.config.js` — `defineConfig([...fsd.configs.recommended])`.
- `courses-frontend/.oxlintrc.json` — template React rules plus `no-restricted-imports` with
  `patterns[0].regex = "^@/[^/]+/[^/]+/.+"` and the message "Import from the slice's public API
  (index.ts), not its internals".
- `courses-frontend/.prettierignore` — `dist/`, `node_modules/`, `playwright-report/`, `test-results/`,
  `package-lock.json`.
- `courses-frontend/vite.config.ts` — `resolve.alias['@']`, `build.outDir = 'dist'`, `server.port = 5173`,
  `server.proxy['/api'] = 'http://localhost:8080'`.
- `courses-frontend/tsconfig.app.json` — `paths: { "@/*": ["./src/*"] }`, no `baseUrl`.
- `courses-frontend/index.html` — `<title>Axon 5 DCB Courses</title>`, entry `/src/app/main.tsx`.
- `courses-frontend/src/app/main.tsx` — React root rendering `<HomePage/>`, explicit error when `#root` is
  missing.
- `courses-frontend/src/app/index.css` — base typography and full-height `#root`.
- `courses-frontend/src/pages/home/index.ts` + `ui/HomePage.tsx` — `<h1>Course catalog</h1>` inside the shell.
- `courses-frontend/src/shared/ui/index.ts` + `AppShell.tsx` — header "Axon 5 DCB Courses" and
  `<main data-testid="app-shell">`.
- Deleted: `courses-frontend/.gitkeep` (placeholder from plan 01-01), and the create-vite leftovers
  `src/App.tsx`, `src/App.css`, `src/main.tsx`, `src/index.css`, `src/assets/`, `public/`,
  `README.md`.

## Verification Run

| Command | Result |
|---|---|
| `npm --prefix frontend install` (one-time, generates the lockfile) | pass — 94 packages, 0 vulnerabilities |
| `npm --prefix frontend run build` | pass — `dist/index.html` + hashed JS/CSS |
| `grep -c '"baseUrl"' courses-frontend/tsconfig.app.json` | pass — `0` (exit 1, no match) |
| `find courses-frontend/src -type f` | pass — exactly 6 files: app/main.tsx, app/index.css, pages/home/index.ts, pages/home/ui/HomePage.tsx, shared/ui/index.ts, shared/ui/AppShell.tsx |
| `npm --prefix frontend run lint:fsd` | pass — `✔ No problems found!`, exit 0 |
| `npm --prefix frontend run format:check` | pass — "All matched files use Prettier code style!", exit 0 |
| Negative control 1 — `import { HomePage } from '@/pages/home'` in `shared/ui/AppShell.tsx` | pass — exit 1, `✘ Forbidden import from higher layer "pages".` / `fsd/forbidden-imports` |
| Negative control 2 — `import { AppShell } from '@/shared/ui/AppShell'` in `pages/home/ui/HomePage.tsx` | pass — exit 1, `eslint(no-restricted-imports): '@/shared/ui/AppShell' import is restricted from being used by a pattern` |
| `test -z "$(git -C frontend status --porcelain -- src/shared/ui/AppShell.tsx src/pages/home/ui/HomePage.tsx)"` | pass — both controls reverted, tree clean |
| `npm --prefix frontend ci` | pass — reproducible install from the lockfile |
| `./gradlew :courses-frontend:assemble :courses-frontend:check --no-daemon` | pass — `BUILD SUCCESSFUL`, 4 tasks executed |
| `./gradlew build --no-daemon` | pass — `:courses-frontend:assemble` and `:courses-frontend:check` both in the graph |

### Negative control 1 output (upward import)

```text
┌ src/shared/ui/AppShell.tsx:1:27
✘ Forbidden import from higher layer "pages".
│
└ fsd/forbidden-imports: https://github.com/feature-sliced/steiger/tree/master/packages/steiger-plugin-fsd/src/forbidden-imports

───────────────────────────────────────────────
 Found 1 error (none can be fixed automatically)
```

### Negative control 2 output (deep import)

```text
> steiger ./src --fail-on-warnings && oxlint
✔ No problems found!
src/pages/home/ui/HomePage.tsx:1:1: error eslint(no-restricted-imports): '@/shared/ui/AppShell' import is restricted from being used by a pattern. help: Import from the slice's public API (index.ts), not its internals
```

Note the two lines above the oxlint error: steiger itself reported **no** problems for the very
import the plan calls out (RESEARCH Pitfall P10). The oxlint rule added in Task 2 is what makes FE-02's
"deep import" failure mode real.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] `package-lock.json` excluded from Prettier**
- **Found during:** Task 2 (`format:check`)
- **Issue:** `prettier --check .` inside `courses-frontend/` would also check the 84 KB generated lockfile;
  the repo already treats lockfiles as unformatted (`**/package-lock.json` in the root
  `.prettierignore`).
- **Fix:** `package-lock.json` added to `courses-frontend/.prettierignore` so the gate checks authored files.
- **Files modified:** `courses-frontend/.prettierignore`
- **Verification:** `npm --prefix frontend run format:check` exits 0 and lists no lockfile.
- **Committed in:** `94fd4f8` (Task 2 commit)

**2. [Rule 3 - Blocking] create-vite 9.3.4 template does not match the researched file list**
- **Found during:** Task 1
- **Issue:** the template ships `public/favicon.svg`, `public/icons.svg`,
  `src/assets/{hero.png,react.svg,vite.svg}`, `README.md` and no `src/vite-env.d.ts`
  (it uses `"types": ["vite/client"]` instead). It also ships an `.oxlintrc.json` that the plan
  expected to author from scratch.
- **Fix:** deleted every template asset and the template README, dropped the favicon `<link>` from
  `index.html`, kept the template `.oxlintrc.json` and extended it with the deep-import rule.
- **Files modified:** `courses-frontend/index.html`, `courses-frontend/.oxlintrc.json`; deleted the asset folders.
- **Verification:** `find frontend -type f -not -path './node_modules/*'` lists only FSD sources and
  config; `npm run build` and `lint:fsd` both pass.
- **Committed in:** `aedc282` (sources) and `94fd4f8` (oxlint config)

**3. [Rule 3 - Blocking] Deleted the create-vite `README.md`**
- **Found during:** Task 1
- **Issue:** the generated README documents `src/App.tsx`, the React Compiler and Oxlint setup of a
  template that no longer exists; leaving it would be a false document.
- **Fix:** removed it. A short FSD/README note can be added when the first real slice lands (Phase 2).
- **Committed in:** `aedc282`

**4. [Rule 1 - Bug] Missing `#root` would crash with a bare non-null assertion**
- **Found during:** Task 1
- **Issue:** the template used `document.getElementById('root')!`; the conventions forbid `!!`-style
  null assertions at boundaries.
- **Fix:** `app/main.tsx` throws a named error when `#root` is absent, then renders.
- **Verification:** `tsc -b` passes; the bundle renders under `vite build`.
- **Committed in:** `aedc282`

### Noted, not changed

- Task 1 and Task 2 both list `courses-frontend/package.json`. The devDependencies were installed together
  with the template dependencies so the lockfile could be generated once, so `package.json` and
  `package-lock.json` landed in the Task 1 commit (`aedc282`); Task 2 then added only the linter and
  Gradle wiring. No behavioural difference, no extra commit.
- `npm ci` prints `1 package has install scripts not yet covered by allowScripts: fsevents@2.3.3`.
  That is npm's advisory for an optional macOS watcher dependency of the template's Vite/React stack;
  no install script runs and the build is unaffected.

---

**Total deviations:** 4 auto-fixed (2 blocking, 1 missing critical functionality, 1 bug) + 1 commit-scope note.
**Impact on plan:** none on scope — every change is inside the `courses-frontend` module and serves the plan's
own acceptance criteria.

## Threat Surface

No new trust boundary beyond the plan's threat model: the only new surface is the Vite dev proxy
`/api → http://localhost:8080`, already listed under Trust Boundaries (`Vite dev proxy → api`), and it
exists only in the dev server, not in the built bundle. `npm ci` runs against the committed lockfile
(T-01-05), and every added package was pre-approved in the RESEARCH package-legitimacy audit — none of
`steiger`, `@feature-sliced/steiger-plugin`, `prettier`, `oxlint` declares an install script.

## Known Stubs

None. `HomePage` renders only `<h1>Course catalog</h1>` on purpose — it is the shell the plan asks
for, and Phase 2 fills the page with real data-loading slices. No hardcoded empty state, mock data or
placeholder copy flows into the UI.

## Next Phase Readiness

- `:courses-api:processResources` can copy `:courses-frontend:dist` into `BOOT-INF/classes/static` (plan 01-04's
  shape) — `:courses-frontend:assembleFrontend` is the task to depend on.
- New slices must be created with a public `index.ts` and imported as `@/<layer>/<slice>`; the oxlint
  rule rejects anything deeper.
- `pages` may hold one reference per slice only after a slice exists; do not create `widgets`,
  `features` or `entities` folders before their first real slice, or steiger fails on
  `fsd/insignificant-slice`.
- Playwright (plan 01-06) can assert the page title `Axon 5 DCB Courses` and
  `data-testid="app-shell"`. The `courses-frontend/.prettierignore` and the FSD `data-testid` conventions are
  already consistent with `courses-frontend/e2e/` living outside `src`.

---

*Phase: 01-build-and-infrastructure-skeleton*
*Completed: 2026-11-11*

## Self-Check: PASSED

All 15 created files exist, all five create-vite template artifacts are removed, and both task commits (`aedc282`, `94fd4f8`) are ancestors of HEAD.
