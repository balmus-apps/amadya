import { simulatePaymentCapture, startGuestPayment, unwrap, type GuestView, type PaymentIntent, type TableRound } from "@amadya/api-client";
import { formatMoney, formatTime, type AppLocale } from "@amadya/i18n";
import { Badge, Button, cn, QuantityStepper, Sheet, SheetContent, SheetDescription, SheetTitle, toast } from "@amadya/ui";
import { Elements, ExpressCheckoutElement, PaymentElement, useElements, useStripe } from "@stripe/react-stripe-js";
import { loadStripe, type Stripe } from "@stripe/stripe-js";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ClockIcon, FlaskConicalIcon, LockIcon, WalletIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "use-intl";
import { guestHeaders, keys, problemMessage, tableCode } from "../lib/guest";

const stateOf = (status: TableRound["status"]) =>
  status === "PREPARING" ? "preparing" : status === "READY" ? "ready" : status === "COMPLETED" ? "served" : status === "CANCELLED" ? "cancelled" : "pending";

const colours = {
  pending: "bg-[var(--status-pending)] text-[var(--status-pending-strong)]",
  preparing: "bg-[var(--status-progress)] text-[var(--status-progress-strong)]",
  ready: "bg-[var(--status-done)] text-[var(--status-done-strong)]",
  served: "bg-muted text-muted-foreground",
  cancelled: "bg-muted text-muted-foreground",
};

