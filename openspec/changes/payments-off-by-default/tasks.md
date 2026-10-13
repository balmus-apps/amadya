## 1. Contract

- [ ] 1.1 In `api/openapi/openapi.yaml`, add `paymentProvider` to `RestaurantSettings` as
      `{ type: string, enum: [none, fake, stripe], readOnly: true }`, not required, with a description. Verify with
      `./gradlew openApiGenerate` in `backend/` and `pnpm gen:api` in `frontend/`; both regenerate without errors.

## 2. Backend: provider selection

- [ ] 2.1 Add `NoPaymentProvider` (`name = "none"`) in `payments/internal/PaymentProviders.kt` as the
      `matchIfMissing` bean. Its methods throw `UnprocessableException("settings.payment_provider_missing")`. Make
      `FakePaymentProvider` require `provider=fake` explicitly.
- [ ] 2.2 Change `application.yml` to `${PAYMENT_PROVIDER:none}`. Confirm that `.env.example`, `docker-compose.yml`
      and `application-test.yml` still set `fake`.
- [ ] 2.3 Add the `settings.payment_provider_missing` message to `messages.properties` and
      `messages_ro.properties`.
- [ ] 2.4 Add a test that starts the context with provider `none` and checks the endpoints:
      - `POST /orders/{id}/payment-intent` → 422 `settings.payment_provider_missing`;
      - `POST /payments/{id}/simulate-capture` → 409 `payment.simulation_disabled`.

      Verify with `./gradlew test`.

## 3. Backend: settings

- [ ] 3.1 Declare `PaymentProviderInfo` in the `settings` module's public API and implement it in `payments` from the
      active provider. Verify that `ModularityTests` still passes, with no module cycle.
- [ ] 3.2 In `SettingsService`, return `paymentProvider` in the admin DTO and ignore it on update. Reject the off → on
      switch of `onlinePayments` while the provider is `none` with 422 `settings.payment_provider_missing`.
- [ ] 3.3 Add migration `V17__online_payments_off.sql`, which sets `features.onlinePayments` to `false`. Verify
      that the API starts against a copy of the current local DB and that `GET /settings/public` reports `false`.
- [ ] 3.4 Add integration tests for the spec scenarios:
      - enable with `none` → 422 and nothing saved;
      - enable with `fake` → saved;
      - disable always works;
      - `paymentProvider` is reported and cannot be changed by a `PUT`;
      - a customer takeaway order with the flag off is `PLACED`;
      - with the flag on and `fake`, it is `PENDING_PAYMENT`.

      Update existing tests that assumed the seeded `true` (`ApiIntegrationTests`, `QrTableOrderingIntegrationTests`)
      so they turn the flag on themselves. Verify with `./gradlew test`.

## 4. Frontend

- [ ] 4.1 `admin` settings page: disable the `onlinePayments` switch while `paymentProvider === "none"` and the switch
      is off, and show the hint. Add the ro/en strings. Verify with `pnpm typecheck` and by looking at the page
      against the local stack with `PAYMENT_PROVIDER` unset and then set to `fake`.
- [ ] 4.2 `menu-web` checkout: add one line, "paid at pickup" or "pay online after placing", driven by
      `features.onlinePayments` from public settings, in ro and en. Verify locally by placing an order with the flag
      off: no payment panel, and the order shows on the kitchen display.
- [ ] 4.3 `menu-mobile` checkout: the same line. Verify with `pnpm typecheck` and on Expo web against the local stack.
- [ ] 4.4 `table` bill: when `features.onlinePayments` is off, hide the pay buttons and show "ask the waiter for the
      bill" in ro and en. Verify locally with a QR session: no pay buttons when off, and the buttons are back when on
      with `fake`.
- [ ] 4.5 Run `pnpm typecheck`, `pnpm test` and `pnpm check:api` in `frontend/` and verify they are all green.

## 5. Docs and end-to-end check

- [ ] 5.1 In `docs/todo/payments.md`, add "How to turn online payments on": set `PAYMENT_PROVIDER` and the keys in
      the server `.env`, redeploy, then turn the switch on in admin. Note that production defaults to `none`.
- [ ] 5.2 Run the full local stack with `PAYMENT_PROVIDER` unset, which means `none`. Walk through:
      - admin shows the switch disabled with the hint;
      - a website takeaway order goes straight to the kitchen;
      - `simulate-capture` returns 409.

      Then run with `PAYMENT_PROVIDER=fake`, turn the switch on, and check that the simulated payment flow still works
      end to end.
