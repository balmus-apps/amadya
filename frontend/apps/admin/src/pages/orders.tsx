import { cancelOrder, completeOrder, listOrders, type Order, type OrderStatus } from "@amadya/api-client";
import { Button, NativeSelect, Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle, Table, TableBody, TableCell, TableHead, TableHeader, TableRow, Textarea } from "@amadya/ui";
import { useState } from "react";
import { Empty, Field, PageHeader } from "@/components/kit";
import { OrderStatusBadge } from "@/components/status";
import { useApi, useApiMutation } from "@/lib/data";
import { formatDateTime, formatMoney } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";

const statuses: OrderStatus[] = ["PENDING_PAYMENT", "PLACED", "PREPARING", "READY", "COMPLETED", "CANCELLED"];

export function OrdersPage() {
  const t = useT();
  const { locale } = useLocale();
  const [status, setStatus] = useState<OrderStatus | "">("");
  const [selected, setSelected] = useState<Order | null>(null);
  const { data = [], isLoading } = useApi(["orders", status], () => listOrders({ query: { status: status ? [status] : undefined, limit: 200 } }), { refetchInterval: 15_000 });

  return (
    <>
      <PageHeader
        title={t("orders.title")}
        actions={
          <NativeSelect value={status} onChange={(e) => setStatus(e.target.value as OrderStatus | "")} className="w-48">
            <option value="">{t("common.all")}</option>
            {statuses.map((s) => (
              <option key={s} value={s}>
                {t(`orders.statuses.${s}`)}
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
              <TableHead>{t("orders.number")}</TableHead>
              <TableHead>{t("orders.createdAt")}</TableHead>
              <TableHead>{t("orders.channel")}</TableHead>
              <TableHead>{t("orders.customer")}</TableHead>
              <TableHead>{t("common.status")}</TableHead>
              <TableHead>{t("orders.payment")}</TableHead>
              <TableHead className="text-right">{t("common.total")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((o) => (
              <TableRow key={o.id} className="cursor-pointer" onClick={() => setSelected(o)}>
                <TableCell className="font-bold tabular-nums">{o.number}</TableCell>
                <TableCell className="whitespace-nowrap">{formatDateTime(o.createdAt, locale)}</TableCell>
                <TableCell>{t(`orders.channels.${o.channel}`)}</TableCell>
                <TableCell>{o.customer ? `${o.customer.name} · ${o.customer.phone}` : "—"}</TableCell>
                <TableCell>
                  <OrderStatusBadge status={o.status} />
                </TableCell>
                <TableCell>{t(`orders.payments.${o.paymentStatus}`)}</TableCell>
                <TableCell className="text-right font-semibold tabular-nums">{formatMoney(o.total, locale)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      <OrderSheet order={selected} onClose={() => setSelected(null)} />
    </>
  );
}

function OrderSheet({ order, onClose }: { order: Order | null; onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const [reason, setReason] = useState("");
  const complete = useApiMutation((id: string) => completeOrder({ path: { orderId: id } }), { invalidate: [["orders"]], onSuccess: onClose })
  const cancel = useApiMutation((id: string) => cancelOrder({ path: { orderId: id }, body: { reason } }), { invalidate: [["orders"]], onSuccess: onClose });
  if (!order) return <Sheet open={false} />;
  const open = !["COMPLETED", "CANCELLED"].includes(order.status);
  return (
    <Sheet open onOpenChange={(o) => !o && onClose()}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>
            {order.number} <OrderStatusBadge status={order.status} />
          </SheetTitle>
          <p className="text-sm text-muted-foreground">
            {t(`orders.channels.${order.channel}`)} · {formatDateTime(order.createdAt, locale)}
          </p>
        </SheetHeader>
        <div className="flex-1 space-y-4 overflow-y-auto px-5">
          {order.customer && (
            <p className="text-sm">
              {order.customer.name} · <a href={`tel:${order.customer.phone}`}>{order.customer.phone}</a>
            </p>
          )}
          <ul className="divide-y rounded-xl border">
            {order.lines.map((l) => (
              <li key={l.id} className="flex justify-between gap-3 p-3 text-sm">
                <span>
                  <b>{l.quantity}×</b> {l.productName}
                  {l.modifiers.length > 0 && <span className="text-muted-foreground"> · {l.modifiers.join(", ")}</span>}
                  {l.notes && <span className="block text-xs text-muted-foreground italic">“{l.notes}”</span>}
                </span>
                <span className="tabular-nums">{formatMoney(l.total, locale)}</span>
              </li>
            ))}
          </ul>
          <p className="flex justify-between font-bold">
            <span>{t("common.total")}</span>
            <span>{formatMoney(order.total, locale)}</span>
          </p>
          {order.notes && <p className="text-sm text-muted-foreground">{order.notes}</p>}
          {order.cancelReason && <p className="text-sm text-destructive">{order.cancelReason}</p>}
          {open && (
            <Field label={t("orders.cancelReason")}>
              <Textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={2} />
            </Field>
          )}
        </div>
        {open && (
          <SheetFooter className="flex-row">
            <Button variant="outline" className="flex-1" disabled={reason.trim().length < 3 || cancel.isPending} onClick={() => cancel.mutate(order.id)}>
              {t("orders.cancel")}
            </Button>
            <Button className="flex-1" disabled={order.status !== "READY" || complete.isPending} onClick={() => complete.mutate(order.id)}>
              {t("orders.complete")}
            </Button>
          </SheetFooter>
        )}
      </SheetContent>
    </Sheet>
  );
}
