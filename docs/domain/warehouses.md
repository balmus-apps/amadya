# Warehouses & stock

## Warehouse types
| Type | Examples | In | Out |
|---|---|---|---|
| `INGREDIENTS` | flour, meat, cabbage, sauces | NIR, transfer | Consumption by recipe on sale, waste, count adjustment |
| `FINISHED_GOODS` | canned drinks, bottled water, packaged desserts | NIR, production | Sale (1:1 for RESALE products), waste |
| `FIXED_ASSETS` (obiecte de inventar) | TV, tables, pots, knives | NIR | Transfer, scrapping (casare) |
| `CONSUMABLES` | paper, napkins, soap, to-go packaging | NIR | Consumption note (bon de consum), optionally auto-consumed per to-go order |

An install can have several warehouses of the same type (e.g. "Kitchen" and "Bar" ingredient stores).

## Model
- `stock_item`: what is stored (sku, localized name, base unit, type, min stock).
- `stock_balance (warehouse, item)`: quantity + total value. This is a projection of the lots / ledger.
- `stock_lot`: one per receipt (NIR line, count surplus, transfer-in): `received_at`, `qty_initial`, `qty_remaining`, `unit_cost`.
- `stock_lot_allocation`: which lots each outgoing movement consumed (qty × cost), giving exact FIFO cost of goods sold.
- `stock_movement`: **append-only ledger**. Each movement references a source document (NIR, transfer, order, count…).
  Balances can always be rebuilt from the ledger.
- Units: see `units-of-measure.md`. Each dimension has one reference unit (g, ml, buc); other units define a factor relative to it
  (1 kg = 1000 g). Item-specific packaging handles conversions like 1 box = 24 buc. Movements and lots are stored in the reference unit.

## FIFO lots
- **In:** every receipt creates a lot. Lots are ordered by `received_at`, then by creation sequence.
- **Out** (consumption, sale, waste, transfer-out): take from the oldest lots with `qty_remaining > 0` until the quantity is covered.
  Write `stock_lot_allocation` rows; the movement cost is Σ(qty × lot cost).
- **Transfer:** the lots consumed at the source are re-created at the destination with the **same `received_at` and cost**, so FIFO
  order and value move with the goods.
- **Count:** a shortage is consumed FIFO; a surplus creates a lot at the last known cost.
- **Concurrency:** lots for one (warehouse, item) are locked with `SELECT … FOR UPDATE` (ordered), so two orders can't take the same lot.

## Products ↔ stock
| Product kind | Stock effect when sold |
|---|---|
| `RECIPE` | Each `recipe_line` is deducted from its warehouse (e.g. 150 g meat, 1 bun) |
| `RESALE` | 1 × the linked `stock_item` is deducted from FINISHED_GOODS |
| `SERVICE` | none |

Modifier options can deduct their own stock item too (e.g. "cheese sauce" = 30 g sauce).

Consumption is posted when the order reaches the kitchen (`OrderPlaced`: paid takeaway, counter or dine-in round) and returned to
the same lots if the order is cancelled (`OrderCancelled`). Negative
stock is **allowed but flagged**, since kitchens can't stop serving mid-shift. With FIFO, any uncovered quantity is recorded as a
**deficit** (`stock_deficit`) at the last known cost. When the next lot arrives, the deficit is settled against it first and the cost
difference corrects the original movement's cost. A low-stock alert is raised and shown on the dashboard.

Implementation: `inventory.internal.FifoLedger` (explicit SQL with row locks). `stock_balance` is recomputed from lots and open
deficits after every change, so it can always be rebuilt from the ledger.

## Other documents
- **Transfer note**: from one warehouse to another; lots move with their original cost and receipt date (FIFO preserved).
- **Consumption note (bon de consum)**: manual consumption, e.g. of consumables.
- **Inventory count**: counted quantity vs. system quantity → an adjustment movement for the difference.
- **Waste (pierderi)**: with a reason.
