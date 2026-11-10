#!/usr/bin/env bash
# Format everything Prettier owns at repository level: docs, README, workflows, root JSON.
# .planning/, AGENTS.md and the SPA are excluded by .prettierignore.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
exec npx --yes prettier@3.9.10 --write --editorconfig .
