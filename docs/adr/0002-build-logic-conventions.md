# ADR-0002: Share build configuration through build-logic conventions

Date: 2026-11-11

Status: Accepted

## Context

The three service modules repeated the same plugins, toolchain, dependency set, test-suite block and
Spotless configuration. The root build applied Spotless through a `subprojects {}` block with
`apply(plugin = ...)`. The version catalog was the only shared source of versions.

## Decision

Use an included build, `build-logic`, with precompiled script plugins:

- `kotlin-conventions` — Kotlin JVM, JDK 21 toolchain, Spotless + ktlint, the `:courses-platform`
  BOM, and the test-suite naming rule.
- `spring-boot-conventions` — Kotlin Spring, the Boot plugin, `jvm-test-suite`, service
  dependencies, the common `withType<JvmTestSuite>` block, build info and image defaults.
- `frontend-conventions` — npm tasks stay in the module; the convention owns only the Paketo image
  mechanism.

`build-logic` imports the same `gradle/libs.versions.toml` and uses its generated `LibrariesForLibs`
accessors. A module applies one convention and declares only its own dependencies and suites.

## Alternatives considered

- `buildSrc` — recompiles on every build-logic change and is harder to consume as a composite build.
- Duplicate configuration in every module — drift was already visible across the three services.
- Keep the root `subprojects {}` block — hides wiring and forces `apply(plugin = ...)` suppressions.

## Consequences

- A module script declares only its dependencies, image name and suites (roughly forty lines for a
  service); the shared wiring lives in one place.
- A convention change affects every consumer, so it is a deliberate, reviewable change.
- `build-logic` is formatted by Spotless and its `spotlessCheck` runs from the root `check`.
