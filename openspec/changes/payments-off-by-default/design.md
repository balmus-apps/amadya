## Context

- **Settings flag:** the switch is `restaurant_settings.features.onlinePayments` (JSONB). `SettingsService.features()`
  reads a missing key as `false`, but `V2__settings.sql` seeded it `true`.
- **Where the flag is read:**
  - `OrderService` decides between `PENDING_PAYMENT` and `PLACED` for customer takeaway orders.
  - `GuestService.pay` refuses QR table payments.
- **Provider selection:** `PaymentProvider` (ADR 0007) is chosen by `amadya.payments.provider`. `FakePaymentProvider`
  is `matchIfMissing = true`, and `application.yml` defaults `${PAYMENT_PROVIDER:fake}`. `docker-compose.prod.yml`
  doesn't set it, so production runs `fake`.
- **Public simulation endpoint:** `/payments/*/simulate-capture` is `permitAll` in `SecurityConfig`. It is guarded
  only by `provider is FakePaymentProvider`.
- **Admin UI:** the settings page renders every key of `Features` as a `Switch`. A "how to turn it on" hint does not
  fit that generic loop, so it needs a small special case.

## Goals / Non-Goals

**Goals:**
- A production install without provider configuration cannot take, or pretend to take, online payments.
- The admin switch states its precondition instead of failing silently.

**Non-Goals:**
- Provider keys in the database or editable from admin.
- Startup validation of Stripe keys. A misconfigured `stripe` already fails at boot (`error(...)` in
  `StripePaymentProvider`).

## Decisions

1. **A `NoPaymentProvider` adapter instead of a nullable provider.** The adapter has `name = "none"` and becomes the
   `matchIfMissing` bean. `createIntent`, `clientSecret` and `refund` throw
   `UnprocessableException("settings.payment_provider_missing")`. `FakePaymentProvider` now needs `provider=fake`
   explicitly. `PaymentService` keeps a single non-null `PaymentProvider`, so every call site stays as is.
   *Alternative:* `ObjectProvider<PaymentProvider>` with null checks everywhere. More branches for the same result.
   *Refund edge case:* with `none` there are no captured online payments to refund. If a provider is removed while
   paid orders exist, refunds fail loudly. That is correct: someone must refund by hand.

2. **Default `${PAYMENT_PROVIDER:none}`.** `.env`, `.env.example` and `docker-compose.yml` already pass
   `PAYMENT_PROVIDER=fake` for development, and `application-test.yml` sets `fake`, so dev and tests don't change.
   Production has no value and becomes `none`.

3. **`settings` owns a small interface that `payments` implements.** `payments` already depends on `ordering`, which
   depends on `settings`. A direct `settings → payments` call would therefore create a module cycle, which
   `ModularityTests` rejects.
   - **Interface:** `settings` declares `PaymentProviderInfo { fun providerName(): String }` next to
     `RestaurantSettingsApi`.
   - **Implementation:** a small `@Component` in `payments` implements it from the active `PaymentProvider`. The new
     dependency is `payments → settings`, which follows the existing direction.
   - **Use:** `SettingsService` reads it when building the admin DTO and when validating an update.
   - **Check:** `req.features.onlinePayments && !current.onlinePayments && provider == "none"` → 422. Only the
     off → on transition is checked, so a row that is somehow `true` while the provider is `none` can still be saved
     with other edits. The migration makes that state impossible right after the upgrade anyway.
   - *Alternative:* `settings` reads `amadya.payments.provider` from the environment. That duplicates the property and
     its default, and the two could drift.

4. **Contract:** `RestaurantSettings.paymentProvider: { type: string, enum: [none, fake, stripe], readOnly: true }`.
   It is not required, so old clients keep working. The server ignores it on `PUT`. `PaymentIntent.provider` is
   unchanged, because a payment intent is never created with `none`.

5. **Migration `V17__online_payments_off.sql`:**
   `UPDATE restaurant_settings SET features = features || '{"onlinePayments": false}'`. It is unconditional, per
   decision 1 in the proposal. It is a single-row, idempotent data change.

6. **Customer-facing text:**
   - **menu-web and menu-mobile checkout:** read `features.onlinePayments` from public settings, which they already
     load, and show one line near "Place order": "You pay when you pick up the order" or "You pay online after
     placing the order".
   - **Table bill:** when `!features.onlinePayments`, replace the pay buttons with "Ask the waiter for the bill". The
     table app already loads public settings in `App.tsx`; pass the flag down.
   - All new strings come in ro and en.

## Risks / Trade-offs

- [Someone set `PAYMENT_PROVIDER=stripe` on the server outside git] → Nothing breaks. The provider stays `stripe`; only
  the flag is turned off, and the owner can turn it back on in admin.
- [A takeaway order is `PENDING_PAYMENT` on the `fake` provider at deploy time] → After the deploy the provider is
  `none`, so the order can't be paid. It is cancelled by the existing 15-minute timeout, the same outcome as today
  when nobody presses "simulate". Deploy outside opening hours, or accept it.
- [The admin SPA is cached and runs the old version] → The switch stays enabled in the old UI, but the server's 422
  still blocks enabling, and the generic error toast shows the localized message.

## Migration Plan

1. **Deploy as usual.** Flyway applies V17 and the API starts with provider `none`.
2. **Check in production:**
   - `GET /api/v1/settings/public` → `onlinePayments: false`.
   - A test takeaway order goes straight to the queue display or kitchen.
   - `POST /api/v1/payments/<any>/simulate-capture` → 409.
3. **Rollback:** redeploy the previous tag. V17 is data-only and the old code reads the flag as written, so online
   payments stay off after a rollback, which is safe. Turning them back on is a manual admin action.
4. **Later, to go live with a real provider:** set `PAYMENT_PROVIDER` and the provider keys in the server `.env`,
   redeploy, then turn the switch on in admin. This is documented in `docs/todo/payments.md`.
