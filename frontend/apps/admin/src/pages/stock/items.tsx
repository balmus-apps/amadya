import { createStockItem, listMeasureUnits, listStockItems, listWarehouses, updateStockItem, type Dimension, type StockItem, type StockItemRequest, type WarehouseType } from "@amadya/api-client";
import { Button, Input, NativeSelect, Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle, Switch, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@amadya/ui";
import { PlusIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";
import { ActiveBadge, emptyText, Field, LocalizedInputs, PageHeader } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { formatQty, lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

const types: WarehouseType[] = ["INGREDIENTS", "FINISHED_GOODS", "FIXED_ASSETS", "CONSUMABLES"];
const dimensions: Dimension[] = ["MASS", "VOLUME", "COUNT", "LENGTH"];

export function ItemsPage() {
  const t = useT();
  const { locale } = useLocale();
  const [type, setType] = useState<WarehouseType | "">("");
  const [q, setQ] = useState("");
  const [editing, setEditing] = useState<StockItem | "new" | null>(null);
  const { data = [] } = useApi(["stock-items", type, q], () => listStockItems({ query: { type: type || undefined, q: q || undefined } }));
  return (
    <>
      <PageHeader
        title={t("stock.itemsTitle")}
        actions={
          <>
            <Input className="w-48" placeholder={t("common.search")} value={q} onChange={(e) => setQ(e.target.value)} />
            <NativeSelect className="w-48" value={type} onChange={(e) => setType(e.target.value as WarehouseType | "")}>
              <option value="">{t("common.all")}</option>
              {types.map((ty) => (
                <option key={ty} value={ty}>
                  {t(`stock.warehouseTypes.${ty}`)}
                </option>
              ))}
            </NativeSelect>
            <Button onClick={() => setEditing("new")}>
              <PlusIcon /> {t("stock.newItem")}
            </Button>
          </>
        }
      />
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("stock.sku")}</TableHead>
            <TableHead>{t("common.name")}</TableHead>
            <TableHead>{t("common.type")}</TableHead>
            <TableHead className="text-right">{t("stock.onHand")}</TableHead>
            <TableHead className="text-right">{t("stock.minStock")}</TableHead>
            <TableHead>{t("common.status")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.map((it) => (
            <TableRow key={it.id} className="cursor-pointer" onClick={() => setEditing(it)}>
              <TableCell className="font-mono text-xs">{it.sku}</TableCell>
              <TableCell className="font-medium">{lt(it.name, locale)}</TableCell>
              <TableCell>{t(`stock.warehouseTypes.${it.type}`)}</TableCell>
              <TableCell className="text-right tabular-nums">{formatQty(it.quantityOnHand, it.baseUnitCode, locale)}</TableCell>
              <TableCell className="text-right text-muted-foreground tabular-nums">{formatQty(it.minStock, it.baseUnitCode, locale)}</TableCell>
              <TableCell>
                <ActiveBadge active={it.active} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {editing && <ItemSheet item={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function ItemSheet({ item, onClose }: { item?: StockItem; onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const { data: units = [] } = useApi(["units"], () => listMeasureUnits());
  const { data: warehouses = [] } = useApi(["warehouses"], () => listWarehouses());
  const baseDimension = units.find((u) => u.id === item?.baseUnitId)?.dimension;
  const [form, setForm] = useState<StockItemRequest>({
    sku: item?.sku ?? "",
    name: item?.name ?? emptyText(),
    type: item?.type ?? "INGREDIENTS",
    dimension: baseDimension ?? "MASS",
    displayUnitId: item?.displayUnitId,
    defaultWarehouseId: item?.defaultWarehouseId,
    minStock: item?.minStock ?? "0",
    active: item?.active ?? true,
    packagings: item?.packagings ?? [],
  });
  const dimension = item ? (baseDimension ?? form.dimension) : form.dimension;
  const set = (patch: Partial<StockItemRequest>) => setForm((f) => ({ ...f, ...patch }));
  const save = useApiMutation(
    (req: StockItemRequest) => (item ? updateStockItem({ path: { id: item.id }, body: { ...req, dimension } }) : createStockItem({ body: req })),
    { invalidate: [["stock-items"]], success: t("common.saved"), onSuccess: onClose },
  );
  const refUnit = units.find((u) => u.isReference && u.dimension === dimension);

  return (
    <Sheet open onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="sm:max-w-xl">
        <SheetHeader>
          <SheetTitle>{item ? lt(item.name, locale) : t("stock.newItem")}</SheetTitle>
        </SheetHeader>
        <div className="flex-1 space-y-4 overflow-y-auto px-5 pb-6">
          <LocalizedInputs value={form.name} onChange={(name) => set({ name })} labelRo={t("common.nameRo")} labelEn={t("common.nameEn")} />
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label={t("stock.sku")}>
              <Input value={form.sku} onChange={(e) => set({ sku: e.target.value.toUpperCase() })} />
            </Field>
            <Field label={t("common.type")}>
              <NativeSelect value={form.type} onChange={(e) => set({ type: e.target.value as WarehouseType })}>
                {types.map((ty) => (
                  <option key={ty} value={ty}>
                    {t(`stock.warehouseTypes.${ty}`)}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            <Field label={t("stock.dimension")} hint={refUnit && `${t("stock.baseUnit")}: ${refUnit.code}`}>
              <NativeSelect value={dimension} disabled={!!item} onChange={(e) => set({ dimension: e.target.value as Dimension, displayUnitId: undefined })}>
                {dimensions.map((d) => (
                  <option key={d} value={d}>
                    {t(`stock.dimensions.${d}`)}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            <Field label={t("stock.displayUnit")}>
              <NativeSelect value={form.displayUnitId ?? ""} onChange={(e) => set({ displayUnitId: e.target.value || undefined })}>
                <option value="">{refUnit?.code ?? "—"}</option>
                {units
                  .filter((u) => u.status === "ACTIVE" && !u.isReference && u.dimension === dimension)
                  .map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.code}
                    </option>
                  ))}
              </NativeSelect>
            </Field>
            <Field label={t("stock.defaultWarehouse")}>
              <NativeSelect value={form.defaultWarehouseId ?? ""} onChange={(e) => set({ defaultWarehouseId: e.target.value || undefined })}>
                <option value="">{t("common.none")}</option>
                {warehouses.map((w) => (
                  <option key={w.id} value={w.id}>
                    {lt(w.name, locale)}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            <Field label={`${t("stock.minStock")} (${refUnit?.code ?? ""})`}>
              <Input inputMode="decimal" value={form.minStock ?? ""} onChange={(e) => set({ minStock: e.target.value.replace(",", ".") })} />
            </Field>
          </div>
          <h3 className="font-bold">{t("stock.packagings")}</h3>
          {(form.packagings ?? []).map((p, i) => (
            <div key={p.id ?? i} className="grid grid-cols-[1fr_8rem_8rem_auto] items-center gap-2">
              <Input placeholder={t("common.name")} value={p.name} onChange={(e) => set({ packagings: form.packagings!.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)) })} />
              <Input
                inputMode="decimal"
                placeholder={t("stock.qtyInBase")}
                value={p.qtyInBase}
                onChange={(e) => set({ packagings: form.packagings!.map((x, j) => (j === i ? { ...x, qtyInBase: e.target.value.replace(",", ".") } : x)) })}
              />
              <Input placeholder={t("stock.barcode")} value={p.barcode ?? ""} onChange={(e) => set({ packagings: form.packagings!.map((x, j) => (j === i ? { ...x, barcode: e.target.value } : x)) })} />
              <Button variant="ghost" size="icon-sm" onClick={() => set({ packagings: form.packagings!.filter((_, j) => j !== i) })} aria-label={t("common.remove")}>
                <Trash2Icon />
              </Button>
            </div>
          ))}
          <Button variant="outline" size="sm" onClick={() => set({ packagings: [...(form.packagings ?? []), { name: "", qtyInBase: "" }] })}>
            <PlusIcon /> {t("stock.addPackaging")}
          </Button>
          <label className="flex items-center gap-2 text-sm font-medium">
            <Switch checked={form.active} onCheckedChange={(active) => set({ active })} /> {t("common.active")}
          </label>
        </div>
        <SheetFooter>
          <Button disabled={save.isPending || !form.sku || !form.name.ro} onClick={() => save.mutate(form)}>
            {t("common.save")}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
