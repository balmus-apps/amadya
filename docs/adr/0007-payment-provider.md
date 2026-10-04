# ADR 0007 — Online payment provider: Stripe first, Netopia for production cost

**Status:** Accepted — 2026-10-04 (verify current prices with each provider before signing a contract)

## Context
Customers pay takeaway orders online with Apple Pay / Google Pay (and card) from `menu-web` (browser) and `menu-mobile` (Expo).
Tickets are small (≈ 20–60 RON), so the **fixed fee per transaction** matters more than the percentage.

## Comparison
| | **Stripe** | **Netopia Payments** | **EuPlătesc** |
|---|---|---|---|
| Published fees (RO) | 1.5% + 1.00 RON (standard EEA); premium EEA 1.9% + 1 RON; non-EEA 3.25% + 1 RON | ~0.99% + 0.30 RON, or monthly plans | Custom quote (fixed rate or Interchange++) |
| Cost on a 30 RON order | ≈ 1.45 RON (4.8%) | ≈ 0.60 RON (2.0%) | depends on the contract |
| Settlement | Payout schedule T+2…T+7 (check whether RON payouts are available for the merchant's account) | RON, T+1 / T+3 | RON, T+1 / T+2 |
| Apple Pay / Google Pay | Yes, at no extra fee. Native in Expo via `@stripe/stripe-react-native` (PlatformPay) and on the web via the Payment Request Button / Express Checkout | Yes, in the hosted checkout (API v2). A native in-app wallet sheet on mobile needs extra work (hosted page in a WebView or token passing) | Not confirmed; check with them |
| Onboarding | Self-serve, minutes; test mode immediately | Contract + KYC, days | Contract with a partner bank, days–weeks |
| Integration effort | Lowest: mature SDKs, webhooks, test cards, CLI to replay webhooks | Medium: JSON API v2 + IPN, smaller ecosystem | Highest: legacy form-POST + HMAC |
| Security | PCI DSS Level 1, tokenized/hosted fields (we stay SAQ-A), Radar fraud scoring, 3DS2 | PCI DSS Level 1, hosted page (SAQ-A), 3DS2 | PCI DSS, hosted page, 3DS2 |
| Support | Email/chat, English | Romanian, phone | Romanian, phone |

## Decision
- **Quickest:** Stripe. **Cheapest for small tickets:** Netopia (≈ 60% lower cost per order at 30 RON).
  **Safety:** equivalent as long as we never touch card data (hosted fields / wallet tokens only), so we stay at PCI SAQ-A.
- Implement a `PaymentProvider` port in the `payments` module:
  `createIntent(order) → clientParams`, `handleWebhook(payload, signature)`, `refund(payment, amount)`.
- **Phase 1–2: Stripe adapter** (fast development, test mode, native wallet sheets in Expo).
- **Before the first production install: Netopia adapter**, selectable per install with `PAYMENT_PROVIDER=stripe|netopia`.
  It becomes the recommended default for Romanian restaurants because of cost and RON settlement.
- EuPlătesc only if a client already has a contract with them.

## Consequences
- The order flow (`PENDING_PAYMENT` → webhook → `PLACED`) doesn't depend on the provider.
- Webhooks are verified by signature and handled idempotently by `provider_ref`.

## Sources
- https://stripe.com/en-ro/pricing
- https://pronetdesign.ro/en/blog/online-payment-processors-romania-2026/
- https://webghid.ro/articole/plata-online-romania
- https://curs.online/merchant-ecommerce
- https://doc.netopia-payments.com/docs/payment-api/v2.x/introduction
