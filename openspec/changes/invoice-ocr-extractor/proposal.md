# Proposal

## Why

`invoice-scan-import` reads supplier photos with Claude. The owner wants **no AI**: documents are read on our own
server, cost nothing per document, and never leave the restaurant's infrastructure. Documents mostly arrive as
**photos** of paper (e.g. a Selgros "aviz"), not as PDFs.

## What Changes

- **Remove the Claude reader:**
  - the `ClaudeInvoiceExtractor` and the `anthropic-java` dependency;
  - the `ANTHROPIC_API_KEY`, `INVOICE_SCAN_MODEL` and `INVOICE_SCAN_BASE_URL` settings.
- **Add an OCR reader on the server.** OCR (Tesseract, open source) turns the photo into text, and our code then finds
  the fields in that text.
  - **Photos:**
    1. scaled down and converted to grayscale in Java;
    2. read by the `tesseract` program with Romanian and English, which gives words with their positions;
    3. regrouped into lines.
  - **PDFs** with a text layer are read directly with Apache PDFBox, without OCR. Scanned PDFs are rendered to an
    image and then read by OCR.
  - **Load on the shared server:** one OCR runs at a time, with a 60-second limit.
- **Text → invoice parser:**
  - a **template per supplier layout**: Selgros first;
  - a **generic fallback** for unknown layouts, which finds the CUIs, dates, IBAN, totals, and rows ending in
    quantity, price and value.
  - The output is the same draft as before, so review, name rules, parities, warnings and the NIR split are unchanged.
- **Docker:** the API image installs `tesseract-ocr` and the Romanian and English language data (about 100 MB measured).
  `INVOICE_SCAN_ENABLED` still turns import on.
- **Review page:**
  - a tip to photograph the document flat, whole and well lit;
  - the "reading" text no longer mentions AI.
- **ADR 0006:** amended so that Tesseract is the only reader.

## Out of scope

- Handwriting.
- Stitching several photos of one long receipt (one photo per page; PDFs can have several pages).
- Perspective correction or dewarping with OpenCV. If folded or angled photos read poorly, this is the next step.
- Templates for suppliers other than Selgros. Each one is a small follow-up, made from a real document.

## Owner decisions

| # | Decision | Options | Recommended |
|---|---|---|---|
| 1 | AI or not | keep Claude as an option / OCR only | **OCR only**: decided by owner |
| 2 | Where OCR runs | browser / server | **Server**: decided by owner |
| 3 | Next templates | which suppliers after Selgros | The 2–3 most frequent suppliers, each from one real photo |
| 4 | Accuracy expectation | — | Header and totals read well. Product rows from folded or angled photos often need manual fixes on the review page, and the totals check flags them. |

## Impact

- **Backend:**
  - `procurement` gets a new extractor and parser, and the Claude adapter is removed;
  - `build.gradle.kts`: PDFBox in, `anthropic-java` out;
  - `backend/Dockerfile`: the tesseract packages.
- **Config:** `application.yml`, `docker-compose.yml` and `.env.example`.
- **Frontend:** the review page texts only.
- **Production:**
  - about 1–5 s of CPU per photo on the shared vCPU, one at a time;
  - the tesseract process runs outside the JVM heap, within the API container's 576 MB.
