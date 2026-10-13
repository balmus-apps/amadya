## 1. Contract and database

- [x] 1.1 In `api/openapi/openapi.yaml`:
      - add `AWAITING_CONFIRMATION` to `OrderStatus`;
      - add `OrderPayment { method, tendered? }`, used by `CreateOrderRequest.payment` (staff `COUNTER` only);
      - add `POST /orders/{orderId}/confirm` (`confirmOrder`) and `POST /orders/{orderId}/refuse` (`refuseOrder`, body
        `{ reason }`);
      - add `GET /orders/events` (staff SSE of `OrderStatusChanged`);
      - add `takeawayConfirmMinutes` (0–60) to `RestaurantSettings` and `PublicSettings`.

      Verify with `./gradlew openApiGenerate` in `backend/` and `pnpm gen:api` in `frontend/`.
- [x] 1.2 Add migration `V18__takeaway_confirmation.sql`: re-create the `customer_order` status CHECK with
      `AWAITING_CONFIRMATION`, and add `restaurant_settings.takeaway_confirm_minutes INT NOT NULL DEFAULT 5 CHECK
      (0..60)`. Verify that the API starts against the local DB (`docker compose up -d --build api`).

## 2. Backend: confirmation

- [x] 2.1 In `settings`, expose `takeawayConfirmMinutes` through `RestaurantSettingsApi`, the admin DTO (saved when
      present) and public settings. Verify with an integration test: default 5, saving 0 works, saving 90 returns 400.
- [x] 2.2 In `ordering`:
      - `create()` puts an unpaid customer `TAKEAWAY` order in `AWAITING_CONFIRMATION` when the minutes are > 0;
      - add `confirm()` (→ `PLACED` + `place()`) and `refuse()` (→ `CANCELLED` with the reason, no refund);
      - add a `@Scheduled(PT1M)` automatic confirmation job;
      - in the controller, both endpoints are allowed for Cashier, Waiter, Manager and Admin.

      Add the ro/en messages.
- [x] 2.3 In `reporting`, exclude `AWAITING_CONFIRMATION` from sales.
- [x] 2.4 Add integration tests, in a new `TakeawayConfirmationIntegrationTests`:
      - a waiting order has no kitchen ticket;
      - confirm → `PLACED` and a ticket exists;
      - refuse → `CANCELLED` with the reason;
      - confirming twice → 409;
      - the automatic job confirms an expired order (setting 1 minute, `createdAt` moved back through JDBC);
      - setting 0 → `PLACED` at once;
      - an online-paid order skips confirmation;
      - dashboard sales leave out waiting orders.

      Update tests that expect `PLACED` for customer orders with online payments off (`PayAtPickupIntegrationTests`).
      Verify with `./gradlew test`.

## 3. Backend: paying when ordering and the staff stream

- [x] 3.1 In `ordering`:
      - accept `CreateOrderRequest.payment` only for staff `COUNTER` orders (422 otherwise);
      - share the validation with `complete()`;
      - mark the order `PAID` and publish `OrderSettledInPerson`.

      Add tests for: cash with change, card, low cash → 422 with no order created, a customer request with `payment` →
      422, a payment row recorded, and an order created with a payment completing without a body.
- [x] 3.2 In `notifications`, add the staff subscribers to `SseHub` and the `GET /orders/events` endpoint fed by
      `OrderStatusChanged`. In `SecurityConfig`, put a staff-only matcher before the public `/orders/*` rule. Add tests:
      - 401 without a token;
      - a cashier receives the event of a new phone order and of its confirmation.

      Verify with `./gradlew test`, including `ModularityTests`.

## 4. Shared staff UI

- [ ] 4.1 Create `frontend/packages/staff-pos` (with `package.json`, `tsconfig` and the workspace entry):
      - move `MenuPicker` from the waiter's `screens/order.tsx`;
      - move a table-agnostic `PaymentPanel` (Cash/Card, amount received, quick notes, change) from
        `screens/payment.tsx`;
      - include their ro/en strings.
