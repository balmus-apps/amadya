## MODIFIED Requirements

### Requirement: Admin API documentation page
The admin app SHALL offer an "Apps & API" page under Settings ("Aplicații și API" in Romanian), shown in the navigation
to ADMIN and MANAGER users.
- **For an ADMIN**, the page SHALL list every operation of the spec grouped by tag, with parameters, request and response
  schemas, below the links to the apps.
- **For a MANAGER**, the page SHALL show only the links to the apps. It SHALL NOT render the documentation or its
  warning, and SHALL NOT request the spec.

#### Scenario: Admin opens the page
- **WHEN** an ADMIN opens Settings → Apps & API
- **THEN** the links to the apps are shown first
- **AND** every tag of the spec is listed, and expanding an operation shows its parameters and schemas

#### Scenario: Manager
- **WHEN** a MANAGER is signed in to the admin app
- **THEN** the Apps & API item is in the navigation
- **AND** opening it shows the links to the apps, without the API documentation or the "requests are real" warning
- **AND** no request is made to `GET /api/v1/admin/openapi.yaml`

## ADDED Requirements

### Requirement: Links to every app of the install
The Apps & API page SHALL list the apps of the install: customer menu, admin, waiter app, cashier, kitchen and bar
display, and the order screen (TV). Each entry SHALL show:
- its name, in the user's language;
- its full address, as a link that opens in a new tab;
- a button that copies the address.

The address SHALL be built from the install's public address (`publicUrl` from the restaurant settings, set from
`AMADYA_DOMAIN` in `.env`). It SHALL fall back to the address the admin app is open on when the setting is missing. A
note SHALL say that table ordering opens from each table's QR code.

#### Scenario: Production
- **WHEN** an ADMIN or MANAGER opens the page on an install with `AMADYA_DOMAIN=amadya.alphatech.ro`
- **THEN** the cashier entry links to `https://amadya.alphatech.ro/cashier/`

#### Scenario: Copy
- **WHEN** the user taps the copy button next to the waiter app
- **THEN** its full address is copied and a confirmation is shown
