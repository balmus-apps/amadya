# ADR 0002 — Contract-first OpenAPI, shared generated clients

**Status:** Accepted — 2026-10-04

## Context
Six frontends (Next.js, Expo, four Vite apps) consume the same API. In the reference project, each app
hand-wrote its own fetch services, and the copies drifted apart.

## Decision
- The OpenAPI 3.1 spec in `api/openapi/` is the **source of truth**. Each module has its own file, bundled into `openapi.yaml`.
- Backend: `openapi-generator` (`kotlin-spring`, `interfaceOnly=true`, `useSpringBoot3=true`) generates
  controller interfaces + DTOs; controllers implement them.
- Frontend: `@hey-api/openapi-ts` generates `frontend/packages/api-client` (types, fetch client, TanStack Query hooks).
  Every app depends on this package.
- CI fails if the generated code is stale or the spec has lint errors (Redocly lint).
- Conventions: `/api/v1/...`, problem+json errors (RFC 9457), cursor or page pagination, `Accept-Language` for
  localized content and error messages, ISO-8601 times, money as decimal string + currency.

## Consequences
- An API change is a spec change first; both sides regenerate.
- Real-time channels (STOMP topics, SSE events) are documented in `api/asyncapi.yaml` (added in Phase 1).
