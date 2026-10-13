import type { InPersonPaymentMethod } from "@amadya/api-client";
import { fromCents, toCents } from "@amadya/customer-core";
import { formatMoney } from "@amadya/i18n";
import { Button, cn, Input } from "@amadya/ui";
import { ClockIcon, CreditCardIcon, WalletIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useLocale, useTranslations } from "use-intl";

/** Cash or card taken now; LATER (cashier app only) leaves the order to be paid at pickup. */
export type PaymentChoice = InPersonPaymentMethod | "LATER";

/** Cash handed over may be left empty; when given it must cover the amount. */
export function paymentValid(method: PaymentChoice, tendered: string, amountCents: number): boolean {
  if (amountCents <= 0) return false;
  if (method !== "CASH" || !tendered) return true;
  return toCents(tendered.replace(",", ".")) >= amountCents;
}

/**
 * Payment method, cash handed over (with quick banknotes) and the change to give. Controlled: the screen owns the
 * values and sends them with its own request.
 */
export function PaymentFields({
  amountCents,
  currency,
  method,
  onMethodChange,
  tendered,
  onTenderedChange,
  allowLater = false,
}: {
  amountCents: number;
  currency: string;
  method: PaymentChoice;
  onMethodChange: (m: PaymentChoice) => void;
  tendered: string;
  onTenderedChange: (v: string) => void;
  allowLater?: boolean;
}) {
  const t = useTranslations("pos");
  const locale = useLocale() as "ro" | "en";
  const tenderedCents = tendered ? toCents(tendered.replace(",", ".")) : 0;
  const changeCents = method === "CASH" && tenderedCents > 0 ? tenderedCents - amountCents : 0;
  const money = (cents: number) => formatMoney({ amount: fromCents(cents), currency }, locale);
  const options: { value: PaymentChoice; label: string; icon: ReactNode }[] = [
    { value: "CASH", label: t("methods.CASH"), icon: <WalletIcon /> },
    { value: "CARD_POS", label: t("methods.CARD_POS"), icon: <CreditCardIcon /> },
    ...(allowLater ? [{ value: "LATER" as const, label: t("methods.LATER"), icon: <ClockIcon /> }] : []),
  ];

  return (
    <>
      <Segmented label={t("method")} value={method} onChange={onMethodChange} options={options} />
      {method === "CASH" && amountCents > 0 && (
        <div className="space-y-2 rounded-xl border p-3">
          <label htmlFor="tendered" className="text-sm font-semibold">
            {t("tendered")}
          </label>
          <div className="flex gap-2">
            <Input id="tendered" inputMode="decimal" placeholder={fromCents(amountCents)} value={tendered} onChange={(e) => onTenderedChange(e.target.value.replace(/[^\d.,]/g, ""))} className="text-lg" />
            {quickNotes(amountCents).map((cents) => (
              <Button key={cents} variant="outline" onClick={() => onTenderedChange(fromCents(cents))}>
                {cents / 100}
              </Button>
            ))}
          </div>
          {tenderedCents > 0 &&
            (changeCents >= 0 ? (
              <p className="text-lg font-extrabold">{t("change", { amount: money(changeCents) })}</p>
            ) : (
              <p role="alert" className="text-sm font-semibold text-destructive">
                {t("tenderedTooLow")}
              </p>
            ))}
        </div>
      )}
    </>
  );
}

/** Banknotes worth offering as "handed over": the next round amounts above the bill. */
export function quickNotes(amountCents: number): number[] {
  const notes = [1000, 2000, 5000, 10_000, 20_000, 50_000];
  return notes.filter((n) => n >= amountCents).slice(0, 2);
}

export function Segmented<T extends string>({ label, value, onChange, options }: { label: string; value: T; onChange: (v: T) => void; options: { value: T; label: string; icon?: ReactNode }[] }) {
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
