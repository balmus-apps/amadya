# Design

## Context

- **Generated code:** the spec at `api/openapi/openapi.yaml` already drives generated code. Hand-implemented tags
  (`streams`, `webhooks`) get a generated interface that is simply not implemented; their controllers map the paths
  themselves.
- **Docker build:** the backend Dockerfile builds from the repo root and copies `api/` before compiling, so the spec
  is available to the Gradle build in Docker too.
- **Security and sessions:**
  - `SecurityConfig` lets ADMIN and MANAGER into `/api/v1/admin/**`; finer checks use `@PreAuthorize`.
  - The admin app's session (`@amadya/staff-auth`) holds the access token in memory and refreshes it via
    `accessToken()`.
  - The shared API client already sends `X-Amadya-App: admin`.

## Goals / Non-Goals

**Goals:**
- The served spec can never differ from the running code.
- The page costs nothing until it is opened.

**Non-Goals:**
- Styling Swagger UI to match the admin theme beyond a neutral container.
- Offline docs.

## Decisions

### 1. Serve the spec from the jar, not from the frontend bundle

- `processResources` copies `../api/openapi/openapi.yaml` to `classpath:openapi/openapi.yaml`.
- A small controller in a new `docs` module returns it with `Content-Type: application/yaml` and `Cache-Control:
  no-store`, behind `@PreAuthorize("hasRole('ADMIN')")`.
- The spec lists the path under a new `docs` tag, documented as hand-implemented, the same pattern as `streams`.

*Alternative considered:* bundle the YAML into the admin app. Rejected:
- the admin and API images can briefly run different versions during a deploy, which would show the wrong contract
- the file would be served to anyone who can download the admin JS, i.e. publicly

### 2. Swagger UI via `swagger-ui-react@5.33.0`, lazy-loaded

- The page component `React.lazy`-imports a viewer module that imports `swagger-ui-react` and its CSS. Vite splits it
  into its own chunk, about 1 MB, so the rest of the admin app does not grow.
- The version is pinned to 5.33.0, the newest release older than two weeks (5.33.1 is 12 days old). Its peer range
  accepts React 19.3.

*Alternative considered:* Redoc. It reads better but has no "try it out", which was the point.

*Alternative considered:* Swagger UI from a CDN. Rejected: the admin app loads nothing from third parties today.

### 3. Authentication: fetch the spec with the session, inject the token per request

- The viewer fetches `/api/v1/admin/openapi.yaml` through the existing authenticated client and passes the text as
  `spec`, not as `url`. This way the spec request carries the token.
- A `requestInterceptor` adds `Authorization: Bearer ${await accessToken()}` and `X-Amadya-App: admin` to every
  try-it-out request. Swagger UI awaits a promise returned from the interceptor.
- The spec's `servers: [/api/v1]` is relative, so calls go to the same origin as the page.
- Swagger UI's own "Authorize" button is hidden. It would only invite pasting tokens.

### 4. Access in the admin app

- The navigation item is `admin: true`, the same mechanism as Users.
- The route sits in the existing ADMIN/MANAGER app shell. The page itself renders a short "only administrators" notice
  for non-admins, as the spec requires. The API still returns 403 regardless, so the page's check is for clarity, not
  for security.

## Risks / Trade-offs

- **[Swagger UI or a transitive dependency needs Node polyfills under Vite]** → Verified by a production
  `vite build` and by opening the page in the dev server. If needed, alias the missing module in the admin
  `vite.config.ts` only.
- **[Large dependency tree added to the admin package]** → It is lazy-loaded and confined to one app. A future upgrade
  follows the same "at least two weeks old" rule.
- **[Try-it-out can change real data]** → This is intended for an admin tool. The warning banner and the activity log
  (which records these calls as app ADMIN) cover it.
- **[Swagger UI's CSS clashes with the admin styles]** → It is scoped inside one container on one lazily loaded page.
