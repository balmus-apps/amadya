# ADR 0003 — On-site Print Bridge for kitchen and fiscal printers

**Status:** Accepted — 2026-10-04

## Context
Kitchen printers (ESC/POS) and the Romanian fiscal printer (AMEF, e.g. Datecs, Tremol) connect over USB, Serial,
Bluetooth or LAN. Browsers and a cloud backend can't reach these devices reliably. Fiscal receipts must
only be issued by the AMEF.

## Decision
- A small **Kotlin agent** (`print-bridge/`) runs on the POS PC, a mini-PC or a Raspberry Pi (an Android build may come later).
- It authenticates with a device token and opens an **outbound** STOMP-over-WSS connection to the Core API.
  It subscribes to the jobs for the printers it owns.
- **Until the hardware is chosen (TODO):** printing and fiscal run through `LogPrinterDriver` / `LogFiscalDriver`. They write the rendered
  ticket or receipt to the log and to `print-bridge/out/*.txt`, and return a fake fiscal number. This works the same way
  inside the API (no bridge needed) when `PRINTING_MODE=log`, so phases 1–5 don't depend on the bridge.
- Drivers:
  - `EscPosDriver` over USB / Serial / Bluetooth SPP / TCP 9100.
  - `FiscalDriver` with the operations `printReceipt`, `xReport`, `zReport`, `cashIn`, `cashOut`, `status`. Implementations: `DryRun` (dev),
    `Datecs` (first), `Tremol` (later).
- Jobs are idempotent (job id). The bridge ACKs or NACKs each job and the API retries with backoff.
  A fiscal job is never re-sent automatically after an ambiguous failure: the bridge checks the
  printer's last receipt number first.
- Templates (Handlebars) are rendered server-side into a printer-neutral document model. The bridge encodes it for the device.

## Consequences
- No inbound ports at the restaurant; works whether the platform runs on-site or in the cloud.
- One extra component to install on-site, packaged as a native installer or a systemd service.
