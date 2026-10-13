## Purpose

Controls whether customers pay takeaway orders and QR table bills online through a payment provider, how the
restaurant turns that on or off from settings, and what customers and staff see in each state.

## ADDED Requirements

### Requirement: Online payments are off by default
The restaurant setting `features.onlinePayments` SHALL be `false` on a new install and on any install where it has
never been set. An upgrade that applies this change SHALL also set it to `false` on the existing install, so that
online payment is re-enabled only deliberately.

#### Scenario: New install
- **WHEN** an administrator opens the restaurant settings on a freshly installed system
- **THEN** the Online payments switch is off

#### Scenario: Existing install after upgrade
- **WHEN** an install that had `onlinePayments = true` is upgraded to this version
- **THEN** `GET /settings/restaurant` and `GET /settings/public` both report `features.onlinePayments = false`

### Requirement: The server reports which payment provider is configured
The admin restaurant settings response SHALL include a read-only `paymentProvider` with the provider the server runs
with: `none`, `fake` or `stripe`. The provider SHALL be `none` when the server's provider setting is not configured.
Requests that update the settings SHALL NOT be able to change it. The public settings SHALL NOT expose it.

#### Scenario: Provider not configured
- **WHEN** the server starts without a payment provider configured
- **THEN** `GET /settings/restaurant` returns `paymentProvider: "none"`

#### Scenario: Stripe configured
- **WHEN** the server runs with the Stripe provider and its keys
- **THEN** `GET /settings/restaurant` returns `paymentProvider: "stripe"`

#### Scenario: Value in an update is ignored
- **WHEN** an administrator sends `PUT /settings/restaurant` with a different `paymentProvider`
- **THEN** the response still reports the provider the server runs with

### Requirement: Online payments can only be turned on with a provider
The server SHALL reject an update that sets `features.onlinePayments` to `true` while the provider is `none`. It SHALL
respond with 422, error code `settings.payment_provider_missing`, a localized message (ro/en), and SHALL leave every
setting unchanged. Turning the switch off SHALL always be allowed.

#### Scenario: Enable without a provider
- **WHEN** the provider is `none` and an administrator saves settings with `onlinePayments: true`
- **THEN** the server responds 422 with code `settings.payment_provider_missing` and nothing is saved

#### Scenario: Enable with a provider
- **WHEN** the provider is `stripe` or `fake` and an administrator saves settings with `onlinePayments: true`
- **THEN** the setting is saved and returned as `true`

#### Scenario: Disable
- **WHEN** an administrator saves settings with `onlinePayments: false`
- **THEN** the setting is saved regardless of the provider

### Requirement: The admin switch reflects whether payments can be turned on
The admin restaurant settings page SHALL disable the Online payments switch while `paymentProvider` is `none` and the
switch is off. It SHALL show a hint in the staff locale (ro/en) explaining that a payment provider account must be set
up first. With a provider configured, the switch SHALL work like the other feature switches.

#### Scenario: No provider
- **WHEN** an administrator opens the restaurant settings and `paymentProvider` is `none`
- **THEN** the Online payments switch is off, cannot be turned on, and the hint is shown

#### Scenario: Provider configured
- **WHEN** `paymentProvider` is `stripe`
- **THEN** the administrator can turn the switch on and save

### Requirement: No payment provider means no payment endpoints
When the provider is `none`:
- Creating a payment intent for an order or a table bill SHALL fail with 422 `settings.payment_provider_missing`.
- `POST /payments/{paymentId}/simulate-capture` SHALL fail with 409 `payment.simulation_disabled`.

Simulated capture SHALL work only when the provider is `fake`.

#### Scenario: Simulation in production
- **WHEN** the provider is `none` and anyone calls `POST /payments/{id}/simulate-capture`
- **THEN** the server responds 409 and no order or payment changes

#### Scenario: Simulation in development
- **WHEN** the provider is `fake` and a customer presses "Simulate payment" on an order awaiting payment
- **THEN** the order is marked paid and goes to the kitchen

### Requirement: With online payments off, customers pay on the spot
While `features.onlinePayments` is `false`:
- A takeaway order placed by a customer SHALL go straight to the kitchen (status `PLACED`, unpaid).
- The checkout in the website and the mobile app SHALL tell the customer that the order is paid at pickup.
- The QR table app SHALL NOT offer to pay from the phone. It SHALL show a message to ask the waiter for the bill.
- Staff SHALL record the payment from the waiter or counter screens, as today.

#### Scenario: Takeaway order with payments off
- **WHEN** a customer places a takeaway order on the website while online payments are off
- **THEN** the order is created as `PLACED`, the kitchen receives it, and the customer sees no payment form

#### Scenario: Checkout wording
- **WHEN** a customer opens the checkout on the website or in the app while online payments are off
- **THEN** the checkout says the order is paid at pickup

#### Scenario: Table bill with payments off
- **WHEN** a guest opens the bill in the QR table app while online payments are off
- **THEN** the bill shows totals and a message to ask the waiter, and no pay buttons

#### Scenario: Table payment attempted anyway
- **WHEN** a guest client calls `POST /t/{code}/payments` while online payments are off
- **THEN** the server responds 422 `tables.online_payments_disabled`

### Requirement: Switching off does not strand orders awaiting payment
Turning `onlinePayments` off SHALL NOT change orders already in `PENDING_PAYMENT`. While a provider is configured,
those orders SHALL remain payable. Unpaid ones SHALL still be cancelled after the payment timeout. Only orders placed
after the switch is off go straight to the kitchen.

#### Scenario: Order awaiting payment when the switch goes off
- **WHEN** an order is `PENDING_PAYMENT` with Stripe configured and an administrator turns online payments off
- **THEN** the customer can still complete the payment, and the order then goes to the kitchen

#### Scenario: Abandoned order after switching off
- **WHEN** that order is not paid within the payment timeout
- **THEN** it is cancelled as before
