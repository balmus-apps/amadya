# Design

## Context

What already exists in amadya:
- **`procurement`:** `supplier`, `supplier_item` (remembered supplier code or description → stock item),
  `purchase_invoice(_line)` with `source = MANUAL | EFACTURA_XML | SCAN`, NIR with one `warehouse_id`, and
  `ProcurementService.autoMatch` / `remember`.
- **`inventory`:** `unit_of_measure` (dimension, factor to the reference unit, aliases, UNMAPPED status), `stock_item`
  (base, display and default warehouse), `stock_item_packaging` (conversion per item), `StockCatalog.resolveUnit` /
  `isConvertible` / `toBase`.
- **`media`:** `POST /admin/files` (JPEG, PNG and WebP only), filesystem or S3 storage.
- **ADR 0006:** `InvoiceExtractor` port, Claude vision first, human review always, raw extraction kept.

## Data (migration `V19__invoice_scan.sql`)

```sql
ALTER TABLE purchase_invoice
  ADD COLUMN doc_type        TEXT NOT NULL DEFAULT 'FACTURA' CHECK (doc_type IN ('FACTURA','AVIZ','BON')),
  ADD COLUMN issue_time      TIME,
  ADD COLUMN supplier_branch TEXT,           -- "Selgros Galați, B-dul Galați 1-C"
  ADD COLUMN client_no       TEXT,           -- Nr. client
  ADD COLUMN card_no         TEXT,           -- Nr. legitimație
  ADD COLUMN register_no     TEXT,           -- Număr casă
  ADD COLUMN cashier_no      TEXT,           -- Număr casier
  ADD COLUMN buyer_name      TEXT,
  ADD COLUMN buyer_cui       TEXT,
  ADD COLUMN buyer_person    TEXT,           -- Cumpărător: person who bought
  ADD COLUMN payment_method  TEXT,           -- CASH / CARD / TRANSFER
  ADD COLUMN paid_amount     NUMERIC(14,2),
  ADD COLUMN discount_total  NUMERIC(14,2),
  ADD COLUMN file_key        TEXT,           -- original photo/PDF in media storage
  ADD COLUMN raw_extraction  JSONB;          -- what the extractor returned, untouched

ALTER TABLE purchase_invoice_line
  ADD COLUMN original_description TEXT,      -- text as printed; description = normalised name
  ADD COLUMN lot          TEXT,
  ADD COLUMN warehouse_id UUID,
  ADD COLUMN line_vat     NUMERIC(14,2),
  ADD COLUMN line_gross   NUMERIC(14,2);

CREATE TABLE product_name_rule (
  id            UUID PRIMARY KEY,
  match_type    TEXT NOT NULL CHECK (match_type IN ('CONTAINS','STARTS_WITH','REGEX')),
  pattern       TEXT NOT NULL,               -- 'LAPTE|MILK' for CONTAINS = any of the words
  target_name   TEXT NOT NULL,               -- 'Lapte'
  stock_item_id UUID,                        -- optional direct link
  priority      INT  NOT NULL DEFAULT 100,
  active        BOOLEAN NOT NULL DEFAULT true,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  version       BIGINT NOT NULL DEFAULT 0
);
```

The supplier's own fields (name, CUI, reg. com, address, IBAN, phone) already exist on `supplier`. The scan upserts the
supplier by CUI. The supplier's head office goes on `supplier` and the store branch goes on the invoice.

## Name rules

- **Matching:** matching is case- and diacritic-insensitive, applied to `original_description`.
  - `CONTAINS`: the pattern is split on `|`, and the rule matches if any word is present as a whole word.
  - `STARTS_WITH`: the description starts with one of the words.
  - `REGEX`: a Java regex with a 50 ms timeout guard, saved only if it compiles.
- **Order:** rules run by `priority` ascending, then by creation date. The first match sets `description = target_name`,
  and `stock_item_id` when the rule has one.
- **Fallback:** without a rule match, the description stays as printed, title-cased.
- **Run order for a line:** name rules → supplier code mapping → remembered description → exact stock item name. Rules
  win because the owner set them on purpose.
- **"Save as rule":** when the user renames a line, the page proposes `CONTAINS` with the first meaningful word of the
  original text, which the user can edit. Example: "UNIVER SOS USTUROI 430G" → pattern `SOS USTUROI` → "Sos usturoi".

## Units

- **Resolution:** each line's printed unit (`KG`, `BU`) goes through `StockCatalog.resolveUnit`, which uses code and
  aliases and creates an UNMAPPED unit when unknown. `BU` (bucată) is added as an alias of `buc` in the migration.
- **New stock item:** the line's unit becomes the item's primary unit. Its `base_unit_id` is the reference unit of that
  dimension, as the units doc requires, and `display_unit_id` is the typed unit.
- **Existing item, different unit:**
  - **Same dimension, known factor** (l ↔ ml, kg ↔ g): the conversion is shown read-only ("1 l = 1000 ml").
  - **Otherwise** (BU for an item in g, or an UNMAPPED unit): the **parity dialog** asks "1 BU = [430] g". Saving it
    creates a `stock_item_packaging` named after the unit, and the line points to that packaging.
