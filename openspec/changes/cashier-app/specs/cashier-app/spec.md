## Purpose

The counter app of a fast-food: the person at the front takes orders and money, confirms or refuses phone orders, and
hands ready orders over, all from one touchscreen.

## ADDED Requirements

### Requirement: Cashier app access
The cashier app SHALL be served at `/cashier`. Only staff with the Cashier, Manager or Admin role SHALL be able to use
it. Other staff SHALL see the login screen with a "no access" message. The interface SHALL be available in Romanian
(default) and English, and SHALL be usable on a landscape tablet and on a desktop screen.

#### Scenario: Cashier logs in
- **WHEN** a user with the Cashier role logs in at `/cashier`
- **THEN** the counter screen opens

#### Scenario: Kitchen user
- **WHEN** a user with only the Kitchen role logs in at `/cashier`
- **THEN** access is refused, and no order data is shown

### Requirement: Take a counter order and its payment
The cashier SHALL be able to build an order from the active menu: products, required and optional options, quantities
and notes. The app SHALL enforce the same option rules as the customer menu. It SHALL show the server-calculated total
before payment.

The cashier SHALL then choose:
- **Cash**, with an optional amount received that must cover the total, and the change shown;
- **Card at the terminal**;
- **Pay at pickup**.

Sending SHALL create a `COUNTER` order in one request.
- With Cash or Card, the order SHALL be `PAID`, and the payment SHALL be recorded with the method, the amount received
  and the staff member.
- With Pay at pickup, the order SHALL be `UNPAID`, and the payment is taken at handover.

The order SHALL go straight to the kitchen (`PLACED`), or become `READY` at once when nothing needs preparing.

#### Scenario: Paid in cash
- **WHEN** the cashier sends a counter order of 41.00 with Cash and 50.00 received
- **THEN** the order is created `PLACED` and `PAID`, a cash payment of 41.00 with 50.00 received is recorded, and the app
  shows the order number and change 9.00

#### Scenario: Not enough cash
- **WHEN** the amount received is lower than the total
- **THEN** the app does not send the order and shows that the cash doesn't cover the total

#### Scenario: Paid by card
- **WHEN** the cashier sends the order with Card
- **THEN** the order is created `PAID`, with a card-terminal payment recorded

#### Scenario: Pay at pickup
- **WHEN** the cashier sends the order with Pay at pickup
- **THEN** the order is created `UNPAID`, and handing it over asks for Cash or Card

#### Scenario: Missing required option
- **WHEN** the cashier adds a product without choosing a required option
- **THEN** the app asks for the option before adding it, and the server would reject the order with 422

### Requirement: Paying when ordering through the API
`POST /orders` SHALL accept an optional `payment { method: CASH | CARD_POS, tendered? }`, only from staff and only for
`COUNTER` orders. The same rules apply as for payment at pickup:
- `tendered` only with `CASH`, and at least the total;
- otherwise 422 with nothing created.

A `payment` on a customer order, or on a `TAKEAWAY` order, SHALL be rejected with 422.

#### Scenario: Customer sends a payment
- **WHEN** a customer request to `POST /orders` includes `payment`
- **THEN** the server responds 422, and no order is created

### Requirement: Live lists at the counter
The app SHALL show three live lists, updated within seconds of a change and without a manual refresh:
- **To confirm:** `AWAITING_CONFIRMATION` orders, oldest first. Each shows the time left before automatic confirmation.
- **In progress:** `PLACED` and `PREPARING` takeaway and counter orders.
- **Ready:** `READY` takeaway and counter orders.

Each order SHALL show its number, channel, customer name and phone as a call link (for phone orders), items with
options and notes, total, payment status and requested pickup time.

A new order to confirm SHALL play a sound, which the cashier can mute. Table (dine-in) orders SHALL NOT appear.

#### Scenario: Phone order arrives
- **WHEN** a customer places an order paid at pickup
- **THEN** it appears under To confirm within a few seconds, with a sound

#### Scenario: Kitchen marks it ready
- **WHEN** the kitchen marks an order ready
- **THEN** it moves from In progress to Ready on the cashier screen without a refresh

#### Scenario: Connection lost
- **WHEN** the live connection drops
- **THEN** the app shows that it is offline, reconnects by itself, and reloads the lists when it is back

### Requirement: Confirm or refuse from the counter
From To confirm, the cashier SHALL be able to confirm an order with one tap. Refusing SHALL ask for a reason; the app
SHALL offer ready-made reasons ("Product unavailable", "Closing soon", "Could not reach the customer") and SHALL allow
free text.

#### Scenario: Refuse with a ready-made reason
- **WHEN** the cashier taps Refuse and picks "Product unavailable"
- **THEN** the order is refused with that reason and leaves the list

### Requirement: Hand over and cancel from the counter
From Ready, "Picked up" SHALL complete a paid order at once. For an unpaid order it SHALL first ask Cash or Card, with
the amount received and the change.

From To confirm and In progress, the cashier SHALL be able to cancel a `PLACED` order with a reason. Cancelling an order
already `PREPARING` SHALL be offered only to managers and admins, as the server already enforces.

#### Scenario: Hand over an unpaid phone order
- **WHEN** the cashier taps Picked up on a ready, unpaid phone order and chooses Card
- **THEN** the order is `COMPLETED` and `PAID`, and it leaves the Ready list

#### Scenario: Cashier cannot cancel food being prepared
- **WHEN** a user with only the Cashier role opens an order that is `PREPARING`
- **THEN** no Cancel action is offered

### Requirement: Staff order event stream
`GET /orders/events` SHALL stream every order status change as Server-Sent Events, to staff only. The events are
created, confirmed, preparing, ready, completed and cancelled. Each event SHALL carry the order id, number, channel,
status and time. Requests without a staff token SHALL get 401.

#### Scenario: Anonymous client
- **WHEN** a request without a token opens `GET /orders/events`
- **THEN** the server responds 401
