## 1. Remove the AI reader

- [x] 1.1 Delete `ClaudeInvoiceExtractor` and its tests, and drop `anthropic-java`. In `application.yml`,
      `docker-compose.yml` and `.env.example`, drop `ANTHROPIC_API_KEY`, `INVOICE_SCAN_MODEL` and
      `INVOICE_SCAN_BASE_URL`, and add `TESSERACT_PATH`. Verify with `./gradlew compileKotlin`, and check that
      `grep -ri anthropic backend/src` finds nothing.

## 2. OCR reader

- [x] 2.1 Add `TesseractInvoiceExtractor`:
      - image preparation;
      - PDFBox text layer, or rendering when the PDF has none;
      - the tesseract TSV subprocess, with a timeout and a one-at-a-time semaphore;
      - grouping words into lines;
      - availability checked at startup.

      Verify with a unit test of TSV → lines on a sample, and a test that a missing binary means unavailable.
- [x] 2.2 Add `InvoiceTextParser` with `SelgrosTemplate` and `GenericTemplate`. Verify with unit tests on an OCR
      text fixture from the real Selgros photo:
      - document number, date, time, register and cashier numbers, buyer CUI;
      - 5 lines;
      - totals 121.33, 15.84 and 137.17.

      Also test a generic invoice text.
- [x] 2.3 Install `tesseract-ocr tesseract-ocr-ron` in `backend/Dockerfile`. Verify that the image builds, and that
      `tesseract --list-langs` in the image lists `ron`.
- [x] 2.4 Verify on the local stack: import the Selgros photo through the API and compare the draft with the document.
      Record which fields matched, the time taken, and that the review → save → NIR flow still works.

## 3. Texts and docs

- [x] 3.1 On the review page, change "reading" so it doesn't mention AI, and add a tip for taking the photo (ro/en).
      Verify with `pnpm --filter admin typecheck` and `pnpm --filter admin build`.
- [x] 3.2 Amend ADR 0006 (Tesseract only, the owner dropped AI), update `design.md` of `invoice-scan-import`, and
      update the PR description. Verify with `./gradlew test`, and with `openspec validate --strict` for both changes.

## Results

- **Backend tests:** `./gradlew test`: 67 tests pass. The real OCR test is skipped on machines without tesseract;
  it runs where the program is installed.
- **Docker image:** builds, with `tesseract --list-langs` → `eng osd ron`. The tesseract layer is 97.5 MB.
- **Local stack:** the Selgros photo was scanned in 3.1 s end to end, with OCR taking 2.2 s on one core.
  - **Right:** the document number and date; client, card, register and cashier numbers; supplier, branch, buyer and
    buyer CUI; all 5 rows (quantity, unit, price, VAT, lot); the VAT table; totals 121.33 / 15.84 / 137.17; the
    payments (paid 137.17). There were no total warnings.
  - **Not read**, because the photo is too blurred there: the time, the buyer person, the discount, and the printed
    names of rows 1–2. Those rows still went to the right stock item through the remembered supplier code.
- **Review → save → NIR:** unchanged, and covered by `InvoiceScanIntegrationTests`.

