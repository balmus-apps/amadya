import { sendGuestRound, unwrap, type GuestView, type MenuModifierGroup, type MenuProduct } from "@amadya/api-client";
import { cartTotals, chosenOptions, fromCents, initialSelection, lineCents, missingGroups, toCents, toggleOption, toOrderLines, type Selection } from "@amadya/customer-core";
import { formatMoney, type AppLocale } from "@amadya/i18n";
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
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { SearchIcon, SendIcon, ShoppingBagIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "use-intl";
import { cartStore, guestHeaders, keys, problemMessage, tableCode, useCart, useMenu } from "../lib/guest";

/** The menu, the cart and sending the round to the waiter. */
export function MenuTab({ view, token, onSent }: { view: GuestView; token: string; onSent: () => void }) {
  const t = useTranslations("table");
  const locale = useLocale() as AppLocale;
  const menu = useMenu();
  const items = useCart((s) => s.items);
  const [query, setQuery] = useState("");
  const [categoryId, setCategoryId] = useState<string>();
  const [picking, setPicking] = useState<MenuProduct | null>(null);
  const [reviewing, setReviewing] = useState(false);

  const categories = menu.data?.categories ?? [];
  const totals = cartTotals(items);
  const products = useMemo(() => {
    const q = normalize(query);
    if (q) return categories.flatMap((c) => c.products).filter((p) => normalize(p.name).includes(q));
    return (categories.find((c) => c.id === categoryId) ?? categories[0])?.products ?? [];
  }, [categories, categoryId, query]);

  function tap(p: MenuProduct) {
    if (!p.available) return;
    if (p.modifierGroups.length > 0) return setPicking(p);
    cartStore.getState().add({ productId: p.id, name: p.name, unitPrice: p.price.amount, currency: p.price.currency, quantity: 1, options: [] });
    toast.success(p.name, { duration: 1200 });
  }

  return (
    <>
      <div className="relative mb-3">
        <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t("menu.search")} aria-label={t("menu.search")} className="pl-9" />
      </div>
      {!query && (
        <nav className="-mx-4 mb-3 flex gap-2 overflow-x-auto px-4 [scrollbar-width:none]">
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

      {menu.isPending ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-20 rounded-xl" />
          ))}
        </div>
      ) : products.length === 0 ? (
        <p className="py-12 text-center text-muted-foreground">{t("menu.noResults")}</p>
      ) : (
        <ul className="space-y-2">
          {products.map((p) => (
            <li key={p.id}>
              <button onClick={() => tap(p)} disabled={!p.available} className="flex w-full items-center gap-3 rounded-xl border bg-card p-3 text-left shadow-sm transition active:scale-[0.99] disabled:opacity-50">
                {p.imageUrl && <img src={p.imageUrl} alt="" className="size-16 shrink-0 rounded-lg object-cover" />}
                <span className="min-w-0 flex-1">
                  <span className="block font-semibold">{p.name}</span>
                  {p.description && <span className="line-clamp-2 block text-xs text-muted-foreground">{p.description}</span>}
                  <span className="block text-sm font-semibold">
                    {formatMoney(p.price, locale)}
                    <span className="font-normal text-muted-foreground">
                      {p.modifierGroups.length > 0 && ` · ${t("menu.options")}`}
                      {!p.available && ` · ${t("menu.soldOut")}`}
                    </span>
                  </span>
                </span>
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary text-lg font-bold text-primary-foreground" aria-hidden>
                  +
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {items.length > 0 && (
        <div className="fixed inset-x-0 bottom-[calc(4rem+env(safe-area-inset-bottom))] z-10 px-4">
          <Button size="lg" className="mx-auto flex w-full max-w-xl justify-between shadow-lg" onClick={() => setReviewing(true)}>
            <span className="flex items-center gap-2">
              <ShoppingBagIcon /> {t("menu.cart", { count: totals.count })}
            </span>
            <span className="tabular-nums">{formatMoney({ amount: fromCents(totals.totalCents), currency: totals.currency }, locale)}</span>
          </Button>
        </div>
      )}

      <Sheet open={!!picking} onOpenChange={(o) => !o && setPicking(null)}>
        <SheetContent side="bottom" className="max-h-[92dvh] gap-0 overflow-hidden p-0">
          {picking && <OptionsForm key={picking.id} product={picking} onClose={() => setPicking(null)} />}
        </SheetContent>
      </Sheet>
      <Review view={view} token={token} open={reviewing} onClose={() => setReviewing(false)} onSent={onSent} />
    </>
  );
}

function OptionsForm({ product, onClose }: { product: MenuProduct; onClose: () => void }) {
  const t = useTranslations("table");
  const locale = useLocale() as AppLocale;
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
        <SheetDescription>{product.description ?? formatMoney(product.price, locale)}</SheetDescription>
        {product.modifierGroups.map((group) => (
          <fieldset key={group.id} className="mt-4">
            <legend className="mb-2 flex w-full items-baseline justify-between gap-2">
              <span className="font-bold">{group.name}</span>
              <Badge variant={group.minSelect > 0 ? "accent" : "muted"}>{group.minSelect > 0 ? t("menu.required") : t("menu.optional")}</Badge>
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
          {t("menu.notes")}
        </label>
        <Textarea id="line-notes" rows={2} maxLength={200} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={t("menu.notesPlaceholder")} />
      </div>
      <SheetFooter className="flex-row items-center gap-3 border-t bg-background p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
        <QuantityStepper value={quantity} onChange={setQuantity} decreaseLabel="−" increaseLabel="+" />
        <Button
          size="lg"
          className="flex-1"
          disabled={missing.length > 0}
          onClick={() => {
            cartStore.getState().add({
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
          {t("menu.add", { price: formatMoney({ amount: fromCents(unit * quantity), currency: product.price.currency }, locale) })}
        </Button>
      </SheetFooter>
    </>
  );
}

function Review({ view, token, open, onClose, onSent }: { view: GuestView; token: string; open: boolean; onClose: () => void; onSent: () => void }) {
  const t = useTranslations("table");
  const locale = useLocale() as AppLocale;
  const items = useCart((s) => s.items);
  const [notes, setNotes] = useState("");
  const client = useQueryClient();
  const totals = cartTotals(items);
  const send = useMutation({
    mutationFn: () => unwrap(sendGuestRound({ path: { code: tableCode }, headers: guestHeaders(token), body: { lines: toOrderLines(items), notes: notes.trim() || undefined } })),
    onSuccess: async () => {
      cartStore.getState().clear();
      setNotes("");
      await client.invalidateQueries({ queryKey: keys.view });
      toast.success(t("menu.sent"));
      onClose();
      onSent();
    },
    onError: (e) => toast.error(problemMessage(e)),
  });

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="bottom" className="max-h-[92dvh] gap-0 overflow-hidden p-0">
        <div className="overflow-y-auto p-4">
          <SheetTitle>{t("menu.reviewTitle")}</SheetTitle>
          <SheetDescription>{t("menu.reviewHint", { minutes: view.confirmMinutes })}</SheetDescription>
          <ul className="mt-3 divide-y rounded-xl border">
            {items.map((item) => (
              <li key={item.key} className="flex items-center gap-3 p-3">
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">{item.name}</p>
                  {item.options.length > 0 && <p className="text-xs text-muted-foreground">+ {item.options.map((o) => o.name).join(", ")}</p>}
                  {item.notes && <p className="text-xs font-semibold text-amber-700">⚠ {item.notes}</p>}
                  <p className="text-sm tabular-nums">{formatMoney({ amount: fromCents(lineCents(item)), currency: item.currency }, locale)}</p>
                </div>
                <QuantityStepper value={item.quantity} min={0} size="sm" onChange={(q) => cartStore.getState().setQuantity(item.key, q)} decreaseLabel="−" increaseLabel="+" />
              </li>
            ))}
          </ul>
          <label htmlFor="round-notes" className="mt-4 block text-sm font-semibold">
            {t("menu.roundNotes")}
          </label>
          <Input id="round-notes" maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </div>
        <SheetFooter className="border-t bg-background p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
          <Button size="lg" className="w-full" disabled={items.length === 0 || send.isPending} onClick={() => send.mutate()}>
            <SendIcon /> {t("menu.send", { amount: formatMoney({ amount: fromCents(totals.totalCents), currency: totals.currency }, locale) })}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}

/** Case- and diacritics-insensitive search ("limonada" finds "Limonadă"). */
function normalize(s: string) {
  return s.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase().trim();
}
