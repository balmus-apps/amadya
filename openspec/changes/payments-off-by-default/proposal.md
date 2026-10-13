# Proposal

## Why

Online payment for customer orders is not ready for real money. It needs a payment provider account (Stripe or Netopia)
in the company's name, with the CUI, IBAN and the legal representative's ID, plus a contract and keys. None of that
exists yet (see `docs/todo/payments.md`).

Today the platform behaves as if online payment were live:

- The **Online payments** switch already exists in admin (Settings → Restaurant → Features), but the first install
  seeded it **on** (`V2__settings.sql`).
- Production does not set `PAYMENT_PROVIDER`, so it runs the development `fake` provider.
- Result: a takeaway order from the website or app waits as "Awaiting payment" behind a **"Simulate payment"** button.
  Anyone can press it, or call `POST /payments/{id}/simulate-capture`, which is public, and mark the order paid. If
  nobody presses it, the order is cancelled after 15 minutes and never reaches the kitchen.
- With the switch off, the QR table app still shows "Pay everything" / "Pay selected items"; the server then refuses
  the payment.

The switch should be off by default, safe to leave off, and turning it on should only be possible once a real
provider is configured.

## What Changes

- **Off by default:** a migration turns `onlinePayments` off on the existing install. Code that finds the flag missing
  already treats it as off.
- **No provider unless one is configured:** a new provider value, `none`, becomes the default when `PAYMENT_PROVIDER`
  is unset. With `none` there is no simulation endpoint and no payment intent. Development keeps `fake` explicitly
  through `.env` and `docker-compose.yml`, and the tests keep it through `application-test.yml`. **BREAKING** for any
  install that relied on the implicit `fake`; only production does today, and that is the bug.
- **Turning it on needs a provider:** the server refuses to save `onlinePayments = true` while the provider is `none`,
  and returns a clear 422 error. The admin switch is disabled in that state. Its hint, in Romanian and English, says
  that a payment provider account must be set up first. The settings response gains a read-only `paymentProvider`
  field (`none` | `fake` | `stripe`), so admin knows which case it is in.
- **Off means pay on the spot, consistently:**
  - Takeaway orders from customers go straight to the kitchen. This already works.
  - The checkout in menu-web and menu-mobile says the order is paid at pickup.
  - The QR table app hides its pay buttons and says to ask the waiter for the bill. The waiter app already records
    cash and card payments.
- **Switching off mid-flight:** orders already awaiting payment can still be paid, or expire after 15 minutes as
  today. No order gets stuck.

## Capabilities

### New Capabilities
- `online-payments`: When customers can pay orders and table bills online, how the restaurant turns this on or off,
  and what customers see when it is off.

### Modified Capabilities

(none; `openspec/specs/` has no main specs yet)

## Impact

- **API contract** (`api/openapi/openapi.yaml`): `RestaurantSettings` gains a read-only `paymentProvider`. A new error
  code is added, `settings.payment_provider_missing` (422). Both sides regenerate (ADR 0002).
- **Backend:**
  - `payments`: provider selection, with a new `none` provider.
  - `settings`: refuse to enable without a provider; expose the provider.
  - New migration `V17`.
  - i18n messages (ro/en).
- **Frontend:**
  - `admin`: settings switch state and hint.
  - `menu-web` and `menu-mobile`: payment line on the checkout.
  - `table`: bill screen without pay buttons.
  - ro/en messages.
- **Production:** after deploy, the next takeaway orders go straight to the kitchen and are paid at pickup. No env
  change is needed on the server.
- **Docs:** `docs/todo/payments.md` gains a short "how to turn online payments on" section.

## Out of scope

- Storing bank details (IBAN, bank, SWIFT) in Amadya. They belong to the payment provider account, which pays the
  money out. The legal name, CUI and Reg. Com. are already in restaurant settings.
- Entering provider keys from admin. Keys stay in the server's `.env`, never in the database.
- The Netopia adapter, real Stripe onboarding and end-to-end tests against Stripe. Those are tracked in
  `docs/todo/payments.md`.
- Separate switches for takeaway and the QR table. One switch covers both, as today.
- Changing what staff-created orders do. They already skip online payment.

## Decisions for the owner

Each decision lists the recommended option, which this change assumes.

1. **Turn it off on the running install too, not just on new installs.** *Recommended: yes.* Production has no real
   provider, so online payment there is only the public "simulate" button. The alternative is to leave the current
   row alone and flip the switch by hand in admin. That still leaves the simulate endpoint open.
2. **Default provider when `PAYMENT_PROVIDER` is unset.** *Recommended: `none`.* The alternative is to keep `fake` as
   the default and set `PAYMENT_PROVIDER=none` in production's `.env`. That is easy to forget on the next install.
3. **Can admin enable the switch with the `fake` provider?** *Recommended: yes.* Development and demos need the
   simulated flow. `fake` is never the default in production after this change, so it only runs where someone set
   it on purpose.
4. **Bank details in settings.** *Recommended: no (see Out of scope).* If the business wants the IBAN printed on
   invoices or receipts later, that is a separate change about invoices, not payments.
