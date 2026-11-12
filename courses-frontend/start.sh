#!/bin/sh
# Renders the runtime API base URL into config.js, then starts nginx.
# The URL is mandatory: the browser needs the externally reachable gateway, so the container fails
# fast rather than serve a UI that silently calls the wrong origin.
# Runs as the container's `web` process (Procfile), overriding the Paketo nginx buildpack default.
set -e

BASE_URL="${COURSES_API_BASE_URL:-}"
if [ -z "$BASE_URL" ]; then
    echo "COURSES_API_BASE_URL is required: the browser needs the externally reachable gateway URL." >&2
    exit 1
fi

printf 'window.__API_BASE_URL__ = "%s";\n' "$BASE_URL" > /workspace/config.js

exec nginx -p /workspace -c /workspace/nginx.conf -g "pid /tmp/nginx.pid;"
