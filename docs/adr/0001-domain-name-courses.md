# ADR-0001: Name the domain `courses`

Date: 2026-11-11

Status: Accepted

## Context

The domain is course enrollment with a seat cap and a FIFO waitlist. The two reference
implementations for this example use different vocabulary:

- AxonIQ's `university-demo` calls its bounded context **Faculty**, reserves **enroll** for
  `StudentEnrolledInFaculty`, and names the course action **subscribe** (`SubscribeStudentToCourse`,
  `StudentSubscribedToCourse`).
- dcb.events calls the example **Course subscriptions** and uses `StudentSubscribedToCourse`.

The name has to identify this domain without adopting either reference's vocabulary by accident.

## Decision

Name the domain `courses`: package and group `io.github.aanbrn.courses`, Gradle modules `courses-*`,
and the same noun in image names.

This decision is about **naming only**. The project keeps its own enrollment vocabulary for v1 —
`EnrollStudent`, `StudentEnrolled`, `StudentDropped`, `WaitlistJoined`, `WaitlistPromoted`, as
listed in `REQUIREMENTS.md`. AxonIQ's `subscribe`/`unsubscribe` is the reference example's wording,
not this project's. Aligning the two would be a separate decision.

## Alternatives considered

- `enrollment` — collides with AxonIQ's faculty enrollment and is generic.
- `course-subscriptions` — matches dcb.events exactly, but is long as a module prefix.
- `faculty` / `university` — AxonIQ context names, broader than this application.
- `registrar` — accurate office metaphor, but not used by either reference.

## Consequences

- Every package, module and image carries `courses`.
- The name is the entity the boundary is built around, not the action; the action vocabulary stays
  as the requirements define it.
- Renaming later would touch every module, package and image tag.

## References

- [AxonIQ `university-demo`](https://github.com/AxonIQ/university-demo) — bounded context Faculty,
  event list including `StudentEnrolledInFaculty` and `StudentSubscribedToCourse`.
- [AxonIQ getting started: Subscribe Student](https://docs.axoniq.io/axon-framework-5-getting-started/5.4/implement-command-subscribe-student/)
  — `SubscribeStudentToCourse` / `StudentSubscribedToCourse`.
- [dcb.events: Course subscriptions](https://dcb.events/examples/course-subscriptions/) — the same
  example under its DCB name.
