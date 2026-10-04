import { listMeasureUnits, listStockItems, listWarehouses, type Recipe, type RecipeRequest } from "@amadya/api-client";
import { Button, Input, NativeSelect } from "@amadya/ui";
import { PlusIcon, Trash2Icon } from "lucide-react";
import { useEffect, useState } from "react";
import { useApi } from "@/lib/data";
import { formatMoney, lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

interface Line {
  stockItemId: string;
  quantity: string;
  unitId?: string;
  warehouseId?: string;
}

/** Ingredients consumed per sold portion (product) or per chosen option. Quantities default to the item's base unit. */
export function RecipeEditor({ recipe, onSave, saving }: { recipe?: Recipe; onSave: (req: RecipeRequest) => void; saving?: boolean }) {
  const t = useT();
  const { locale } = useLocale();
  const { data: items = [] } = useApi(["stock-items"], () => listStockItems());
  const { data: units = [] } = useApi(["units"], () => listMeasureUnits());
  const { data: warehouses = [] } = useApi(["warehouses"], () => listWarehouses());
  const [lines, setLines] = useState<Line[]>([]);

  useEffect(() => {
    setLines(recipe?.lines.map((l) => ({ stockItemId: l.stockItemId, quantity: l.quantity, warehouseId: l.warehouseId })) ?? []);
  }, [recipe]);

  const unitsFor = (itemId: string) => {
    const item = items.find((i) => i.id === itemId);
    const base = units.find((u) => u.id === item?.baseUnitId);
    return units.filter((u) => u.status === "ACTIVE" && u.dimension === base?.dimension);
  };
  const update = (i: number, patch: Partial<Line>) => setLines((ls) => ls.map((l, j) => (j === i ? { ...l, ...patch } : l)));

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">{t("products.recipeHint")}</p>
      {lines.map((line, i) => (
        <div key={i} className="grid grid-cols-[1fr_6rem_5rem_auto] items-center gap-2">
          <NativeSelect value={line.stockItemId} onChange={(e) => update(i, { stockItemId: e.target.value, unitId: undefined })} aria-label={t("common.item")}>
            <option value="">{t("common.choose")}</option>
            {items.map((it) => (
              <option key={it.id} value={it.id}>
                {lt(it.name, locale)} ({it.baseUnitCode})
              </option>
            ))}
          </NativeSelect>
          <Input inputMode="decimal" value={line.quantity} onChange={(e) => update(i, { quantity: e.target.value.replace(",", ".") })} aria-label={t("common.quantity")} />
          <NativeSelect value={line.unitId ?? ""} onChange={(e) => update(i, { unitId: e.target.value || undefined })} aria-label={t("common.unit")}>
            {unitsFor(line.stockItemId).map((u) => (
              <option key={u.id} value={u.isReference ? "" : u.id}>
                {u.code}
              </option>
            ))}
          </NativeSelect>
          <Button variant="ghost" size="icon-sm" onClick={() => setLines((ls) => ls.filter((_, j) => j !== i))} aria-label={t("common.remove")}>
            <Trash2Icon />
          </Button>
          {warehouses.length > 4 && (
            <NativeSelect className="col-span-4" value={line.warehouseId ?? ""} onChange={(e) => update(i, { warehouseId: e.target.value || undefined })}>
              <option value="">{t("stock.defaultWarehouse")}</option>
              {warehouses.map((w) => (
                <option key={w.id} value={w.id}>
                  {lt(w.name, locale)}
                </option>
              ))}
            </NativeSelect>
          )}
        </div>
      ))}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Button variant="outline" size="sm" onClick={() => setLines((ls) => [...ls, { stockItemId: "", quantity: "" }])}>
          <PlusIcon /> {t("products.addIngredient")}
        </Button>
        {recipe?.estimatedCost && <span className="text-sm font-semibold">{t("products.recipeCost", { cost: formatMoney(recipe.estimatedCost, locale) })}</span>}
      </div>
      <Button disabled={saving || lines.some((l) => !l.stockItemId || !(Number(l.quantity) > 0))} onClick={() => onSave({ lines })}>
        {t("common.save")}
      </Button>
    </div>
  );
}
