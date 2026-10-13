## 1. Contract

- [x] 1.1 In `api/openapi/openapi.yaml`:
      - add `CompleteOrderRequest { method: InPersonPaymentMethod (required), tendered?: string }` as an optional
        request body of `completeOrder`;
      - add a `422` response;
      - describe the "unpaid takeaway or counter orders need a method" rule in the operation summary.

      Verify with `./gradlew openApiGenerate` in `backend/` and `pnpm gen:api` in `frontend/`; both regenerate without
      errors.

## 2. Backend: off by default

- [x] 2.1 Add migration `V17__online_payments_off.sql`, which sets `features.onlinePayments` to `false`. Verify that
      the API starts against a copy of the current local DB and that `GET /settings/public` reports `false`.
- [x] 2.2 Update the existing tests that assumed the seeded `true` (`ApiIntegrationTests`,
      `QrTableOrderingIntegrationTests`) so they turn the flag on themselves. Add a test that a customer takeaway order
      is `PLACED` with the flag off and `PENDING_PAYMENT` with it on. Verify with `./gradlew test`.

## 3. Backend: pay at pickup

- [x] 3.1 In `ordering`:
      - `complete(orderId, request?)` requires a method for `UNPAID` `TAKEAWAY`/`COUNTER` orders and fails otherwise
        with 422 `order.payment_required`;
      - it validates `tendered` (cash only, at least the total), sets `PAID` and `COMPLETED`, and publishes
        `OrderSettledInPerson`.

      Add the ro/en messages.
- [x] 3.2 In `payments`: an `@ApplicationModuleListener` for `OrderSettledInPerson` writes a `CAPTURED`
      `in_person` payment row for the order (method, amount, tendered, `recorded_by`). Verify that `ModularityTests`
      passes, with no module cycle.
- [x] 3.3 Add integration tests for the spec scenarios:
      - cash with change;
      - card;
      - no method → 422 and the order stays unchanged;
      - tendered too low → 422;
      - an order already paid completes with no body and gets no second row;
      - a dine-in round still completes with no body.

      Verify with `./gradlew test`.

## 4. Frontend

- [x] 4.1 `admin` orders dialog:
      - **Unpaid order:** Complete opens a Cash / Card choice, with an optional "Cash received" field and the change
        computed live.
      - **Paid order:** completes directly.

      Add the ro/en strings. Verify with `pnpm typecheck` and by completing orders against the local stack. The
      orders list then shows them as Paid.
- [x] 4.2 `menu-web` checkout: add one line, "paid at pickup" or "pay online after placing", driven by
      `features.onlinePayments`, in ro and en. Verify locally with the flag off: no payment panel, and the order reaches
      the kitchen display.
- [x] 4.3 `menu-mobile` checkout: the same line. Verify with `pnpm typecheck` and on Expo web against the local stack.
- [x] 4.4 `table` bill: when `features.onlinePayments` is off, hide the pay buttons and show "ask the waiter for the
      bill" in ro and en. Verify locally with a QR session: no pay buttons when off, and the buttons are back when on.
- [x] 4.5 Run `pnpm typecheck`, `pnpm test` and `pnpm check:api` in `frontend/` and verify they are all green.

## 5. Docs and end-to-end check

- [x] 5.1 In `docs/todo/payments.md`, cover:
      - online payments are off by default, and customers pay at pickup;
      - how to run a demo with the `fake` provider (switch on, then off afterwards);
      - why the switch must stay off on the public install until a real provider is configured.
- [x] 5.2 Walk through the full local stack:
      - **Flag off:** a website order goes to the kitchen, is marked ready, and is completed in admin with cash. The
        order is Paid, and a payment row exists.
      - **Flag on:** a new order shows "Simulate payment" and reaches the kitchen after it is pressed.
