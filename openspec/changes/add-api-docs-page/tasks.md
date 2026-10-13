# Tasks

## 1. Contract and backend

- [x] 1.1 Add the `docs` tag and `GET /admin/openapi.yaml` (response `application/yaml`, 401/403) to
  `api/openapi/openapi.yaml`, and list `docs` among the hand-implemented tags in `info.description`. Verify that
  `./gradlew openApiGenerate` succeeds and the frontend's `pnpm gen:api` regenerates without errors.
- [x] 1.2 Copy the spec into the jar via `processResources` (`openapi/openapi.yaml`). Verify with `./gradlew bootJar`
  and `unzip -l build/libs/*.jar | grep openapi.yaml`.
- [x] 1.3 Add the `docs` module with an ADMIN-only controller that serves the classpath file
  (`application/yaml`, `Cache-Control: no-store`). Verify with an integration test:
  - ADMIN gets 200 with a body equal to the repo file
  - MANAGER and WAITER get 403
  - no token gets 401
  - `ModularityTests` still pass

## 2. Admin page

- [x] 2.1 Add `swagger-ui-react@5.33.0` (exact version) to `apps/admin`. Verify `pnpm install` and `pnpm typecheck`
  succeed.
- [x] 2.2 Build the page:
  - `pages/api-docs.tsx` with the lazy viewer
  - authenticated spec fetch, and a `requestInterceptor` adding the bearer token and `X-Amadya-App`
  - "Authorize" hidden
  - the warning banner
  - a "not allowed" notice for non-admins

  Verify on the local stack:
  - every tag renders
  - "Try it out" on `GET /admin/activity` returns 200
  - the call appears in the activity log only if it is a write, so also try a harmless write, e.g. `PUT
    /admin/settings` with unchanged values, and see it logged as app ADMIN
- [x] 2.3 Add the route `/api-docs`, the admin-only sidebar item "API" under Settings, and RO/EN labels (nav item,
  title, warning, not-allowed notice). Verify:
  - the admin messages test passes (same keys in ro and en)
  - signed in as a manager, the item is hidden and the direct URL shows the notice
- [x] 2.4 Check the bundle split. Verify that `vite build` for admin emits the Swagger UI code in a separate chunk and
  the main chunk grows by less than 10 kB.

## 3. Documentation

- [x] 3.1 Mention the page in `backend/README.md` (API section) and in the `docs` tag description of the spec. Verify
  the README link points to `/admin/api-docs`.

## Workflow follow-up

- After the change ships, archive it (`/opsx:archive`) so `openspec/specs/api-docs/spec.md` becomes the living spec.
