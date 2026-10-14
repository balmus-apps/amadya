import {
  client,
  createProductNameRule,
  createPurchaseInvoice,
  getInvoiceScanStatus,
  listMeasureUnits,
  listStockItems,
  listSuppliers,
  listWarehouses,
  type InvoiceDocType,
  type InvoiceDraft,
  type InvoiceDraftWarning,
  type InvoicePaymentMethod,
  type MeasureUnit,
  type ProductNameMatchType,
  type PurchaseInvoiceLineRequest,
  type PurchaseInvoiceRequest,
  type StockItem,
  type SupplierRequest,
} from "@amadya/api-client";
import { Badge, Button, Card, CardContent, Dialog, DialogContent, DialogDescription, DialogTitle, Input, NativeSelect, Table, TableBody, TableCell, TableHead, TableHeader, TableRow, toast } from "@amadya/ui";
import { useNavigate } from "@tanstack/react-router";
import { AlertTriangleIcon, FileUpIcon, Loader2Icon, PlusIcon, Trash2Icon, WandSparklesIcon } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Field, PageHeader } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { formatMoney, lt, today } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";
import { accessToken } from "@/lib/session";

/** One editable line. `description` is our name; `originalDescription` is what the document printed. */
type Line = {
  key: string;
  originalDescription?: string;
  description: string;
  stockItemId?: string;
  ruleId?: string;
  supplierCode?: string;
  lot?: string;
  quantity: string;
  unitCode: string;
  packagingId?: string;
  newPackaging?: { name: string; qtyInBase: string };
  unitPrice: string;
  vatPercent: string;
  warehouseId: string;
};

type Header = Omit<PurchaseInvoiceRequest, "lines">;

const DOC_TYPES: InvoiceDocType[] = ["FACTURA", "AVIZ", "BON"];
const PAYMENTS: InvoicePaymentMethod[] = ["CASH", "CARD", "TRANSFER", "OTHER"];

let seq = 0;
const newKey = () => `l${++seq}`;

const emptyLine = (warehouseId = ""): Line => ({ key: newKey(), description: "", quantity: "", unitCode: "", unitPrice: "", vatPercent: "", warehouseId });

/** Accepts "28,24" as typed in Romanian. */
const toNum = (v: string) => (v.trim() === "" ? NaN : Number(v.replace(/\s/g, "").replace(",", ".")));
const clean = (v: string) => (Number.isFinite(toNum(v)) ? String(toNum(v)) : v);
const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;
const fold = (s: string) => s.normalize("NFD").replace(/\p{M}+/gu, "").toUpperCase().trim();

export function findUnit(units: MeasureUnit[], code: string) {
  const c = fold(code);
  if (!c) return undefined;
  return units.find((u) => fold(u.code) === c) ?? units.find((u) => u.aliases.some((a) => fold(a) === c));
}

/** How a line's unit converts into its item's stock unit, or why it does not. */
export function unitStatus(line: Line, item: StockItem | undefined, units: MeasureUnit[]) {
  const unit = findUnit(units, line.unitCode);
  if (!item) {
    if (!unit || unit.status !== "ACTIVE" || !unit.dimension) return { ok: false as const, kind: "newNeedsKnownUnit" as const, unit };
    return { ok: true as const, kind: "primary" as const, unit };
  }
  const base = units.find((u) => u.id === item.baseUnitId);
  if (line.newPackaging) return { ok: true as const, kind: "parity" as const, text: `1 ${line.newPackaging.name} = ${Number(line.newPackaging.qtyInBase)} ${base?.code ?? ""}`, unit };
  const packaging = item.packagings.find((p) => p.id === line.packagingId) ?? item.packagings.find((p) => fold(p.name) === fold(line.unitCode));
  if (packaging) return { ok: true as const, kind: "packaging" as const, packagingId: packaging.id, text: `1 ${packaging.name} = ${Number(packaging.qtyInBase)} ${base?.code ?? ""}`, unit };
  if (unit && unit.status === "ACTIVE" && unit.dimension && unit.dimension === base?.dimension) {
    return { ok: true as const, kind: "unit" as const, text: unit.id === base.id ? undefined : `1 ${unit.code} = ${Number(unit.factor)} ${base.code}`, unit };
  }
  return { ok: false as const, kind: "needsParity" as const, unit };
}

