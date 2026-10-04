# NIR — Notă de Intrare-Recepție și Constatare de Diferențe

The NIR is the accounting document for the receipt of goods into a warehouse. It records any difference
between the quantities on the supplier document and the quantities actually received.

## Header fields
| Field | Notes |
|---|---|
| Unit | Restaurant legal name, CUI, Reg. Com. (from `settings`) |
| NIR number / date | Sequence per year (`NIR-2026-000123`); configurable prefix |
| Warehouse (gestiune) | Target warehouse; its type decides which stock items are allowed |
| Supplier | Name, CUI |
| Supplier document | Invoice series/number/date and/or delivery note (aviz) number/date |
| Reception committee | 1–3 member names (they sign the printed NIR) |
| Status | `DRAFT` → `POSTED` → (`CANCELLED` only via a reversing NIR) |

## Line fields
| Field | Notes |
|---|---|
| Stock item, unit | Unit as on the document; converted to the item's base unit when posting |
| Qty per document | `qty_document` |
| Qty received | `qty_received` |
| Difference | `qty_received - qty_document` (generated column), with a reason when ≠ 0 |
| Unit price excl. VAT | Purchase price |
| VAT rate, VAT value | From the invoice line |
| Value excl. VAT | `qty_received × unit_price` |
| Sale price (optional) | For retail-style valuation of finished goods |

## Rules
1. A NIR is created from a purchase invoice (XML, scan or manual) or standalone (e.g. a delivery note only).
2. While in `DRAFT` it can be edited freely. **Posting** happens in one transaction:
   - converts each line to the item's reference unit (see `units-of-measure.md`); lines with an `UNMAPPED` unit block posting;
   - writes one `stock_movement` per line (`RECEIPT_NIR`, +qty, unit cost per reference unit);
   - creates one **FIFO lot** per line (`stock_lot`: received_at = NIR date, qty, unit_cost) and updates `stock_balance`;
   - publishes `NirPosted`.
3. A posted NIR is immutable. To correct it, post a reversing NIR (negative quantities) linked to the original.
   A reversal is only allowed while the lots it created are not yet consumed; otherwise it is posted as an adjustment.
4. Differences are recorded on the NIR and reported per supplier; they never silently change the invoice.
5. The NIR is printable in the legal layout, with the committee signature block (print view in the admin app; save as PDF from the browser).
7. Invoice lines are matched to stock items by the supplier's item code, then by remembered description, then by exact name;
   each manual match is remembered per supplier. Unknown units (e.g. `XBX`) become `UNMAPPED` units or are mapped to an item packaging.
6. Allowed warehouse types for a NIR: all four (INGREDIENTS, FINISHED_GOODS, FIXED_ASSETS, CONSUMABLES).

## Valuation: FIFO
Stock is valued **FIFO** (first in, first out); see `warehouses.md` § FIFO lots.

## Open (confirm with the accountant)
- Whether a sale-price column (retail valuation, with markup and VAT included) is required for finished goods.
