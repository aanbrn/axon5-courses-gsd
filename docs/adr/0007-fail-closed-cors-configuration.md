# ADR-0007: CORS configuration is typed and fail-closed

Date: 2026-11-11

Status: Accepted

## Context

The split-origin frontend requires the gateway to allow its origin. A permissive default (`*`) would
allow any site to call the API; a `@Value` string split by commas had no validation.

## Decision

Bind `courses.cors.allowed-origins` to `@ConfigurationProperties("courses.cors")` with JSR-380
validation. The property is `List<@URL(regexp = "^https?://.*") String>`; blank entries are dropped,
and an empty list registers no CORS mapping. Kotlin emits JVM type annotations only with
`-Xemit-jvm-type-annotations`, so the Kotlin convention enables it. The deployment sets
`COURSES_CORS_ALLOWED_ORIGINS`; `application.yml` keeps an empty default.

## Alternatives considered

- `*` — any origin could call the gateway.
- A localhost default in `application.yml` — wrong for `bootRun` (Vite proxies the domain paths) and
  wrong for every other environment.
- A plain `@Value List<String>` — no validation and no typed metadata.

## Consequences

- The gateway starts with no cross-origin access unless a deployment names the origin.
- An invalid origin fails startup instead of surfacing as a browser error.
- Type-use constraints need the compiler flag; without it the annotation is silently invisible to
  Hibernate Validator.
