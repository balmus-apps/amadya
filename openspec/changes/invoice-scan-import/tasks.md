## 1. Contract and database

- [x] 1.1 In `api/openapi/openapi.yaml`:
      - extend `PurchaseInvoice`, `PurchaseInvoiceRequest` and the line schemas with the new fields;
      - add `InvoiceDraft`, `scanPurchaseInvoice` (`POST /admin/invoices/scan`, multipart);
      - add the `ProductNameRule` CRUD and `testProductNameRule`.

      Verify with `./gradlew openApiGenerate` in `backend/` and `pnpm gen:api` in `frontend/`.
- [x] 1.2 Add migration `V19__invoice_scan.sql`: the invoice and line columns, `product_name_rule`, and `BU`/`BUC` as aliases
      of `buc`. Verify that the API starts against the local DB (`docker compose up -d --build api`).

## 2. Backend

- [x] 2.1 In `media`, accept PDF (`%PDF-` signature) and set the multipart limit to 10 MB. Verify with a test: PDF
      accepted, a fake PDF with a `.pdf` name but other bytes refused.
- [x] 2.2 Add `ProductNameRules` (matching, ordering, diacritic-insensitive, safe regex) and its CRUD endpoints.
      Verify with unit tests: "LAPTE FULGA 3.5%" and "Lapte Zuzu 3,7% grăsime" → "Lapte"; priority order respected;
      an invalid regex returns 422.
- [x] 2.3 Add `InvoiceExtractor` port, `ClaudeInvoiceExtractor` adapter (load the `claude-api` skill first) and
      `amadya.invoice-scan.*` config with the flag. Verify with an adapter test that uses a stubbed HTTP response
      built from the Selgros sample, and with the bean absent when the flag is off.
- [x] 2.4 Add `scan()`: store the file, extract, upsert-preview the supplier, run name rules then `autoMatch`, resolve
      units, set default warehouses, and add warnings. Verify with an integration test using the sample: 5 lines,
      net 121.33, VAT 15.84 (11% 10.60, 21% 5.24), gross 137.17, no warnings; and a duplicate-number warning on the
      second scan after saving.
- [x] 2.5 Extend `createPurchaseInvoice`: new fields, new stock items and packagings, remember mappings, and one draft
      NIR per warehouse. Verify with an integration test: lines in MATERII and MARFA → 2 draft NIRs with the right
      lines; an unconvertible unit → 422.

## 3. Admin frontend

- [x] 3.1 Build the `invoice-new` page with the header form, lines table, computed totals and warehouse dropdowns,
      plus the router and navigation entries. Verify that a manual 2-line invoice saves and opens its detail page.
- [x] 3.2 Add the import button (hidden when the flag is off), file preview and warnings. Verify by uploading the
      Selgros photo and getting every field filled.
- [x] 3.3 Add the parity dialog and the "save as rule" dialog. Verify that "Univer sos usturoi" in BU on an item
      measured in g asks for "1 BU = ? g", and that the rule saved after a rename applies on the next scan.
- [x] 3.4 Build the name rules page (list, edit, priority, active, test box), with ro/en messages. Verify with
      `pnpm -F admin typecheck` and `pnpm -F admin build`.

## 4. Docs and finish

- [x] 4.1 Update ADR 0006 (keyword rules instead of fuzzy matching; NIR split by warehouse), `docs/domain/nir.md`,
      `units-of-measure.md` (primary and secondary units, parity dialog) and `.env.example` (`INVOICE_SCAN_*`).
- [x] 4.2 Do an end-to-end check in the running app (`run` skill): scan → edit → save → 2 draft NIRs → post one and
      check that stock went up in the right warehouse.
      Done on the local stack through the API that the page calls: the Selgros photo through Caddy, then scan
      (Claude answered by a local stub), save, 2 draft NIRs, posting MATERII, and stock checks: Pleskavica +2298 g, sauce
      +860 ml (2 BU × 430 ml), cucumbers +2 buc. A rescan reuses the names and flags the duplicate. Still open: a visual
      pass in a browser (blocked in this session), and one scan with a real `ANTHROPIC_API_KEY`.
- [ ] 4.3 Run `./gradlew test` and the frontend build in CI; both must be green. Open the PR. Do not merge to main.
