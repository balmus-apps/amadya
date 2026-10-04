import {
  createNir,
  getNir,
  getPublicSettings,
  getRestaurantSettings,
  listMeasureUnits,
  listNirs,
  listStockItems,
  listSuppliers,
  listWarehouses,
  postNir,
  reverseNir,
  updateNir,
  type Nir,
  type NirLineRequest,
  type NirRequest,
} from "@amadya/api-client";
import { Badge, Button, Card, CardContent, Input, NativeSelect, Table, TableBody, TableCell, TableHead, TableHeader, TableRow, Textarea, toast } from "@amadya/ui";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { PlusIcon, PrinterIcon, Trash2Icon } from "lucide-react";
import { useEffect, useState } from "react";
import { Empty, Field, PageHeader, splitList } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { formatDate, formatMoney, lt, today } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

const statusTone = { DRAFT: "outline", POSTED: "success", REVERSED: "muted" } as const;

export function NirsPage() {
  const t = useT();
  const { locale } = useLocale();
  const navigate = useNavigate();
  const { data = [], isLoading } = useApi(["nirs"], () => listNirs({ query: { limit: 200 } }));
  const { data: warehouses = [] } = useApi(["warehouses"], () => listWarehouses());
  const create = useApiMutation(() => createNir({ body: { warehouseId: warehouses[0]!.id, date: today(), lines: [] } }), {
    invalidate: [["nirs"]],
    onSuccess: (n) => navigate({ to: "/nirs/$id", params: { id: n.id } }),
  });
  return (
    <>
      <PageHeader
        title={t("procurement.nirsTitle")}
        actions={
          <Button disabled={!warehouses.length || create.isPending} onClick={() => create.mutate(undefined)}>
            <PlusIcon /> {t("procurement.newNir")}
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
              <TableHead>{t("procurement.supplier")}</TableHead>
              <TableHead>{t("common.warehouse")}</TableHead>
              <TableHead>{t("common.status")}</TableHead>
              <TableHead className="text-right">{t("procurement.valueNet")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((n) => (
              <TableRow key={n.id} className="cursor-pointer" onClick={() => navigate({ to: "/nirs/$id", params: { id: n.id } })}>
                <TableCell className="font-mono text-xs">{n.number ?? t("procurement.draftNumber")}</TableCell>
                <TableCell>{formatDate(n.date, locale)}</TableCell>
                <TableCell>{n.supplierName ?? "—"}</TableCell>
                <TableCell>{lt(warehouses.find((w) => w.id === n.warehouseId)?.name, locale)}</TableCell>
                <TableCell>
                  <Badge variant={statusTone[n.status]}>{t(`procurement.nirStatuses.${n.status}`)}</Badge>
                </TableCell>
                <TableCell className="text-right tabular-nums">{formatMoney({ amount: n.totalNet, currency: n.currency }, locale)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </>
  );
}

function toRequest(n: Nir): NirRequest {
  return {
    warehouseId: n.warehouseId,
    supplierId: n.supplierId,
    date: n.date,
    invoiceRef: n.invoiceRef,
    deliveryNoteRef: n.deliveryNoteRef,
    committee: n.committee,
    notes: n.notes,
    lines: n.lines.map((l) => ({
      stockItemId: l.stockItemId,
      unitId: l.unitId,
      packagingId: l.packagingId,
      quantityDocument: l.quantityDocument,
      quantityReceived: l.quantityReceived,
      unitPrice: l.unitPrice,
      vatPercent: l.vatPercent,
      discrepancyReason: l.discrepancyReason,
    })),
  };
}

export function NirDetailPage() {
  const t = useT();
  const { locale } = useLocale();
  const navigate = useNavigate();
  const { id } = useParams({ strict: false }) as { id: string };
  const { data: nir } = useApi(["nir", id], () => getNir({ path: { id } }));
  const { data: warehouses = [] } = useApi(["warehouses"], () => listWarehouses());
  const { data: suppliers = [] } = useApi(["suppliers"], () => listSuppliers());
  const { data: items = [] } = useApi(["stock-items"], () => listStockItems());
  const { data: units = [] } = useApi(["units"], () => listMeasureUnits());
  const [form, setForm] = useState<NirRequest | null>(null);
  useEffect(() => {
    if (nir) setForm(toRequest(nir));
  }, [nir]);

  const save = useApiMutation((req: NirRequest) => updateNir({ path: { id }, body: req }), { invalidate: [["nir", id], ["nirs"]], success: t("common.saved") });
  const post = useApiMutation(() => postNir({ path: { id } }), {
    invalidate: [["nir", id], ["nirs"], ["balances"], ["stock-items"]],
    onSuccess: (n) => toast.success(t("procurement.postedOk", { number: n.number ?? "" })),
  });
  const reverse = useApiMutation(() => reverseNir({ path: { id } }), {
    invalidate: [["nir", id], ["nirs"], ["balances"]],
    onSuccess: (n) => navigate({ to: "/nirs/$id", params: { id: n.id } }),
  });
  if (!nir || !form) return null;
  const draft = nir.status === "DRAFT";
  const lines = form.lines ?? [];
  const setLine = (i: number, patch: Partial<NirLineRequest>) => setForm({ ...form, lines: lines.map((l, j) => (j === i ? { ...l, ...patch } : l)) });
  const choices = (itemId: string) => {
    const item = items.find((x) => x.id === itemId);
    const base = units.find((u) => u.id === item?.baseUnitId);
    return [
      ...units.filter((u) => u.status === "ACTIVE" && u.dimension === base?.dimension).map((u) => ({ key: `u:${u.id}`, label: u.code })),
      ...(item?.packagings ?? []).map((p) => ({ key: `p:${p.id}`, label: p.name })),
    ];
  };
  const money = (amount: string) => formatMoney({ amount, currency: nir.currency }, locale);

  return (
    <>
      <PageHeader
        title={nir.number ?? `${t("procurement.nir")} ${t("procurement.draftNumber")}`}
        description={nir.reversalOfId ? t("procurement.reversalOf", { number: "" }) : undefined}
        actions={
          <>
            <Badge variant={statusTone[nir.status]}>{t(`procurement.nirStatuses.${nir.status}`)}</Badge>
            {nir.invoiceId && (
              <Button variant="ghost" asChild>
                <Link to="/invoices/$id" params={{ id: nir.invoiceId }}>
                  {t("procurement.invoice")}
                </Link>
              </Button>
            )}
            {!draft && (
              <Button variant="outline" asChild>
                <Link to="/nirs/$id/print" params={{ id }}>
                  <PrinterIcon /> {t("common.print")}
                </Link>
              </Button>
            )}
            {nir.status === "POSTED" && !nir.reversalOfId && (
              <Button variant="outline" disabled={reverse.isPending} onClick={() => confirm(t("procurement.reverseConfirm")) && reverse.mutate(undefined)}>
                {t("procurement.reverse")}
              </Button>
            )}
            {draft && (
              <>
                <Button variant="outline" disabled={save.isPending} onClick={() => save.mutate(form)}>
                  {t("common.save")}
                </Button>
                <Button disabled={post.isPending || lines.length === 0} onClick={async () => confirm(t("procurement.postConfirm")) && (await save.mutateAsync(form), post.mutate(undefined))}>
                  {t("procurement.post")}
                </Button>
              </>
            )}
          </>
        }
      />
      <Card className="mb-4">
        <CardContent className="grid gap-3 p-5 sm:grid-cols-3">
          <Field label={t("common.date")}>
            <Input type="date" disabled={!draft} value={form.date ?? ""} onChange={(e) => setForm({ ...form, date: e.target.value })} />
          </Field>
          <Field label={t("common.warehouse")}>
            <NativeSelect disabled={!draft} value={form.warehouseId} onChange={(e) => setForm({ ...form, warehouseId: e.target.value })}>
              {warehouses.map((w) => (
                <option key={w.id} value={w.id}>
                  {lt(w.name, locale)}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label={t("procurement.supplier")}>
            <NativeSelect disabled={!draft} value={form.supplierId ?? ""} onChange={(e) => setForm({ ...form, supplierId: e.target.value || undefined })}>
              <option value="">{t("common.none")}</option>
              {suppliers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label={t("procurement.invoiceRef")}>
            <Input disabled={!draft} value={form.invoiceRef ?? ""} onChange={(e) => setForm({ ...form, invoiceRef: e.target.value })} />
          </Field>
          <Field label={t("procurement.deliveryNote")}>
            <Input disabled={!draft} value={form.deliveryNoteRef ?? ""} onChange={(e) => setForm({ ...form, deliveryNoteRef: e.target.value })} />
          </Field>
          <Field label={t("procurement.committee")} hint={t("procurement.committeeHint")}>
            <Input disabled={!draft} value={(form.committee ?? []).join(", ")} onChange={(e) => setForm({ ...form, committee: splitList(e.target.value).slice(0, 3) })} />
          </Field>
        </CardContent>
      </Card>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("common.item")}</TableHead>
            <TableHead className="w-24">{t("common.unit")}</TableHead>
            <TableHead className="w-24 text-right">{t("procurement.qtyDocument")}</TableHead>
            <TableHead className="w-24 text-right">{t("procurement.qtyReceived")}</TableHead>
            <TableHead className="w-28 text-right">{t("procurement.unitPrice")}</TableHead>
            <TableHead className="w-20 text-right">TVA %</TableHead>
            <TableHead className="text-right">{t("procurement.valueNet")}</TableHead>
            {draft && <TableHead />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {lines.map((l, i) => {
            const diff = Number(l.quantityReceived) - Number(l.quantityDocument);
            const value = (Number(l.quantityReceived) * Number(l.unitPrice)).toFixed(2);
            const choice = l.packagingId ? `p:${l.packagingId}` : l.unitId ? `u:${l.unitId}` : "";
            return (
              <TableRow key={i} className="align-top">
                <TableCell>
                  {draft ? (
                    <NativeSelect value={l.stockItemId} onChange={(e) => setLine(i, { stockItemId: e.target.value, unitId: undefined, packagingId: undefined })}>
                      <option value="">{t("common.choose")}</option>
                      {items.map((it) => (
                        <option key={it.id} value={it.id}>
                          {lt(it.name, locale)}
                        </option>
                      ))}
                    </NativeSelect>
                  ) : (
                    nir.lines[i]?.itemName
                  )}
                  {diff !== 0 &&
                    (draft ? (
                      <Input className="mt-1" placeholder={t("procurement.discrepancyReason")} value={l.discrepancyReason ?? ""} onChange={(e) => setLine(i, { discrepancyReason: e.target.value })} />
                    ) : (
                      <span className="block text-xs text-accent">{l.discrepancyReason}</span>
                    ))}
                </TableCell>
                <TableCell>
                  {draft ? (
                    <NativeSelect
                      value={choice}
                      onChange={(e) => {
                        const [k, v] = e.target.value.split(":");
                        setLine(i, { unitId: k === "u" ? v : undefined, packagingId: k === "p" ? v : undefined });
                      }}
                    >
                      <option value="">{items.find((x) => x.id === l.stockItemId)?.baseUnitCode ?? "—"}</option>
                      {choices(l.stockItemId).map((o) => (
                        <option key={o.key} value={o.key}>
                          {o.label}
                        </option>
                      ))}
                    </NativeSelect>
                  ) : (
                    nir.lines[i]?.unitCode
                  )}
                </TableCell>
                {(["quantityDocument", "quantityReceived", "unitPrice", "vatPercent"] as const).map((k) => (
                  <TableCell key={k} className="text-right">
                    {draft ? (
                      <Input className="text-right" inputMode="decimal" value={l[k] ?? ""} onChange={(e) => setLine(i, { [k]: e.target.value.replace(",", ".") })} />
                    ) : (
                      <span className="tabular-nums">{Number(l[k] ?? 0)}</span>
                    )}
                    {k === "quantityReceived" && diff !== 0 && <Badge variant="accent">{diff > 0 ? `+${diff}` : diff}</Badge>}
                  </TableCell>
                ))}
                <TableCell className="text-right tabular-nums">{money(value)}</TableCell>
                {draft && (
                  <TableCell>
                    <Button variant="ghost" size="icon-sm" onClick={() => setForm({ ...form, lines: lines.filter((_, j) => j !== i) })} aria-label={t("common.remove")}>
                      <Trash2Icon />
                    </Button>
                  </TableCell>
                )}
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
      {draft && (
        <Button
          variant="outline"
          size="sm"
          className="mt-3"
          onClick={() => setForm({ ...form, lines: [...lines, { stockItemId: "", quantityDocument: "", quantityReceived: "", unitPrice: "", vatPercent: "11" }] })}
        >
          <PlusIcon /> {t("stock.addLine")}
        </Button>
      )}
      <div className="mt-4 ml-auto w-72 space-y-1 text-sm">
        <p className="flex justify-between">
          <span>{t("procurement.valueNet")}</span>
          <span className="tabular-nums">{money(nir.totalNet)}</span>
        </p>
        <p className="flex justify-between">
          <span>{t("procurement.vatValue")}</span>
          <span className="tabular-nums">{money(nir.totalVat)}</span>
        </p>
      </div>
      {draft && (
        <Field label={t("common.notes")} className="mt-4">
          <Textarea rows={2} value={form.notes ?? ""} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
        </Field>
      )}
    </>
  );
}

/** Legal-layout print view (A4 landscape); save as PDF from the browser. */
export function NirPrintPage() {
  const t = useT();
  const { locale } = useLocale();
  const { id } = useParams({ strict: false }) as { id: string };
  const { data: nir } = useApi(["nir", id], () => getNir({ path: { id } }));
  const { data: settings } = useApi(["restaurant-settings"], () => getRestaurantSettings());
  const { data: pub } = useApi(["public-settings"], () => getPublicSettings());
  const { data: warehouses = [] } = useApi(["warehouses"], () => listWarehouses());
  if (!nir || (!settings && !pub)) return null;
  const money = (amount: string) => formatMoney({ amount, currency: nir.currency }, locale);
  const company = settings ?? pub;
  return (
    <div className="mx-auto max-w-5xl bg-white p-8 text-black print:p-0">
      <style>{"@page { size: A4 landscape; margin: 12mm; }"}</style>
      <div className="no-print mb-4 flex justify-end gap-2">
        <Button variant="outline" asChild>
          <Link to="/nirs/$id" params={{ id }}>
            {t("common.back")}
          </Link>
        </Button>
        <Button onClick={() => window.print()}>
          <PrinterIcon /> {t("common.print")}
        </Button>
      </div>
      <div className="flex justify-between text-sm">
        <div>
          <p>
            {t("procurement.unitName")}: <b>{settings?.legalName ?? company?.name}</b>
          </p>
          {settings?.cui && <p>CUI: {settings.cui}</p>}
          {settings?.regCom && <p>Reg. Com.: {settings.regCom}</p>}
          <p>{company?.address}</p>
        </div>
        <div className="text-right">
          <p>
            {t("common.warehouse")}: <b>{lt(warehouses.find((w) => w.id === nir.warehouseId)?.name, locale)}</b>
          </p>
          <p>
            {t("procurement.supplier")}: <b>{nir.supplierName ?? "—"}</b>
          </p>
          {nir.invoiceRef && <p>{t("procurement.invoiceRef")}: {nir.invoiceRef}</p>}
          {nir.deliveryNoteRef && <p>{t("procurement.deliveryNote")}: {nir.deliveryNoteRef}</p>}
        </div>
      </div>
      <h1 className="mt-6 text-center text-lg font-bold">{t("procurement.printTitle")}</h1>
      <p className="mb-4 text-center text-sm">
        Nr. <b>{nir.number}</b> / {formatDate(nir.date, locale)}
      </p>
      <table className="w-full border-collapse text-xs [&_td]:border [&_td]:border-black [&_td]:px-1.5 [&_td]:py-1 [&_th]:border [&_th]:border-black [&_th]:px-1.5 [&_th]:py-1">
        <thead>
          <tr>
            <th>Nr.</th>
            <th className="text-left">{t("common.item")}</th>
            <th>{t("common.unit")}</th>
            <th>{t("procurement.qtyDocument")}</th>
            <th>{t("procurement.qtyReceived")}</th>
            <th>{t("stock.difference")}</th>
            <th>{t("procurement.unitPrice")}</th>
            <th>{t("procurement.valueNet")}</th>
            <th>TVA %</th>
            <th>{t("procurement.vatValue")}</th>
          </tr>
        </thead>
        <tbody>
          {nir.lines.map((l) => (
            <tr key={l.id}>
              <td className="text-center">{l.position}</td>
              <td>
                {l.itemName}
                {l.discrepancyReason && <span className="block italic">{l.discrepancyReason}</span>}
              </td>
              <td className="text-center">{l.unitCode}</td>
              <td className="text-right">{Number(l.quantityDocument)}</td>
              <td className="text-right">{Number(l.quantityReceived)}</td>
              <td className="text-right">{Number(l.difference) || ""}</td>
              <td className="text-right">{l.unitPrice}</td>
              <td className="text-right">{l.valueNet}</td>
              <td className="text-right">{l.vatPercent}</td>
              <td className="text-right">{l.vatValue}</td>
            </tr>
          ))}
          <tr className="font-bold">
            <td colSpan={7} className="text-right">
              {t("common.total")}
            </td>
            <td className="text-right">{money(nir.totalNet)}</td>
            <td />
            <td className="text-right">{money(nir.totalVat)}</td>
          </tr>
        </tbody>
      </table>
      <div className="mt-10 grid grid-cols-2 gap-10 text-sm">
        <div>
          <p className="font-bold">{t("procurement.signatures")}</p>
          {(nir.committee.length ? nir.committee : ["", "", ""]).map((name, i) => (
            <p key={i} className="mt-6 border-b border-black pb-1">
              {i + 1}. {name}
            </p>
          ))}
        </div>
        <div>
          <p className="font-bold">{t("procurement.receivedBy")}</p>
          <p className="mt-6 border-b border-black pb-1">&nbsp;</p>
        </div>
      </div>
    </div>
  );
}
