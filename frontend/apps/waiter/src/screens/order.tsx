import { addTableRound, type MenuModifierGroup, type MenuProduct } from "@amadya/api-client";
import { cartTotals, chosenOptions, initialSelection, lineCents, missingGroups, toggleOption, toOrderLines, type NewCartItem, type Selection } from "@amadya/customer-core";
import { formatMoney } from "@amadya/i18n";
import {
  Badge,
  Button,
  Checkbox,
  cn,
  Input,
  QuantityStepper,
  RadioGroup,
  RadioGroupItem,
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetTitle,
  Skeleton,
  Textarea,
  toast,
} from "@amadya/ui";
import { ArrowLeftIcon, ChevronUpIcon, SearchIcon, SendIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "use-intl";
import { useStore } from "zustand";
import { fromCents, toCents } from "../lib/bill";
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
  const [query, setQuery] = useState("");
  const [categoryId, setCategoryId] = useState<string>();
  const [picking, setPicking] = useState<MenuProduct | null>(null);
  const [reviewing, setReviewing] = useState(false);

  const categories = menu.data?.categories ?? [];
  const totals = cartTotals(draft.items);
  const products = useMemo(() => {
    const q = normalize(query);
    if (q) return categories.flatMap((c) => c.products).filter((p) => normalize(p.name).includes(q));
    return (categories.find((c) => c.id === categoryId) ?? categories[0])?.products ?? [];
  }, [categories, categoryId, query]);
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
        <div className="relative mt-2 px-2">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t("order.search")} aria-label={t("order.search")} className="pl-9" />
        </div>
        {!query && (
          <nav aria-label={t("order.categories")} className="mt-2 flex gap-2 overflow-x-auto px-2 [scrollbar-width:none]">
            {categories.map((c) => {
              const active = c.id === (categoryId ?? categories[0]?.id);
              return (
                <button key={c.id} onClick={() => setCategoryId(c.id)} aria-current={active ? "true" : undefined} className={cn("shrink-0 rounded-full border px-3.5 py-1.5 text-sm font-semibold", active ? "border-primary bg-primary text-primary-foreground" : "bg-card text-muted-foreground")}>
                  {c.name}
                </button>
              );
            })}
          </nav>
        )}
      </header>

      <main className="flex-1 overflow-y-auto p-3">
        {menu.isPending ? (
          <div className="space-y-2">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-16 rounded-xl" />
            ))}
          </div>
        ) : products.length === 0 ? (
          <p className="py-12 text-center text-muted-foreground">{t("order.noResults")}</p>
        ) : (
          <ul className="grid gap-2 sm:grid-cols-2">
            {products.map((p) => (
              <li key={p.id}>
                <button
                  onClick={() => tap(p)}
                  disabled={!p.available}
                  className={cn("flex min-h-16 w-full items-center gap-3 rounded-xl border bg-card p-3 text-left shadow-sm transition active:scale-[0.99] disabled:opacity-50", counts.get(p.id) && "border-primary")}
                >
                  <span className="min-w-0 flex-1">
                    <span className="block leading-snug font-semibold">{p.name}</span>
                    <span className="block text-sm text-muted-foreground">
                      {formatMoney(p.price, locale)}
                      {p.modifierGroups.length > 0 && ` · ${t("order.hasOptions")}`}
                      {!p.available && ` · ${t("order.soldOut")}`}
                    </span>
                  </span>
                  {counts.get(p.id) ? (
                    <span className="flex size-8 items-center justify-center rounded-full bg-primary font-black text-primary-foreground tabular-nums">{counts.get(p.id)}</span>
                  ) : (
                    <span className="flex size-8 items-center justify-center rounded-full border text-lg font-bold" aria-hidden>
                      +
                    </span>
                  )}
                </button>
              </li>
            ))}
          </ul>
        )}
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

function OptionsSheet({ product, onClose, onAdd }: { product: MenuProduct | null; onClose: () => void; onAdd: (item: NewCartItem) => void }) {
  return (
    <Sheet open={!!product} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="bottom" className="max-h-[92dvh] gap-0 overflow-hidden p-0">{product && <OptionsForm key={product.id} product={product} onClose={onClose} onAdd={onAdd} />}</SheetContent>
    </Sheet>
  );
}

