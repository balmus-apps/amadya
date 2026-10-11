# RO e-Factura — findings

Research notes from October 2026, gathered while planning the integration ([ADR 0009](../adr/0009-efactura-integration.md)).
Check the official ANAF sources before relying on a detail: the API and the rules change often.

## What it is
- RO e-Factura is the **national e-invoicing system** run by ANAF. Invoices are uploaded as **UBL 2.1 XML** following
  the Romanian specification **CIUS-RO**; credit notes use the same standard as `CN`. ANAF validates each invoice, signs
  it, and makes it available to both parties in their **SPV** (Spațiul Privat Virtual, the company's online ANAF
  account).
- An invoice that did not go through e-Factura is treated as **non-existent**: the buyer cannot deduct its VAT.

## Who must send what
- **B2B** (between companies), **B2G** (to public bodies) and, since 2025, **B2C** (to individuals): every invoice
  issued by a Romanian business must be uploaded.
- **Deadline:** **5 working days** from the issue date. Until the end of 2025 it was 5 calendar days; OUG 89/2025
  changed it from 1 January 2026.
- **Fines:** unchanged by the 2026 change; 5,000–10,000 RON for large taxpayers, lower bands for small and medium ones.

### Restaurant specifics
- **Fiscal receipt (bon fiscal) with the buyer's CUI:** when it meets the conditions of a simplified invoice
  (B2B, under 100 EUR), it is **exempt**. Its data already reaches ANAF through the fiscal printer.
- **A separate invoice**, even one issued based on a fiscal receipt (e.g. a company asks for an invoice for a company
  lunch), **must be uploaded**.
- **Supplier invoices:** every invoice from a Romanian supplier is already in the restaurant's SPV as signed XML.
  This gives exact data (supplier, lines, quantities, prices, VAT) without typing or scanning. Photographing paper
  invoices is only needed for foreign suppliers or ones outside the system.

## Access: OAuth 2.0
- **Flow:** OAuth 2.0 authorization code.
  - Authorize: `https://logincert.anaf.ro/anaf-oauth2/v1/authorize` with `response_type=code`, `client_id`,
    `redirect_uri` and `token_content_type=jwt`.
  - Token: `https://logincert.anaf.ro/anaf-oauth2/v1/token` with HTTP Basic auth (`client_id:client_secret`) and
    `grant_type=authorization_code` or `refresh_token`. The code must be exchanged within about 60 seconds.
- **The authorize step needs a qualified digital certificate** (USB token) whose holder has **SPV rights for the
  company's CUI**: the administrator or the accountant. It happens once, in a browser, and cannot be automated. The
  token exchange itself needs no certificate.
- **Token lifetimes:**
  - The access token (a JWT) lasts **90 days**.
  - The refresh token lasts **365 days**.
  - **A refresh returns a new access token and a new refresh token**; store both every time.
  - In practice: re-authorize with the certificate about **once a year**.
- **App registration:** a developer account on anaf.ro (*Servicii Online → Înregistrare utilizatori → Dezvoltatori
  aplicații → Înregistrare pentru API-uri*) creates an app profile.
  - The profile has a name and one or more callback URLs, which must use `https://` (localhost is allowed).
  - Registering it gives the `client_id` and `client_secret`.
- **Revocation:** there is no API for it. Use *Renunțare OAuth* in the portal, which deletes all of the account's app
  profiles.
- **Alternative:** certificate-based mTLS at `webserviceapl.anaf.ro` needs the USB token for every call, so it is not
  usable from a server.

## API
Base URL: `https://api.anaf.ro/{prod|test}/FCTEL/rest`. Authenticate with `Authorization: Bearer <access_token>`. XML
bodies go as `Content-Type: text/plain`.