- **Saving:** blocked while any line fails `isConvertible`. This is the same check the NIR uses today.

## Extraction

- **Port:** `InvoiceExtractor.extract(bytes, mediaType): ExtractedInvoice`, inside `procurement/internal`.
- **Adapter:** `ClaudeInvoiceExtractor` calls the Anthropic Messages API with the official Java SDK.
  - **Input:** an image block for JPEG, PNG or WebP, or a document block for PDF.
  - **Output:** structured outputs: `output_config.format` carries a strict JSON schema of `ExtractedInvoice`.
    Sonnet 5.5 rejects a forced `tool_choice`, so no tool call is used. Server-side refusal fallbacks (`fallbacks: "default"`) are on.
    The schema covers:
    - supplier: name, CUI, reg. com, address, branch, IBAN;
    - buyer;
    - doc type, number, date and time;
    - client, card, register and cashier numbers;
    - lines: code, description, lot, unit, quantity, unit price, VAT %, net, VAT and gross;
    - VAT breakdown and totals;
    - payments and discount.
  - **Numbers:** they come back as strings in the document's format and are parsed server-side (`28,240` → 28.240),
    so the model never rounds.
- **Config:**

  ```yaml
  amadya.invoice-scan:
    enabled: ${INVOICE_SCAN_ENABLED:false}
    model: ${INVOICE_SCAN_MODEL:claude-sonnet-5-5}
    api-key: ${ANTHROPIC_API_KEY:}
    max-bytes: 10MB
  ```

  The bean exists only when `enabled=true` (`@ConditionalOnProperty`, the same as `S3Storage`). When it is off, the
  scan endpoint returns 404, and `GET /admin/invoices/scan` reports `enabled: false` so the UI hides the button.
  `INVOICE_SCAN_BASE_URL` can point at an HTTP proxy in front of the API. Photos are scaled down to a 2400 px longest edge
  before sending.
- **Validation after extraction** returns warnings and never errors:
  - line net = quantity × price, within ±0.02;
  - Σ lines = VAT breakdown = totals;
  - CUI checksum;
  - a duplicate invoice with the same supplier, series and number.

## API (contract-first)

- `POST /admin/invoices/scan`: multipart `file`. It stores the file through `media` under a private key that the
  public `/files` endpoint refuses. It then extracts, applies rules and matching, and returns `InvoiceDraft` (header,
  lines, warnings, `fileKey`). **Nothing is persisted** except the file; unknown units are not created yet.
- `GET /admin/invoices/{id}/file`: the stored original, for staff only.
- `POST /admin/invoices` (`createPurchaseInvoice`, extended): the request gains the new header fields, `fileKey`,
  `rawExtraction`, and per line `originalDescription`, `lot`, `warehouseId` and optional
  `newStockItem { name, unitId, defaultWarehouseId }` and `packaging { unitCode, qtyInBase }`.
  - In one transaction it:
    1. upserts the supplier;
    2. creates missing stock items and packagings;
    3. saves the invoice;
    4. remembers supplier mappings;
    5. creates one draft NIR per distinct `warehouseId`.
  - It returns the invoice with its NIR ids.
- `GET/POST/PUT/DELETE /admin/product-name-rules` and `POST /admin/product-name-rules/test` (body `{ text }` →
  resulting name). Access: Admin and Manager.
- **`media`:** add `application/pdf`, detected from the `%PDF-` signature. Raise the multipart limit to 10 MB / 11 MB.

## Frontend

- **`pages/procurement/invoice-new.tsx`:**
  - The header uses a form grid of `Field`s.
  - The "Import photo / PDF" button shows a spinner while scanning, then fills the form. Warnings appear above the
    table.
  - The original file opens in a side preview so the user can compare it with the form.
- **Line row:**
  - original text (muted);
  - name combobox (stock items plus "create '…'");
  - quantity;
  - unit input with an autocomplete of units;
  - price and VAT %;
  - computed total;
  - warehouse `NativeSelect`;
  - delete.
  - Manual mode adds empty rows.
- **Footer:** net, VAT per rate, gross, and the document total read from the scan, with a red badge when they differ.
- **Dialogs:** a parity dialog (unit), and a "save as rule" dialog that opens when the user renames a line.
- **`pages/procurement/name-rules.tsx`:** a table of rules with up/down priority, an active switch, and a "test a name"
  box.
- **Router and navigation:**
  - `page("/invoices/new")` is registered before `/invoices/$id`;
  - Procurement navigation gets "Name rules";
  - the invoices list gets a "New invoice" button.

## Database note

`ux_nir_invoice`, which allowed one active NIR per invoice, becomes `ux_nir_invoice_warehouse`, which allows one per
invoice and warehouse.

## Risks

- **Wrong extraction:** a human always reviews the draft, totals are cross-checked, and the raw output is kept.
- **Cost or outage of the API:** the feature is behind a flag, and manual entry always works.
- **Personal data sent to Anthropic:** only supplier documents are sent. The buyer person's name appears on them, so
  this is noted in the privacy notes (`docs`).
- **Bad regex rules:** the regex is compiled on save, and a timeout guard protects matching.
