# Proposal

## Why

Settings → API now opens with the links to every app of the install (admin, waiter, cashier, kitchen, TV, menu). Those
links are what a manager needs when setting up a new tablet or TV, but the whole page, including the links, is
admin-only. The API documentation below the links is different: its "Try it out" sends real requests, and it shows the
full API description. That part should stay with the administrator.

## What Changes

- **Managers see the page with the links only.**
  - The navigation item is shown to MANAGER as well as ADMIN, renamed **"Aplicații și API" / "Apps & API"**, since it now
    holds both.
  - A manager opening the page sees the links card ("Apps of this restaurant").
  - The API documentation, its warning and "Try it out" are not rendered for a manager, and the Swagger bundle (about
    1 MB) is not downloaded.
- **Admins see the page as today:** the links card, then the warning and the documentation.
- **The server does not change:**
  - `GET /admin/openapi.yaml` stays admin-only (403 for managers);
  - the links read the install address from `GET /admin/settings`, which managers can already read.
- Other staff (waiter, kitchen, cashier) never see the admin app's settings, as today.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities
- `api-docs`:
  - The page requirement changes: the page is shown to MANAGER too, but only ADMIN gets the documentation.
  - A requirement is added for the links card, which was built in PR #17 without a spec.

## Impact

- **Frontend (`apps/admin`):**
  - `components/layout.tsx`: drop `admin: true` from the API nav item and rename its label;
  - `pages/api-docs/index.tsx`: render the links for ADMIN and MANAGER, and the docs viewer only for ADMIN;
  - ro/en messages.
- **Backend, contract, infra:** none.

## Out of scope

- Opening the API documentation or the spec endpoint to managers.
- Showing the links to waiters, kitchen staff or cashiers. They don't use the admin app; for them the README and the
  setup guide list the addresses.
- Moving the links to a separate page or to the dashboard.

## Decisions for the owner

Each decision lists the recommended option, which this change assumes.

1. **What managers see.** *Recommended: only the links.*
   - The alternative is the full documentation with "Try it out". The server would still refuse a manager anything their
     role doesn't allow, so it is not a security hole.
   - But it exposes every endpoint, including admin-only ones, and invites real data changes from a test page. A manager
     gains nothing from it for running the restaurant.
2. **Name of the menu item.** *Recommended: "Aplicații și API" / "Apps & API".* Managers would see "Aplicații" /
   "Apps" since they get no API. That's a small extra condition, offered as an alternative if you prefer exact labels.
