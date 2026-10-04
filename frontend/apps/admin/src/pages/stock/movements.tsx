import { listStockMovements, listWarehouses } from "@amadya/api-client";
import { NativeSelect, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@amadya/ui";
import { useState } from "react";
import { Empty, PageHeader } from "@/components/kit";
import { useApi } from "@/lib/data";
import { formatDateTime, formatMoney, formatQty, lt } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

export function MovementsPage() {
  const t = useT();
  const { locale } = useLocale();
  const [warehouseId, setWarehouseId] = useState("");
  const { data: warehouses = [] } = useApi(["warehouses"], () => listWarehouses());
  const { data = [], isLoading } = useApi(["movements", warehouseId], () => listStockMovements({ query: { warehouseId: warehouseId || undefined, limit: 300 } }));
  return (
    <>
      <PageHeader
        title={t("stock.movementsTitle")}
        actions={
          <NativeSelect className="w-52" value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)}>
            <option value="">{t("common.all")}</option>
            {warehouses.map((w) => (
              <option key={w.id} value={w.id}>
                {lt(w.name, locale)}
              </option>
            ))}
          </NativeSelect>
        }
      />
      {data.length === 0 ? (
        <Empty loading={isLoading} />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("common.date")}</TableHead>
              <TableHead>{t("common.type")}</TableHead>
              <TableHead>{t("common.item")}</TableHead>
              <TableHead>{t("common.warehouse")}</TableHead>
              <TableHead className="text-right">{t("common.quantity")}</TableHead>
              <TableHead className="text-right">{t("stock.cost")}</TableHead>
              <TableHead>{t("stock.source")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((m) => (
              <TableRow key={m.id}>
                <TableCell className="whitespace-nowrap">{formatDateTime(m.occurredAt, locale)}</TableCell>
                <TableCell>{t(`stock.movementTypes.${m.type}`)}</TableCell>
                <TableCell className="font-medium">{m.itemName}</TableCell>
                <TableCell>{lt(warehouses.find((w) => w.id === m.warehouseId)?.name, locale)}</TableCell>
                <TableCell className={`text-right tabular-nums ${Number(m.quantity) < 0 ? "text-destructive" : "text-success"}`}>{formatQty(m.quantity, m.unitCode, locale)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatMoney(m.cost, locale)}</TableCell>
                <TableCell className="text-muted-foreground">{m.sourceRef ?? m.sourceType}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </>
  );
}
