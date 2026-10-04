# C4 Level 3 — Core API components (Spring Modulith modules)

```mermaid
C4Component
title Core API – Modules

Container_Boundary(api, "Core API") {
  Component(identity, "identity", "Users, roles, JWT, customer accounts")
  Component(settings, "settings", "Profile, branding, VAT, stations, printers, feature flags")
  Component(catalog, "catalog", "Categories, products, modifiers, promotions, i18n")
  Component(ordering, "ordering", "Orders, lines, status machine, order numbers")
  Component(tables, "tables", "Floor, tables, sessions, bill split")
  Component(kitchen, "kitchen", "Tickets per station, timers, bump")
  Component(payments, "payments", "Payment intents, providers, webhooks, refunds")
  Component(fiscal, "fiscal", "Fiscal receipts, X/Z reports")
  Component(printing, "printing", "Print jobs, templates, bridge routing")
  Component(inventory, "inventory", "Warehouses, stock ledger, recipes, transfers, counts")
  Component(procurement, "procurement", "Suppliers, invoices, e-Factura import, OCR, NIR")
  Component(notifications, "notifications", "SSE, Expo push, Web push")
  Component(reporting, "reporting", "Sales, stock value, prep-time KPIs")
}

ContainerDb(db, "PostgreSQL")

Rel(ordering, catalog, "Reads products/prices")
Rel(tables, ordering, "Creates orders for sessions")
Rel(ordering, kitchen, "OrderPlaced")
Rel(kitchen, printing, "TicketCreated")
Rel(kitchen, ordering, "TicketReady")
Rel(ordering, notifications, "OrderReady")
Rel(payments, ordering, "PaymentCaptured")
Rel(ordering, fiscal, "OrderPaid")
Rel(fiscal, printing, "FiscalReceiptRequested")
Rel(ordering, inventory, "OrderPaid → consume recipe")
Rel(procurement, inventory, "NirPosted → stock receipt")
Rel(reporting, db, "Read models / views")
```

## Rules
- Each module is a top-level package `ro.amadya.<module>`. Other modules may only use its `api` sub-package
  (services, DTOs, events). Everything else is internal. `ApplicationModules.verify()` in tests enforces this.
- Modules talk to each other through **domain events**, persisted in the Spring Modulith event publication registry (transactional outbox).
  Synchronous calls are only for queries (e.g. ordering reads catalog prices).
- `shared` holds only primitives: Money, Quantity, LocalizedText, ids, and error types.