/** The bill without names: what was ordered, its state, and paying all or chosen items from the phone. */
export function BillTab({ view, token }: { view: GuestView; token: string }) {
  const t = useTranslations("table");
  const locale = useLocale() as AppLocale;
  const [choosing, setChoosing] = useState(false);
  const [selection, setSelection] = useState<Record<string, number>>({});
  const [intent, setIntent] = useState<PaymentIntent | null>(null);

  const start = useMutation({
    mutationFn: (body: { all?: boolean; items?: { lineId: string; quantity: number }[] }) =>
      unwrap(startGuestPayment({ path: { code: tableCode }, headers: guestHeaders(token), body })),
    onSuccess: (i) => {
      setIntent(i);
      setChoosing(false);
      setSelection({});
    },
    onError: (e) => toast.error(problemMessage(e)),
  });

  const rounds = [...view.rounds].reverse();
  const lines = view.rounds.filter((r) => r.status !== "CANCELLED").flatMap((r) => r.lines);
  const free = (l: (typeof lines)[number]) => l.quantity - l.paidQuantity - l.reservedQuantity;
  const chosenCents = lines.reduce((sum, l) => sum + Math.round((Number(l.total.amount) * 100 * (selection[l.lineId] ?? 0)) / l.quantity), 0);
  const money = (amount: { amount: string; currency: string }) => formatMoney(amount, locale);
  const payable = Number(view.payable.amount) > 0;

  return (
    <div className="space-y-3">
      {view.pendingRounds.map((r) => (
        <article key={r.id} className={cn("rounded-2xl border bg-card p-3", r.status === "REJECTED" && "opacity-70")}>
          <p className={cn("mb-2 flex items-center gap-1.5 text-sm font-semibold", r.status === "REJECTED" ? "text-destructive" : "text-[var(--status-pending-strong)]")}>
            <ClockIcon className="size-4" />
            {r.status === "REJECTED" ? t("bill.rejected", { reason: r.reason ? `: ${r.reason}` : "" }) : t("bill.waiting", { time: formatTime(r.deadline, locale) })}
          </p>
          <ul className="space-y-1 text-sm">
            {r.lines.map((l, i) => (
              <li key={i} className="flex justify-between gap-2">
                <span>
                  {l.quantity} × {l.productName}
                  {l.modifiers.length > 0 && <span className="text-muted-foreground"> + {l.modifiers.join(", ")}</span>}
                </span>
                <span className="tabular-nums">{money(l.total)}</span>
              </li>
            ))}
          </ul>
        </article>
      ))}

      {rounds.length === 0 && view.pendingRounds.length === 0 && <p className="py-12 text-center text-muted-foreground">{t("bill.empty")}</p>}

      {rounds.map((round) => {
        const state = stateOf(round.status);
        return (
          <article key={round.orderId} className={cn("overflow-hidden rounded-2xl border bg-card", state === "cancelled" && "opacity-60")}>
            <div className={cn("flex items-center justify-between px-3 py-2 text-sm font-bold", colours[state])}>
              <span>{t("bill.round", { number: round.number })}</span>
              <span className="text-xs uppercase">{t(`bill.state.${state}`)}</span>
            </div>
            <ul className="divide-y">
              {round.lines.map((l) => (
                <li key={l.lineId} className="flex gap-3 px-3 py-2 text-sm">
                  <span className="w-6 shrink-0 font-black">{l.quantity}×</span>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold">{l.productName}</p>
                    {l.modifiers.length > 0 && <p className="text-xs text-muted-foreground">+ {l.modifiers.join(", ")}</p>}
                    <div className="flex gap-1">
                      {l.paidQuantity > 0 && <Badge variant="success">{t("bill.paidUnits", { count: l.paidQuantity })}</Badge>}
                      {l.reservedQuantity > 0 && <Badge variant="muted">{t("bill.reservedUnits", { count: l.reservedQuantity })}</Badge>}
                    </div>
                  </div>
                  <span className="tabular-nums">{money(l.total)}</span>
                </li>
              ))}
            </ul>
          </article>
        );
      })}

      {view.rounds.length > 0 && (
        <section className="space-y-3 rounded-2xl border bg-card p-4">
          <dl className="space-y-1 text-sm">
            <Row label={t("bill.total")} value={money(view.total)} />
            <Row label={t("bill.paid")} value={money(view.paid)} />
            {Number(view.pending.amount) > 0 && <Row label={t("bill.inProgress")} value={money(view.pending)} />}
            <Row label={t("bill.remaining")} value={money(view.payable)} strong />
          </dl>
          {!payable ? (
            <p className="text-center text-sm text-muted-foreground">{t("bill.nothing")}</p>
          ) : !choosing ? (
            <div className="grid gap-2">
              <Button size="lg" disabled={start.isPending} onClick={() => start.mutate({ all: true })}>
                <WalletIcon /> {t("bill.payAll", { amount: money(view.payable) })}
              </Button>
              <Button size="lg" variant="outline" onClick={() => setChoosing(true)}>
                {t("bill.payItems")}
              </Button>
            </div>
          ) : (
            <div className="space-y-3">
              <div>
                <p className="font-bold">{t("bill.choose")}</p>
                <p className="text-xs text-muted-foreground">{t("bill.chooseHint")}</p>
              </div>
              <ul className="divide-y rounded-xl border">
                {lines.filter((l) => free(l) > 0).map((l) => (
                  <li key={l.lineId} className="flex items-center gap-3 p-3">
                    <span className="min-w-0 flex-1 text-sm">
                      <span className="block font-semibold">{l.productName}</span>
                      <span className="text-xs text-muted-foreground">{money(l.unitPrice)}</span>
                    </span>
                    <QuantityStepper value={selection[l.lineId] ?? 0} min={0} max={free(l)} size="sm" onChange={(v) => setSelection((s) => ({ ...s, [l.lineId]: v }))} decreaseLabel="−" increaseLabel="+" />
                  </li>
                ))}
              </ul>
              <div className="grid grid-cols-2 gap-2">
                <Button variant="ghost" onClick={() => (setChoosing(false), setSelection({}))}>
                  {t("bill.cancel")}
                </Button>
                <Button
                  disabled={chosenCents <= 0 || start.isPending}
                  onClick={() => start.mutate({ items: Object.entries(selection).filter(([, q]) => q > 0).map(([lineId, quantity]) => ({ lineId, quantity })) })}
                >
                  {t("bill.paySelected", { amount: formatMoney({ amount: (chosenCents / 100).toFixed(2), currency: view.total.currency }, locale) })}
                </Button>
              </div>
            </div>
          )}
        </section>
      )}

      <PaymentSheet intent={intent} onClose={() => setIntent(null)} />
    </div>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={cn("flex justify-between", strong && "text-lg font-extrabold")}>
      <dt>{label}</dt>
      <dd className="tabular-nums">{value}</dd>
    </div>
  );
}

const stripeCache = new Map<string, Promise<Stripe | null>>();

