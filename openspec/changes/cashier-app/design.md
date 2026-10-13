## Context

- **Order lifecycle** (`ordering/internal/OrderService.kt`):
  - `create()` decides `PENDING_PAYMENT` or `PLACED`.
  - `place()` publishes `OrderPlaced`, which the kitchen turns into tickets.
  - Every visible change publishes `OrderStatusChanged`.
  - A `@Scheduled` job (every minute) cancels `PENDING_PAYMENT` orders after `paymentTimeout`.
  - `complete(orderId, PickupPayment?)` takes the payment at pickup and publishes `OrderSettledInPerson`; `payments`
    records the `in_person` row.
- **Statuses:** a CHECK constraint in `V5__ordering.sql` lists them. Every frontend has a status label map, and the
  waiter's `bill.ts` has an exhaustive `switch`.
- **The same pattern in the tables module (ADR 0010):** pending guest rounds are confirmed by a waiter, or automatically
  after `guest_confirm_minutes` (`restaurant_settings` column, 1–60).
- **Live feeds:**
  - `SseHub` in `notifications` serves `/orders/{id}/events` (customer) and `/queue/events`.
  - The waiter app reads `/tables/events` with the generated fetch-based SSE client, which sends the staff token on
    every reconnect (`apps/waiter/src/lib/feed.ts`).
- **Security:** `SecurityConfig` makes `GET /api/v1/orders/*` public, for the tracking page with a token. A new
  `/api/v1/orders/events` would match that rule.
- **Activity log** (ADR 0011): every staff write request is recorded automatically by the `audit` interceptor, with the
  `operationId` as the action. New endpoints are logged without extra code.
- **Shared UI:**
  - `@amadya/customer-core` holds the cart and modifier logic.
  - The waiter app holds the touch UI built on it: the menu picker in `screens/order.tsx` and Cash/Card with change in
    `screens/payment.tsx`.
- **Staff SPAs** (`waiter`, `kitchen`): Vite + React, a `staff-auth` login gate, `use-intl` ro/en, an nginx image of
  about 10 MB RSS with a 64 MB limit, served by Caddy under a path.

## Goals / Non-Goals

**Goals:**
- One counter screen that covers taking orders, taking money, confirming phone orders and handing orders over.
- No logic duplicated between the waiter and cashier apps.
- The kitchen never cooks an unconfirmed unpaid phone order, and never loses one either.

**Non-Goals:**
- Offline mode. The counter needs the API; the app shows it is offline and blocks sending.
- Hardware: receipt printer, cash drawer, card terminal.

## Decisions

1. **New status `AWAITING_CONFIRMATION` instead of a flag on `PLACED`.** Kitchen, queue, reports and customers must
   all treat a waiting order differently. A status makes every consumer handle it explicitly, and TypeScript's
   exhaustive switches point out each place.
   - **Flow:** `create()` for a customer `TAKEAWAY` order goes to `AWAITING_CONFIRMATION` when `UNPAID`,
     `onlinePayments` is off and `takeawayConfirmMinutes > 0`. Online-paid orders keep
     `PENDING_PAYMENT → PLACED`.
   - **Confirm:** `confirm(orderId)` is `transition(AWAITING_CONFIRMATION → PLACED)` and then `place()`.
   - **Refuse:** `refuse(orderId, reason)` reuses `cancelInternal` with the reason. The event says
     `wasPaid = false`, so no refund is attempted.
   - **Automatic confirmation:** a second `@Scheduled(PT1M)` job confirms orders where
     `createdAt + takeawayConfirmMinutes` has passed. It reads the minutes from settings on every run, so a change
     applies at once to orders that are still waiting.
   - *Alternative:* reuse the QR "pending round" model. Takeaway orders have no session, so that would mean a new
     table for no gain.

2. **Settings: a new `takeaway_confirm_minutes` column**, `INT NOT NULL DEFAULT 5 CHECK (BETWEEN 0 AND 60)`. It is
   exposed as `RestaurantSettings.takeawayConfirmMinutes` (optional on PUT, like `guestConfirmMinutes`) and through
   `RestaurantSettingsApi`. It is separate from `guestConfirmMinutes`, because a restaurant may want different times
   for tables and for to-go, and 0 has to mean off here.

3. **Paying when ordering:** `CreateOrderRequest.payment: OrderPayment { method, tendered? }`. It is allowed only for
   staff and `COUNTER`, and is validated exactly like `complete()`.
   - The shared validation becomes one private `validatePayment(order total, payment)` used by both paths.
   - `createOrder` marks the order `PAID` and publishes `OrderSettledInPerson` in the same transaction. The existing
     payments listener records the row with no change.
   - `CompleteOrderRequest` stays, so the contract doesn't break, and its body is the same `OrderPayment` shape.

