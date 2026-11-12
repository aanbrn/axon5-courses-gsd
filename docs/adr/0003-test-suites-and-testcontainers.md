# ADR-0003: Separate test suites by directory and let them own their infrastructure

Date: 2026-11-11

Status: Accepted

## Context

Tests that need Axon Server or PostgreSQL were initially filtered by JUnit tags inside `src/test`.
Tags are easy to forget, and a stale tag can silently move a Docker-dependent test into the fast
build. Shared Testcontainers setup was duplicated in every service.

## Decision

Use the Gradle `jvm-test-suite` plugin with source sets and name suffixes:

| Directory                    | Suffix                                     |
| ---------------------------- | ------------------------------------------ |
| `src/test/kotlin`            | `Tests`                                    |
| `src/componentTest/kotlin`   | `CT`                                       |
| `src/integrationTest/kotlin` | `IT`                                       |
| `src/e2eTest/kotlin`         | `E2E`                                      |
| `src/negativeControl/kotlin` | `NC` (added with the first class, Phase 8) |

Each suite runs only classes with its suffix, and `checkTestNaming` fails the build on a mismatch.
Integration suites own their infrastructure through Testcontainers: a fresh container per class on
random host ports, removed after the run. Shared setup lives in the `courses-test-support` module,
which is consumed as `implementation(project(":courses-test-support"))`. `./gradlew build` runs unit
tests only and stays Docker-free.

## Alternatives considered

- JUnit tags — silent, easy to mis-tag, and invisible in the path.
- A Compose stack started before the tests — shared state, fixed ports, leftover volumes.
- Duplicating container setup per module — drift across three services.
- Test fixtures in `courses-domain` — pollutes the contract module with Spring and Testcontainers.

## Consequences

- The directory is the contract; no tag filtering remains.
- `courses-test-support` is a test-only module and must never reach a production classpath.
- Integration tests need only a Docker daemon, not a pre-started stack.
