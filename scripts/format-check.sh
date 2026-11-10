#!/usr/bin/env bash
# Verify repository-level formatting without rewriting files. Used by CI.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
exec npx --yes prettier@3.9.10 --check --editorconfig .
