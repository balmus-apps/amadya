# ADR 0006 — Supplier invoice input: e-Factura XML first, scan/OCR second

**Status:** Accepted — 2026-10-04

## Context
B2B invoices in Romania go through ANAF e-Factura as UBL 2.1 XML (RO_CIUS), which contains exact structured data.
Paper or PDF invoices and delivery notes still exist.

## Decision
- `procurement` accepts:
  1. an **e-Factura XML upload** (later: automatic download from the SPV via the ANAF OAuth API), parsed deterministically;
  2. a **photo or PDF scan** sent to an `InvoiceExtractor` port. The first adapter uses the Claude vision API with a strict
     JSON schema; a Tesseract adapter is possible for offline installs.
- Both produce a **draft purchase invoice + draft NIR**. Lines are auto-matched to stock items using the supplier SKU,
  then a remembered supplier→item mapping, then a fuzzy name match. The user must confirm before posting.
- The raw extraction is stored (`raw_extraction jsonb`) along with the original file in object storage, for audit.

## Consequences
- OCR never writes stock directly; a human always reviews it.
- The OCR feature can be turned off per install with a feature flag.

## Amendment (2026-10-13) — `openspec/changes/invoice-scan-import`
- **Name matching:** "fuzzy name match" is replaced by **owner-maintained keyword rules** (`product_name_rule`), which
  run before the supplier mappings.
- **Warehouses:** lines carry a warehouse. Saving creates **one draft NIR per warehouse**.
- **Manual entry:** the full manual entry form shares the same page and review flow as the scan.
- **Feature flag:** the flag is `INVOICE_SCAN_ENABLED`, off by default.
- **Draft:** a scan returns an unsaved draft. Only the original file is stored, under a private key that the public
  `/files` endpoint refuses. Staff open it through `GET /admin/invoices/{id}/file`.

## Amendment (2026-10-14) — `openspec/changes/invoice-ocr-extractor`: no AI
The owner decided that documents are read **without AI**, on the restaurant's own server. The Claude adapter is
removed. The only reader is **Tesseract OCR**, plus a text parser with one template per supplier layout and a generic
fallback. A PDF's text layer is read directly with PDFBox.

**Measured on the Selgros photo:**
- the read takes about 2 s on one core;
- every quantity, price, VAT rate and total is right, as are the document number, date, client, card, register and
  cashier numbers, and the buyer CUI;
- the time, the buyer person and some product names were too blurred to read, and are typed or matched on the review
  page.

**Cost:**
- about 100 MB is added to the API image;
- one OCR runs at a time.