/** Words the printed name and our name share ("LAPTE FULGA 3.5%" + "Lapte" → "LAPTE"); else the printed words without sizes. */
function suggestPattern(printed: string, name: string) {
  const words = (s: string) => fold(s).split(/[^\p{L}\p{N}%.]+/u).filter((w) => w.length > 1);
  const ours = new Set(words(name));
  const shared = words(printed).filter((w) => ours.has(w));
  if (shared.length > 0) return shared.join(" ");
  return words(printed).filter((w) => !/\d/.test(w)).slice(0, 2).join(" ");
}

export function InvoiceNewPage() {
  const t = useT();
  const { locale } = useLocale();
  const navigate = useNavigate();
  const fileInput = useRef<HTMLInputElement>(null);
  const { data: scanStatus } = useApi(["invoice-scan-status"], () => getInvoiceScanStatus());
  const { data: items = [] } = useApi(["stock-items"], () => listStockItems());
  const { data: units = [] } = useApi(["units"], () => listMeasureUnits());
  const { data: warehouses = [] } = useApi(["warehouses"], () => listWarehouses());
  const { data: suppliers = [] } = useApi(["suppliers"], () => listSuppliers());
  const defaultWarehouse = warehouses.find((w) => w.type === "INGREDIENTS" && w.active)?.id ?? warehouses[0]?.id ?? "";

  const [header, setHeader] = useState<Header>({ number: "", issueDate: today(), docType: "FACTURA", currency: "RON" });
  const [supplier, setSupplier] = useState<SupplierRequest>({ name: "" });
  const [lines, setLines] = useState<Line[]>([]);
  const [warnings, setWarnings] = useState<InvoiceDraftWarning[]>([]);
  const [printed, setPrinted] = useState<InvoiceDraft["printed"]>();
  const [preview, setPreview] = useState<{ url: string; pdf: boolean }>();
  const [scanning, setScanning] = useState(false);
  const [parityFor, setParityFor] = useState<Line>();
  const [ruleFor, setRuleFor] = useState<Line>();

  useEffect(() => {
    if (lines.length === 0 && defaultWarehouse) setLines([emptyLine(defaultWarehouse)]);
  }, [defaultWarehouse, lines.length]);
  useEffect(() => () => preview && URL.revokeObjectURL(preview.url), [preview]);

  const itemById = useMemo(() => new Map(items.map((i) => [i.id, i])), [items]);
  const itemByName = useMemo(() => new Map(items.flatMap((i) => [[fold(i.name.ro), i] as const, ...(i.name.en ? [[fold(i.name.en), i] as const] : [])])), [items]);
  const set = (patch: Partial<Header>) => setHeader((h) => ({ ...h, ...patch }));
  const setLine = (key: string, patch: Partial<Line>) => setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...patch } : l)));

  /** Typing a name picks the existing item with that name; an existing item brings its primary unit when none is set. */
  function rename(line: Line, description: string) {
    const item = itemByName.get(fold(description));
    const patch: Partial<Line> = { description, stockItemId: item?.id, packagingId: undefined, newPackaging: undefined };
    if (item) {
      const primary = units.find((u) => u.id === (item.displayUnitId ?? item.baseUnitId));
      if (!line.unitCode && primary) patch.unitCode = primary.code;
      if (item.defaultWarehouseId) patch.warehouseId = item.defaultWarehouseId;
    }
    setLine(line.key, patch);
  }

  async function importFile(file: File) {
    setScanning(true);
    try {
      const body = new FormData();
      body.append("file", file);
      const res = await fetch(`${client.getConfig().baseUrl}/admin/invoices/scan`, {
        method: "POST",
        body,
        headers: { Accept: "application/json, application/problem+json", "Accept-Language": locale, Authorization: `Bearer ${await accessToken()}` },
      });
      const json = await res.json();
      if (!res.ok) throw new Error(json.detail ?? res.statusText);
      const draft = json as InvoiceDraft;
      const { lines: drafted, supplier: s, ...rest } = draft.invoice;
      setHeader({ ...rest, issueDate: rest.issueDate });
      setSupplier(s ?? { name: "" });
      setLines(
        drafted.map((l, i) => ({
          key: newKey(),
          originalDescription: l.originalDescription,
          description: l.description,
          stockItemId: l.stockItemId,
          ruleId: draft.lineInfo?.[i]?.ruleId,
          supplierCode: l.supplierCode,
          lot: l.lot,
          quantity: l.quantity,
          unitCode: l.unitCode ?? draft.lineInfo?.[i]?.printedUnit ?? "",
          packagingId: l.packagingId,
          unitPrice: l.unitPrice,
          vatPercent: l.vatPercent,
          warehouseId: l.warehouseId ?? defaultWarehouse,
        })),
      );
      setWarnings(draft.warnings);
      setPrinted(draft.printed);
      setPreview({ url: URL.createObjectURL(file), pdf: file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf") });
      toast.success(t("invoiceNew.scanned", { count: drafted.length }));
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setScanning(false);
      if (fileInput.current) fileInput.current.value = "";
    }
  }

  // ---- totals and validation, one row per line
  const rows = lines.map((l) => {
    const q = toNum(l.quantity);
    const p = toNum(l.unitPrice);
    return { line: l, total: Number.isFinite(q) && Number.isFinite(p) ? round2(q * p) : NaN, status: unitStatus(l, l.stockItemId ? itemById.get(l.stockItemId) : undefined, units) };
  });
  const byRate = new Map<number, { net: number; vat: number }>();
  for (const { line, total } of rows) {
    const rate = toNum(line.vatPercent);
    if (!Number.isFinite(rate) || !Number.isFinite(total)) continue;
    const r = byRate.get(rate) ?? { net: 0, vat: 0 };
    byRate.set(rate, { net: round2(r.net + total), vat: round2(r.vat + round2((total * rate) / 100)) });
  }
  const totalNet = round2([...byRate.values()].reduce((a, r) => a + r.net, 0));
  const totalVat = round2([...byRate.values()].reduce((a, r) => a + r.vat, 0));
  const totalGross = round2(totalNet + totalVat);
  const printedGross = printed?.totalGross ? Number(printed.totalGross) : undefined;
  const grossDiff = printedGross !== undefined ? round2(totalGross - printedGross) : 0;
  const money = (n: number) => formatMoney({ amount: n.toFixed(2), currency: header.currency ?? "RON" }, locale);

  const problems: string[] = [];
  if (!header.supplierId && !supplier.name.trim()) problems.push(t("invoiceNew.needSupplier"));
  if (!header.number.trim()) problems.push(t("invoiceNew.needNumber"));
  rows.forEach(({ line: l, status }, i) => {
    const n = i + 1;
    if (!l.description.trim()) problems.push(t("invoiceNew.lineNeedsName", { n }));
    if (!(toNum(l.quantity) > 0) || !(toNum(l.unitPrice) >= 0) || !(toNum(l.vatPercent) >= 0)) problems.push(t("invoiceNew.lineNeedsNumbers", { n }));
    if (!l.warehouseId) problems.push(t("invoiceNew.lineNeedsWarehouse", { n }));
    if (!status.ok) problems.push(status.kind === "needsParity" ? t("invoiceNew.lineNeedsParity", { n, unit: l.unitCode }) : t("invoiceNew.lineNeedsUnit", { n }));
  });

  const save = useApiMutation(
    () => {
      const body: PurchaseInvoiceRequest = {
        ...header,
        supplier: header.supplierId ? undefined : { ...supplier, name: supplier.name.trim() },
        createNirs: true,
        lines: rows.map(({ line: l, status }): PurchaseInvoiceLineRequest => {
          const unit = status.unit;
          return {
            description: l.description.trim(),
            originalDescription: l.originalDescription,
            supplierCode: l.supplierCode,
            lot: l.lot,
            quantity: clean(l.quantity),
            unitPrice: clean(l.unitPrice),
            vatPercent: clean(l.vatPercent),
            unitCode: l.unitCode.trim() || undefined,
            unitId: unit?.status === "ACTIVE" ? unit.id : undefined,
            packagingId: status.kind === "packaging" ? status.packagingId : undefined,
            newPackaging: l.newPackaging,
            stockItemId: l.stockItemId,
            newStockItem: l.stockItemId ? undefined : { name: l.description.trim(), unitId: unit!.id },
            warehouseId: l.warehouseId,
          };
        }),
      };
      return createPurchaseInvoice({ body });
    },
    {
      invalidate: [["invoices"], ["nirs"], ["stock-items"], ["suppliers"]],
      success: t("invoiceNew.saved"),
      onSuccess: (inv) => navigate({ to: "/invoices/$id", params: { id: inv.id } }),
    },
  );

  const text = (key: keyof Header, label: string, props: React.ComponentProps<typeof Input> = {}) => (
    <Field label={label}>
      <Input value={(header[key] as string | undefined) ?? ""} onChange={(e) => set({ [key]: e.target.value || undefined } as Partial<Header>)} {...props} />
    </Field>
  );
  const supplierText = (key: keyof SupplierRequest, label: string) => (
    <Field label={label}>
      <Input value={(supplier[key] as string | undefined) ?? ""} onChange={(e) => setSupplier({ ...supplier, [key]: e.target.value })} />
    </Field>
  );

  return (
    <>
      <PageHeader
        title={t("invoiceNew.title")}
        description={scanStatus?.enabled ? `${t("invoiceNew.description")} ${t("invoiceNew.photoTip")}` : t("invoiceNew.description")}
        actions={
          scanStatus?.enabled && (
            <>
              <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp,application/pdf" capture="environment" hidden onChange={(e) => e.target.files?.[0] && importFile(e.target.files[0])} />
              <Button onClick={() => fileInput.current?.click()} disabled={scanning}>
                {scanning ? <Loader2Icon className="animate-spin" /> : <FileUpIcon />} {scanning ? t("invoiceNew.reading") : t("invoiceNew.import")}
              </Button>
            </>
          )
        }
      />

      {warnings.length > 0 && (
        <Card className="mb-4 border-accent">
          <CardContent className="space-y-1 py-3 text-sm">
            {warnings.map((w, i) => (
              <p key={i} className="flex items-start gap-2">
                <AlertTriangleIcon className="mt-0.5 size-4 shrink-0 text-accent-foreground" /> {w.message}
              </p>
            ))}
          </CardContent>
        </Card>
      )}

      <div className={preview ? "grid gap-6 xl:grid-cols-[1fr_380px]" : ""}>
        <div className="min-w-0 space-y-6">
          {/* ---- supplier & document */}
          <section className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardContent className="space-y-3 py-4">
                <h2 className="font-bold">{t("procurement.supplier")}</h2>
                <Field label={t("invoiceNew.existingSupplier")}>
                  <NativeSelect value={header.supplierId ?? ""} onChange={(e) => set({ supplierId: e.target.value || undefined })}>
                    <option value="">{t("invoiceNew.supplierFromFields")}</option>
                    {suppliers.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name} {s.cui ? `(${s.cui})` : ""}
                      </option>
                    ))}
                  </NativeSelect>
                </Field>
                {!header.supplierId && (
                  <>
                    {supplierText("name", t("common.name"))}
                    <div className="grid gap-3 sm:grid-cols-2">
                      {supplierText("cui", t("procurement.cui"))}
                      {supplierText("regCom", t("procurement.regCom"))}
                    </div>
                    {supplierText("address", t("procurement.address"))}
                  </>
                )}
                {text("supplierBranch", t("invoiceNew.branch"))}
              </CardContent>
            </Card>
            <Card>
              <CardContent className="space-y-3 py-4">
                <h2 className="font-bold">{t("invoiceNew.document")}</h2>
                <div className="grid gap-3 sm:grid-cols-3">
                  <Field label={t("common.type")}>
                    <NativeSelect value={header.docType ?? "FACTURA"} onChange={(e) => set({ docType: e.target.value as InvoiceDocType })}>
                      {DOC_TYPES.map((d) => (
                        <option key={d} value={d}>
                          {t(`invoiceNew.docTypes.${d}`)}
                        </option>
                      ))}
                    </NativeSelect>
                  </Field>
                  {text("series", t("procurement.series"))}
                  {text("number", t("procurement.number"), { required: true })}
                </div>
                <div className="grid gap-3 sm:grid-cols-3">
                  <Field label={t("procurement.issueDate")}>
                    <Input type="date" value={header.issueDate} onChange={(e) => set({ issueDate: e.target.value })} />
                  </Field>
                  <Field label={t("invoiceNew.time")}>
                    <Input type="time" value={header.issueTime ?? ""} onChange={(e) => set({ issueTime: e.target.value || undefined })} />
                  </Field>
                  <Field label={t("procurement.dueDate")}>
                    <Input type="date" value={header.dueDate ?? ""} onChange={(e) => set({ dueDate: e.target.value || undefined })} />
                  </Field>
                </div>
                <div className="grid gap-3 sm:grid-cols-4">
                  {text("clientNo", t("invoiceNew.clientNo"))}
                  {text("cardNo", t("invoiceNew.cardNo"))}
                  {text("registerNo", t("invoiceNew.registerNo"))}
                  {text("cashierNo", t("invoiceNew.cashierNo"))}
                </div>
              </CardContent>
            </Card>
          </section>
          <Card>
            <CardContent className="grid gap-3 py-4 sm:grid-cols-2 lg:grid-cols-5">
              {text("buyerName", t("invoiceNew.buyer"))}
              {text("buyerCui", t("invoiceNew.buyerCui"))}
              {text("buyerPerson", t("invoiceNew.buyerPerson"))}
              <Field label={t("invoiceNew.payment")}>
                <NativeSelect value={header.paymentMethod ?? ""} onChange={(e) => set({ paymentMethod: (e.target.value || undefined) as InvoicePaymentMethod | undefined })}>
                  <option value="">{t("common.none")}</option>
                  {PAYMENTS.map((p) => (
                    <option key={p} value={p}>
                      {t(`invoiceNew.payments.${p}`)}
                    </option>
                  ))}
                </NativeSelect>
              </Field>
              {text("paidAmount", t("invoiceNew.paid"), { inputMode: "decimal" })}
            </CardContent>
          </Card>

          {/* ---- lines */}
          <section>
            <datalist id="stock-item-names">
              {items.map((i) => (
                <option key={i.id} value={lt(i.name, "ro")} />
              ))}
            </datalist>
            <datalist id="unit-codes">
              {units
                .filter((u) => u.status === "ACTIVE")
                .map((u) => (
                  <option key={u.id} value={u.code} />
                ))}
            </datalist>
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>#</TableHead>
                    <TableHead className="min-w-64">{t("invoiceNew.product")}</TableHead>
                    <TableHead className="w-24 text-right">{t("common.quantity")}</TableHead>
                    <TableHead className="w-40">{t("common.unit")}</TableHead>
                    <TableHead className="w-28 text-right">{t("procurement.unitPrice")}</TableHead>
                    <TableHead className="w-20 text-right">{t("procurement.vat")} %</TableHead>
                    <TableHead className="w-28 text-right">{t("common.total")}</TableHead>
                    <TableHead className="w-44">{t("common.warehouse")}</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map(({ line: l, status, total }, i) => {
                    const item = l.stockItemId ? itemById.get(l.stockItemId) : undefined;
                    const renamed = l.originalDescription && fold(l.originalDescription) !== fold(l.description);
                    return (
                      <TableRow key={l.key} className="align-top">
                        <TableCell className="pt-4">{i + 1}</TableCell>
                        <TableCell className="space-y-1">
                          {l.originalDescription && <p className="font-mono text-xs text-muted-foreground">{l.originalDescription}</p>}
                          <Input list="stock-item-names" value={l.description} onChange={(e) => rename(l, e.target.value)} aria-label={t("invoiceNew.product")} />
                          <div className="flex flex-wrap items-center gap-1">
                            {item ? <Badge variant="outline">{item.sku}</Badge> : l.description.trim() && <Badge variant="accent">{t("invoiceNew.newProduct")}</Badge>}
                            {l.ruleId && <Badge variant="muted">{t("invoiceNew.byRule")}</Badge>}
                            {renamed && !l.ruleId && (
                              <Button variant="ghost" size="sm" className="h-6 px-2 text-xs" onClick={() => setRuleFor(l)}>
                                <WandSparklesIcon /> {t("invoiceNew.saveAsRule")}
                              </Button>
                            )}
                          </div>
                        </TableCell>
                        <TableCell>
                          <Input className="text-right" inputMode="decimal" value={l.quantity} onChange={(e) => setLine(l.key, { quantity: e.target.value })} aria-label={t("common.quantity")} />
                        </TableCell>
                        <TableCell className="space-y-1">
                          <Input list="unit-codes" value={l.unitCode} onChange={(e) => setLine(l.key, { unitCode: e.target.value, packagingId: undefined, newPackaging: undefined })} aria-label={t("common.unit")} />
                          {status.ok && "text" in status && status.text && <p className="text-xs text-muted-foreground">{status.text}</p>}
                          {status.ok && status.kind === "primary" && <p className="text-xs text-muted-foreground">{t("invoiceNew.primaryUnit")}</p>}
                          {!status.ok && status.kind === "needsParity" && (
                            <Button size="sm" variant="outline" className="h-7 w-full text-xs" onClick={() => setParityFor(l)}>
                              {t("invoiceNew.defineParity")}
                            </Button>
                          )}
                          {!status.ok && status.kind === "newNeedsKnownUnit" && l.unitCode && <p className="text-xs text-destructive">{t("invoiceNew.unknownUnit")}</p>}
                        </TableCell>
                        <TableCell>
                          <Input className="text-right" inputMode="decimal" value={l.unitPrice} onChange={(e) => setLine(l.key, { unitPrice: e.target.value })} aria-label={t("procurement.unitPrice")} />
                        </TableCell>
                        <TableCell>
                          <Input className="text-right" inputMode="decimal" value={l.vatPercent} onChange={(e) => setLine(l.key, { vatPercent: e.target.value })} aria-label={t("procurement.vat")} />
                        </TableCell>
                        <TableCell className="pt-4 text-right tabular-nums">{Number.isFinite(total) ? money(total) : "—"}</TableCell>
                        <TableCell>
                          <NativeSelect value={l.warehouseId} onChange={(e) => setLine(l.key, { warehouseId: e.target.value })} aria-label={t("common.warehouse")}>
                            <option value="">{t("common.choose")}</option>
                            {warehouses
                              .filter((w) => w.active)
                              .map((w) => (
                                <option key={w.id} value={w.id}>
                                  {lt(w.name, locale)}
                                </option>
                              ))}
                          </NativeSelect>
                        </TableCell>
                        <TableCell>
                          <Button variant="ghost" size="icon" onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))} aria-label={t("common.remove")} disabled={lines.length === 1}>
                            <Trash2Icon />
                          </Button>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
            <Button variant="outline" className="mt-2" onClick={() => setLines((ls) => [...ls, emptyLine(defaultWarehouse)])}>
              <PlusIcon /> {t("invoiceNew.addLine")}
            </Button>
          </section>

          {/* ---- totals & save */}
          <section className="flex flex-wrap items-start justify-between gap-6">
            <div className="max-w-md text-sm text-muted-foreground">
              {problems.length > 0 && (
                <ul className="list-disc space-y-0.5 pl-5">
                  {problems.slice(0, 6).map((p) => (
                    <li key={p}>{p}</li>
                  ))}
                  {problems.length > 6 && <li>…</li>}
                </ul>
              )}
            </div>
            <div className="ml-auto w-80 space-y-1 text-sm">
              {[...byRate.entries()]
                .sort(([a], [b]) => a - b)
                .map(([rate, r]) => (
                  <p key={rate} className="flex justify-between text-muted-foreground">
                    <span>
                      {t("procurement.vat")} {rate}% ({money(r.net)})
                    </span>
                    <span className="tabular-nums">{money(r.vat)}</span>
                  </p>
                ))}
              <p className="flex justify-between">
                <span>{t("procurement.net")}</span>
                <span className="tabular-nums">{money(totalNet)}</span>
              </p>
              <p className="flex justify-between">
                <span>{t("procurement.vat")}</span>
                <span className="tabular-nums">{money(totalVat)}</span>
              </p>
              <p className="flex justify-between text-base font-bold">
                <span>{t("invoiceNew.total")}</span>
                <span className="tabular-nums">{money(totalGross)}</span>
              </p>
              {printedGross !== undefined && (
                <p className="flex items-center justify-between gap-2 text-xs">
                  <span className="text-muted-foreground">{t("invoiceNew.printedTotal", { amount: money(printedGross) })}</span>
                  {Math.abs(grossDiff) > 0.02 ? <Badge className="bg-destructive text-white">{t("invoiceNew.difference", { amount: money(grossDiff) })}</Badge> : <Badge variant="success">{t("invoiceNew.matches")}</Badge>}
                </p>
              )}
              <Button className="mt-3 w-full" size="lg" disabled={problems.length > 0 || save.isPending} onClick={() => save.mutate(undefined)}>
                {save.isPending && <Loader2Icon className="animate-spin" />} {t("invoiceNew.save")}
              </Button>
              <p className="text-center text-xs text-muted-foreground">{t("invoiceNew.saveHint")}</p>
            </div>
          </section>
        </div>

        {preview && (
          <aside className="xl:sticky xl:top-4 xl:self-start">
            <Card>
              <CardContent className="p-2">{preview.pdf ? <iframe src={preview.url} title={t("invoiceNew.original")} className="h-[80vh] w-full rounded" /> : <img src={preview.url} alt={t("invoiceNew.original")} className="w-full rounded" />}</CardContent>
            </Card>
          </aside>
        )}
      </div>

      {parityFor && (
        <ParityDialog
          line={parityFor}
          item={itemById.get(parityFor.stockItemId!)!}
          units={units}
          onSave={(newPackaging) => {
            setLine(parityFor.key, { newPackaging, packagingId: undefined });
            setParityFor(undefined);
          }}
          onClose={() => setParityFor(undefined)}
        />
      )}
      {ruleFor && (
        <RuleDialog
          line={ruleFor}
          onSaved={(ruleId) => {
            setLine(ruleFor.key, { ruleId });
            setRuleFor(undefined);
          }}
          onClose={() => setRuleFor(undefined)}
        />
      )}
    </>
  );
}

