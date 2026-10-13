# Proposal

## Why

Supplier documents still arrive on paper or as PDF. A typical one is a Selgros "Aviz de însoțire a mărfii cash and
carry": a supplier and branch block, our company as buyer, document number, date and time, client, ID and register numbers,
a list of lines with code, lot, unit, unit price, quantity, net, VAT and gross, a VAT breakdown per rate, the total, and how it was paid.

Today amadya can only import e-Factura XML. Nothing lets the owner type such a document in, and nothing reads a photo.
Product names on these documents carry brand, flavour and fat percentage ("Lapte Fulga 3.5%", "Lapte Zuzu 3.7%").
Each spelling would become a different stock item unless names are normalised. Units also differ between documents
and our stock items ("BU", "KG", "1 L" against "1000 ml").

## What Changes

- **New admin page "New invoice"** at `/invoices/new` under Procurement, with:
  - **Manual entry:**
    - header inputs for supplier (CUI, name, branch address), document type, series and number, date and time,
      client, cashier and register numbers, buyer, payment method and amount paid;
    - an editable lines table.
  - **Import from photo or PDF:** an upload button that reads the document and fills the header inputs and the lines.
    The result is an unsaved draft that the user reviews and edits before saving. Nothing reaches stock without a human.
- **Lines table:**
  - Each line shows the original scanned text (read-only) and the **normalised product name**, which is editable. The user
    picks an existing stock item or types a new name.
  - Other inputs per line: quantity, unit, unit price and VAT %.
  - **Computed values:**
    - line total = quantity × unit price;
    - VAT per rate;
    - **invoice total**.
  - If the computed total differs from the total read on the document, the difference is highlighted.
  - A **warehouse dropdown** on every line. It is pre-filled from the stock item's default warehouse.
- **Product name rules:** the owner keeps a list of keyword rules, for example `contains "LAPTE" or "MILK" → Lapte`.
  - Rules run in priority order on every scanned or typed line, and the first match wins.
  - When the user renames a line, the page offers to save the change as a rule.
  - The next document with "Lapte Zuzu 3.7%" is renamed to "Lapte" automatically.
  - A small settings page lists, edits, orders and disables rules.
- **Primary and secondary units** (the rule taken from event-planner):
  - **New product:** the unit typed on the line becomes its **primary** unit.
  - **Existing product:** the line is pre-filled with the product's primary unit.
  - **Different unit typed:** that unit is a **secondary** unit.
    - When the conversion is already known (same dimension, e.g. l and ml), it is shown for information.
    - Otherwise a **parity dialog** asks for it, e.g. "1 BU = 430 g", and saves it as a packaging of that product.
    - The line cannot be saved until every unit converts.
- **Saving** creates the purchase invoice with all header fields, the lines and the stored original file. It also creates
  **one draft NIR per warehouse** used on the lines. NIRs are still posted by a person, as today.
- **Data:**
  - new columns on `purchase_invoice` and `purchase_invoice_line`;
  - a new `product_name_rule` table;
  - the media upload accepts PDF.
- **Extraction:**
  - implements ADR 0006's `InvoiceExtractor` port with a Claude vision adapter that returns strict JSON;
  - is behind a feature flag and uses the `ANTHROPIC_API_KEY` reserved in `.env.example`.

## Out of scope

- **e-Factura:** downloading from the ANAF SPV, and bulk splitting of the ANAF archive. ADR 0009 keeps this on hold; it
  stays a separate change. The existing e-Factura XML import button stays on the invoices list.
- **AI name matching:** the owner chose keyword rules instead.
- **Automatic posting:** NIRs are not posted automatically.
- **Offline OCR:** no Tesseract adapter.
- **Batches:** importing several documents in one upload. A multi-page PDF of one document is one upload.
- **Payments:** tracking supplier payments or debts beyond the "paid" fields read from the document.

## Owner decisions

| # | Decision | Options | Recommended |
|---|---|---|---|
| 1 | Third page feature | e-Factura on the same page / separate change | **Separate change** (owner: ANAF sends invoices in bulk) — decided |
| 2 | Name normalisation | AI + learned aliases / learned aliases only / keyword rules | **Keyword rules** — decided by owner |
| 3 | Claude model for extraction | `claude-sonnet-5-5` / `claude-opus-5-5` | **`claude-sonnet-5-5`**: cheaper per scan, accurate on printed receipts; config override available |
| 4 | Where an "aviz" lives | same table with `doc_type` / separate table | **Same table, `doc_type` FACTURA / AVIZ / BON** |
| 5 | Keep original scans | forever / purge after N months | **Forever** (audit; files are small) |
| 6 | Line warehouse vs NIR | per-line warehouse on NIR lines / one NIR per warehouse | **One NIR per warehouse**: a legal NIR belongs to one store |

## Impact

- **Contract:** `api/openapi/openapi.yaml`, adding scan, extended invoice create, and name rules CRUD.
- **Backend:** `procurement` (service, extractor, rules), `media` (PDF), `inventory` (`StockCatalog` creates items and
  packaging), plus migration `V19`.
- **Frontend:** the admin app (`pages/procurement/`, router, navigation, ro/en messages).
- **Config:** `amadya.invoice-scan.*`, using `ANTHROPIC_API_KEY`.
- **Cost:** each scan is one Claude API call, a few cents.
- **Production:** the 1 vCPU VPS only forwards the file, so it takes no extra load.
