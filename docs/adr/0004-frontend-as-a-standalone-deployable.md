# ADR-0004: Ship the frontend as a standalone deployable

Date: 2026-11-11

Status: Accepted

## Context

The SPA was embedded in `courses-api`: `processResources` copied `courses-frontend/build/dist` into
`static/`, and the gateway served the UI and the API from one origin. That coupled the UI release
cycle and static asset serving to the gateway runtime, and made every API image carry the browser
bundle.

## Decision

Build the SPA in `courses-frontend` and ship it as its own nginx image. `courses-api` serves no
static assets. The image is built from `build/dist` with the Paketo nginx and procfile buildpacks;
`start.sh` renders `config.js` from `COURSES_API_BASE_URL` at container start and fails fast if it
is unset. The browser talks to the gateway cross-origin; the gateway allows only
`COURSES_CORS_ALLOWED_ORIGINS` and is fail-closed when it is empty. Compose exposes the frontend on
`FRONTEND_PORT` (default 3000) and the gateway on `API_PORT` (default 8080).

## Alternatives considered

- Embed the SPA in the gateway — couples release cycles and asset serving.
- A committed nginx Dockerfile — no Dockerfile policy; base-OS patches come from the run image.
- Serve assets through a reverse proxy in front of the gateway — viable, but adds a proxy whose only
  job is static files.

## Consequences

- The UI and gateway version independently and communicate only over HTTP.
- There are two origins: CORS is mandatory in every environment, and the UI URL is a runtime input.
- The image is built by `./gradlew :courses-frontend:dockerBuildImage`; `pack` must be on `PATH`.
