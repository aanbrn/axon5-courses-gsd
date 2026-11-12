# ADR-0005: Build every deployable with Paketo buildpacks, without Dockerfiles

Date: 2026-11-11

Status: Accepted

## Context

The four deployables — `courses-write`, `courses-read`, `courses-api` and `courses-frontend` — need
container images for the Compose stack. Committed Dockerfiles would pin base images and require
manual OS patching.

## Decision

Use Cloud Native Buildpacks:

- JVM services: `bootBuildImage` with `paketobuildpacks/builder-noble-java-tiny`, the `java`,
  `spring-boot` and `health-checker` buildpacks, `BP_JVM_VERSION` and `BP_HEALTH_CHECKER_ENABLED`.
- Frontend: `PackBuildImageTask` with the Paketo `nginx` and `procfile` buildpacks.

No Dockerfile is committed. Every service listens on 8080 inside its image
(`BPE_DEFAULT_SERVER_PORT`). Services that use Axon Server set
`BPE_DEFAULT_AXON_SERVER=axonserver:8124` in their own `bootBuildImage` block, because that hostname
is a service-specific default, not build mechanics. Images are built by `./gradlew bootBuildImage`
and `./gradlew :courses-frontend:dockerBuildImage`. CI does not build them for a normal build or a
pull request; the Playwright e2e job is the single exception, because the browser journey runs
against the real images, not against a mocked or locally assembled stack (ADR-0006).

## Alternatives considered

- A Dockerfile per service — hand-pinned base images and OS patching.
- Jib — no health-checker buildpack and a different runtime layout.
- Building images from Compose — no per-service image names and no reproducible image pipeline.

## Consequences

- Base-OS and JVM patches come from the Paketo run images.
- `pack` and a Docker daemon are required for local image builds.
- The health-checker binary allows Compose health checks in an image that has neither shell nor
  `curl`.
- Buildpack and builder pins are invisible to Dependabot, so
  `.github/workflows/buildpack-updates.yml` compares them with Docker Hub weekly and keeps one open
  issue. The bump itself stays manual: a new buildpack is only proven by an image build, and a bad
  release is held back in the catalog with `# hold-back: <version>` rather than hidden.
  `docs/tooling.md` lists the failure modes this kind of bump has produced — the builder and its
  bundled buildpacks drifting apart, an unversioned reference becoming ambiguous, a floating builder
  flipping the platform, a published release that `pack` cannot extract.
- **A buildpack pin is not the security schedule — the run image is.** CVE fixes for OpenSSL, glibc
  and `ca-certificates` arrive in the run image that the builder references, not in the buildpack
  version, so the images have to be rebuilt for a patch to take effect. They are rebuilt before a
  demo and at least monthly, with `./scripts/smoke-stack.sh`, which builds all four and exercises
  the stack. Images are built in CI only by that e2e job and are never pushed to a registry, so
  nothing scans them either; adding a scanner becomes meaningful only when there is a published
  artifact to scan (backlog item 999.2).
