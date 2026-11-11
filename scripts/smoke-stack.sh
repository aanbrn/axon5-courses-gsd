#!/usr/bin/env bash
# Prove the whole stack works from one command:
#   1. docker compose up -d --wait brings Axon Server and PostgreSQL to healthy
#   2. ./gradlew integrationTest --tests '*AxonServerRegistrationIT' proves write, read and api
#      each register with Axon Server (AxonServerConnectionManager.connections()["default"] == true)
#   3. ./gradlew bootRun --parallel starts the three services and each answers /actuator/health
#   4. api serves the built SPA shell from BOOT-INF/classes/static
#
# Registration is asserted inside each app, never by scraping /actuator/health: this starter
# combination registers no Axon health indicator, so a green health endpoint says nothing about
# the Axon connection.
#
# The Gradle run is backgrounded under `set -m` job control so it owns a process group and
# `kill -- "-${RUN_PID}"` reaps the bootRun tasks together with their three JVMs. `setsid` is not
# used because it does not exist on macOS/darwin, and killing only the Gradle PID orphans the
# services. `--no-daemon` keeps the client, the tasks and the JVMs in that single group.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

LOG_FILE="build/bootrun.log"
RUN_PID=""

cleanup() {
  local status
  status=$?
  trap - EXIT INT TERM
  if [ -n "$RUN_PID" ]; then
    kill -- "-${RUN_PID}" 2>/dev/null || true
    wait "$RUN_PID" 2>/dev/null || true
  fi
  docker compose down -v --remove-orphans >/dev/null 2>&1 || true
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

echo "==> docker compose up -d --wait"
docker compose up -d --wait

echo "==> Axon registration for write, read and api"
# --rerun forces the assertion to actually execute: the test's inputs do not include Axon Server
# availability, so an up-to-date result would make a repeat run prove nothing about the stack.
./gradlew integrationTest --tests '*AxonServerRegistrationIT' --rerun --no-daemon --console=plain
echo "all three services registered with Axon Server"

mkdir -p build
echo "==> ./gradlew bootRun --parallel (log: ${LOG_FILE})"
set -m
./gradlew bootRun --parallel --no-daemon --console=plain >"$LOG_FILE" 2>&1 &
RUN_PID=$!

wait_for_health() {
  local port="$1"
  local attempt
  for ((attempt = 1; attempt <= 60; attempt++)); do
    if curl -fsS "http://localhost:${port}/actuator/health" >/dev/null 2>&1; then
      echo "port ${port}: up"
      return 0
    fi
    sleep 2
  done
  echo "port ${port}: not up after 120s" >&2
  echo "--- last 60 lines of ${LOG_FILE} ---" >&2
  tail -n 60 "$LOG_FILE" >&2 || true
  return 1
}

for port in 8081 8082 8080; do
  wait_for_health "$port"
done

curl -fsS http://localhost:8080/ | grep -q '<div id="root">'
echo "SPA shell served by api"

echo "stack is up: Axon Server, PostgreSQL, write, read and api"
