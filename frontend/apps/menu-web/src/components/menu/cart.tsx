"use client";

import { formatMoney, type AppLocale } from "@amadya/i18n";
import { Button, QuantityStepper, Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@amadya/ui";
import { ShoppingBagIcon } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import { Link } from "@/i18n/navigation";
import { cartTotals, fromCents, lineCents, useCart, type CartItem } from "@/lib/cart";
import { useHydrated } from "../header";
import { useSettings } from "../providers";

/** Floating summary at the bottom of the menu that opens the cart. */
export function CartBar() {
  const t = useTranslations("cart");
  const locale = useLocale() as AppLocale;
  const items = useCart((s) => s.items);
  const hydrated = useHydrated();
  const [open, setOpen] = useState(false);
  const { count, totalCents, currency } = cartTotals(items);
  if (!hydrated || count === 0) return null;

  return (
    <>
      <div className="fixed inset-x-0 bottom-0 z-40 p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
        <Button size="lg" className="mx-auto flex h-14 w-full max-w-lg justify-between rounded-2xl text-base shadow-xl" onClick={() => setOpen(true)}>
          <span className="flex items-center gap-2">
            <span className="flex size-7 items-center justify-center rounded-full bg-primary-foreground/20 text-sm tabular-nums">{count}</span>
            {t("view")}
          </span>
          <span>{formatMoney({ amount: fromCents(totalCents), currency }, locale)}</span>
        </Button>
      </div>
      <CartSheet open={open} onOpenChange={setOpen} />
    </>
  );
}

export function CartSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const t = useTranslations("cart");
  const locale = useLocale() as AppLocale;
  const settings = useSettings();
  const items = useCart((s) => s.items);
  const { count, totalCents, currency } = cartTotals(items);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" closeLabel="×">
        <SheetHeader>
          <SheetTitle>{t("title")}</SheetTitle>
          <p className="text-sm text-muted-foreground">{t("items", { count })}</p>
        </SheetHeader>
        {items.length === 0 ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-2 p-8 text-center text-muted-foreground">
            <ShoppingBagIcon className="size-10" />
            <p className="font-semibold text-foreground">{t("empty")}</p>
            <p className="text-sm">{t("emptyHint")}</p>
          </div>
        ) : (
          <ul className="flex-1 divide-y overflow-y-auto px-5">
            {items.map((item) => (
              <CartLine key={item.key} item={item} locale={locale} />
            ))}
          </ul>
        )}
        {items.length > 0 && (
          <SheetFooter>
            <div className="flex items-center justify-between text-lg font-bold">
              <span>{t("total")}</span>
              <span>{formatMoney({ amount: fromCents(totalCents), currency }, locale)}</span>
            </div>
            <Button asChild size="lg" disabled={!settings.features.takeaway}>
              <Link href="/checkout" onClick={() => onOpenChange(false)}>
                {t("checkout")}
              </Link>
            </Button>
          </SheetFooter>
        )}
      </SheetContent>
    </Sheet>
  );
}

function CartLine({ item, locale }: { item: CartItem; locale: AppLocale }) {
  const t = useTranslations("cart");
  const setQuantity = useCart((s) => s.setQuantity);
  return (
    <li className="flex gap-3 py-4">
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{item.name}</p>
        {item.options.length > 0 && <p className="text-sm text-muted-foreground">{item.options.map((o) => o.name).join(", ")}</p>}
        {item.notes && <p className="text-sm text-muted-foreground italic">“{item.notes}”</p>}
        <p className="mt-1 text-sm font-bold">{formatMoney({ amount: fromCents(lineCents(item)), currency: item.currency }, locale)}</p>
      </div>
      <QuantityStepper
        size="sm"
        min={0}
        value={item.quantity}
        onChange={(q) => setQuantity(item.key, q)}
        decreaseLabel={t("decrease")}
        increaseLabel={t("increase")}
      />
    </li>
  );
}