- [ ] 4.2 Switch the waiter app to `@amadya/staff-pos` with no visible change. Verify with `pnpm --filter waiter test`,
      `typecheck`, and on the local stack: send a round and record a cash payment with change at a table.

## 5. Cashier app

- [ ] 5.1 Scaffold `frontend/apps/cashier`, based on the waiter app:
      - Vite, React, `staff-auth` gate with the Cashier, Manager and Admin roles;
      - ro/en `use-intl`, with locale and sound preferences;
      - the `/cashier/` base path;
      - a Dockerfile and nginx config;
      - a messages parity test like the other apps.

      Verify with `pnpm --filter cashier build`.
- [ ] 5.2 Live data (`lib/feed.ts`, `lib/data.ts`):
      - the `GET /orders/events` stream with the bearer token on every reconnect;
      - a live/offline indicator;
      - `listOrders` queries for the three lists (TAKEAWAY and COUNTER only), invalidated by events, with a 30 s
        refetch;
      - a sound and a toast for a new order to confirm.

      Verify with a unit test of the list grouping and filtering.
- [ ] 5.3 **New order** screen:
      - `MenuPicker` and the ticket;
      - Send → `PaymentPanel` (Cash / Card / Pay at pickup) → `createOrder` with `payment`;
      - a confirmation card with the order number and change;
      - Send is blocked while offline.

      Verify on the local stack: cash with change, card, pay at pickup, and the order appearing on the kitchen
      display.
- [ ] 5.4 **To confirm** list: order cards with the countdown and a tap-to-call phone; Confirm; Refuse with ready-made
      reasons or free text. Verify on the local stack: a website order appears with a sound within seconds; confirm
      sends it to the kitchen; refuse shows the reason on the customer's order page.
- [ ] 5.5 **In progress** and **Ready** lists:
      - Picked up (completes directly when paid; otherwise asks Cash or Card with change);
      - Cancel with a reason, offered for `PREPARING` only to Manager and Admin.

      Verify on the local stack: the kitchen marks an order ready → it moves to Ready live → hand it over.

## 6. Other apps and settings

- [ ] 6.1 Add `AWAITING_CONFIRMATION` labels (ro/en) in `packages/i18n` (customer label and description), admin
      (status badge, filter list, `status.tsx` variant) and the waiter's `bill.ts`. Verify with `pnpm typecheck`; the
      exhaustive switches must compile.
- [ ] 6.2 menu-web and menu-mobile order screens: show the waiting state, and show `cancelReason` for cancelled orders.
      Verify on the local stack by placing an order on the website and refusing it from the cashier app.
- [ ] 6.3 Admin → Settings → Restaurant: a "To-go confirmation (minutes, 0 = off)" field next to the QR one, in ro and
      en. Verify on the local stack by setting 0 and placing an order.

## 7. Deploy and docs

- [ ] 7.1 Add the `cashier` service to `docker-compose.yml` and to `docker-compose.prod.yml` (64 MB limit, same
      hardening as `waiter`); add `/cashier` to `infra/caddy/Caddyfile`; add `cashier` to the build matrix in
      `.github/workflows/main.yml` and to `scripts/release.sh`. Verify with `docker compose up -d --build cashier
      caddy` and by opening `https://localhost/cashier`.
- [ ] 7.2 Docs:
      - in the README, add the cashier app to both app tables (ro/en) with the login hint;
      - in `docs/domain/`, document the takeaway order states with confirmation;
      - in `docs/todo/payments.md`, note that counter orders are paid when ordering.
- [ ] 7.3 End-to-end on the local stack, as a cashier account:
      1. Take a counter order paid in cash. The kitchen marks it ready, and it is handed over.
      2. A website order arrives and is confirmed, cooked and handed over with card.
      3. A second website order is refused, and the customer page shows the reason.
      4. A third order is left alone and is confirmed automatically after the setting's minutes.

      Then run `./gradlew test`, `pnpm typecheck`, `pnpm test` and `pnpm check:api`, and verify they are all green.
