## 1. Admin app

- [ ] 1.1 In `components/layout.tsx`, show the API nav item to MANAGER as well (remove `admin: true`). In ro/en messages,
      rename `nav.apiDocs` to "Aplicații și API" / "Apps & API". Verify with `pnpm --filter @amadya/admin typecheck` and
      the messages parity test (`pnpm --filter @amadya/admin test`).
- [ ] 1.2 In `pages/api-docs/index.tsx`:
      - allow ADMIN and MANAGER;
      - render `AppLinks` for both;
      - render the warning and the lazy `ApiDocsViewer` only for ADMIN;
      - pick the page title and description by role, adding a manager description in ro/en.

      Verify with `pnpm --filter @amadya/admin typecheck`.

## 2. Verification

- [ ] 2.1 Run on the local stack (`docker compose up -d --build admin`) with a MANAGER account created in Admin → Users.
      Check that:
      - the Apps & API item is in the navigation;
      - the page shows the six links with `https://localhost/...`;
      - no warning or documentation is shown, and the browser's network panel shows no request to
        `/api/v1/admin/openapi.yaml` and no Swagger chunk.

      Then, as ADMIN, check that the links, the warning and the documentation all appear as before.
- [ ] 2.2 Confirm the server is unchanged for managers: `GET /api/v1/admin/openapi.yaml` with a MANAGER token returns
      403. This is already covered by `ApiDocsIntegrationTests`; run `./gradlew test --tests '*ApiDocs*'`.