/** "1 BU = [430] [g]": saved with the invoice as a packaging (secondary unit) of the item. */
function ParityDialog({ line, item, units, onSave, onClose }: { line: Line; item: StockItem; units: MeasureUnit[]; onSave: (p: { name: string; qtyInBase: string }) => void; onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const base = units.find((u) => u.id === item.baseUnitId);
  const choices = units.filter((u) => u.status === "ACTIVE" && u.dimension === base?.dimension);
  const [amount, setAmount] = useState("");
  const [unitId, setUnitId] = useState(item.displayUnitId ?? item.baseUnitId);
  const unit = choices.find((u) => u.id === unitId);
  const qtyInBase = toNum(amount) * Number(unit?.factor ?? 1);
  const name = line.unitCode.trim();
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{t("invoiceNew.parityTitle")}</DialogTitle>
        <DialogDescription>{t("invoiceNew.parityHint", { unit: name, item: lt(item.name, locale) })}</DialogDescription>
        <div className="flex items-end gap-2">
          <span className="pb-2 font-semibold whitespace-nowrap">1 {name} =</span>
          <Field label={t("common.quantity")} className="flex-1">
            <Input autoFocus inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </Field>
          <Field label={t("common.unit")} className="w-28">
            <NativeSelect value={unitId} onChange={(e) => setUnitId(e.target.value)}>
              {choices.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.code}
                </option>
              ))}
            </NativeSelect>
          </Field>
        </div>
        {qtyInBase > 0 && unit && unit.id !== base?.id && (
          <p className="text-sm text-muted-foreground">
            = {Number(qtyInBase.toFixed(6))} {base?.code}
          </p>
        )}
        <Button disabled={!(qtyInBase > 0) || !name} onClick={() => onSave({ name, qtyInBase: String(Number(qtyInBase.toFixed(6))) })}>
          {t("common.save")}
        </Button>
      </DialogContent>
    </Dialog>
  );
}

