import {
  createStockDocument,
  listMeasureUnits,
  listStockDocuments,
  listStockItems,
  listWarehouses,
  type StockDocument,
  type StockDocumentRequest,
  type StockDocumentType,
} from "@amadya/api-client";
import { Button, Dialog, DialogContent, DialogTitle, Input, NativeSelect, Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle, Table, TableBody, TableCell, TableHead, TableHeader, TableRow, Textarea, toast } from "@amadya/ui";
import { PlusIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";
import { Empty, Field, PageHeader } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { formatDate, formatMoney, formatQty, lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

const types: StockDocumentType[] = ["TRANSFER", "CONSUMPTION", "WASTE", "COUNT"];

export function DocumentsPage() {
  const t = useT();
  const { locale } = useLocale();
  const [creating, setCreating] = useState(false);
  const [viewing, setViewing] = useState<StockDocument | null>(null);
  const { data: warehouses = [] } = useApi(["warehouses"], () => listWarehouses());
  const { data = [], isLoading } = useApi(["documents"], () => listStockDocuments({ query: { limit: 200 } }));
  const wh = (id?: string) => (id ? lt(warehouses.find((w) => w.id === id)?.name, locale) : "");
  return (
    <>
      <PageHeader
        title={t("stock.documentsTitle")}
        actions={
          <Button onClick={() => setCreating(true)}>
            <PlusIcon /> {t("stock.newDocument")}
          </Button>
        }
      />
      {data.length === 0 ? (
        <Empty loading={isLoading} />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("procurement.number")}</TableHead>
              <TableHead>{t("common.date")}</TableHead>
              <TableHead>{t("common.type")}</TableHead>
              <TableHead>{t("common.warehouse")}</TableHead>
              <TableHead className="text-right">{t("stock.cost")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((d) => (
              <TableRow key={d.id} className="cursor-pointer" onClick={() => setViewing(d)}>
                <TableCell className="font-mono text-xs">{d.number}</TableCell>
                <TableCell>{formatDate(d.date, locale)}</TableCell>
                <TableCell>{t(`stock.documentTypes.${d.type}`)}</TableCell>
                <TableCell>
                  {wh(d.warehouseId)}
                  {d.targetWarehouseId && ` → ${wh(d.targetWarehouseId)}`}
                </TableCell>
                <TableCell className="text-right tabular-nums">{formatMoney(d.totalCost, locale)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {creating && <NewDocumentSheet onClose={() => setCreating(false)} />}
      {viewing && (
        <Dialog open onOpenChange={(o) => !o && setViewing(null)}>
          <DialogContent className="max-w-2xl">
            <DialogTitle>
              {viewing.number} · {t(`stock.documentTypes.${viewing.type}`)}
            </DialogTitle>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("common.item")}</TableHead>
                  <TableHead className="text-right">{t("common.quantity")}</TableHead>
                  {viewing.type === "COUNT" && <TableHead className="text-right">{t("stock.difference")}</TableHead>}
                  <TableHead className="text-right">{t("stock.cost")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {viewing.lines.map((l, i) => (
                  <TableRow key={i}>
                    <TableCell>
                      {l.itemName}
                      {l.reason && <span className="block text-xs text-muted-foreground">{l.reason}</span>}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{formatQty(l.quantity, l.unitCode, locale)}</TableCell>
                    {viewing.type === "COUNT" && <TableCell className="text-right tabular-nums">{l.difference && formatQty(l.difference, l.unitCode, locale)}</TableCell>}
                    <TableCell className="text-right tabular-nums">{l.cost && formatMoney(l.cost, locale)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {viewing.note && <p className="text-sm text-muted-foreground">{viewing.note}</p>}
          </DialogContent>
        </Dialog>
      )}
    </>
  );
}

function NewDocumentSheet({ onClose }: { onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const { data: warehouses = [] } = useApi(["warehouses"], () => listWarehouses());
  const { data: items = [] } = useApi(["stock-items"], () => listStockItems());
  const { data: units = [] } = useApi(["units"], () => listMeasureUnits());
  const [form, setForm] = useState<StockDocumentRequest>({ type: "CONSUMPTION", warehouseId: "", lines: [{ stockItemId: "", quantity: "" }] });
  const save = useApiMutation((req: StockDocumentRequest) => createStockDocument({ body: req }), {
    invalidate: [["documents"], ["balances"], ["movements"]],
    onSuccess: (doc) => {
      onClose();
      toast.success(t("stock.posted", { number: doc.number }));
    },
  });
  const setLine = (i: number, patch: Partial<StockDocumentRequest["lines"][number]>) => setForm((f) => ({ ...f, lines: f.lines.map((l, j) => (j === i ? { ...l, ...patch } : l)) }));
  const choices = (itemId: string) => {
    const item = items.find((x) => x.id === itemId);
    const base = units.find((u) => u.id === item?.baseUnitId);
    return [
      ...units.filter((u) => u.status === "ACTIVE" && u.dimension === base?.dimension).map((u) => ({ key: `u:${u.id}`, label: u.code, base: u.isReference })),
      ...(item?.packagings ?? []).map((p) => ({ key: `p:${p.id}`, label: p.name, base: false })),
    ];
  };

  return (
    <Sheet open onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="sm:max-w-2xl">
        <SheetHeader>
          <SheetTitle>{t("stock.newDocument")}</SheetTitle>
        </SheetHeader>
        <div className="flex-1 space-y-4 overflow-y-auto px-5 pb-6">
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label={t("common.type")}>
              <NativeSelect value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as StockDocumentType })}>
                {types.map((ty) => (
                  <option key={ty} value={ty}>
                    {t(`stock.documentTypes.${ty}`)}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            <Field label={t("common.warehouse")}>
              <NativeSelect value={form.warehouseId} onChange={(e) => setForm({ ...form, warehouseId: e.target.value })}>
                <option value="">{t("common.choose")}</option>
                {warehouses.map((w) => (
                  <option key={w.id} value={w.id}>
                    {lt(w.name, locale)}
                  </option>
                ))}
              </NativeSelect>
            </Field>
            {form.type === "TRANSFER" && (
              <Field label={t("stock.targetWarehouse")}>
                <NativeSelect value={form.targetWarehouseId ?? ""} onChange={(e) => setForm({ ...form, targetWarehouseId: e.target.value || undefined })}>
                  <option value="">{t("common.choose")}</option>
                  {warehouses
                    .filter((w) => w.id !== form.warehouseId)
                    .map((w) => (
                      <option key={w.id} value={w.id}>
                        {lt(w.name, locale)}
                      </option>
                    ))}
                </NativeSelect>
              </Field>
            )}
          </div>
          {form.lines.map((line, i) => {
            const opts = choices(line.stockItemId);
            const selected = line.packagingId ? `p:${line.packagingId}` : line.unitId ? `u:${line.unitId}` : (opts.find((o) => o.base)?.key ?? "");
            return (
              <div key={i} className="grid grid-cols-[1fr_7rem_6rem_auto] items-center gap-2">
                <NativeSelect value={line.stockItemId} onChange={(e) => setLine(i, { stockItemId: e.target.value, unitId: undefined, packagingId: undefined })} aria-label={t("common.item")}>
                  <option value="">{t("common.choose")}</option>
                  {items.map((it) => (
                    <option key={it.id} value={it.id}>
                      {lt(it.name, locale)}
                    </option>
                  ))}
                </NativeSelect>
                <Input
                  inputMode="decimal"
                  placeholder={form.type === "COUNT" ? t("stock.counted") : t("common.quantity")}
                  value={line.quantity}
                  onChange={(e) => setLine(i, { quantity: e.target.value.replace(",", ".") })}
                />
                <NativeSelect
                  value={selected}
                  onChange={(e) => {
                    const [kind, id] = e.target.value.split(":");
                    setLine(i, { unitId: kind === "u" ? id : undefined, packagingId: kind === "p" ? id : undefined });
                  }}
                  aria-label={t("common.unit")}
                >
                  {opts.map((o) => (
                    <option key={o.key} value={o.key}>
                      {o.label}
                    </option>
                  ))}
                </NativeSelect>
                <Button variant="ghost" size="icon-sm" onClick={() => setForm({ ...form, lines: form.lines.filter((_, j) => j !== i) })} aria-label={t("common.remove")}>
                  <Trash2Icon />
                </Button>
                {(form.type === "WASTE" || form.type === "CONSUMPTION") && (
                  <Input className="col-span-4" placeholder={t("stock.reason")} value={line.reason ?? ""} onChange={(e) => setLine(i, { reason: e.target.value })} />
                )}
              </div>
            );
          })}
          <Button variant="outline" size="sm" onClick={() => setForm({ ...form, lines: [...form.lines, { stockItemId: "", quantity: "" }] })}>
            <PlusIcon /> {t("stock.addLine")}
          </Button>
          <Field label={t("common.notes")}>
            <Textarea rows={2} value={form.note ?? ""} onChange={(e) => setForm({ ...form, note: e.target.value })} />
          </Field>
        </div>
        <SheetFooter>
          <Button
            disabled={
              save.isPending ||
              !form.warehouseId ||
              (form.type === "TRANSFER" && !form.targetWarehouseId) ||
              form.lines.length === 0 ||
              form.lines.some((l) => !l.stockItemId || !(Number(l.quantity) >= 0) || l.quantity === "")
            }
            onClick={() => save.mutate(form)}
          >
            {t("common.save")}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
