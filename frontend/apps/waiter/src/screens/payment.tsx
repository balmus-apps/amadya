import { recordTablePayment, type InPersonPaymentMethod, type TableSession } from "@amadya/api-client";
import { formatMoney } from "@amadya/i18n";
import { Button, cn, Input, QuantityStepper, Sheet, SheetContent, SheetDescription, SheetTitle, toast } from "@amadya/ui";
import { CreditCardIcon, WalletIcon } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useLocale, useTranslations } from "use-intl";
import { equalShareCents, fromCents, itemsAmountCents, toCents, unpaidUnits } from "../lib/bill";
import { useSessionMutation } from "../lib/data";
import { go } from "../lib/route";

type Mode = "full" | "equal" | "items";

/** Taking a payment at the table: the whole rest, an equal share, or the items one guest had. */
export function PaymentSheet({ session: s, open, onClose }: { session: TableSession; open: boolean; onClose: () => void }) {
  const t = useTranslations();
  const locale = useLocale() as "ro" | "en";
  const currency = s.remaining.currency;
  const remainingCents = toCents(s.remaining.amount);

  const [mode, setMode] = useState<Mode>("full");
  const [method, setMethod] = useState<InPersonPaymentMethod>("CASH");
  const [people, setPeople] = useState(Math.max(2, s.guests));
  const [selection, setSelection] = useState<Record<string, number>>({});
  const [tendered, setTendered] = useState("");

  // A fresh start each time the sheet opens; the number of people left shrinks as they pay.
  useEffect(() => {
    if (open) {
      setSelection({});
      setTendered("");
    }
  }, [open]);

  const lines = useMemo(
    () => s.rounds.filter((r) => r.status !== "CANCELLED").flatMap((r) => r.lines).filter((l) => unpaidUnits(l) > 0),
    [s.rounds],
  );
  const unservedRounds = s.rounds.filter((r) => ["PLACED", "PREPARING", "READY"].includes(r.status)).length;

  const amountCents = mode === "full" ? remainingCents : mode === "equal" ? equalShareCents(remainingCents, people) : Math.min(itemsAmountCents(lines, selection), remainingCents);
  const tenderedCents = tendered ? toCents(tendered.replace(",", ".")) : 0;
  const changeCents = method === "CASH" && tenderedCents > 0 ? tenderedCents - amountCents : 0;
  const closesTable = amountCents === remainingCents;
  const money = (cents: number) => formatMoney({ amount: fromCents(cents), currency }, locale);
  const valid = amountCents > 0 && (method !== "CASH" || !tendered || tenderedCents >= amountCents);

  const pay = useSessionMutation(
    () =>
      recordTablePayment({
        path: { id: s.id },
        body: {
          method,
          ...(mode === "items"
            ? { items: Object.entries(selection).filter(([, q]) => q > 0).map(([lineId, quantity]) => ({ lineId, quantity })) }
            : { amount: fromCents(amountCents) }),
          ...(method === "CASH" && tendered ? { tendered: fromCents(tenderedCents) } : {}),
        },
      }),
    (updated) => {
      toast.success(t("pay.recorded", { amount: money(amountCents) }), changeCents > 0 ? { description: t("pay.giveChange", { amount: money(changeCents) }), duration: 10_000 } : undefined);
      if (updated.status !== "OPEN") {
        toast.success(t("pay.tableFreed", { label: updated.tableLabel }));
        onClose();
        go({ name: "floor" });
        return;
      }
      if (mode === "equal") setPeople((p) => Math.max(1, p - 1));
      setSelection({});
      setTendered("");
      if (mode !== "equal") onClose();
    },
  );

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="bottom" className="max-h-[92dvh] gap-0 overflow-y-auto p-0">
        <div className="space-y-4 p-4">
          <div>
            <SheetTitle>{t("pay.title", { label: s.tableLabel })}</SheetTitle>
            <SheetDescription>{t("pay.remaining", { amount: formatMoney(s.remaining, locale) })}</SheetDescription>
          </div>

          <Segmented
            label={t("pay.how")}
            value={mode}
            onChange={setMode}
            options={[
              { value: "full", label: t("pay.mode.full") },
              { value: "equal", label: t("pay.mode.equal") },
              { value: "items", label: t("pay.mode.items") },
            ]}
          />

          {mode === "equal" && (
            <div className="flex items-center justify-between gap-3 rounded-xl border p-3">
              <div>
                <p className="font-semibold">{t("pay.peopleLeft")}</p>
                <p className="text-sm text-muted-foreground">{t("pay.eachPays", { amount: money(equalShareCents(remainingCents, people)) })}</p>
              </div>
              <QuantityStepper value={people} onChange={(v) => setPeople(Math.max(1, Math.min(50, v)))} decreaseLabel="−" increaseLabel="+" />
            </div>
          )}

          {mode === "items" && (
            <ul className="divide-y rounded-xl border">
              {lines.length === 0 && <li className="p-3 text-sm text-muted-foreground">{t("pay.allItemsPaid")}</li>}
              {lines.map((l) => {
                const max = unpaidUnits(l);
                const chosen = selection[l.lineId] ?? 0;
                return (
                  <li key={l.lineId} className={cn("flex items-center gap-3 p-3", chosen > 0 && "bg-primary/5")}>
                    <button className="min-w-0 flex-1 text-left" onClick={() => setSelection((sel) => ({ ...sel, [l.lineId]: chosen === max ? 0 : max }))}>
                      <span className="block font-semibold">{l.productName}</span>
                      {l.modifiers.length > 0 && <span className="block text-xs text-muted-foreground">+ {l.modifiers.join(", ")}</span>}
                      <span className="block text-xs text-muted-foreground">
                        {t("pay.unitsLeft", { count: max })} · {formatMoney(l.unitPrice, locale)}
                      </span>
                    </button>
                    <QuantityStepper value={chosen} min={0} max={max} size="sm" onChange={(v) => setSelection((sel) => ({ ...sel, [l.lineId]: v }))} decreaseLabel="−" increaseLabel="+" />
                  </li>
                );
              })}
            </ul>
          )}

          <Segmented
            label={t("pay.method")}
            value={method}
            onChange={setMethod}
            options={[
              { value: "CASH", label: t("payments.method.CASH"), icon: <WalletIcon /> },
              { value: "CARD_POS", label: t("payments.method.CARD_POS"), icon: <CreditCardIcon /> },
            ]}
          />

          {method === "CASH" && amountCents > 0 && (
            <div className="space-y-2 rounded-xl border p-3">
              <label htmlFor="tendered" className="text-sm font-semibold">
                {t("pay.tendered")}
              </label>
              <div className="flex gap-2">
                <Input id="tendered" inputMode="decimal" placeholder={fromCents(amountCents)} value={tendered} onChange={(e) => setTendered(e.target.value.replace(/[^\d.,]/g, ""))} className="text-lg" />
                {quickNotes(amountCents).map((cents) => (
                  <Button key={cents} variant="outline" onClick={() => setTendered(fromCents(cents))}>
                    {cents / 100}
                  </Button>
                ))}
              </div>
              {tenderedCents > 0 &&
                (changeCents >= 0 ? (
                  <p className="text-lg font-extrabold">{t("pay.change", { amount: money(changeCents) })}</p>
                ) : (
                  <p role="alert" className="text-sm font-semibold text-destructive">
                    {t("pay.tenderedTooLow")}
                  </p>
                ))}
            </div>
          )}

          {closesTable && unservedRounds > 0 && (
            <p role="note" className="rounded-xl bg-[var(--status-pending)] p-3 text-sm font-semibold text-[var(--status-pending-strong)]">
              {t("pay.unservedWarning", { count: unservedRounds })}
            </p>
          )}
        </div>

        <div className="sticky bottom-0 border-t bg-background p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
          <Button size="lg" className="w-full" disabled={!valid || pay.isPending} onClick={() => pay.mutate(undefined)}>
            {t("pay.confirm", { amount: money(amountCents) })}
            {closesTable && <span className="font-normal opacity-80"> · {t("pay.closes")}</span>}
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}

/** Banknotes worth offering as "handed over": the next round amounts above the bill. */
function quickNotes(amountCents: number): number[] {
  const notes = [1000, 2000, 5000, 10_000, 20_000, 50_000];
  return notes.filter((n) => n >= amountCents).slice(0, 2);
}

function Segmented<T extends string>({ label, value, onChange, options }: { label: string; value: T; onChange: (v: T) => void; options: { value: T; label: string; icon?: React.ReactNode }[] }) {
  return (
    <div role="radiogroup" aria-label={label} className="grid gap-1 rounded-xl bg-muted p-1" style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}>
      {options.map((o) => (
        <button
          key={o.value}
          role="radio"
          aria-checked={value === o.value}
          onClick={() => onChange(o.value)}
          className={cn("flex h-11 items-center justify-center gap-1.5 rounded-lg text-sm font-semibold transition [&_svg]:size-4", value === o.value ? "bg-background shadow-sm" : "text-muted-foreground")}
        >
          {o.icon}
          {o.label}
        </button>
      ))}
    </div>
  );
}