/** Turns a rename into a keyword rule so the next document is renamed automatically. */
function RuleDialog({ line, onSaved, onClose }: { line: Line; onSaved: (ruleId: string) => void; onClose: () => void }) {
  const t = useT();
  const [matchType, setMatchType] = useState<ProductNameMatchType>("CONTAINS");
  const [pattern, setPattern] = useState(() => suggestPattern(line.originalDescription ?? "", line.description));
  const save = useApiMutation(() => createProductNameRule({ body: { matchType, pattern: pattern.trim(), targetName: line.description.trim(), stockItemId: line.stockItemId } }), {
    invalidate: [["name-rules"]],
    success: t("common.saved"),
    onSuccess: (r) => onSaved(r.id),
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{t("invoiceNew.ruleTitle")}</DialogTitle>
        <DialogDescription>{t("invoiceNew.ruleHint", { name: line.description })}</DialogDescription>
        <p className="rounded-lg bg-muted p-2 font-mono text-xs">{line.originalDescription}</p>
        <div className="grid gap-3 sm:grid-cols-[10rem_1fr]">
          <Field label={t("nameRules.matchType")}>
            <NativeSelect value={matchType} onChange={(e) => setMatchType(e.target.value as ProductNameMatchType)}>
              {(["CONTAINS", "STARTS_WITH", "REGEX"] as const).map((m) => (
                <option key={m} value={m}>
                  {t(`nameRules.types.${m}`)}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label={t("nameRules.pattern")} hint={t("nameRules.patternHint")}>
            <Input value={pattern} onChange={(e) => setPattern(e.target.value)} />
          </Field>
        </div>
        <p className="text-sm">
          → <b>{line.description}</b>
        </p>
        <Button disabled={!pattern.trim() || save.isPending} onClick={() => save.mutate(undefined)}>
          {t("invoiceNew.saveRule")}
        </Button>
      </DialogContent>
    </Dialog>
  );
}
