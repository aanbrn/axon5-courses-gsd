# Conventions

## Language policy

- **Repository artifacts are English.** Every file written into this repo — source code, comments, KDoc, commit messages, README, design notes, and everything under `.planning/` — is written in English.
- **Responses follow the participant's language, defaulting to English.** No language preference is committed to this repo: participants may work in different languages, so GSD's default is English and the assistant mirrors the language a participant writes in.
- **Never translate identifiers.** Class, function, event, command, tag-key and endpoint names stay in English regardless of the conversation language.
- Subagent prompts stay in English; subagent summaries follow the invoking conversation's language.

## Kotlin

- Kotlin on JVM 21, Gradle Kotlin DSL (`build.gradle.kts`).
- Events and commands are immutable `data class`es; no mutable state in the event contract.
- Prefer `val`, explicit non-null types at boundaries, and no `!!` on Axon/Spring return values.
- Keep Axon decision-state classes small and side-effect free; they are decision inputs, not aggregates.
- Annotate external/API boundaries, not obvious internals.

## Event sourcing

- Events are the only shared contract between write and read sides; slices never depend on each other.
- Tag keys are constants in one shared tag-keys object used by both `@EventTag` and `@EventCriteriaBuilder`.
- Never read a read model inside a command handler — the decision sources events.
- Read-side split: `projection` writes with JPA (mutable entities, Flyway); `query` reads with Spring Data JDBC into immutable types. Do not share JPA entities between them.

## Testing

- Tests use `AxonTestFixture.with(application.configurer())` against the production configurer.
- One fixture per test, unique ids per test, no `Thread.sleep`.
- Contention tests assert on the rejection mechanism, not only on the business outcome, and carry a negative control.
- Test names read as sentences describing the invariant.

## Git

- Commit messages: conventional prefixes (`feat`, `fix`, `docs`, `chore`, `test`, `refactor`, `perf`, `ci`, `build`, `style`, `revert`), optional scope (`feat(command): ...`), imperative, English, ≤72 characters on the subject line.
- The PR title becomes the squash commit in `main`, so it must follow the same format; CI enforces it (`pr-title` workflow).
- One logical change per commit; planning artifacts commit alongside the work they describe.
