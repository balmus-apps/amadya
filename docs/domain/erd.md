# Entity-relationship overview

Conventions:
- UUIDv7 primary keys.
- `created_at`, `updated_at`, `version` on every table.
- Money is `NUMERIC(12,2)`; quantities are `NUMERIC(14,3)`.
- Localized text is `jsonb` `{"ro": "...", "en": "..."}`.
- No cross-module foreign keys: other modules are referenced by id only.

```mermaid
erDiagram
  %% settings
  VAT_RATE ||--o{ PRODUCT : taxes
  STATION ||--o{ PRODUCT : prepared_at
  STATION ||--o{ PRINTER : prints_for

  %% catalog
  CATEGORY ||--o{ PRODUCT : contains
  PRODUCT ||--o{ PRODUCT_MODIFIER_GROUP : has
  MODIFIER_GROUP ||--o{ PRODUCT_MODIFIER_GROUP : used_by
  MODIFIER_GROUP ||--o{ MODIFIER_OPTION : offers

  %% inventory
  WAREHOUSE ||--o{ STOCK_BALANCE : holds
  STOCK_ITEM ||--o{ STOCK_BALANCE : balance
  STOCK_ITEM ||--o{ STOCK_MOVEMENT : ledger
  STOCK_ITEM ||--o{ STOCK_LOT : "FIFO lots"
  WAREHOUSE ||--o{ STOCK_LOT : holds
  STOCK_MOVEMENT ||--o{ STOCK_LOT_ALLOCATION : consumes
  STOCK_LOT ||--o{ STOCK_LOT_ALLOCATION : from
  UNIT_OF_MEASURE ||--o{ STOCK_ITEM : "reference unit"
  STOCK_ITEM ||--o{ STOCK_ITEM_PACKAGING : packaging
  WAREHOUSE ||--o{ STOCK_MOVEMENT : ledger
  PRODUCT ||--o{ RECIPE : "made by"
  RECIPE ||--o{ RECIPE_LINE : lines
  STOCK_ITEM ||--o{ RECIPE_LINE : uses

  %% procurement
  SUPPLIER ||--o{ PURCHASE_INVOICE : issues
  PURCHASE_INVOICE ||--o{ PURCHASE_INVOICE_LINE : lines
  PURCHASE_INVOICE ||--o| NIR : received_by
  NIR ||--o{ NIR_LINE : lines
  WAREHOUSE ||--o{ NIR : into
  STOCK_ITEM ||--o{ NIR_LINE : item

  %% tables & ordering
  FLOOR_AREA ||--o{ DINING_TABLE : has
  DINING_TABLE ||--o{ TABLE_SESSION : sessions
  TABLE_SESSION ||--o{ CUSTOMER_ORDER : rounds
  APP_USER ||--o{ CUSTOMER_ORDER : places
  CUSTOMER_ORDER ||--o{ ORDER_LINE : lines
  ORDER_LINE ||--o{ ORDER_LINE_MODIFIER : modifiers

  %% kitchen
  CUSTOMER_ORDER ||--o{ KITCHEN_TICKET : split_by_station
  KITCHEN_TICKET ||--o{ KITCHEN_TICKET_LINE : lines

  %% payments, fiscal, printing
  CUSTOMER_ORDER ||--o{ PAYMENT : paid_by
  PAYMENT ||--o{ REFUND : refunds
  CUSTOMER_ORDER ||--o{ FISCAL_RECEIPT : receipts
  PRINTER ||--o{ PRINT_JOB : jobs
```

Detailed columns are in the Flyway migrations (`backend/src/main/resources/db/migration`), which are the single source of truth.
