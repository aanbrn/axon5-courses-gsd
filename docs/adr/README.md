# Architecture Decision Records

Decisions with context, alternatives and consequences. Format: Nygard; location:
`docs/adr/NNNN-slug.md` (the shape GSD's document ingestion recognises).

| ADR                                                  | Title                                                                     | Status   |
| ---------------------------------------------------- | ------------------------------------------------------------------------- | -------- |
| [0001](0001-domain-name-courses.md)                  | Name the domain `courses`                                                 | Accepted |
| [0002](0002-build-logic-conventions.md)              | Share build configuration through build-logic conventions                 | Accepted |
| [0003](0003-test-suites-and-testcontainers.md)       | Separate test suites by directory and let them own infrastructure         | Accepted |
| [0004](0004-frontend-as-a-standalone-deployable.md)  | Ship the frontend as a standalone deployable                              | Accepted |
| [0005](0005-paketo-images-without-dockerfiles.md)    | Build every deployable with Paketo buildpacks, without Dockerfiles        | Accepted |
| [0006](0006-compose-runs-the-whole-stack.md)         | Compose runs the whole stack; only the gateway and frontend publish ports | Accepted |
| [0007](0007-fail-closed-cors-configuration.md)       | CORS configuration is typed and fail-closed                               | Accepted |
| [0008](0008-reproducible-build-info.md)              | Build metadata is reproducible                                            | Accepted |
| [0009](0009-dependency-update-tracking.md)           | Track dependency updates with Dependabot, not a bespoke pipeline          | Accepted |
| [0010](0010-github-native-vulnerability-scanning.md) | Track vulnerabilities with GitHub-native scanning                         | Accepted |
| [0011](0011-session-cookie-authentication.md)        | The SPA authenticates with a session cookie                               | Accepted |

New decisions that involve real alternatives get a new ADR; the short index stays in
`.planning/PROJECT.md` under Key Decisions.
