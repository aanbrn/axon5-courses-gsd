# ADR-0006: Compose runs the whole stack; only the gateway and frontend publish ports

Date: 2026-11-11

Status: Accepted

## Context

Compose started only Axon Server and PostgreSQL, while the services ran through `bootRun`. The demo
needed two coordinated commands, and the frontend image was not exercised by the normal run.

## Decision

Compose defines six services — `axonserver`, `postgres`, `write`, `read`, `api` and `frontend` —
built from four `courses-*` images plus the `axoniq/axonserver` and `postgres` infrastructure
images. Only `courses-api` (`API_PORT`, default 8080) and `courses-frontend` (`FRONTEND_PORT`,
default 3000) publish host ports; `courses-write` and `courses-read` stay on the Compose network.
Every service listens on 8080 inside its container. The gateway CORS origin derives from
`FRONTEND_PORT`, and the frontend API base derives from `API_PORT`. `docker compose up -d --wait` is
the demo path; the development loop still runs the infrastructure in Compose and the services plus
Vite from Gradle.

## Alternatives considered

- Compose for infrastructure only — simpler development loop, but the deployment wiring is untested.
- A Compose profile for the frontend — adds a mode without adding a second environment.
- Publish the write and read ports — exposes internals for no benefit.

## Consequences

- One command proves the deployed stack.
- A port change is one variable each, not two edits.
- Integration tests do not use Compose; they use Testcontainers.
