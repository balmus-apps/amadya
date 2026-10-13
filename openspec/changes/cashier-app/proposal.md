# Proposal

## Why

The front counter of a fast-food has no screen of its own.
- **Counter orders:** staff can only create them through the API. The waiter app builds table rounds, not counter
  orders.
- **Pickup:** taking the payment at pickup happens in Admin → Orders, a back-office table that isn't made for a queue
  of customers.
- **Phone orders:** to-go orders from customers' phones go straight to the kitchen with nobody looking at them. With
  online payment off (the default since `payments-off-by-default`), nothing stops a prank order from being cooked.

A cashier app gives the person at the counter one screen for the whole front-of-house job:
- take an order and the money;
- confirm or refuse phone orders;
- hand over ready orders.

## What Changes

- **New staff app `apps/cashier`**, served at `/cashier`, for a touchscreen at the counter (tablet in landscape, or a
  PC). Staff with the Cashier, Manager or Admin role log in. Romanian is the default language, English is available.
  - **New order:**
    - pick products with their options and notes from the menu;
    - review the order;
    - take **Cash** (with the amount received and the change) or **Card at the terminal**;
    - send the order. It goes to the kitchen **already paid**.
    - "Pay at pickup" stays available as an exception.
  - **To confirm:** phone orders waiting for the cashier appear live, with a sound. Each shows the customer's name with
    a tap-to-call phone number, the items, the total and the requested pickup time. The cashier **confirms** the order
    (it goes to the kitchen) or **refuses** it with a reason the customer sees.
  - **In progress and ready:** live lists of to-go and counter orders being prepared and ready.
    - **Ready orders** are handed over with "Picked up". An unpaid order first asks Cash or Card, as in admin today.
    - **An order not yet in preparation** can be cancelled with a reason.
- **Phone orders wait for confirmation:**
  - A customer's to-go order paid at pickup now starts as **`AWAITING_CONFIRMATION`** (new status) instead of going
    straight to the kitchen.
  - If nobody confirms it within **N minutes**, it goes to the kitchen automatically, so a busy counter never loses an
    order.
  - N is a restaurant setting, **5 by default**. 0 turns confirmation off.
  - Orders already paid online skip confirmation.
  - **BREAKING** for API clients that list every `OrderStatus`: a new enum value.
- **Paying when ordering:** `POST /orders` accepts an optional `payment { method, tendered? }` for staff counter
  orders. The order is created `PAID`, and the payment is recorded like a pickup payment.
- **Live feed for staff:** a new `GET /orders/events` (Server-Sent Events, staff only) streams order status changes. The
  cashier app updates without polling.
- **Customers see it:**
  - The website and the app show "Waiting for the restaurant to confirm".
  - A refused order shows the reason.
- **Deploy:** a new image `amadya-cashier`, a Caddy route `/cashier`, and an entry in the release workflow. It is a
  static site under 64 MB on the shared VPS, like the waiter app.

## Capabilities

### New Capabilities
- `cashier-app`: The counter app: who can use it, taking and paying counter orders, the live lists, handing orders
  over, and cancelling.
- `takeaway-confirmation`: Customer to-go orders that wait for the restaurant's confirmation: the new status, confirm
  and refuse, automatic confirmation after N minutes, the setting, and what the customer sees.

### Modified Capabilities
- `online-payments`: the requirement "With online payments off, customers pay at pickup" changes. Unpaid customer
  orders now wait for confirmation before the kitchen, instead of going straight to it.

## Impact

- **API contract** (`api/openapi/openapi.yaml`):
  - new `OrderStatus` value `AWAITING_CONFIRMATION`;
  - `POST /orders/{id}/confirm` and `POST /orders/{id}/refuse` (with a reason);
  - an optional `payment` on `CreateOrderRequest`;
  - `GET /orders/events` (SSE);
  - a `takeawayConfirmMinutes` restaurant setting.
- **Backend:**
  - `ordering`: new status, confirm and refuse, automatic confirmation, paying when ordering.
  - `notifications`: staff order stream.
  - `settings`: new setting.
  - `reporting`: excludes unconfirmed orders from sales.
  - Migration `V18`: status check constraint and setting column.
  - ro/en messages.
- **Frontend:**
  - new `apps/cashier`;
  - the waiter app's menu picker and Cash/Card panel move to a shared package, so both apps use them;
  - new status labels in admin, waiter, menu-web, menu-mobile and `packages/i18n`;
  - the setting in Admin → Settings.
- **Infra:** `docker-compose.yml`, `docker-compose.prod.yml`, `infra/caddy/Caddyfile`, `.github/workflows/main.yml`,
  `scripts/release.sh`. The README gets new rows in its apps tables.
- **Activity log** (ADR 0011): confirm, refuse and cancel are recorded with the staff member.

## Out of scope

- **Fiscal receipts** (bon fiscal) and printing order slips. The counter must print through the fiscal printer (ADR 0003,
  Phase 6). That is a separate change.
- Card-terminal integration. The cashier records "Card" by hand after the terminal approves.
- Cash drawer, shifts and end-of-day cash count (Z report).
- Discounts, vouchers and splitting a counter order between payers.
- Push notifications to the customer when an order is refused. The customer's open order page updates live; push stays
  for "ready".
- Table service. It stays in the waiter app.

## Decisions for the owner

Each decision lists the recommended option, which this change assumes.

1. **Confirming phone orders.** *Decided: they wait for the cashier and are confirmed automatically after N minutes.*
2. **When counter customers pay.** *Decided: when ordering.* "Pay at pickup" is the exception.
3. **Default N.** *Recommended: 5 minutes*, the same as the QR tables. 0 turns confirmation off for restaurants that
   don't want it.
4. **Do orders paid online wait for confirmation?** *Recommended: no.* A paid order is not a prank. The cashier still
   sees it in "In progress".
5. **A separate app, or a tab in the waiter app?** *Recommended: a separate app.* The counter screen is laid out for a
   fixed landscape screen and a queue of customers; the waiter app is for a phone on the floor. The two apps share the
   menu picker and payment panel through a package, so nothing is written twice.
6. **Who can use it.** *Recommended: Cashier, Manager, Admin.* Waiters keep the waiter app. Kitchen staff can't take
   money.
