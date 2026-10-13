## Context

- **The page** (`apps/admin/src/pages/api-docs/index.tsx`) returns "not allowed" unless the session has ADMIN. It then
  renders `PageHeader`, `AppLinks`, the warning and the lazily loaded `ApiDocsViewer`, which holds Swagger UI (about
  1 MB).
- **The nav item** in `components/layout.tsx` carries `admin: true`, so it is hidden from managers.
- **`AppLinks`** reads `publicUrl` through `getRestaurantSettings()`. On the server that is
  `hasAnyRole('ADMIN','MANAGER')`, so no backend change is needed.
- **`GET /admin/openapi.yaml`** is ADMIN-only on the server and stays so.

## Goals / Non-Goals

**Goals:** managers reach the links. The documentation stays admin-only in the UI and on the server, and managers never
download Swagger.

**Non-Goals:** route-level guards in the router. The page checks roles itself, like the other admin pages.

## Decisions

1. **One page, role-dependent content, instead of a second page.**
   - The page renders `AppLinks` for ADMIN and MANAGER.
   - It renders the warning and `<Suspense><ApiDocsViewer/></Suspense>` only for ADMIN. Because `ApiDocsViewer` is a
     `lazy()` import, a manager never fetches the Swagger chunk.
   - The "not allowed" message remains for anyone who is neither (defensive; such users cannot sign in to admin).
   - *Alternative:* a separate "Apps" page. That means two places to maintain for the same links; rejected.
2. **The page description depends on the role.** Admins keep "Every endpoint of this server's API…". Managers get "The
   addresses of this restaurant's apps, to open on a tablet, phone or TV." That way the header never promises
   documentation that isn't there.
3. **Labels:** `nav.apiDocs` becomes "Aplicații și API" / "Apps & API", and the page title follows it. The route stays
   `/api-docs`, so existing bookmarks keep working.

## Risks / Trade-offs

- [The role is read from the token claims in the browser] → It only decides what is rendered. The server still answers
  403 to a manager asking for the spec, so a modified client gains nothing.
