## MODIFIED Requirements

### Requirement: With online payments off, customers pay at pickup
While `features.onlinePayments` is `false`:
- A takeaway order placed by a customer SHALL NOT ask for payment. Its payment is `UNPAID`.
- It SHALL wait for the restaurant's confirmation (status `AWAITING_CONFIRMATION`) before it reaches the kitchen, as
  described by the `takeaway-confirmation` capability.
- When confirmation is turned off (`takeawayConfirmMinutes = 0`), it SHALL go straight to the kitchen (status
  `PLACED`).
- The checkout in the website and the mobile app SHALL tell the customer that the order is paid at pickup.
- The order page SHALL NOT show a payment form.

#### Scenario: Takeaway order with payments off
- **WHEN** a customer places a takeaway order on the website while online payments are off and confirmation is on
- **THEN** the order is created as `AWAITING_CONFIRMATION` and `UNPAID`, the kitchen does not receive it yet, and the
  customer sees no payment form

#### Scenario: Takeaway order with confirmation turned off
- **WHEN** a customer places a takeaway order while online payments are off and `takeawayConfirmMinutes` is 0
- **THEN** the order is created as `PLACED` and the kitchen receives it

#### Scenario: Checkout wording
- **WHEN** a customer opens the checkout on the website or in the app while online payments are off
- **THEN** the checkout says the order is paid at pickup
