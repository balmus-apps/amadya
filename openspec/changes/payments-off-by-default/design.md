## Context

- **Settings flag:** the switch is `restaurant_settings.features.onlinePayments` (JSONB). `SettingsService.features()`
  reads a missing key as `false`, but `V2__settings.sql` seeded it `true`.
- **Where the flag is read:**
  - `OrderService` decides between `PENDING_PAYMENT` and `PLACED` for customer takeaway orders.
  - `GuestService.pay` refuses QR table payments.
- **Provider:** chosen by `PAYMENT_PROVIDER` (ADR 0007). The default is `fake`, and it stays that way for demos.
- **Customer phone:** `CustomerContact` already requires `name` and an E.164 `phone`, and `OrderService` already
  requires a customer for takeaway orders. Admin's order dialog shows `tel:` links. The spec captures this; no code
  change is needed.
- **Completing orders:** `POST /orders/{id}/complete` moves an order from `READY` to `COMPLETED`. It is called from
  admin's orders page and from the waiter app for table rounds.
- **Recording payments:**
  - `payment` rows already support staff-recorded payments: method `CASH`/`CARD_POS`, provider `in_person`,
    `tendered`, `recorded_by` (V14).
  - The CHECK `order_id IS NOT NULL OR table_session_id IS NOT NULL` allows a row tied to an order.
  - Today only table sessions use them, through `SessionPayments`.
- **Module directions:** `payments → ordering` (`OrderPayments`) and `tables → payments`. So `ordering` must not call
  `payments`.

## Goals / Non-Goals

**Goals:**
- Every handed-over takeaway or counter order ends up `PAID`, with its payment method on record.
- The public release shows no online-payment UI unless an admin turns it on.

**Non-Goals:**
- Any change to how providers are selected, or to the `fake` simulation endpoint. The simulation stays public by
  design while `fake` is active (see Risks).
- Partial or split payments for takeaway orders. One payment always covers the full total.

## Decisions

1. **Settle inside `completeOrder`, not with a separate "record payment" endpoint.** Pickup is the moment money changes
   hands, so one action is less to forget and cannot leave the order half done.
   - The body is optional: `CompleteOrderRequest { method: InPersonPaymentMethod, tendered?: string }`. Table rounds
     and orders already paid keep calling it with no body.
   - *Alternative:* a separate `POST /orders/{id}/payments`. That needs two clicks and allows completed-but-unpaid
     orders, the gap this change closes.

2. **Where the payment row is written.** `ordering` validates the request and sets `paymentStatus = PAID` and
   `COMPLETED` in its own transaction. It then publishes `OrderSettledInPerson(orderId, method, amount, currency,
   tendered, staffUserId)`. A `payments` `@ApplicationModuleListener` writes the `payment` row (`provider =
   "in_person"`, `CAPTURED`).
   - This follows the existing event style (`OrderCancelled`) and keeps the module direction intact.
   - The payment row is written after commit through the event publication registry (V1), so it is retried if the
     listener fails.
   - The order's `PAID` status is the source of truth for the UI. The row is the audit record.
   - *Alternative:* `ordering` calls a `payments` interface directly. That creates a module cycle, which
     `ModularityTests` rejects.

3. **Which orders need a method.** Orders with `paymentStatus == UNPAID` and channel `TAKEAWAY` or `COUNTER`.
   `DINE_IN` orders belong to a table session and are settled through the table bill, so `complete` keeps ignoring
   payment for them.
   - `tendered` is accepted only with `CASH`, and it must be at least the total; otherwise 422 with
     `order.tendered_too_low` (reuse the tables wording if a key already exists).
   - Change due is `tendered - total`. The admin UI computes and shows it; the `Order` response is not extended.

4. **Migration `V17__online_payments_off.sql`:**
   `UPDATE restaurant_settings SET features = features || '{"onlinePayments": false}'`. It is unconditional, per
   decision 1 in the proposal. It is a single-row, idempotent data change.

5. **Customer-facing text:**
   - **menu-web and menu-mobile checkout:** read `features.onlinePayments` from public settings, which they already
     load, and show one line near "Place order": "You pay when you pick up the order" or "You pay online after
     placing the order".
   - **Table bill:** when `!features.onlinePayments`, replace the pay buttons with "Ask the waiter for the bill". The
     table app already loads public settings in `App.tsx`; pass the flag down.
   - **Admin orders dialog:** on Complete for an unpaid order, show a Cash / Card choice and an optional "Cash
     received" field with the change computed live, before confirming.
   - All new strings come in ro and en.

## Risks / Trade-offs

- [While Online payments is on with the `fake` provider, anyone can call `simulate-capture` and mark an order paid]
  → Acceptable for demos. The switch is off by default after this change. `docs/todo/payments.md` says to keep it off
  on the public install until a real provider is configured.
- [A takeaway order is `PENDING_PAYMENT` at deploy time] → It can still be paid, or it expires after 15 minutes as
  today. Turning the switch off doesn't touch it.
- [An old cached admin build calls `complete` without a body for an unpaid order] → The server returns 422
  `order.payment_required` with a localized message, and staff reload. The rule cannot be bypassed.
- [The listener that writes the payment row fails] → The order already shows `PAID`. Modulith's event publication
  registry keeps the event and retries it on restart, so the row is eventually written.

## Migration Plan

1. **Deploy as usual.** Flyway applies V17.
2. **Check in production:**
   - `GET /api/v1/settings/public` → `onlinePayments: false`.
   - A test takeaway order goes straight to the kitchen.
   - Completing it in admin asks for Cash or Card, and the order then shows Paid.
3. **Rollback:** redeploy the previous tag.
   - V17 is data-only, so online payments stay off, which is safe.
   - Payment rows written by the new flow are ordinary `in_person` rows that the old code ignores for orders.
4. **For a demo:** turn Online payments on in admin, show the simulated flow, then turn it off again.
