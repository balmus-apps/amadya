## MODIFIED Requirements

### Requirement: Import from photo or PDF
The page SHALL offer an import button that accepts JPEG, PNG, WebP or PDF up to 10 MB. The button SHALL fill the
header and lines from the document as an unsaved draft. The document SHALL be read **on the restaurant's own server
with OCR, without any AI service**: a PDF's text layer is used when present, and OCR otherwise. Text SHALL be turned
into fields by a template for the supplier's layout when one exists, and by generic rules otherwise. The original file
and the recognised text SHALL be kept with the saved invoice. Import SHALL be hidden when the scan feature is turned
off or the OCR engine is not installed. Manual entry SHALL always remain available.

#### Scenario: Selgros delivery note photo
- **WHEN** the user imports a clear photo of a Selgros "Aviz de însoțire a mărfii"
- **THEN** the document number, date, time, client, register and cashier numbers, and buyer are filled
- **AND** the product lines and the printed totals (137.17) are proposed, and any line that does not add up is flagged

#### Scenario: Nothing leaves the server
- **WHEN** a document is imported
- **THEN** no request is made to any external service

#### Scenario: Nothing saved before review
- **WHEN** a document is imported and the user leaves the page without saving
- **THEN** no invoice, stock item or NIR exists

#### Scenario: Unknown layout
- **WHEN** the supplier has no template
- **THEN** the generic rules fill what they find (CUIs, date, total, rows that add up), and the rest is entered by hand