function OptionsForm({ product, onClose, onAdd }: { product: MenuProduct; onClose: () => void; onAdd: (item: NewCartItem) => void }) {
  const t = useTranslations();
  const locale = useLocale() as "ro" | "en";
  const [selected, setSelected] = useState<Selection>(() => initialSelection(product.modifierGroups));
  const [quantity, setQuantity] = useState(1);
  const [notes, setNotes] = useState("");
  const chosen = chosenOptions(product.modifierGroups, selected);
  const missing = missingGroups(product.modifierGroups, selected);
  const unit = toCents(product.price.amount) + chosen.reduce((sum, o) => sum + toCents(o.priceDelta.amount), 0);

  const toggle = (group: MenuModifierGroup, optionId: string, checked: boolean) => setSelected((s) => toggleOption(s, group, optionId, checked));

  return (
    <>
      <div className="overflow-y-auto p-4">
        <SheetTitle>{product.name}</SheetTitle>
        <SheetDescription>{formatMoney(product.price, locale)}</SheetDescription>
        {product.modifierGroups.map((group) => (
          <fieldset key={group.id} className="mt-4">
            <legend className="mb-2 flex w-full items-baseline justify-between gap-2">
              <span className="font-bold">{group.name}</span>
              <Badge variant={group.minSelect > 0 ? "accent" : "muted"}>{group.minSelect > 0 ? t("order.required") : t("order.optional")}</Badge>
            </legend>
            {group.maxSelect === 1 ? (
              <RadioGroup value={selected[group.id]?.[0] ?? ""} onValueChange={(v) => toggle(group, v, true)}>
                {group.options.map((o) => (
                  <label key={o.id} htmlFor={`${group.id}-${o.id}`} className="flex min-h-11 items-center gap-3 rounded-xl border px-3">
                    <RadioGroupItem id={`${group.id}-${o.id}`} value={o.id} />
                    <span className="flex-1">{o.name}</span>
                    {Number(o.priceDelta.amount) !== 0 && <span className="text-sm text-muted-foreground">+{formatMoney(o.priceDelta, locale)}</span>}
                  </label>
                ))}
              </RadioGroup>
            ) : (
              <div className="grid gap-2">
                {group.options.map((o) => {
                  const checked = selected[group.id]?.includes(o.id) ?? false;
                  const full = !checked && (selected[group.id]?.length ?? 0) >= group.maxSelect;
                  return (
                    <label key={o.id} htmlFor={`${group.id}-${o.id}`} className={cn("flex min-h-11 items-center gap-3 rounded-xl border px-3", full && "opacity-50")}>
                      <Checkbox id={`${group.id}-${o.id}`} checked={checked} disabled={full} onCheckedChange={(c) => toggle(group, o.id, c === true)} />
                      <span className="flex-1">{o.name}</span>
                      {Number(o.priceDelta.amount) !== 0 && <span className="text-sm text-muted-foreground">+{formatMoney(o.priceDelta, locale)}</span>}
                    </label>
                  );
                })}
              </div>
            )}
          </fieldset>
        ))}
        <label htmlFor="line-notes" className="mt-4 block text-sm font-semibold">
          {t("order.notes")}
        </label>
        <Textarea id="line-notes" rows={2} maxLength={200} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={t("order.notesPlaceholder")} />
      </div>
      <SheetFooter className="flex-row items-center gap-3 border-t bg-background p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
        <QuantityStepper value={quantity} onChange={setQuantity} decreaseLabel="−" increaseLabel="+" />
        <Button
          size="lg"
          className="flex-1"
          disabled={missing.length > 0}
          onClick={() => {
            onAdd({
              productId: product.id,
              name: product.name,
              unitPrice: product.price.amount,
              currency: product.price.currency,
              quantity,
              options: chosen.map((o) => ({ id: o.id, name: o.name, priceDelta: o.priceDelta.amount })),
              notes: notes.trim() || undefined,
            });
            onClose();
          }}
        >
          {t("order.add", { price: formatMoney({ amount: fromCents(unit * quantity), currency: product.price.currency }, locale) })}
        </Button>
      </SheetFooter>
    </>
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

/** Case- and diacritics-insensitive search ("ciorba" finds "Ciorbă"). */
function normalize(s: string) {
  return s.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase().trim();
}
