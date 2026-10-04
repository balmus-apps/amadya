# Units of measure

Builds on the `quantity_types` pattern from `event-planner`
(`backend/app/modules/warehouse/flyway/V7__quantity_types.sql`). That pattern is a lookup table with names unique
case-insensitively, created on the fly from product and invoice input. Here it is extended with **dimensions**, a **reference unit per
dimension**, and **conversion factors** to that reference unit.

## Concepts
| Concept | Meaning |
|---|---|
| Dimension | `MASS`, `VOLUME`, `COUNT`, `LENGTH` (extensible) |
| Reference unit | Exactly one per dimension, with `factor = 1`. All stock is stored and costed in it. Defaults: `g`, `ml`, `buc` (pcs), `cm` |
| Unit | Belongs to one dimension. `factor` = how many reference units 1 of this unit equals (`kg` → 1000, `l` → 1000, `bax`... see below) |
| Item packaging | Conversion that depends on the item (1 box of *Cola 330ml* = 24 buc; 1 bag of *Flour* = 25 kg). It can cross dimensions only through the item |

So `1 kg = 1000 g` is the parity definition `unit(kg).factor = 1000` relative to `unit(g)`, the reference unit of MASS.

## Tables
```sql
CREATE TABLE unit_of_measure (
  id            UUID PRIMARY KEY,
  code          TEXT NOT NULL,              -- 'kg', 'g', 'l', 'ml', 'buc'
  name          JSONB NOT NULL,             -- {"ro":"kilogram","en":"kilogram"}
  dimension     TEXT NOT NULL CHECK (dimension IN ('MASS','VOLUME','COUNT','LENGTH')),
  factor        NUMERIC(18,6) NOT NULL CHECK (factor > 0),  -- in reference units
  is_reference  BOOLEAN NOT NULL DEFAULT false,
  aliases       TEXT[] NOT NULL DEFAULT '{}', -- 'kilogram','kgr','KG' — for invoice matching
  status        TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','UNMAPPED')),
  ...audit columns
);
CREATE UNIQUE INDEX ux_uom_code_lower ON unit_of_measure (lower(code));
-- exactly one reference unit per dimension, and it must have factor 1
CREATE UNIQUE INDEX ux_uom_reference ON unit_of_measure (dimension) WHERE is_reference;
ALTER TABLE unit_of_measure ADD CONSTRAINT ck_reference_factor CHECK (NOT is_reference OR factor = 1);

CREATE TABLE stock_item_packaging (
  id             UUID PRIMARY KEY,
  stock_item_id  UUID NOT NULL REFERENCES stock_item(id),
  name           TEXT NOT NULL,             -- 'bax 24', 'sac 25kg'
  qty_in_base    NUMERIC(18,6) NOT NULL CHECK (qty_in_base > 0),  -- in the item's base (reference) unit
  barcode        TEXT,
  UNIQUE (stock_item_id, lower(name))
);
```
`stock_item.base_unit_id` must be the **reference unit** of the item's dimension (g, ml or buc). Items can declare
other `allowed_unit_ids` for input, such as recipes in g, purchases in kg, and display in kg.

## Rules
1. **Storage and costing** always use the item's reference unit. Unit cost is stored per reference unit, with 6 decimals
   (e.g. 0.024500 RON/g) so precision isn't lost.
2. **Conversion**: `qty_ref = qty × unit.factor` for a unit in the same dimension, or `qty × packaging.qty_in_base` for a packaging.
   Converting across dimensions without a packaging definition is rejected (e.g. a recipe line in `buc` for an item measured in `g`).
3. **Display**: the UI shows a quantity in the best-fitting unit (e.g. `12500 g` shows as `12.5 kg`) using the item's preferred display unit.
4. **On-the-fly creation** (from `event-planner`): an unknown unit on an invoice (e.g. `"BAX"`) is matched against `code` and `aliases`,
   case-insensitively. If nothing matches, a unit is created with `status = UNMAPPED`. The NIR line stays blocked until an admin
   either maps it to a dimension and factor, or defines it as packaging for that item. Stock is never posted with an unknown factor.
5. The reference units and the standard units (`g, kg, mg, ml, l, cl, buc, cm, m`) are seeded by a migration (not dev seed),
   because the business rules depend on them. Admins can add more units but cannot change a reference unit's factor.
6. Changing a non-reference unit's factor only affects new documents. Posted documents keep the quantity they computed in reference units.

## Examples
| Input | Stored |
|---|---|
| NIR: 2.5 kg pork at 32 RON/kg | +2500 g at 0.032 RON/g |
| Recipe: 150 g pork | −150 g |
| NIR: 3 bax Cola (24 buc each) at 96 RON/bax | +72 buc at 4.00 RON/buc |
| Recipe: 0.03 l sauce | −30 ml |
