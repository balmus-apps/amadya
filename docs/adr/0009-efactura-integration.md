# ADR 0009 — RO e-Factura integration (ANAF SPV)

**Status:** Proposed — 2026-10-11. It replaces "Phase 7: invoice scan" as the next phase. Phase 6 (printers and fiscal
printer) moves after it, until the hardware is bought.

## Context

RO e-Factura is ANAF's national e-invoicing system. Every invoice between Romanian companies (B2B), and since 2025 also
invoices to individuals (B2C), must be uploaded to it as UBL 2.1 XML (CIUS-RO), **within 5 working days** of issue
(5 calendar days until 2025). ANAF validates the invoice, signs it and makes it available to the buyer in their SPV
(Spațiul Privat Virtual). An invoice not sent through e-Factura counts as non-existent for VAT deduction. Fines for large
taxpayers are 5,000–10,000 RON.

What this means for a restaurant:
- **Supplier invoices (inbound).** Every invoice from a Romanian supplier is already in the restaurant's SPV as signed
  XML. Downloading it gives exact data: supplier, lines, quantities, prices, VAT. Photographing paper invoices
  (the old Phase 7) is only needed for foreign or non-SPV suppliers.
- **Invoices the restaurant issues (outbound).** These are invoices to companies on request: company lunches, catering,
  events. They must be uploaded within 5 working days.
  - A **fiscal receipt with the buyer's CUI** that meets the conditions of a simplified invoice is **exempt**: its data
    already reaches ANAF through the fiscal printer.
  - A **separate** invoice, even one based on a fiscal receipt, must go through e-Factura.

What Amadya already has:
- `EFacturaParser`, an XXE-safe UBL parser.
- Manual XML import (`POST /admin/invoices/import/efactura`).
- Remembered supplier code → stock item mappings and auto-matching of lines.
- Purchase invoice → NIR → FIFO stock.

Only the connection to ANAF is missing.

## The ANAF API
The full findings are in [docs/domain/efactura.md](../domain/efactura.md). What drives this design:
- **OAuth 2.0.** A person authorizes once in a browser with a **qualified certificate** that has SPV rights for the CUI.
  The access token lasts 90 days and the refresh token 365 days, and refreshing rotates both. In practice someone
  re-authorizes once a year.
- **Received invoices** come from `listaMesajePaginatieFactura` (`filtru=P`), then `descarcare`: a ZIP with the signed
  XML. It can only be downloaded for **60 days**.
- **Sending** uses `upload` (`uploadb2c` for individuals), then `stareMesaj` until `ok`/`nok`. `validare` and
  `transformare` (PDF) are public and production-only.

## Decision

Add an **`efactura` module** in the backend, behind ports, as with payments and media storage. Deliver it in two steps.

### Step 1: receive supplier invoices automatically (about 4 days)

1. **Connect to ANAF** (Admin → Setări → e-Factura).
   - The restaurant's CUI comes from the settings. "Conectează cu ANAF" opens the authorize page; whoever holds the
     certificate approves it, and ANAF calls back to `https://<domain>/api/v1/admin/efactura/oauth/callback`.
   - Tokens are stored **encrypted** (AES-GCM, key in `.env` as `EFACTURA_TOKEN_KEY`) and refreshed automatically before
     the access token expires.
   - The page shows the connection state and the refresh-token expiry. A warning appears 30 days before re-authorizing is
     due.
2. **Sync.**
   - A scheduled job (every 30 minutes; configurable, plus a "Sincronizează acum" button) lists received invoices
     (`filtru=P`) since the last successful sync, minus an overlap for the indexing lag.
   - Each message is recorded once in `efactura_message` (ANAF id, type, upload index, date, status).
   - The ZIP is downloaded and **archived unchanged** in media storage. The signed XML is the legal original; the
     archive is kept for the legal retention period, not just 60 days.
3. **Draft purchase invoice.** The module publishes `SupplierInvoiceReceived(xml, messageId)`. Procurement then:
   - parses the XML;
   - finds or creates the supplier by CUI, with name and address from the ANAF lookup;
   - creates a purchase invoice with `source = EFACTURA_SPV`, deduplicated by supplier, series and number;
   - auto-matches the lines.

   The manager then reviews the draft and creates the NIR, as with a manual import.
4. **Admin.**
   - The Facturi list shows a badge for invoices that came from SPV.
   - Each invoice links to the official PDF (`transformare`) and the archived ZIP.
   - A message log shows the sync history and any errors.
5. **Tests.** A fake ANAF server (WireMock) covers OAuth, listing, download and token refresh. A manual check against
   the ANAF **test** environment needs a real certificate.

### Step 2: issue invoices to companies (about 5 days)

1. **Invoices module (`invoicing`).**
   - Issue an invoice from a paid order or a table bill, with the customer's company data (CUI lookup fills in the rest).
   - Series and yearly numbering, using the `document_counter` that NIRs already use.
   - VAT per line from the order snapshot. Credit notes for cancellations and refunds.
2. **UBL CIUS-RO generator.** Each invoice is checked with `validare` first, so errors show before upload.
3. **Upload** (`upload`, or `uploadb2c` for an individual) and **status tracking** (`stareMesaj`).
   - `ok`: the signed ZIP is archived.
   - `nok`: the error report is shown to the user, who can fix and resend.
4. **The 5-working-day deadline.** The admin dashboard warns about invoices not yet accepted 3 working days after issue.
5. **Receipt vs invoice.** A fiscal receipt with the CUI stays on the fiscal printer (Phase 6) and is not uploaded. Only
   separate invoices are.

### Later (optional)
- OCR of photos and PDFs for foreign suppliers, as originally planned for Phase 7.
- Buyer messages (`RASP`) to reply to suppliers about wrong invoices.

## Consequences
- Supplier invoices reach Amadya without typing or scanning. The manager only checks them and receives the goods (NIR).
- Someone with the qualified certificate must connect the restaurant once and renew it **every year**. The admin warns
  before the deadline.
- Amadya holds a long-lived ANAF credential: it is encrypted at rest, never logged, and only ADMIN can connect or
  disconnect.
- If the accountant's software already sends the restaurant's own invoices to e-Factura, Step 2 can wait. Step 1 is
  still useful, because the data feeds stock and NIRs.

## Open questions (for the owner and the accountant)
1. **Who registers the ANAF OAuth app?** Recommended: the developer (balmus-apps) once, with one callback per install,
   so restaurants only click "Conectează". The alternative is that each restaurant registers its own app.
2. **Who holds the qualified certificate with SPV rights:** the administrator or the accountant? That person does the
   yearly authorization.
3. **Does the restaurant issue invoices to companies today, and with what software?** This decides whether Step 2 is
   needed now.
4. **The restaurant's CUI** for the test environment, and whether we start on ANAF's test environment, prod read-only,
   or both.

## Sources
See [docs/domain/efactura.md](../domain/efactura.md#sources).
