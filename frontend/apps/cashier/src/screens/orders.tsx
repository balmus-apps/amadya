import { cancelOrder, completeOrder, confirmOrder, refuseOrder, type Order } from "@amadya/api-client";
import { fromCents, toCents } from "@amadya/customer-core";
import { formatMoney, formatTime } from "@amadya/i18n";
import { PaymentFields, paymentValid, type PaymentChoice } from "@amadya/staff-pos";
import { hasAnyRole } from "@amadya/staff-auth";
import { Badge, Button, cn, Sheet, SheetContent, SheetDescription, SheetTitle, Skeleton, Textarea, toast } from "@amadya/ui";
import { CheckIcon, PackageCheckIcon, PhoneIcon, TimerIcon, XIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "use-intl";
import { useOrderAction, useSettings } from "../lib/data";
import { canCancel, secondsUntilAuto, type ListName } from "../lib/lists";

type Reasoning = { order: Order; kind: "refuse" | "cancel" };

/** One of the live lists: To confirm, In progress or Ready. */
export function OrderList({ list, orders, loading }: { list: ListName; orders: Order[]; loading: boolean }) {
  const t = useTranslations("orders");
  const [reasoning, setReasoning] = useState<Reasoning | null>(null);
  const [handingOver, setHandingOver] = useState<Order | null>(null);

  if (loading) {
    return (
      <div className="grid gap-3 p-3 md:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-48 rounded-2xl" />
        ))}
      </div>
    );
  }
  return (
    <div className="h-full overflow-y-auto p-3">
      {orders.length === 0 ? (
        <p className="py-16 text-center text-muted-foreground">{t("empty")}</p>
      ) : (
        <ul className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {orders.map((o) => (
            <li key={o.id}>
              <OrderCard order={o} list={list} onReason={(kind) => setReasoning({ order: o, kind })} onHandOver={() => setHandingOver(o)} />
            </li>
          ))}
        </ul>
      )}
      <ReasonSheet reasoning={reasoning} onClose={() => setReasoning(null)} />
      <HandoverSheet key={handingOver?.id} order={handingOver} onClose={() => setHandingOver(null)} />
    </div>
  );
}

function OrderCard({ order: o, list, onReason, onHandOver }: { order: Order; list: ListName; onReason: (kind: "refuse" | "cancel") => void; onHandOver: () => void }) {
  const t = useTranslations("orders");
  const locale = useLocale() as "ro" | "en";
  const confirm = useOrderAction(() => confirmOrder({ path: { orderId: o.id } }), (order) => toast.success(t("confirmed", { number: order.number })));
  const complete = useOrderAction(() => completeOrder({ path: { orderId: o.id } }), (order) => toast.success(t("handedOver", { number: order.number })));
  const manager = hasAnyRole("MANAGER", "ADMIN");

  return (
    <article className={cn("flex h-full flex-col gap-3 rounded-2xl border bg-card p-4 shadow-sm", list === "confirm" && "border-[var(--status-attention-strong)]")}>
      <header className="flex items-start justify-between gap-2">
        <div>
          <p className="text-3xl leading-none font-black tabular-nums">{o.number}</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {t(`channels.${o.channel}`)} · {formatTime(o.createdAt, locale)} · {o.pickupAt ? t("pickup", { time: formatTime(o.pickupAt, locale) }) : t("asap")}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <Badge variant={list === "ready" ? "accent" : "muted"}>{t(`statuses.${o.status}`)}</Badge>
          <Badge variant={o.paymentStatus === "PAID" ? "accent" : "muted"}>{o.paymentStatus === "PAID" ? t("paid") : t("unpaid")}</Badge>
        </div>
      </header>

      {o.customer && (
        <a href={`tel:${o.customer.phone}`} className="flex items-center gap-2 rounded-xl bg-muted px-3 py-2 text-sm font-semibold" aria-label={t("call", { phone: o.customer.phone })}>
          <PhoneIcon className="size-4" /> {o.customer.name} · {o.customer.phone}
        </a>
      )}

      <ul className="flex-1 space-y-1 text-sm">
        {o.lines.map((l) => (
          <li key={l.id}>
            <b>{l.quantity}×</b> {l.productName}
            {l.modifiers.length > 0 && <span className="text-muted-foreground"> · {l.modifiers.join(", ")}</span>}
            {l.notes && <span className="block text-xs font-semibold text-amber-700">⚠ {l.notes}</span>}
          </li>
        ))}
        {o.notes && <li className="text-xs text-muted-foreground italic">“{o.notes}”</li>}
      </ul>

      <p className="flex justify-between font-bold">
        <span>{t("total")}</span>
        <span className="tabular-nums">{formatMoney(o.total, locale)}</span>
      </p>

      {list === "confirm" && <AutoConfirmCountdown createdAt={o.createdAt} />}

      <footer className="flex gap-2">
        {list === "confirm" && (
          <>
            <Button variant="outline" className="h-12 flex-1" onClick={() => onReason("refuse")}>
              <XIcon /> {t("refuse")}
            </Button>
            <Button className="h-12 flex-1" disabled={confirm.isPending} onClick={() => confirm.mutate(undefined)}>
              <CheckIcon /> {t("confirm")}
            </Button>
          </>
        )}
        {list !== "confirm" && canCancel(o.status, manager) && (
          <Button variant="outline" className="h-12 flex-1" onClick={() => onReason("cancel")}>
            <XIcon /> {t("cancel")}
          </Button>
        )}
        {list === "ready" && (
          <Button className="h-12 flex-1" disabled={complete.isPending} onClick={() => (o.paymentStatus === "PAID" ? complete.mutate(undefined) : onHandOver())}>
            <PackageCheckIcon /> {t("pickedUp")}
          </Button>
        )}
      </footer>
    </article>
  );
}

