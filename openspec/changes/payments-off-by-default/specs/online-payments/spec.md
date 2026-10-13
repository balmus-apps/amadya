## Purpose

Controls whether customers pay takeaway orders and QR table bills online or at pickup, how the restaurant turns online
payment on or off from settings, and how staff settle unpaid takeaway orders when they hand them over.

## ADDED Requirements

### Requirement: Online payments are off by default
The restaurant setting `features.onlinePayments` SHALL be `false` on a new install and on any install where it has
never been set. An upgrade that applies this change SHALL also set it to `false` on the existing install. Admins SHALL
be able to turn it on and off from the restaurant settings with any configured provider, including the simulated one
used for demos.

#### Scenario: New install
- **WHEN** an administrator opens the restaurant settings on a freshly installed system
- **THEN** the Online payments switch is off

#### Scenario: Existing install after upgrade
- **WHEN** an install that had `onlinePayments = true` is upgraded to this version
- **THEN** `GET /settings/restaurant` and `GET /settings/public` both report `features.onlinePayments = false`

#### Scenario: Demo with simulated payments
- **WHEN** an administrator turns Online payments on while the server runs the simulated provider
- **THEN** a new customer takeaway order waits for payment, and "Simulate payment" marks it paid and sends it to the
  kitchen

### Requirement: Customers order with a reachable phone number
A takeaway order placed by a customer SHALL require a name and a phone number in international format. Staff SHALL see
both on the order in admin, with the phone usable as a call link.

#### Scenario: Missing phone
- **WHEN** a customer submits a takeaway order without a valid phone number
- **THEN** the order is rejected with a validation error and nothing is created

#### Scenario: Staff calls the customer
- **WHEN** a staff member opens a takeaway order in admin
- **THEN** the customer's name and phone are shown, and tapping the phone starts a call

### Requirement: With online payments off, customers pay at pickup
While `features.onlinePayments` is `false`:
- A takeaway order placed by a customer SHALL go straight to the kitchen (status `PLACED`, payment `UNPAID`).
- The checkout in the website and the mobile app SHALL tell the customer that the order is paid at pickup.
- The order page SHALL NOT show a payment form.

#### Scenario: Takeaway order with payments off
- **WHEN** a customer places a takeaway order on the website while online payments are off
- **THEN** the order is created as `PLACED`, the kitchen receives it, and the customer sees no payment form

#### Scenario: Checkout wording
- **WHEN** a customer opens the checkout on the website or in the app while online payments are off
- **THEN** the checkout says the order is paid at pickup

### Requirement: The table app does not offer phone payment when it is off
While `features.onlinePayments` is `false`, the QR table app SHALL show the bill totals without pay buttons, together
with a message to ask the waiter for the bill. The server SHALL keep refusing guest payments.

#### Scenario: Table bill with payments off
- **WHEN** a guest opens the bill in the QR table app while online payments are off
- **THEN** the bill shows totals and a message to ask the waiter, and no pay buttons

#### Scenario: Table payment attempted anyway
- **WHEN** a guest client calls `POST /t/{code}/payments` while online payments are off
- **THEN** the server responds 422 `tables.online_payments_disabled`

### Requirement: Staff settle unpaid orders at pickup
Completing a takeaway or counter order whose payment is `UNPAID` SHALL require a payment method: `CASH` or
`CARD_POS`. For cash, staff MAY add the amount tendered; when given, it MUST cover the order total, and the response
SHALL let staff show the change due.

Completing the order SHALL do all of the following in one step:
- record a captured payment for the order total, with the method, the amount tendered and the staff member;
- set the order's payment to `PAID`;
- mark the order `COMPLETED`.

Without a method, the request SHALL fail with 422 `order.payment_required` and change nothing. An order already
`PAID` SHALL complete without a method; any method sent is ignored. Table (dine-in) orders SHALL keep being settled
through the table bill.

#### Scenario: Cash at pickup
- **WHEN** staff complete a `READY`, unpaid takeaway order of 42.50 RON with method `CASH` and tendered 50.00
- **THEN** the order is `COMPLETED` and `PAID`, a cash payment of 42.50 RON is recorded, and the change due is 7.50

#### Scenario: Card at pickup
- **WHEN** staff complete a `READY`, unpaid takeaway order with method `CARD_POS`
- **THEN** the order is `COMPLETED` and `PAID`, and a card-terminal payment for the total is recorded

#### Scenario: No method for an unpaid order
- **WHEN** staff complete a `READY`, unpaid takeaway order without a method
- **THEN** the server responds 422 `order.payment_required`, and the order stays `READY` and `UNPAID`

#### Scenario: Cash tendered below the total
- **WHEN** staff send method `CASH` with a tendered amount lower than the order total
- **THEN** the server responds 422, and nothing is recorded

#### Scenario: Already paid online
- **WHEN** staff complete a `READY` takeaway order that was paid online
- **THEN** the order is `COMPLETED` without a method, and no second payment is recorded

#### Scenario: Admin chooses the method
- **WHEN** staff press Complete on an unpaid order in admin
- **THEN** admin asks for Cash or Card (with an optional cash amount and the change shown) before completing

### Requirement: Switching off does not strand orders awaiting payment
Turning `onlinePayments` off SHALL NOT change orders already in `PENDING_PAYMENT`. Those orders SHALL remain payable.
Unpaid ones SHALL still be cancelled after the payment timeout. Only orders placed after the switch is off go straight
to the kitchen.

#### Scenario: Order awaiting payment when the switch goes off
- **WHEN** an order is `PENDING_PAYMENT` and an administrator turns online payments off
- **THEN** the customer can still complete the payment, and the order then goes to the kitchen

#### Scenario: Abandoned order after switching off
- **WHEN** that order is not paid within the payment timeout
- **THEN** it is cancelled as before
