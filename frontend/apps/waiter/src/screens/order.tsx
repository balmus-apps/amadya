import { addTableRound, type MenuProduct } from "@amadya/api-client";
import { cartTotals, lineCents, toOrderLines } from "@amadya/customer-core";
import { formatMoney } from "@amadya/i18n";
import { MenuFilters, OptionsSheet, ProductGrid, useProductFilter } from "@amadya/staff-pos";
import { Button, Input, QuantityStepper, Sheet, SheetContent, SheetDescription, SheetFooter, SheetTitle, toast } from "@amadya/ui";
import { ArrowLeftIcon, ChevronUpIcon, SendIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "use-intl";
import { useStore } from "zustand";
import { fromCents } from "../lib/bill";
import { useMenu, useSession, useSessionMutation } from "../lib/data";
import { drafts, useDraft } from "../lib/draft";
import { go } from "../lib/route";

/** Taking a round: pick dishes (with their options and notes), review, send to the kitchen and bar. */
export function OrderScreen({ sessionId }: { sessionId: string }) {
  const t = useTranslations();
  const locale = useLocale() as "ro" | "en";
  const menu = useMenu();
  const session = useSession(sessionId);
  const draft = useDraft(sessionId);
  const add = useStore(drafts, (s) => s.add);
  const [picking, setPicking] = useState<MenuProduct | null>(null);
  const [reviewing, setReviewing] = useState(false);

  const categories = menu.data?.categories ?? [];
  const totals = cartTotals(draft.items);
  const filter = useProductFilter(categories);
  const counts = useMemo(() => {
    const m = new Map<string, number>();
    draft.items.forEach((i) => m.set(i.productId, (m.get(i.productId) ?? 0) + i.quantity));
    return m;
  }, [draft.items]);

  function tap(product: MenuProduct) {
    if (!product.available) return;
    if (product.modifierGroups.length > 0) {
      setPicking(product);
      return;
    }
    add(sessionId, { productId: product.id, name: product.name, unitPrice: product.price.amount, currency: product.price.currency, quantity: 1, options: [] });
    navigator.vibrate?.(15);
  }

  const label = session.data ? t("table.label", { label: session.data.tableLabel }) : "…";

  return (
    <div className="flex h-dvh flex-col bg-muted/40">
      <header className="z-20 border-b bg-background px-2 pt-[max(0.5rem,env(safe-area-inset-top))] pb-2">
        <div className="flex items-center gap-2">
          <button onClick={() => go({ name: "table", sessionId })} aria-label={t("common.back")} className="rounded-lg p-2 hover:bg-muted">
            <ArrowLeftIcon className="size-5" />
          </button>
          <h1 className="min-w-0 flex-1 truncate text-lg font-extrabold">
            {label} · {t("order.title")}
          </h1>
        </div>
        <MenuFilters categories={categories} filter={filter} />
      </header>

      <main className="flex-1 overflow-y-auto p-3">
        <ProductGrid products={filter.products} loading={menu.isPending} counts={counts} onTap={tap} />
      </main>

      <footer className="border-t bg-background p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
        <Button size="lg" className="w-full justify-between" disabled={draft.items.length === 0} onClick={() => setReviewing(true)}>
          <span className="flex items-center gap-2">
            <ChevronUpIcon /> {t("order.review", { count: totals.count })}
          </span>
          <span className="tabular-nums">{formatMoney({ amount: fromCents(totals.totalCents), currency: totals.currency }, locale)}</span>
        </Button>
      </footer>

      <OptionsSheet product={picking} onClose={() => setPicking(null)} onAdd={(item) => add(sessionId, item)} />
      <ReviewSheet sessionId={sessionId} open={reviewing} onClose={() => setReviewing(false)} />
    </div>
  );
}

function ReviewSheet({ sessionId, open, onClose }: { sessionId: string; open: boolean; onClose: () => void }) {
  const t = useTranslations();
  const locale = useLocale() as "ro" | "en";
  const draft = useDraft(sessionId);
  const { setQuantity, setNotes, clear } = useStore(drafts, (s) => s);
  const totals = cartTotals(draft.items);
  const send = useSessionMutation(
    () => addTableRound({ path: { id: sessionId }, body: { lines: toOrderLines(draft.items), notes: draft.notes.trim() || undefined } }),
    () => {
      toast.success(t("order.sent"));
      clear(sessionId);
      onClose();
      go({ name: "table", sessionId });
    },
  );

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="bottom" className="max-h-[92dvh] gap-0 overflow-hidden p-0">
        <div className="overflow-y-auto p-4">
          <SheetTitle>{t("order.reviewTitle")}</SheetTitle>
          <SheetDescription>{t("order.reviewHint")}</SheetDescription>
          <ul className="mt-3 divide-y rounded-xl border">
            {draft.items.map((item) => (
              <li key={item.key} className="flex items-center gap-3 p-3">
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">{item.name}</p>
                  {item.options.length > 0 && <p className="text-xs text-muted-foreground">+ {item.options.map((o) => o.name).join(", ")}</p>}
                  {item.notes && <p className="text-xs font-semibold text-amber-700">⚠ {item.notes}</p>}
                  <p className="text-sm tabular-nums">{formatMoney({ amount: fromCents(lineCents(item)), currency: item.currency }, locale)}</p>
                </div>
                <QuantityStepper value={item.quantity} min={0} size="sm" onChange={(q) => setQuantity(sessionId, item.key, q)} decreaseLabel="−" increaseLabel="+" />
              </li>
            ))}
          </ul>
          <label htmlFor="round-notes" className="mt-4 block text-sm font-semibold">
            {t("order.roundNotes")}
          </label>
          <Input id="round-notes" maxLength={500} value={draft.notes} onChange={(e) => setNotes(sessionId, e.target.value)} placeholder={t("order.roundNotesPlaceholder")} />
        </div>
        <SheetFooter className="border-t bg-background p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
          <Button size="lg" className="w-full" disabled={draft.items.length === 0 || send.isPending} onClick={() => send.mutate(undefined)}>
            <SendIcon /> {t("order.send", { amount: formatMoney({ amount: fromCents(totals.totalCents), currency: totals.currency }, locale) })}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
