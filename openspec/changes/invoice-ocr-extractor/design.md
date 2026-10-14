# Design

## Pipeline

```
photo/PDF ──► InvoiceScanService.scan (unchanged: size check, private storage, draft building)
                 │
                 ▼
          TesseractInvoiceExtractor : InvoiceExtractor
             ├─ PDF with a text layer ─► PDFBox text (positions kept) ─┐
             ├─ PDF without text ─► PDFBox render 200 dpi ─┐           │
             └─ image ─► ImageIO (subsampled) ─► gray, ≤ 2400 px ─► tesseract TSV ─► words ─► lines
                                                                                              │
                                                                       InvoiceTextParser ◄────┘
                                                                         ├─ SelgrosTemplate (matched by CUI / name)
                                                                         └─ GenericTemplate
                                                                              ▼
                                                                       ExtractedInvoice (unchanged model)
```

## OCR

- **Command:** `tesseract <png> <out> -l ron+eng --psm 6 txt`, with `OMP_THREAD_LIMIT=1`. `--psm 6` means "a single
  uniform block", which suits receipt tables better than automatic page segmentation. Plain text output already
  keeps one printed line per line, so TSV positions were not needed.
- **Preparation (measured on the Selgros photo):**
  - Scale the longest edge to about 3600 px, which upscales small phone photos. Convert to gray and stretch the
    contrast between the 1st and 99th percentiles.
  - On the raw 1080×1920 photo, rows were garbled. With this preparation, every number in every row came out right.
- **Process:** a `ProcessBuilder` with a 60 s timeout, killed when it runs over. Only one OCR runs at a time
  (`Semaphore(1)`). A second request waits up to 30 s, then gets "busy, try again".
- **Image:** decoded with `ImageReadParam.setSourceSubsampling`, so a 4000×3000 photo is read at about 2000 px
  without first allocating the full raster. It is then turned gray with a contrast stretch and written as PNG to a
  temporary file, which is deleted afterwards.
- **Config:**

  ```yaml
  amadya.invoice-scan:
    enabled: ${INVOICE_SCAN_ENABLED:false}
    tesseract-path: ${TESSERACT_PATH:tesseract}
    languages: ron+eng
    timeout: 60s
  ```

  When enabled, the bean checks `tesseract --version` at startup. If the binary is missing, it logs a warning and
  the extractor reports itself unavailable, so the Import button stays hidden.

## Parsing

- **Normalising:** text is folded (case, diacritics) for the anchors, while values are taken from the original text.
- **Numbers:** read with the existing `num()`, which accepts "28,240" and "1.234,56".
- **Template choice:** the first template whose `matches(text)` is true, then the generic one. Selgros matches on CUI
  `11805367` or the word "SELGROS".
- **Selgros template:**
  - **Header:** values after the labels "Nr. document", "Data document" (date and time), "Nr. client",
    "Nr. legitimație", "Număr casă", "Număr casier", "Cod fiscal" (the buyer, in the customer block) and "Cumpăr.".
    The supplier is Selgros Cash & Carry S.R.L., CUI 11805367 from "C.I.F. RO11805367"; the branch is the block that
    starts with "Selgros <city>".
  - **Rows:** a line that starts with a position and contains a 13-digit EAN. The description is the text between the
    EAN and the unit. The numbers at the end of the line, in order, are price, UV, quantity, net, VAT %, VAT and gross.
    A following "Lot:" line sets the lot. A description that continues on the next line ("430G", "1KG") is appended.
  - **Totals:** the `11,0 %` and `21,0 %` rows of the "Cotă TVA" table, "Total marfă" (net, VAT, gross), the
    "Numerar" lines as payments, and "Reducerea totală" as the discount.
- **Generic template:**
  - **Parties:** CUIs found with `(RO)?\d{2,10}` near "CIF", "CUI" or "Cod fiscal"; the first one is the supplier and
    the second the buyer.
  - **Header:** the first dd.mm.yyyy date is the document date. "Nr.", "Factura nr", "Seria" and similar labels give
    the number. An RO IBAN is picked up when present.
  - **Total:** a line with "Total" followed by the largest amount is the gross total.
  - **Rows:** lines with at least three numbers at the end. They are read as quantity, unit price and value, or as
    quantity × price = value when the arithmetic agrees within 0.02.
- **Raw text:** `rawExtraction` stores `{ "engine": "tesseract", "template": "...", "text": "<OCR text>" }` for audit.

## Removal

- **Code:** `ClaudeInvoiceExtractor.kt`, `ClaudeInvoiceExtractorTest.kt` (Claude parts) and the `anthropic-java`
  dependency.
- **Settings:** `ANTHROPIC_API_KEY`, `INVOICE_SCAN_MODEL` and `INVOICE_SCAN_BASE_URL` in `application.yml`,
  `docker-compose.yml` and `.env.example`.
- **Kept:** the number parser and CUI tests.

## Risks

- **Poor photos:** folded, angled or dark photos give partial rows. The review page, the totals cross-check and the
  printed original next to the form catch this. A shooting tip is shown at the Import button.
- **CPU:** OCR takes about 1–5 s of the shared vCPU per photo, one photo at a time.
- **Template drift:** if Selgros changes its layout, the generic template takes over and the owner fixes rows by hand
  until the template is updated.
