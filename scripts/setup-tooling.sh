#!/usr/bin/env bash
#
# Local tooling: install or verify GSD Core, and report the prerequisites the build needs.
#
#   ./scripts/setup-tooling.sh          # install or verify GSD Core, then report prerequisites
#   ./scripts/setup-tooling.sh --force  # reinstall even if the version matches
#   ./scripts/setup-tooling.sh --check  # verify only — no install — and fail on a missing tool
#
# GSD is optional: `./gradlew build` works without it, given a JDK, Node with npm and actionlint.
# Integration tests and images add Docker, Compose and pack. See docs/tooling.md.
#
# The user-level MCP servers are not wired here: `/setup-tooling --mcp` in OpenCode runs this script
# and then the `connect-mcp-servers` skill, which owns the global config.

set -euo pipefail

GSD_VERSION="1.20.1"
ACTIONLINT_PINNED="1.7.12"
NODE_MIN_MAJOR=24

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

force=false
check_only=false

case "${1:-}" in
  "") ;;
  --force) force=true ;;
  --check) check_only=true ;;
  --mcp)
    printf 'error: --mcp is a command flag, not a script flag. In OpenCode run: /setup-tooling --mcp\n' >&2
    exit 2
    ;;
  *)
    printf 'error: unknown argument: %s (expected --force or --check)\n' "$1" >&2
    exit 2
    ;;
esac

fail() {
  printf 'error: %s\n' "$1" >&2
  exit 1
}

# Prints one prerequisite row: name, state, version, and the task that needs it.
row() {
  detail="$3"
  if [ "${#detail}" -gt 18 ]; then
    detail="$(printf '%s' "$detail" | cut -c1-18)"
  fi
  printf '  %-14s %-7s %-18s %s\n' "$1" "$2" "$detail" "$4"
}

missing=""
missing_count=0

note_missing() {
  missing="${missing}  - $1
"
  missing_count=$((missing_count + 1))
}

version_of() {
  # $1 = command, $2 = sed expression applied to its first output line
  "$1" --version 2>&1 | head -1 | sed -E "$2" || true
}

report_tool() {
  # $1 = label, $2 = command, $3 = sed for the version, $4 = needed-by
  if command -v "$2" >/dev/null 2>&1; then
    detail="$(version_of "$2" "$3")"
    row "$1" "ok" "${detail:--}" "$4"
  else
    row "$1" "missing" "-" "$4"
    note_missing "$1"
  fi
}

report_prerequisites() {
  printf '\nPrerequisites:\n'

  if command -v java >/dev/null 2>&1; then
    row "java" "ok" "$(java -version 2>&1 | head -1 | sed -E 's/.*version "([^"]+)".*/\1/')" \
      "every Gradle task (21 is auto-provisioned)"
  else
    row "java" "missing" "-" "every Gradle task"
    note_missing "java"
  fi

  if command -v node >/dev/null 2>&1; then
    node_version="$(node --version 2>/dev/null | sed 's/^v//')"
    node_major="${node_version%%.*}"
    if [ "${node_major:-0}" -ge "$NODE_MIN_MAJOR" ]; then
      row "node" "ok" "$node_version" "SPA build, npm ci"
    else
      row "node" "too old" "$node_version" "SPA build needs ${NODE_MIN_MAJOR}.x"
      note_missing "node >= ${NODE_MIN_MAJOR} (found ${node_version})"
    fi
  else
    row "node" "missing" "-" "SPA build, npm ci"
    note_missing "node"
  fi

  report_tool "npm" "npm" 's/^([0-9][^ ]*).*/\1/' "SPA build"
  report_tool "actionlint" "actionlint" 's/^([0-9][^ ]*).*/\1/' "./gradlew build (lintWorkflows)"
  report_tool "docker" "docker" 's/^Docker version ([^,]+).*/\1/' "integrationTest, bootBuildImage"
  report_tool "pack" "pack" 's/^([0-9][^ ]*).*/\1/' ":courses-frontend:dockerBuildImage"

  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    row "docker compose" "ok" \
      "$(docker compose version --short 2>/dev/null || echo '-')" "docker compose up"
  else
    row "docker compose" "missing" "-" "docker compose up"
    note_missing "docker compose (the v2 plugin)"
  fi

  if [ "$missing_count" -gt 0 ]; then
    printf '\nMissing or unusable:\n%s' "$missing"
    case "$(uname -s)" in
      Darwin)
        printf 'Install (macOS): brew install actionlint; brew install buildpacks/tap/pack;\n'
        printf 'Docker: brew install --cask docker, or brew install colima docker docker-compose.\n'
        ;;
      Linux)
        printf 'Install (Debian/Ubuntu): sudo apt install docker.io docker-compose-v2 openjdk-21-jdk;\n'
        printf 'actionlint and pack ship as release binaries (github.com/rhysd/actionlint, github.com/buildpacks/pack).\n'
        ;;
      *)
        printf 'Install: actionlint (https://github.com/rhysd/actionlint), pack\n'
        printf '(https://buildpacks.io/docs/tools/pack/), Docker Engine with the Compose v2 plugin.\n'
        ;;
    esac
    case "$missing" in
      *actionlint*)
        printf 'CI pins actionlint %s; a different local version is fine as long as it accepts the workflows.\n' \
          "$ACTIONLINT_PINNED"
        ;;
    esac
  fi
}

if [ "$check_only" = false ]; then
  command -v node >/dev/null 2>&1 || fail "node is required (>= ${NODE_MIN_MAJOR}) to install GSD Core"
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
fi

report_prerequisites

if [ "$check_only" = true ]; then
  if [ "$missing_count" -gt 0 ]; then
    printf '\nCheck failed: install the tools above, then run this command again.\n' >&2
    exit 1
  fi
  printf '\nAll prerequisites are present.\n'
else
  printf '\nDone. Next: /gsd-progress (or /gsd-plan-phase 1).\n'
fi
