# ADR-0011: The SPA authenticates with a session cookie

Date: 2026-11-12

Status: Accepted

## Context

The SPA is a separate origin from the gateway (ADR-0004), and the gateway's CORS mapping is typed
and fail-closed (ADR-0007). Two credential transport choices were open while Phase 1 built the
skeleton: a session cookie or a bearer token.

A cookie is cross-origin by nature, so it forces two things at once: `allowCredentials(true)` on the
CORS mapping, and `withCredentials` on the client — including for `EventSource`, which cannot set
headers but does send cookies with the right origin. A bearer token avoids CORS credentials but
moves the token into JavaScript, where every XSS becomes an account takeover, and needs an
`Authorization` header on every fetch and a query parameter on the SSE request, because
`EventSource` cannot set one.

## Decision

Authenticate the SPA with an HTTP session cookie:

- the gateway enables credentials on the CORS mapping for the origins in
  `courses.cors.allowed-origins`, and never uses a wildcard origin;
- the SPA sends `credentials: 'include'` on fetches and `withCredentials: true` on `EventSource`;
- no token is stored in JavaScript, and the SSE stream authenticates through the same cookie.

This is a demo authentication: one seeded admin and three seeded students, no registration and no
password management (AUTH-04 is out of scope).

## Alternatives considered

- **Bearer token** — no CORS credentials, but the token lives in JavaScript, must be re-attached to
  every call, and `EventSource` cannot send an `Authorization` header at all, so the SSE stream
  would need the token in the query string, where it lands in logs.
- **Cookie without `allowCredentials`** — the browser silently drops it on cross-origin responses,
  so every request would look unauthenticated while the server logs a successful login.
- **Serving the SPA from the gateway origin** — removes CORS entirely and was rejected in ADR-0004
  for release-cycle reasons; the cookie choice must work in the split topology, not around it.

## Consequences

- The CORS mapping must allow credentials; a wildcard origin is invalid with credentials and is
  already impossible here because the origin list is typed and fail-closed.
- CSRF is handled by the cookie's `SameSite=Lax` attribute rather than a token: the gateway exposes
  no state-changing `GET`, so a cross-site form or link cannot mutate state. A token becomes
  necessary only if the SPA and the gateway stop sharing a site, which forces
  `SameSite=None; Secure` and with it a CSRF token.
- The SSE stream authenticates like any other request, with no token in the URL.
- `EventSource` reconnects reuse the cookie, so `Last-Event-ID` recovery (VIEW-03) needs no extra
  credential handling.
- A future move to OIDC replaces the login mechanism but not this transport decision.

## References

- [Spring Security: CORS with credentials](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)
- ADR-0004 (standalone frontend), ADR-0007 (fail-closed CORS)
