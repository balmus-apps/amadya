## Purpose

Record supplier invoices and delivery notes in full, by typing them or by importing a photo or PDF. Product names and
units are normalised so that the same goods always reach the same stock item.

## ADDED Requirements

### Requirement: Manual invoice entry
The admin app SHALL provide a "New invoice" page, available to Admin and Manager, with inputs for:
- supplier: CUI, name and branch address;
- document: type (factura, aviz or bon), series, number, date and time;
- client, card, register and cashier numbers;
- buyer: name, CUI and person;
- payment method and amount paid.

The page SHALL also have an editable list of lines.

#### Scenario: Save a typed invoice
- **WHEN** a manager fills the header and two lines and saves
- **THEN** the invoice is stored with source MANUAL and every header field, and its detail page opens

### Requirement: Import from photo or PDF
The page SHALL offer an import button that accepts JPEG, PNG, WebP or PDF up to 10 MB. The button SHALL fill the header
and lines from the document as an unsaved draft. The original file SHALL be stored, and the raw extraction SHALL be kept
with the saved invoice. Import SHALL be hidden when the scan feature is turned off. Manual entry SHALL always remain
available.

#### Scenario: Selgros delivery note
- **WHEN** the user imports a photo of a Selgros "Aviz de însoțire a mărfii"
- **THEN** the supplier, document number, date, time, client, register and cashier numbers, and buyer are filled
- **AND** 5 lines appear, with net 121.33, VAT 15.84 and total 137.17

#### Scenario: Nothing saved before review
- **WHEN** a document is imported and the user leaves the page without saving
- **THEN** no invoice, stock item or NIR exists

#### Scenario: Totals do not match
- **WHEN** the computed total differs from the total read on the document by more than 0.02
- **THEN** the difference is highlighted, and saving is still allowed

### Requirement: Computed totals
Each line SHALL show line total = quantity × unit price, computed live. The page SHALL show net, VAT per rate and the
invoice total, computed from the lines.

#### Scenario: Change a quantity
- **WHEN** the user changes a line quantity from 2 to 3 at 9.69
- **THEN** the line total becomes 29.07, and the invoice totals update

### Requirement: Product name rules
The system SHALL keep keyword rules (contains, starts with, regex) that map a printed product name to a normalised name
and optionally to a stock item. Rules SHALL be applied in priority order, and the first match wins, ignoring case and
diacritics. Rules SHALL apply to imported and typed lines. When a user renames a line, the page SHALL offer to save the
change as a rule.

#### Scenario: Different brands become the same product
- **GIVEN** a rule `contains "LAPTE|MILK" → Lapte`
- **WHEN** a document has "Lapte Fulga 3.5% grăsime" and another has "Milk ZUZU 3.7% fat"
- **THEN** both lines are named "Lapte"

#### Scenario: Invalid regex
- **WHEN** a manager saves a regex rule that does not compile
- **THEN** the rule is refused with an explanation

### Requirement: Primary and secondary units
For a new product, the line's unit SHALL become its primary unit. For an existing product, the line SHALL be
pre-filled with the primary unit. When the user enters a different unit and the conversion is not known, a dialog SHALL
ask for the parity (for example "1 BU = 430 g") and save it for that product. An invoice SHALL NOT be saved while any
line's unit cannot be converted.

#### Scenario: Known conversion
- **WHEN** an item measured in ml receives a line in l
- **THEN** the page shows "1 l = 1000 ml", and no dialog opens

#### Scenario: Unknown conversion
- **WHEN** an item measured in g receives a line in BU
- **THEN** the parity dialog asks how many g one BU is, and after saving, the line converts

### Requirement: Warehouse per line
Every line SHALL have a warehouse dropdown, pre-filled with the stock item's default warehouse. Saving SHALL create one
draft NIR per distinct warehouse, containing that warehouse's lines.

#### Scenario: Two warehouses
- **WHEN** an invoice with lines in MATERII and MARFA is saved
- **THEN** two draft NIRs are created, one per warehouse, and no stock is posted until a person posts them