| Endpoint | What it does | Notes / daily limits |
|---|---|---|
| `GET listaMesajePaginatieFactura?startTime=&endTime=&cif=&pagina=&filtru=` | Messages in a time window | **Times in ms**, window at most **60 days** back, `endTime` not in the future, 500 per page. `filtru`: `P` = received invoices, `T` = sent, `E` = errors, `R` = buyer messages. 100,000 calls a day per CUI. |
| `GET listaMesajeFactura?zile=1..60&cif=&filtru=` | The same list by number of days | At most 500 results, then use the paginated call. 1,500 calls a day per CUI. |
| `GET descarcare?id=` | ZIP with **the invoice XML (or the error report) and the Ministry of Finance signature XML** | **Only available for 60 days**, then it returns a JSON error. 10 downloads a day per message. |
| `POST upload?standard=UBL\|CN\|CII\|RASP&cif=[&extern=DA][&autofactura=DA]` | Send an invoice | Returns `index_incarcare`; `ExecutionStatus` 0 = accepted for processing. `RASP` messages are limited to 1,000 a day per CUI. |
| `POST uploadb2c?…` | Send an invoice to an individual | Mandatory for B2C since 31 March 2025. |
| `GET stareMesaj?id_incarcare=` | Status of a sent invoice | `in prelucrare` (still processing), `ok` (accepted, with `id_descarcare`), `nok` (rejected), or rejected at upload. 100 queries a day per message. |
| `POST validare/FACT1\|FCN` | Validate the XML before sending | Public, no login (also on `webservicesp.anaf.ro/prod`). **Production only.** |
| `POST transformare/FACT1\|FCN[/DA]` | Official PDF of an invoice XML | Public, **production only**. `/DA` skips validation. |
| `POST /api/validate/signature` (at the host root) | Check the ANAF signature of a downloaded invoice | multipart: `file` + `signature` |

**Each message in the list** has `id` (for `descarcare`), `id_solicitare` (the upload index), `data_creare`, `cif`,
`tip` (`FACTURA PRIMITA`, `FACTURA TRIMISA`, `ERORI FACTURA` or `MESAJ CUMPARATOR`) and `detalii`. The `detalii` field
is free text that holds the supplier and buyer CUIs.

**Global limit:** about 1,000 requests a minute. Going over the limits repeatedly can block the app.

### Gotchas
- **The 60-day download window:** a received invoice not downloaded in time is lost to the API. Sync regularly and
  archive the ZIP unchanged: the signed XML is the legal original.
- **Listing lag:** a message appears in the list seconds to about 15 minutes after processing. `data_creare` is the time
  it was listed, not the upload time. Overlap the sync windows.
- **Clock:** the time window is checked against ANAF's clock. A server clock even about 1 second ahead is rejected, and
  the error message quotes ANAF's time.
- **Self-billing** creates two entries (sent and received) sharing one `id_solicitare`.
- **Firewall on `webservicesp`:** some XML patterns are blocked, and the block arrives as an HTML page with HTTP 200.
  Known triggers are `xsi:schemaLocation` paths and shell-like characters in addresses. Strip `xsi:schemaLocation`
  before validating or converting.
- **Test environment:** `api.anaf.ro/test` uses the same OAuth tokens, but `validare`, `transformare` and signature
  validation exist **only in production** (test returns 404).
- **Company lookup:** ANAF's public `PlatitorTvaRest` service (by CUI) returns name, address and VAT status. It is useful
  for filling in suppliers and invoice customers.

## Already in Amadya
- **UBL parser:** `EFacturaParser` is XXE-safe and reads CIUS-RO invoices.
- **Manual import:** `POST /admin/invoices/import/efactura` takes an XML downloaded by hand from SPV.
- **Line matching:** supplier code → stock item mappings are remembered, and new invoices are matched automatically.
- **Stock flow:** purchase invoice → NIR → FIFO stock.

What is missing is the connection to ANAF (OAuth, sync, archive) and issuing our own invoices. Both are planned in
[ADR 0009](../adr/0009-efactura-integration.md).

## Sources
- [ANAF: e-Factura web services (official URL list)](https://static.anaf.ro/static/10/Anaf/Informatii_R/Servicii_web/url_eFactura.html)
- [Ministry of Finance: e-Factura API presentation](https://mfinante.gov.ro/static/10/eFactura/prezentare%20api%20efactura.pdf)
- [anafpy: e-Factura API reference](https://anafpy.readthedocs.io/en/latest/anaf-reference/efactura/api/)
- [anafpy: ANAF OAuth reference](https://anafpy.readthedocs.io/en/latest/anaf-reference/oauth/authentication/)
- [Mandato: ANAF e-Factura integration guide (2026)](https://getmandato.dev/guides/romania-anaf)
- [StartupCafe: 5 working days from 2026 (OUG 89/2025)](https://startupcafe.ro/e-factura-2026-oug-trenulet-a-modificat-termenul-de-transmitere-a-facturilor-ce-amenzi-risca-antreprenorii-daca-nu-l-respecta-si-ce-recomanda-un-contabil-93528)
- [Fiscalitatea: simplified invoices and the fiscal receipt exception](https://www.fiscalitatea.ro/e-factura-este-obligatorie-transmiterea-facturilor-simplificate-cu-o-anumita-exceptie-24433/)
