# TODO — Payments

Status on 2026-10-05: about two-thirds built. The Stripe path compiles but has **never run against a real Stripe account**.
See also ADR 0007 (provider choice).

## What already exists
- **Backend:** a `PaymentProvider` interface with two adapters:
  - `fake` (active now): the "Simulează plata" (simulate payment) button;
  - `stripe`: creates the payment, receives Stripe's confirmation webhook (signature-checked, repeats handled safely),
    and refunds automatically when a paid order is cancelled. Each payment uses an idempotency key, so a retry can't charge twice.
- **Website (menu-web):** Stripe card form plus Apple Pay / Google Pay buttons.
- **Mobile app (menu-mobile):** Stripe's native payment sheet with Apple Pay / Google Pay.
- **Order flow:** an online order waits as "Așteaptă plata" (awaiting payment). When Stripe confirms the payment, it goes to the kitchen;
  unpaid orders are cancelled after 15 minutes.

## 1. Accounts — owner: restaurant
- [ ] **Stripe account** for the company (SRL): company details, CUI, IBAN, ID of the legal representative.
      Test mode works right away; real money needs Stripe's identity checks to pass.
- [ ] **Test-mode keys:** publishable key, secret key.
- [ ] **Webhook endpoint** `https://<domain>/api/v1/payments/webhooks/stripe` added in Stripe; copy its signing secret.
- [ ] Put the keys in `.env` only (`STRIPE_PUBLISHABLE_KEY`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`), never in chat or git.
- [ ] **Apple Pay on the web:** a real public HTTPS domain (not `localhost`) registered in Stripe (Payment method domains).
- [ ] **Apple Pay in the iOS app:** Apple Developer account ($99/year), a merchant ID, and a certificate set up through Stripe.
- [ ] **Google Pay:** works through Stripe on the web. In the Android app it needs an Expo development or store build.

## 2. Code — owner: development (about 1 day)
- [ ] Switch to `PAYMENT_PROVIDER=stripe` with the test keys. Receive webhooks locally with the Stripe CLI:
      `stripe listen --forward-to localhost:8080/api/v1/payments/webhooks/stripe`.
- [ ] Test end to end on the website and in the app with Stripe test cards (success, declined, 3-D Secure).
- [ ] Handle the `payment_intent.canceled` and refund webhooks.
- [ ] Add the Apple Pay verification file under `/.well-known/` on the website, if Stripe's current instructions still require it.
- [ ] Set the statement descriptor (restaurant name on the card statement).
- [ ] Show payment details on the order in the admin.
- [ ] Automated tests for the Stripe adapter (Stripe test mode or a simulated Stripe server).

## 3. Payments at the counter and at the table (with the waiter app, Phase 4)
- [ ] "Cash" and "Card at the terminal" options in the waiter or cashier screen. It starts as the staff marking the payment as received.
- [ ] Optional later: a direct link to the card terminal (depends on the bank's terminal model).

## 4. Fiscal — check with the accountant
- [ ] **Counter and table payments:** cash or card at the terminal **must** go through the fiscal printer (bon fiscal), which is Phase 6.
- [ ] **Online card payments:** confirm whether they are exempt from the bon fiscal (invoice issued on request) or must also be printed.
      The answer decides whether the fiscal printer must print for online orders.

## 5. Production provider choice
- [ ] **Stripe:** fastest to integrate; about 1.5% + 1 RON per payment (≈ 4.8% on a 30 RON order).
- [ ] **Netopia:** about 0.99% + 0.30 RON (≈ 2% on a 30 RON order), payouts in RON the next day.
      Needs a contract with Netopia and a second adapter (1–2 days); the order flow doesn't change.
- [ ] Confirm current prices with each provider before signing.

## Suggested order
1. Create the Stripe account and put the **test-mode** keys in `.env`. No company verification is needed for this.
2. Development connects it and tests the whole online flow on the website and in the app with test cards (section 2).
3. Before launch: the accountant answers the fiscal question (section 4), and the restaurant chooses Stripe or Netopia for real money (section 5).
