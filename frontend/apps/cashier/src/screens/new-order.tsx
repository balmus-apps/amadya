import { createOrder, type MenuProduct, type Order } from "@amadya/api-client";
import { cartTotals, fromCents, lineCents, toCents, toOrderLines } from "@amadya/customer-core";
import { formatMoney } from "@amadya/i18n";
import { MenuFilters, OptionsSheet, PaymentFields, paymentValid, ProductGrid, useProductFilter, type PaymentChoice } from "@amadya/staff-pos";
import { Button, Input, QuantityStepper, Sheet, SheetContent, SheetDescription, SheetTitle } from "@amadya/ui";
import { CheckCircle2Icon, SendIcon, Trash2Icon } from "lucide-react";
import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "use-intl";
import { useStore } from "zustand";
import { useMenu, useOrderAction } from "../lib/data";
import { ticket } from "../lib/ticket";

interface Done {
  order: Order;
  method: PaymentChoice;
  changeCents: number;
}

/** Taking an order at the counter: products on the left, the ticket on the right, then the money. */
export function NewOrderScreen({ live }: { live: boolean }) {
  const t = useTranslations("ticket");
  const locale = useLocale() as "ro" | "en";
  const menu = useMenu();
  const categories = menu.data?.categories ?? [];
  const filter = useProductFilter(categories);
  const items = useStore(ticket, (s) => s.items);
  const { add, setQuantity, clear } = useStore(ticket, (s) => s);
  const [notes, setNotes] = useState("");
  const [picking, setPicking] = useState<MenuProduct | null>(null);
  const [paying, setPaying] = useState(false);
  const [done, setDone] = useState<Done | null>(null);
  const totals = cartTotals(items);
  const money = (cents: number) => formatMoney({ amount: fromCents(cents), currency: totals.currency }, locale);

  const counts = useMemo(() => {
    const m = new Map<string, number>();
    items.forEach((i) => m.set(i.productId, (m.get(i.productId) ?? 0) + i.quantity));
    return m;
  }, [items]);

  function tap(product: MenuProduct) {
    if (!product.available) return;
    if (product.modifierGroups.length > 0) {
      setPicking(product);
      return;
    }
    add({ productId: product.id, name: product.name, unitPrice: product.price.amount, currency: product.price.currency, quantity: 1, options: [] });
  }

  return (
    <div className="grid h-full lg:grid-cols-[minmax(0,1fr)_400px]">
      <section className="flex min-h-0 flex-col">
        <div className="border-b bg-background pb-2">
          <MenuFilters categories={categories} filter={filter} />
        </div>
        <div className="flex-1 overflow-y-auto p-3">
          <ProductGrid products={filter.products} loading={menu.isPending} counts={counts} onTap={tap} className="xl:grid-cols-3" />
        </div>
      </section>

      <aside className="flex min-h-0 flex-col border-t bg-background lg:border-t-0 lg:border-l">
        <div className="flex items-center justify-between px-4 pt-3">
          <h2 className="text-lg font-extrabold">{t("title")}</h2>
          {items.length > 0 && (
            <Button variant="ghost" size="sm" onClick={clear}>
              <Trash2Icon /> {t("clear")}
            </Button>
          )}
        </div>
        <div className="flex-1 overflow-y-auto px-4">
          {items.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">{t("empty")}</p>
          ) : (
            <ul className="divide-y">
              {items.map((item) => (
                <li key={item.key} className="flex items-center gap-3 py-2">
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold">{item.name}</p>
                    {item.options.length > 0 && <p className="text-xs text-muted-foreground">+ {item.options.map((o) => o.name).join(", ")}</p>}
                    {item.notes && <p className="text-xs font-semibold text-amber-700">⚠ {item.notes}</p>}
                    <p className="text-sm tabular-nums">{money(lineCents(item))}</p>
                  </div>
                  <QuantityStepper value={item.quantity} min={0} size="sm" onChange={(q) => setQuantity(item.key, q)} decreaseLabel="−" increaseLabel="+" />
                </li>
              ))}
            </ul>
          )}
          <label htmlFor="order-notes" className="mt-3 block text-sm font-semibold">
            {t("notes")}
          </label>
          <Input id="order-notes" maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={t("notesPlaceholder")} />
        </div>
        <div className="space-y-2 border-t p-4">
          <p className="flex justify-between text-lg font-extrabold">
            <span>{t("total")}</span>
            <span className="tabular-nums">{money(totals.totalCents)}</span>
          </p>
          {!live && (
            <p role="alert" className="text-sm font-semibold text-destructive">
              {t("offline")}
            </p>
          )}
          <Button size="lg" className="h-14 w-full text-base" disabled={items.length === 0 || !live} onClick={() => setPaying(true)}>
            {t("charge", { amount: money(totals.totalCents) })}
          </Button>
        </div>
      </aside>

      <OptionsSheet product={picking} onClose={() => setPicking(null)} onAdd={add} />
      <PaySheet
        open={paying}
        onClose={() => setPaying(false)}
        totalCents={totals.totalCents}
        currency={totals.currency}
        live={live}
        send={(payment) => createOrder({ body: { channel: "COUNTER", lines: toOrderLines(items), notes: notes.trim() || undefined, ...(payment ? { payment } : {}) } })}
        onDone={(d) => {
          setPaying(false);
          clear();
          setNotes("");
          setDone(d);
        }}
      />
      <DoneSheet done={done} onClose={() => setDone(null)} />
    </div>
  );
}

