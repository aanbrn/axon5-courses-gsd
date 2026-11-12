#!/usr/bin/env bash
# Prove the deployed stack from one command:
#   1. ./gradlew bootBuildImage :courses-frontend:dockerBuildImage builds all four images
#   2. docker compose up -d --wait brings the whole stack to healthy
#   3. Axon Server logs one "Application connected" line per service, which proves registration
#   4. the frontend serves the SPA shell on :3000 and renders the gateway URL into config.js
#
# Liveness is the Compose healthcheck. Registration is read from the server's own log, never from
# /actuator/health: this starter combination registers no Axon health indicator, so a green health
# endpoint says nothing about the Axon connection. The integration suite proves the same thing
# self-contained, with Testcontainers; this script proves the Compose stack specifically.
#
# Teardown keeps the Compose volumes, so a local PostgreSQL or event store survives the run.
# Pass --wipe-volumes for a clean slate.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

wipe_volumes=0
for arg in "$@"; do
  case "$arg" in
    --wipe-volumes) wipe_volumes=1 ;;
    *)
      printf 'unknown argument: %s (expected --wipe-volumes)\n' "$arg" >&2
      exit 2
      ;;
  esac
done

cleanup() {
  local status
  status=$?
  trap - EXIT INT TERM
  if [ "$wipe_volumes" = 1 ]; then
    docker compose down -v --remove-orphans >/dev/null 2>&1 || true
  else
    docker compose down --remove-orphans >/dev/null 2>&1 || true
  fi
  exit "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for tool in curl docker; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "required tool is missing: ${tool}" >&2
    exit 1
  fi
done

if [ ! -x ./gradlew ]; then
  echo "required file is missing or not executable: ./gradlew" >&2
  exit 1
fi

echo "==> building images (bootBuildImage + frontend)"
./gradlew bootBuildImage :courses-frontend:dockerBuildImage --no-daemon --console=plain

echo "==> docker compose up -d --wait"
docker compose up -d --wait

wait_for_registration() {
  local service="$1"
  local attempt
  for attempt in $(seq 1 15); do
    if docker compose logs axonserver | grep -q "Application connected: ${service}"; then
      return 0
    fi
    sleep 2
  done
  return 1
}

for service in write read api; do
  if ! wait_for_registration "$service"; then
    echo "Axon Server logged no connection for ${service}" >&2
    docker compose logs axonserver | grep 'Application connected' >&2 || true
    exit 1
  fi
  echo "Axon registration: ${service}"
done

FRONTEND_URL="http://localhost:${FRONTEND_PORT:-3000}"
API_URL="http://localhost:${API_PORT:-8080}"

curl -fsS "${FRONTEND_URL}/" | grep -q '<div id="root">'
curl -fsS "${FRONTEND_URL}/config.js" | grep -q "${API_URL}"
echo "SPA shell served by frontend on ${FRONTEND_PORT:-3000}, gateway URL ${API_URL} rendered into config.js"

echo "stack is up: Axon Server, PostgreSQL, write, read, api and frontend"