/** Time left before a waiting phone order goes to the kitchen by itself. */
function AutoConfirmCountdown({ createdAt }: { createdAt: string }) {
  const t = useTranslations("orders");
  const settings = useSettings();
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  const minutes = settings.data?.takeawayConfirmMinutes;
  if (minutes === undefined) return null;
  const left = secondsUntilAuto(createdAt, minutes, now);
  const time = `${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`;
  return (
    <p className="flex items-center gap-1.5 rounded-xl bg-[var(--status-attention)] px-3 py-2 text-sm font-semibold text-[var(--status-attention-strong)]">
      <TimerIcon className="size-4" /> {left > 0 ? t("autoIn", { time }) : t("autoNow")}
    </p>
  );
}

const PRESETS = ["unavailable", "closing", "unreachable"] as const;

/** Refusing a phone order or cancelling one: a ready-made reason or free text, which the customer sees. */
function ReasonSheet({ reasoning, onClose }: { reasoning: Reasoning | null; onClose: () => void }) {
  const t = useTranslations();
  const [text, setText] = useState("");
  const close = () => {
    setText("");
    onClose();
  };
  const submit = useOrderAction(
    (reason: string) =>
      reasoning!.kind === "refuse"
        ? refuseOrder({ path: { orderId: reasoning!.order.id }, body: { reason } })
        : cancelOrder({ path: { orderId: reasoning!.order.id }, body: { reason } }),
    (order) => {
      toast.success(t(reasoning!.kind === "refuse" ? "orders.refused" : "orders.cancelled", { number: order.number }));
      close();
    },
  );
  const number = reasoning?.order.number ?? "";

  return (
    <Sheet open={!!reasoning} onOpenChange={(o) => !o && close()}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-md">
        <div className="flex-1 space-y-4 overflow-y-auto p-4">
          <div>
            <SheetTitle>{reasoning?.kind === "refuse" ? t("reason.refuseTitle", { number }) : t("reason.cancelTitle", { number })}</SheetTitle>
            <SheetDescription>{t("reason.hint")}</SheetDescription>
          </div>
          <div className="grid gap-2">
            {PRESETS.map((p) => (
              <Button key={p} variant="outline" size="lg" className="justify-start" disabled={submit.isPending} onClick={() => submit.mutate(t(`reason.presets.${p}`))}>
                {t(`reason.presets.${p}`)}
              </Button>
            ))}
          </div>
          <label htmlFor="reason" className="block text-sm font-semibold">
            {t("reason.other")}
          </label>
          <Textarea id="reason" rows={2} maxLength={300} value={text} onChange={(e) => setText(e.target.value)} />
        </div>
        <div className="border-t p-4">
          <Button size="lg" variant="destructive" className="w-full" disabled={text.trim().length < 3 || submit.isPending} onClick={() => submit.mutate(text.trim())}>
            {reasoning?.kind === "refuse" ? t("reason.submitRefuse") : t("reason.submitCancel")}
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}

/** Handing over an order that is not paid yet: take Cash or Card first. */
function HandoverSheet({ order, onClose }: { order: Order | null; onClose: () => void }) {
  const t = useTranslations();
  const [method, setMethod] = useState<PaymentChoice>("CASH");
  const [tendered, setTendered] = useState("");
  const totalCents = order ? toCents(order.total.amount) : 0;
  const tenderedCents = method === "CASH" && tendered ? toCents(tendered.replace(",", ".")) : 0;
  const complete = useOrderAction(
    () =>
      completeOrder({
        path: { orderId: order!.id },
        body: { method: method === "CARD_POS" ? "CARD_POS" : "CASH", ...(tenderedCents > 0 ? { tendered: fromCents(tenderedCents) } : {}) },
      }),
    (done) => {
      toast.success(t("orders.handedOver", { number: done.number }));
      onClose();
    },
  );

  return (
    <Sheet open={!!order} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-md">
        {order && (
          <>
            <div className="flex-1 space-y-4 overflow-y-auto p-4">
              <div>
                <SheetTitle>{t("handover.title", { number: order.number })}</SheetTitle>
                <SheetDescription>{t("handover.unpaid")}</SheetDescription>
              </div>
              <PaymentFields amountCents={totalCents} currency={order.total.currency} method={method} onMethodChange={setMethod} tendered={tendered} onTenderedChange={setTendered} />
            </div>
            <div className="border-t p-4">
              <Button size="lg" className="h-14 w-full text-base" disabled={!paymentValid(method, tendered, totalCents) || complete.isPending} onClick={() => complete.mutate(undefined)}>
                {t("handover.submit")}
              </Button>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