/** Same payment flow as takeaway orders (ADR 0007): the provider's wallet sheet, or the simulation in development. */
function PaymentSheet({ intent, onClose }: { intent: PaymentIntent | null; onClose: () => void }) {
  const t = useTranslations();
  const locale = useLocale() as AppLocale;
  const client = useQueryClient();
  const paid = async () => {
    toast.success(t("table.pay.done"));
    onClose();
    await client.invalidateQueries({ queryKey: keys.view });
  };
  return (
    <Sheet open={!!intent} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="bottom" className="max-h-[92dvh] overflow-y-auto pb-[max(1rem,env(safe-area-inset-bottom))]">
        {intent && (
          <>
            <SheetTitle>{t("table.pay.title", { amount: formatMoney(intent.amount, locale) })}</SheetTitle>
            <SheetDescription className="flex items-center gap-1">
              <LockIcon className="size-3" /> {t("payment.secure")}
            </SheetDescription>
            {intent.provider === "fake" && <FakePayment intent={intent} onPaid={paid} />}
            {intent.provider === "stripe" && intent.clientSecret && intent.publishableKey && <StripePayment intent={intent} onPaid={paid} />}
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

function FakePayment({ intent, onPaid }: { intent: PaymentIntent; onPaid: () => void }) {
  const t = useTranslations("payment");
  const locale = useLocale() as AppLocale;
  const [busy, setBusy] = useState(false);
  return (
    <div className="space-y-3 pt-3">
      <p className="flex items-start gap-2 rounded-lg bg-muted p-3 text-sm text-muted-foreground">
        <FlaskConicalIcon className="mt-0.5 size-4 shrink-0" /> {t("simulateHint")}
      </p>
      <Button
        size="lg"
        className="w-full"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          try {
            await unwrap(simulatePaymentCapture({ path: { paymentId: intent.paymentId } }));
            onPaid();
          } catch {
            toast.error(t("failed"));
            setBusy(false);
          }
        }}
      >
        {busy ? t("paying") : `${t("simulate")} · ${formatMoney(intent.amount, locale)}`}
      </Button>
    </div>
  );
}

function StripePayment({ intent, onPaid }: { intent: PaymentIntent; onPaid: () => void }) {
  const locale = useLocale();
  const stripe = useMemo(() => {
    const key = intent.publishableKey!;
    if (!stripeCache.has(key)) stripeCache.set(key, loadStripe(key));
    return stripeCache.get(key)!;
  }, [intent.publishableKey]);
  const primary = getComputedStyle(document.documentElement).getPropertyValue("--primary").trim();
  return (
    <Elements stripe={stripe} options={{ clientSecret: intent.clientSecret!, locale: locale === "en" ? "en" : "ro", appearance: { theme: "stripe", variables: { colorPrimary: primary || undefined, borderRadius: "10px" } } }}>
      <StripeForm intent={intent} onPaid={onPaid} />
    </Elements>
  );
}

function StripeForm({ intent, onPaid }: { intent: PaymentIntent; onPaid: () => void }) {
  const t = useTranslations("payment");
  const locale = useLocale() as AppLocale;
  const stripe = useStripe();
  const elements = useElements();
  const [busy, setBusy] = useState(false);
  async function confirm() {
    if (!stripe || !elements) return;
    setBusy(true);
    const { error } = await stripe.confirmPayment({ elements, confirmParams: { return_url: window.location.href }, redirect: "if_required" });
    if (error) {
      toast.error(error.message ?? t("failed"));
      setBusy(false);
      return;
    }
    // The webhook records the payment; the live feed refreshes the bill.
    onPaid();
  }
  return (
    <div className="space-y-4 pt-3">
      <ExpressCheckoutElement onConfirm={confirm} options={{ buttonHeight: 48, paymentMethods: { applePay: "always", googlePay: "always" } }} />
      <PaymentElement options={{ layout: "tabs" }} />
      <Button size="lg" className="w-full" disabled={!stripe || busy} onClick={confirm}>
        {busy ? t("paying") : t("payNow", { total: formatMoney(intent.amount, locale) })}
      </Button>
      <p className="text-center text-xs text-muted-foreground">{t("wallets")}</p>
    </div>
  );
}