4. **Staff stream: `GET /orders/events`**, in `notifications`.
   - `SseHub` gets a `staffSubscribers` list.
   - `OrderNotifications` already listens to `OrderStatusChanged` and fans each event out to staff as well.
   - The payload is the existing `OrderStatusChanged` DTO. The app reacts by invalidating its order queries, so no
     second payload format is needed.
   - **Security:** add `/api/v1/orders/events` with `hasAnyRole(ADMIN, MANAGER, WAITER, CASHIER)` **before** the public
     `GET /api/v1/orders/*` matcher. A test asserts 401 without a token.
   - Creating an order already publishes `OrderStatusChanged`: `createOrder` calls `place()` or `statusChanged()`.
     So "To confirm" sees new orders with no extra event.

5. **Endpoints:** `POST /orders/{id}/confirm` (no body) and `POST /orders/{id}/refuse` (`{ reason }`, like
   `CancelOrderRequest`), for the Cashier, Waiter, Manager and Admin roles. Their `operationId`s (`confirmOrder`,
   `refuseOrder`) become the activity log action names. Refuse is separate from cancel, so the log and the customer
   wording can tell them apart.

6. **Shared staff UI package:** a new `packages/staff-pos`, used by both the waiter and cashier apps, containing:
   - `MenuPicker` (search, categories, a product sheet with options, quantities and notes), moved from the waiter's
     `screens/order.tsx`. It returns `NewCartItem`s and knows nothing about tables or counters.
   - `PaymentPanel` (Cash / Card, amount received, quick bank notes, change, too-low warning), moved from the waiter's
     `screens/payment.tsx` without its table-specific modes (whole bill, equal shares, selected items).
   - Their ro/en strings, merged into each app's messages.

   The waiter app switches to the package with no visible change; its tests and typecheck show nothing broke.
   *Alternative:* copy the code into the cashier app. That is faster, but the two copies would drift on every menu
   change.

7. **App layout** (`apps/cashier`, cloned from the waiter app's setup):
   - **Header:** order tabs **New order**, **To confirm (n)**, **In progress (n)**, **Ready (n)**, plus the live/offline
     dot, the sound toggle, the language switch and logout.
   - **New order:** the menu picker on the left and the ticket (lines, total, Send) on the right. On a narrow screen the
     ticket is a bottom sheet. Send opens the payment panel; after success, a full-width card shows the order number
     and the change, until the next tap.
   - **Order cards** use the same component in all three lists. Actions depend on the status and on the user's role:
     cancelling a `PREPARING` order is shown only to Manager and Admin.
   - **To confirm** shows a countdown to automatic confirmation, computed from `createdAt` and the setting in public
     settings. That needs `takeawayConfirmMinutes` in `PublicSettings` too, which is harmless.
   - **Data:** `listOrders(status, channel)` queries are invalidated by the staff stream, with a 30 s safety refetch.

8. **Customer apps:** menu-web and menu-mobile add the `AWAITING_CONFIRMATION` label and description, and show
   `cancelReason` when the order is `CANCELLED`. Today only an icon is shown; `Order.cancelReason` already exists in
   the contract.

## Risks / Trade-offs

- [`/orders/events` falls under the public `/orders/*` rule] → An explicit matcher placed first, plus a 401 test
  (decision 4).
- [A new enum value breaks clients that switch exhaustively] → All in-repo clients are updated in this change. The
  mobile app in the stores is not published yet, so there are no old clients outside.
- [The cashier ignores To confirm, and every order waits N minutes before cooking] → It's the trade-off of decision 1.
  The countdown and the sound make waiting orders visible, and the owner can set N lower or 0.
- [Automatic confirmation runs only once a minute] → An order can wait up to N+1 minutes, which is acceptable.
- [Moving the waiter's picker into a package] → Done as its own first task group, with the waiter's tests, typecheck
  and a manual round on the local stack, before any cashier code depends on it.
- [VPS memory] → One more nginx container with a 64 MB limit, about 10 MB in use, within budget.

## Migration Plan

1. **Migration `V18__takeaway_confirmation.sql`:**
   - drop and re-add the `customer_order` status CHECK constraint with `AWAITING_CONFIRMATION`;
   - add `restaurant_settings.takeaway_confirm_minutes`.
2. **Deploy:** the merge to `main` deploys the api and every image, including the new `amadya-cashier` (ADR 0012).
   - The deploy script pulls every service in `docker-compose.prod.yml`, so the new service starts with no manual
     step.
   - Caddy's config is part of the web image, so `/cashier` is routed after the deploy.
3. **After deploy:** create a Cashier account in Admin → Users and open `https://<domain>/cashier` on the counter
   tablet.
4. **Rollback:** redeploy the previous tag.
   - V18 widens the constraint, so old code still runs.
   - Orders left in `AWAITING_CONFIRMATION` would not be understood by the old code. Before a rollback, confirm or
     refuse them, or set the setting to 0 and wait one minute.
