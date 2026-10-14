import { client, createNir, getPurchaseInvoice, listMeasureUnits, listPurchaseInvoices, listStockItems, listWarehouses, matchInvoiceLine, type PurchaseInvoice, type PurchaseInvoiceLine } from "@amadya/api-client";
import { Badge, Button, Checkbox, Dialog, DialogContent, DialogTitle, NativeSelect, Table, TableBody, TableCell, TableHead, TableHeader, TableRow, toast } from "@amadya/ui";
import { useNavigate, useParams } from "@tanstack/react-router";
import { CheckCircle2Icon, FileTextIcon, FileUpIcon, Loader2Icon, PlusIcon } from "lucide-react";
import { useRef, useState } from "react";
import { Empty, Field, PageHeader } from "@/components/kit";
import { useApi, useApiMutation } from "@/lib/data";
import { formatDate, formatMoney, lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";
import { accessToken } from "@/lib/session";
import { useQueryClient } from "@tanstack/react-query";

export function InvoicesPage() {
  const t = useT();
  const { locale } = useLocale();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const { data = [], isLoading } = useApi(["invoices"], () => listPurchaseInvoices({ query: { limit: 200 } }));

  async function importXml(file: File) {
    setBusy(true);
    try {
      const res = await fetch(`${client.getConfig().baseUrl}/admin/invoices/import/efactura`, {
        method: "POST",
        body: await file.text(),
        headers: { "Content-Type": "application/xml", Accept: "application/json, application/problem+json", "Accept-Language": locale, Authorization: `Bearer ${await accessToken()}` },
      });
      const json = await res.json();
      if (!res.ok) throw new Error(json.detail ?? res.statusText);
      toast.success(t("procurement.imported", { number: json.number }));
      await queryClient.invalidateQueries({ queryKey: ["invoices"] });
      await navigate({ to: "/invoices/$id", params: { id: json.id } });
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
      if (input.current) input.current.value = "";
    }
  }

  return (
    <>
      <PageHeader
        title={t("procurement.invoicesTitle")}
        description={t("procurement.importHint")}
        actions={
          <>
            <input ref={input} type="file" accept=".xml,application/xml,text/xml" hidden onChange={(e) => e.target.files?.[0] && importXml(e.target.files[0])} />
            <Button variant="outline" onClick={() => input.current?.click()} disabled={busy}>
              {busy ? <Loader2Icon className="animate-spin" /> : <FileUpIcon />} {t("procurement.importEFactura")}
            </Button>
            <Button onClick={() => navigate({ to: "/invoices/new" })}>
              <PlusIcon /> {t("invoiceNew.title")}
            </Button>
          </>
        }
      />
      {data.length === 0 ? (
        <Empty loading={isLoading} />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("procurement.number")}</TableHead>
              <TableHead>{t("procurement.issueDate")}</TableHead>
              <TableHead>{t("procurement.supplier")}</TableHead>
              <TableHead>{t("procurement.source")}</TableHead>
              <TableHead>{t("common.status")}</TableHead>
              <TableHead className="text-right">{t("procurement.gross")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((inv) => {
              const unmatched = inv.lines.filter((l) => !l.matched).length;
              return (
                <TableRow key={inv.id} className="cursor-pointer" onClick={() => navigate({ to: "/invoices/$id", params: { id: inv.id } })}>
                  <TableCell className="font-semibold">{inv.number}</TableCell>
                  <TableCell>{formatDate(inv.issueDate, locale)}</TableCell>
                  <TableCell>{inv.supplierName}</TableCell>
                  <TableCell>{t(`procurement.sources.${inv.source}`)}</TableCell>
                  <TableCell>
                    {inv.nirId ? (
                      <Badge variant="success">{t("procurement.nir")}</Badge>
                    ) : unmatched > 0 ? (
                      <Badge variant="accent">
                        {t("procurement.unmatched")}: {unmatched}
                      </Badge>
                    ) : (
                      <Badge variant="outline">{t("procurement.matched")}</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{formatMoney({ amount: inv.totalGross, currency: inv.currency }, locale)}</TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </>
  );
}

export function InvoiceDetailPage() {
  const t = useT();
  const { locale } = useLocale();
  const navigate = useNavigate();
  const { id } = useParams({ strict: false }) as { id: string };
  const [matching, setMatching] = useState<PurchaseInvoiceLine | null>(null);
  const [warehouseId, setWarehouseId] = useState("");
  const { data: invoice } = useApi(["invoice", id], () => getPurchaseInvoice({ path: { id } }));
  const { data: warehouses = [] } = useApi(["warehouses"], () => listWarehouses());
  const nir = useApiMutation(() => createNir({ body: { invoiceId: id, warehouseId } }), {
    invalidate: [["invoices"], ["nirs"]],
    onSuccess: (n) => navigate({ to: "/nirs/$id", params: { id: n.id } }),
  });
  if (!invoice) return null;
  const allMatched = invoice.lines.every((l) => l.matched);
  const money = (amount: string) => formatMoney({ amount, currency: invoice.currency }, locale);
  const warehouseName = (id?: string) => lt(warehouses.find((w) => w.id === id)?.name, locale);
  const details: [string, string | undefined][] = [
    [t("common.type"), invoice.docType && t(`invoiceNew.docTypes.${invoice.docType}`)],
    [t("invoiceNew.time"), invoice.issueTime],
    [t("invoiceNew.branch"), invoice.supplierBranch],
    [t("invoiceNew.clientNo"), invoice.clientNo],
    [t("invoiceNew.cardNo"), invoice.cardNo],
    [t("invoiceNew.registerNo"), invoice.registerNo],
    [t("invoiceNew.cashierNo"), invoice.cashierNo],
    [t("invoiceNew.buyer"), [invoice.buyerName, invoice.buyerCui].filter(Boolean).join(" · ") || undefined],
    [t("invoiceNew.buyerPerson"), invoice.buyerPerson],
    [t("invoiceNew.payment"), invoice.paymentMethod && `${t(`invoiceNew.payments.${invoice.paymentMethod}`)}${invoice.paidAmount ? ` · ${money(invoice.paidAmount)}` : ""}`],
    [t("invoiceNew.discount"), invoice.discountTotal && money(invoice.discountTotal)],
  ];
  const nirIds = invoice.nirIds ?? (invoice.nirId ? [invoice.nirId] : []);

  return (
    <>
      <PageHeader
        title={`${t("procurement.invoice")} ${invoice.number}`}
        description={`${invoice.supplierName} · ${formatDate(invoice.issueDate, locale)} · ${t(`procurement.sources.${invoice.source}`)}`}
        actions={
          <>
            {invoice.hasFile && (
              <Button variant="outline" onClick={() => openInvoiceFile(invoice.id)}>
                <FileTextIcon /> {t("invoiceNew.original")}
              </Button>
            )}
            {nirIds.length > 0 ? (
              nirIds.map((nirId, i) => (
                <Button key={nirId} onClick={() => navigate({ to: "/nirs/$id", params: { id: nirId } })}>
                  {t("procurement.viewNir")}
                  {nirIds.length > 1 ? ` ${i + 1}` : ""}
                </Button>
              ))
            ) : (
              <>
                <NativeSelect className="w-52" value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} aria-label={t("common.warehouse")}>
                  <option value="">{t("common.warehouse")}…</option>
                  {warehouses.map((w) => (
                    <option key={w.id} value={w.id}>
                      {lt(w.name, locale)}
                    </option>
                  ))}
                </NativeSelect>
                <Button disabled={!allMatched || !warehouseId || nir.isPending} onClick={() => nir.mutate(undefined)}>
                  {t("procurement.createNir")}
                </Button>
              </>
            )}
          </>
        }
      />
      {details.some(([, v]) => v) && (
        <dl className="mb-6 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2 lg:grid-cols-4">
          {details
            .filter(([, v]) => v)
            .map(([label, value]) => (
              <div key={label}>
                <dt className="text-xs text-muted-foreground">{label}</dt>
                <dd className="font-medium">{value}</dd>
              </div>
            ))}
        </dl>
      )}
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>#</TableHead>
            <TableHead>{t("procurement.description")}</TableHead>
            <TableHead className="text-right">{t("common.quantity")}</TableHead>
            <TableHead>{t("common.unit")}</TableHead>
            <TableHead className="text-right">{t("procurement.unitPrice")}</TableHead>
            <TableHead className="text-right">{t("procurement.vat")} %</TableHead>
            <TableHead className="text-right">{t("procurement.net")}</TableHead>
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {invoice.lines.map((l) => (
            <TableRow key={l.id}>
              <TableCell>{l.position}</TableCell>
              <TableCell>
                <span className="font-medium">{l.description}</span>
                {l.originalDescription && l.originalDescription !== l.description && <span className="block text-xs text-muted-foreground">{l.originalDescription}</span>}
                {(l.supplierCode || l.lot || l.warehouseId) && (
                  <span className="block font-mono text-xs text-muted-foreground">{[l.supplierCode, l.lot && `lot ${l.lot}`, l.warehouseId && warehouseName(l.warehouseId)].filter(Boolean).join(" · ")}</span>
                )}
              </TableCell>
              <TableCell className="text-right tabular-nums">{Number(l.quantity)}</TableCell>
              <TableCell>{l.unitCode}</TableCell>
              <TableCell className="text-right tabular-nums">{l.unitPrice}</TableCell>
              <TableCell className="text-right tabular-nums">{l.vatPercent}</TableCell>
              <TableCell className="text-right tabular-nums">{money(l.lineNet)}</TableCell>
              <TableCell className="text-right">
                {l.matched ? (
                  <Button variant="ghost" size="sm" onClick={() => setMatching(l)} disabled={nirIds.length > 0}>
                    <CheckCircle2Icon className="text-success" /> {t("procurement.matched")}
                  </Button>
                ) : (
                  <Button size="sm" onClick={() => setMatching(l)}>
                    {t("procurement.match")}
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <div className="mt-4 ml-auto w-72 space-y-1 text-sm">
        <p className="flex justify-between">
          <span>{t("procurement.net")}</span>
          <span className="tabular-nums">{money(invoice.totalNet)}</span>
        </p>
        <p className="flex justify-between">
          <span>{t("procurement.vat")}</span>
          <span className="tabular-nums">{money(invoice.totalVat)}</span>
        </p>
        <p className="flex justify-between text-base font-bold">
          <span>{t("procurement.gross")}</span>
          <span className="tabular-nums">{money(invoice.totalGross)}</span>
        </p>
      </div>
      {matching && <MatchDialog invoice={invoice} line={matching} onClose={() => setMatching(null)} />}
    </>
  );
}

function MatchDialog({ invoice, line, onClose }: { invoice: PurchaseInvoice; line: PurchaseInvoiceLine; onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const { data: items = [] } = useApi(["stock-items"], () => listStockItems());
  const { data: units = [] } = useApi(["units"], () => listMeasureUnits());
  const [stockItemId, setStockItemId] = useState(line.stockItemId ?? "");
  const [choice, setChoice] = useState(line.packagingId ? `p:${line.packagingId}` : line.unitId ? `u:${line.unitId}` : "");
  const [remember, setRemember] = useState(true);
  const item = items.find((i) => i.id === stockItemId);
  const base = units.find((u) => u.id === item?.baseUnitId);
  const options = [
    ...units.filter((u) => u.status === "ACTIVE" && u.dimension === base?.dimension).map((u) => ({ key: `u:${u.id}`, label: `${u.code}` })),
    ...(item?.packagings ?? []).map((p) => ({ key: `p:${p.id}`, label: `${p.name} (${Number(p.qtyInBase)} ${item?.baseUnitCode})` })),
  ];
  const [kind, id] = choice.split(":");
  const save = useApiMutation(
    () =>
      matchInvoiceLine({
        path: { id: invoice.id, lineId: line.id },
        body: { stockItemId, unitId: kind === "u" ? id : undefined, packagingId: kind === "p" ? id : undefined, remember },
      }),
    { invalidate: [["invoice", invoice.id], ["invoices"]], onSuccess: onClose },
  );
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogTitle>{t("procurement.matchTitle")}</DialogTitle>
        <p className="rounded-lg bg-muted p-3 text-sm">
          <b>{line.description}</b> · {Number(line.quantity)} {line.unitCode}
        </p>
        <Field label={t("common.item")}>
          <NativeSelect value={stockItemId} onChange={(e) => setStockItemId(e.target.value)}>
            <option value="">{t("common.choose")}</option>
            {items.map((it) => (
              <option key={it.id} value={it.id}>
                {lt(it.name, locale)} ({it.sku})
              </option>
            ))}
          </NativeSelect>
        </Field>
        <Field label={t("procurement.unitOrPackaging")} hint={`${line.unitCode} = ?`}>
          <NativeSelect value={choice} onChange={(e) => setChoice(e.target.value)} disabled={!item}>
            <option value="">{t("common.choose")}</option>
            {options.map((o) => (
              <option key={o.key} value={o.key}>
                {o.label}
              </option>
            ))}
          </NativeSelect>
        </Field>
        <label className="flex items-center gap-2 text-sm">
          <Checkbox checked={remember} onCheckedChange={(c) => setRemember(c === true)} /> {t("procurement.remember")}
        </label>
        <Button disabled={!stockItemId || !choice || save.isPending} onClick={() => save.mutate(undefined)}>
          {t("common.save")}
        </Button>
      </DialogContent>
    </Dialog>
  );
}

/** The stored photo or PDF needs the staff token, so it is fetched and opened as a local blob. */
async function openInvoiceFile(id: string) {
  const tab = window.open("", "_blank");
  try {
    const res = await fetch(`${client.getConfig().baseUrl}/admin/invoices/${id}/file`, { headers: { Authorization: `Bearer ${await accessToken()}` } });
    if (!res.ok) throw new Error(res.statusText);
    const url = URL.createObjectURL(await res.blob());
    if (tab) tab.location.href = url;
    else window.open(url, "_blank");
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (e) {
    tab?.close();
    toast.error((e as Error).message);
  }
}
