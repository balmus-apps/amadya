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
- **Extraction:** Claude (`claude-sonnet-5-5` by default, `INVOICE_SCAN_MODEL`) uses **structured outputs**, a JSON
  schema in `output_config.format`, rather than a forced tool call, which current models reject. Server-side refusal
  fallbacks are on.
- **Feature flag:** the flag is `INVOICE_SCAN_ENABLED`, off by default.
- **Draft:** a scan returns an unsaved draft. Only the original file is stored, under a private key that the public
  `/files` endpoint refuses. Staff open it through `GET /admin/invoices/{id}/file`.

