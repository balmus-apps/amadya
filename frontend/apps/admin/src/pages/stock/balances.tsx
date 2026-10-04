import { listStockBalances, listStockLots, listWarehouses, type StockBalance } from "@amadya/api-client";
import { Badge, Dialog, DialogContent, DialogTitle, NativeSelect, Switch, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@amadya/ui";
import { useNavigate, useSearch } from "@tanstack/react-router";
import { useState } from "react";
import { Empty, PageHeader } from "@/components/kit";
import { useApi } from "@/lib/data";
import { formatDate, formatMoney, formatQty, lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

export function BalancesPage() {
  const t = useT();
  const { locale } = useLocale();
  const search = useSearch({ strict: false }) as { low?: boolean; warehouseId?: string };
  const navigate = useNavigate();
  const [lotsOf, setLotsOf] = useState<StockBalance | null>(null);
  const { data: warehouses = [] } = useApi(["warehouses"], () => listWarehouses());
  const { data = [], isLoading } = useApi(["balances", search.warehouseId, search.low], () =>
    listStockBalances({ query: { warehouseId: search.warehouseId, lowOnly: !!search.low } }),
  );
  const total = data.reduce((sum, b) => sum + Number(b.value.amount), 0);
  const wh = (id: string) => lt(warehouses.find((w) => w.id === id)?.name, locale);

  return (
    <>
      <PageHeader
        title={t("stock.balancesTitle")}
        description={data.length ? `${t("stock.value")}: ${formatMoney({ amount: total.toFixed(2), currency: data[0]!.value.currency }, locale)}` : undefined}
        actions={
          <>
            <NativeSelect className="w-52" value={search.warehouseId ?? ""} onChange={(e) => navigate({ to: ".", search: { ...search, warehouseId: e.target.value || undefined } })}>
              <option value="">{t("common.all")}</option>
              {warehouses.map((w) => (
                <option key={w.id} value={w.id}>
                  {lt(w.name, locale)}
                </option>
              ))}
            </NativeSelect>
            <label className="flex items-center gap-2 text-sm font-medium">
              <Switch checked={!!search.low} onCheckedChange={(low) => navigate({ to: ".", search: { ...search, low: low || undefined } })} /> {t("stock.lowOnly")}
            </label>
          </>
        }
      />
      {data.length === 0 ? (
        <Empty loading={isLoading} />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("stock.sku")}</TableHead>
              <TableHead>{t("common.item")}</TableHead>
              <TableHead>{t("common.warehouse")}</TableHead>
              <TableHead className="text-right">{t("common.quantity")}</TableHead>
              <TableHead className="text-right">{t("stock.minStock")}</TableHead>
              <TableHead className="text-right">{t("stock.value")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((b) => (
              <TableRow key={`${b.warehouseId}-${b.stockItemId}`} className="cursor-pointer" onClick={() => setLotsOf(b)}>
                <TableCell className="font-mono text-xs">{b.sku}</TableCell>
                <TableCell className="font-medium">
                  {b.itemName} {b.low && <Badge variant="accent">{t("stock.low")}</Badge>}
                </TableCell>
                <TableCell>{wh(b.warehouseId)}</TableCell>
                <TableCell className={`text-right tabular-nums ${Number(b.quantity) < 0 ? "text-destructive" : ""}`}>{formatQty(b.quantity, b.unitCode, locale)}</TableCell>
                <TableCell className="text-right text-muted-foreground tabular-nums">{formatQty(b.minStock, b.unitCode, locale)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatMoney(b.value, locale)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {lotsOf && <LotsDialog balance={lotsOf} onClose={() => setLotsOf(null)} />}
    </>
  );
}

function LotsDialog({ balance, onClose }: { balance: StockBalance; onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const { data = [] } = useApi(["lots", balance.warehouseId, balance.stockItemId], () =>
    listStockLots({ query: { warehouseId: balance.warehouseId, stockItemId: balance.stockItemId } }),
  );
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogTitle>
          {t("stock.lots")}: {balance.itemName}
        </DialogTitle>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("stock.receivedAt")}</TableHead>
              <TableHead className="text-right">{t("stock.remaining")}</TableHead>
              <TableHead className="text-right">{t("stock.unitCost")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((l) => (
              <TableRow key={l.id}>
                <TableCell>{formatDate(l.receivedAt, locale)}</TableCell>
                <TableCell className="text-right tabular-nums">
                  {formatQty(l.quantityRemaining, l.unitCode, locale)} / {formatQty(l.quantityInitial, l.unitCode, locale)}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {Number(l.unitCost).toFixed(4)} / {l.unitCode}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </DialogContent>
    </Dialog>
  );
}
