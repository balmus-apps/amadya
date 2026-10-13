# Proposal

## Why

The first public release will not take online payments. A real payment provider (Stripe or Netopia) needs an account
in the company's name, with the CUI, IBAN and the legal representative's ID, plus a contract. None of that exists yet
(see `docs/todo/payments.md`). Customers must still be able to order online, have the kitchen prepare the order, and
pay when they pick it up.

Today the platform behaves as if online payment were live:

- The **Online payments** switch exists in admin (Settings → Restaurant → Features), but the first install seeded it
  **on** (`V2__settings.sql`).
- With the switch on and the `fake` provider, a website or app takeaway order waits as "Awaiting payment" behind a
  "Simulate payment" button. If nobody presses it, the order is cancelled after 15 minutes and never reaches the
  kitchen.
- With the switch off, takeaway orders already go straight to the kitchen. But:
  - the checkout doesn't tell the customer they pay at pickup;
  - the QR table app still shows pay buttons that the server then refuses;
  - staff cannot record that a takeaway order was paid at pickup, so every such order stays `UNPAID` forever.

The `fake` provider stays. It is how the product is shown to businesses: turn the switch on, and the simulated online
payment runs end to end.

## What Changes

- **Off by default:** a migration turns `onlinePayments` off on the existing install. Code that finds the flag missing
  already treats it as off. Admins can still turn it on, for example for a demo with the `fake` provider.
- **Pay at pickup:** completing an unpaid takeaway or counter order requires staff to choose how it was paid: **Cash**
  (optionally with the amount handed over, to show the change) or **Card at the terminal**. The payment is recorded
  and the order becomes `PAID`. `POST /orders/{id}/complete` gains an optional body; leaving it out for an unpaid
  order returns 422. Table orders keep being settled through the table bill.
- **Clear wording when online payments are off:**
  - The checkout in menu-web and menu-mobile says the order is paid at pickup.
  - The QR table app hides its pay buttons and says to ask the waiter for the bill.
- **Phone stays mandatory:** a takeaway order already needs a name and a valid phone number, and admin already shows
  the phone as a tap-to-call link. The spec records this as required behavior, so staff can always call to confirm.
- **Switching off mid-flight:** orders already awaiting payment can still be paid, or expire after 15 minutes as today.

## Capabilities

### New Capabilities
- `online-payments`: When customers pay online or at pickup, how the restaurant turns online payment on or off, what
  customers see, and how staff settle unpaid takeaway orders at pickup.

### Modified Capabilities

(none; `openspec/specs/` has no main specs yet)

## Impact

- **API contract** (`api/openapi/openapi.yaml`):
  - `completeOrder` gains an optional `CompleteOrderRequest { method, tendered? }`, reusing `InPersonPaymentMethod`.
  - New 422 error code `order.payment_required`.
  - Both sides regenerate (ADR 0002).
- **Backend:**
  - `ordering`: completing an order requires a payment when the order is unpaid.
  - `payments`: records the pickup payment.
  - New migration `V17`.
  - i18n messages (ro/en).
- **Frontend:**
  - `admin` orders: a Cash / Card choice when completing an unpaid order.
  - `menu-web` and `menu-mobile`: a payment line on the checkout.
  - `table`: bill screen without pay buttons when online payments are off.
  - ro/en messages.
- **Production:** after deploy, new takeaway orders go straight to the kitchen and are paid at pickup. No env change
  is needed.
- **Docs:** `docs/todo/payments.md` notes the off-by-default behavior and how to turn online payment on.

## Out of scope

- Any change to the payment providers. `fake` stays the default and is used for demos; Stripe and Netopia work stays
  in `docs/todo/payments.md`.
- Storing bank details (IBAN, bank, SWIFT) in Amadya. They belong to the payment provider account. The legal name,
  CUI and Reg. Com. are already in restaurant settings.
- A staff confirmation step before the kitchen starts. Orders go straight to the kitchen; staff call the customer
  only when needed.
- Fiscal receipts for pickup payments. They come with the fiscal printer integration (ADR 0003).
- Separate switches for takeaway and the QR table. One switch covers both, as today.

## Decisions for the owner

Each decision lists the recommended option, which this change assumes.

1. **Turn it off on the running install too, not just on new installs.** *Recommended: yes.* The public release
   should not show "Awaiting payment". The alternative is to flip the switch by hand in admin after the deploy.
2. **Recording the payment at pickup.** *Decided: required.* An unpaid takeaway or counter order cannot be completed
   until staff choose Cash or Card, so every handed-over order ends up `PAID`.
3. **Order confirmation by phone.** *Recommended: no extra step.* Orders go to the kitchen at once, and staff call when
   something is unclear. The alternative is an "awaiting confirmation" state that staff must accept first. It is
   safer against prank orders but slows every order down, and it can be added later if prank orders happen.
4. **Bank details in settings.** *Recommended: no (see Out of scope).*