function PaySheet({
  open,
  onClose,
  totalCents,
  currency,
  live,
  send,
  onDone,
}: {
  open: boolean;
  onClose: () => void;
  totalCents: number;
  currency: string;
  live: boolean;
  send: (payment: { method: "CASH" | "CARD_POS"; tendered?: string } | undefined) => Promise<{ data?: Order; error?: unknown; response?: Response }>;
  onDone: (done: Done) => void;
}) {
  const t = useTranslations("ticket");
  const locale = useLocale() as "ro" | "en";
  const [method, setMethod] = useState<PaymentChoice>("CASH");
  const [tendered, setTendered] = useState("");
  const tenderedCents = method === "CASH" && tendered ? toCents(tendered.replace(",", ".")) : 0;
  const submit = useOrderAction(
    () => send(method === "LATER" ? undefined : { method, ...(tenderedCents > 0 ? { tendered: fromCents(tenderedCents) } : {}) }),
    (order) => {
      onDone({ order, method, changeCents: tenderedCents > 0 ? tenderedCents - toCents(order.total.amount) : 0 });
      setMethod("CASH");
      setTendered("");
    },
  );
  const amount = formatMoney({ amount: fromCents(totalCents), currency }, locale);

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-md">
        <div className="flex-1 space-y-4 overflow-y-auto p-4">
          <div>
            <SheetTitle>{t("payTitle", { amount })}</SheetTitle>
            <SheetDescription className="sr-only">{amount}</SheetDescription>
          </div>
          <PaymentFields amountCents={totalCents} currency={currency} method={method} onMethodChange={setMethod} tendered={tendered} onTenderedChange={setTendered} allowLater />
        </div>
        <div className="border-t p-4">
          <Button size="lg" className="h-14 w-full text-base" disabled={!live || !paymentValid(method, tendered, totalCents) || submit.isPending} onClick={() => submit.mutate(undefined)}>
            <SendIcon /> {method === "LATER" ? t("send") : t("sendPaid")}
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}

/** The number to call out, and the change to give, until the next tap. */
function DoneSheet({ done, onClose }: { done: Done | null; onClose: () => void }) {
  const t = useTranslations();
  const locale = useLocale() as "ro" | "en";
  return (
    <Sheet open={!!done} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="bottom" className="items-center gap-3 p-6 text-center">
        {done && (
          <>
            <CheckCircle2Icon className="size-12 text-[var(--status-done-strong)]" />
            <SheetTitle className="text-4xl font-black">{t("ticket.doneTitle", { number: done.order.number })}</SheetTitle>
            <SheetDescription className="text-base">
              {done.method === "LATER" ? t("ticket.doneLater") : t("ticket.donePaid", { method: t(`pos.methods.${done.method}`) })} · {formatMoney(done.order.total, locale)}
            </SheetDescription>
            {done.changeCents > 0 && <p className="text-2xl font-extrabold">{t("ticket.giveChange", { amount: formatMoney({ amount: fromCents(done.changeCents), currency: done.order.total.currency }, locale) })}</p>}
            <Button size="lg" className="mt-2 h-14 w-full max-w-sm text-base" onClick={onClose}>
              {t("ticket.next")}
            </Button>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
