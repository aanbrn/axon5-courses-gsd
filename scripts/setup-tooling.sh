#!/usr/bin/env bash
#
# Reproducible local tooling setup: GSD Core (pinned) + sanity checks.
#
#   ./scripts/setup-tooling.sh          # install or verify
#   ./scripts/setup-tooling.sh --force  # reinstall even if the version matches
#
# The repository builds and tests without this script; it only enables the
# /gsd-* agent workflows. See docs/tooling.md.

set -euo pipefail

GSD_VERSION="1.20.1"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

force=false
if [ "${1:-}" = "--force" ]; then
  force=true
fi

fail() {
  printf 'error: %s\n' "$1" >&2
  exit 1
}

command -v node >/dev/null 2>&1 || fail "node is required (>= 24)"

node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
if [ "${node_major:-0}" -lt 24 ]; then
  fail "node >= 24 is required by ${GSD_VERSION}; found $(node --version 2>/dev/null)"
fi
command -v npx >/dev/null 2>&1 || fail "npx is required (comes with npm)"

current=""
if [ -f .opencode/gsd-core/VERSION ]; then
  current="$(tr -d '[:space:]' < .opencode/gsd-core/VERSION)"
fi

if [ "$current" = "$GSD_VERSION" ] && [ "$force" = false ] && [ -f .opencode/gsd-core/bin/gsd-tools.cjs ]; then
  printf 'GSD Core %s is already installed — nothing to do.\n' "$GSD_VERSION"
else
  printf 'Installing GSD Core %s for OpenCode, project scope...\n' "$GSD_VERSION"
  npx --yes "@opengsd/gsd-core@${GSD_VERSION}" --opencode --local
fi

[ -f .opencode/gsd-core/bin/gsd-tools.cjs ] || fail "install failed: .opencode/gsd-core/bin/gsd-tools.cjs is missing"

installed="$(tr -d '[:space:]' < .opencode/gsd-core/VERSION)"
printf 'GSD Core: %s\n' "$installed"

if [ -d .agents/skills ]; then
  skills="$(find .agents/skills -name SKILL.md | wc -l | tr -d ' ')"
  printf 'Agent skills: %s\n' "$skills"
fi

[ -f opencode.jsonc ] && printf 'Project MCP config: opencode.jsonc\n'
[ -f .planning/config.json ] && printf 'Planning config: .planning/config.json\n'

if [ -f .planning/config.json ]; then
  node .opencode/gsd-core/bin/gsd-tools.cjs query init.new-project >/dev/null \
    || fail "gsd-tools cannot read the planning config"
  printf 'gsd-tools: ok\n'
fi

printf '\nDone. Next: /gsd-progress (or /gsd-plan-phase 1).\n'
