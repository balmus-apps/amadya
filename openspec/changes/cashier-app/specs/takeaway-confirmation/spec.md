## Purpose

Customer to-go orders that are paid at pickup wait for the restaurant to confirm them before the kitchen starts. This
protects the kitchen from prank orders, while making sure a busy counter never loses a real one.

## ADDED Requirements

### Requirement: Unpaid customer orders wait for confirmation
A takeaway order placed by a customer, with payment `UNPAID`, SHALL be created with status `AWAITING_CONFIRMATION`
while `takeawayConfirmMinutes` is greater than 0.

Kitchen tickets and the queue display SHALL NOT include such an order until it is confirmed. Orders paid online, and
orders created by staff, SHALL NOT wait for confirmation.

#### Scenario: Phone order waits
- **WHEN** a customer places an order paid at pickup from the website or the app
- **THEN** it is `AWAITING_CONFIRMATION`, and no kitchen ticket exists for it

#### Scenario: Paid online order does not wait
- **WHEN** a customer's online payment for a `PENDING_PAYMENT` order is captured
- **THEN** the order goes to `PLACED` and the kitchen, without waiting for confirmation

#### Scenario: Staff order does not wait
- **WHEN** a cashier creates a counter order
- **THEN** it is never `AWAITING_CONFIRMATION`

### Requirement: Staff confirm or refuse a waiting order
Staff with the Cashier, Waiter, Manager or Admin role SHALL be able to:
- **confirm** an `AWAITING_CONFIRMATION` order, which moves it to `PLACED` and sends it to the kitchen;
- **refuse** it with a reason of 3–300 characters, which moves it to `CANCELLED` with that reason.

Both actions SHALL be recorded in the activity log with the staff member. Confirming or refusing an order in any other
status SHALL fail with 409 and change nothing.

#### Scenario: Confirm
- **WHEN** a cashier confirms an `AWAITING_CONFIRMATION` order
- **THEN** it becomes `PLACED`, its kitchen tickets appear, and the activity log shows who confirmed it

#### Scenario: Refuse
- **WHEN** a cashier refuses a waiting order with the reason "Nu mai avem pui"
- **THEN** it becomes `CANCELLED` with that reason, and no kitchen ticket is created

#### Scenario: Too late to confirm
- **WHEN** a cashier confirms an order that was already confirmed automatically
- **THEN** the server responds 409 and the order is unchanged

### Requirement: Waiting orders are confirmed automatically
An order still `AWAITING_CONFIRMATION` after `takeawayConfirmMinutes` minutes SHALL be confirmed automatically and sent
to the kitchen. The check SHALL run at least once a minute. Because no staff member acted, the activity log SHALL
NOT show an entry for it.

#### Scenario: Nobody answers
- **WHEN** a phone order has waited 5 minutes with the setting at 5 and nobody confirmed or refused it
- **THEN** within the next minute it becomes `PLACED` and the kitchen receives it

### Requirement: The confirmation time is a restaurant setting
The restaurant settings SHALL include `takeawayConfirmMinutes`, an integer from 0 to 60, 5 by default. Admins and
managers SHALL be able to change it in Admin → Settings. 0 SHALL turn confirmation off. A change SHALL apply to orders
created after it.

#### Scenario: Turn confirmation off
- **WHEN** an admin sets `takeawayConfirmMinutes` to 0
- **THEN** new customer orders paid at pickup are created as `PLACED`

#### Scenario: Out of range
- **WHEN** an admin saves `takeawayConfirmMinutes` = 90
- **THEN** the server responds 400 and nothing is saved

### Requirement: Customers see confirmation and refusal
The order page in the website and the app SHALL show a waiting order as "Waiting for the restaurant to confirm", in the
customer's language (ro/en). A refused order SHALL show that it was refused, together with the reason. Both SHALL update
live over the order's event stream.

#### Scenario: Waiting
- **WHEN** a customer opens the page of an `AWAITING_CONFIRMATION` order
- **THEN** it says the restaurant has not confirmed the order yet

#### Scenario: Refused while watching
- **WHEN** the cashier refuses the order while the customer has its page open
- **THEN** the page switches to "Refused" and shows the reason, without a reload

### Requirement: Unconfirmed orders are not sales
Reports SHALL exclude `AWAITING_CONFIRMATION` orders from sales, the same way they exclude `PENDING_PAYMENT` orders.

#### Scenario: Dashboard
- **WHEN** an order is waiting for confirmation
- **THEN** it is not counted in today's sales or order count
