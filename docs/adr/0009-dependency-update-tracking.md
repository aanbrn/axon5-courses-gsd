# ADR-0009: Track dependency updates with Dependabot, not a bespoke reporting pipeline

Date: 2026-11-11

Status: Accepted

## Context

Every external version lives in one file, `gradle/libs.versions.toml`, and `build-logic` imports the
same catalog. Two more ecosystems sit outside it: GitHub Actions pins in `.github/workflows/` and
the SPA's npm packages in `courses-frontend/package.json`.

Four kinds of version are read by the build instead of being declared as dependencies: the Gradle
wrapper and its checksum, `axonserver`, `postgres`, and the Paketo builder, buildpack and nginx
versions. No dependency ecosystem sees those, and two of them (`axonserver`, `postgres`) change
behaviour or on-disk layout rather than just a number.

The project is a solo learning sandbox whose subject is a DCB boundary, not release engineering.

Two delivery models were available: automated pull requests, where the upgrade is already applied
and CI has verified it, and a periodic report, where a human reads a list and does the work.

## Decision

Use Dependabot for the three ecosystems it can see, and keep the remaining pins manual:

- `github-actions`, `gradle` and `npm` on a weekly schedule, grouped into one PR per ecosystem —
  Actions at any size, Gradle and npm for minor and patch updates;
- major upgrades of Kotlin, Spring Boot, Axon, React, Vite and TypeScript are ignored, because each
  one invalidates assumptions recorded in `docs/adr/` or `.planning/research/STACK.md` and starts
  from a decision rather than a bot PR;
- `steiger` and `@feature-sliced/steiger-plugin` are grouped, since they share one FSD spec version;
- the wrapper, `axonserver`, `postgres` and the Paketo pins are updated by hand, each with a guard
  task or a documented procedure in `docs/tooling.md`.

## Alternatives considered

- **A bespoke reporting pipeline** — the ben-manes `dependencyUpdates` plugin plus custom Gradle
  tasks, suppression files for held-back coordinates and major-blocked groups, and scheduled
  workflows that fold every report into one "Dependency updates" issue. This is the right shape when
  a repository has many surfaces no ecosystem sees (Helm chart, buildpack pins, wrapper, upstream
  references in docs) and when PRs would compete with a strict merge queue. In a comparable setup it
  costs roughly 1.6k lines of build logic and workflow code plus tests, and it carries its own
  noise: constraint-floor false positives and npm advisories needing dated suppressions. Here it
  would be maintenance debt for four manual pins.
- **Renovate** — strictly more capable: it updates the Gradle wrapper, supports regex managers for
  the image and buildpack pins, and has first-class grouping. Rejected because it is another
  external app and a larger configuration surface to learn and maintain, while the gap it closes is
  four pins that change a few times a year. It becomes the better choice if those pins start to
  drift unnoticed.
- **Dependabot with no `ignore` rules** — every major of Kotlin, Boot and Axon would arrive as a PR
  that cannot pass CI. A permanently red PR queue trains the reviewer to ignore it.
- **No automation** — rejected: the catalog exists so versions are tracked, and untracked versions
  are how a learning project silently ends up on an unsupported stack.

## Consequences

- One grouped PR per ecosystem per week; CI builds it and runs the integration suites, so merging is
  a review of the version jump, not a re-verification of the build.
- Stack majors stay manual. Their migrations are recorded as ADRs, and the ignore list is the
  machine-readable reminder of that policy.
- Four pin classes stay manual, but none can drift silently: `checkComposeImages` compares the
  catalog with `compose.yaml`, and the Paketo pins fail the image build when wrong.
- Dependabot reads only declared dependencies, so a version inside `[versions]` that no library
  references is invisible to it. That gap is documented in `docs/tooling.md` rather than papered
  over.
- Vulnerability alerts and security updates are a separate repository setting, not part of this
  configuration; enabling them later does not conflict with the `ignore` rules above.
- If grouped PRs ever become noise — for example when Axon starts shipping frequent minors — the
  replacement is a scheduled job that opens one issue from a `dependencyUpdates` report, not a port
  of the whole pipeline.

## References

- [Dependabot options reference](https://docs.github.com/en/code-security/dependabot/working-with-dependabot/dependabot-options-reference)
- [ben-manes/gradle-versions-plugin](https://github.com/ben-manes/gradle-versions-plugin) — the
  report task behind the rejected alternative.
