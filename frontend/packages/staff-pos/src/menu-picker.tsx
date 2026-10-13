import type { MenuCategory, MenuModifierGroup, MenuProduct } from "@amadya/api-client";
import { chosenOptions, fromCents, initialSelection, missingGroups, toCents, toggleOption, type NewCartItem, type Selection } from "@amadya/customer-core";
import { formatMoney } from "@amadya/i18n";
import { Badge, Button, Checkbox, cn, Input, QuantityStepper, RadioGroup, RadioGroupItem, Sheet, SheetContent, SheetDescription, SheetFooter, SheetTitle, Skeleton, Textarea } from "@amadya/ui";
import { SearchIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "use-intl";

/** Search and category filter over the menu; a search looks in every category. */
export function useProductFilter(categories: MenuCategory[]) {
  const [query, setQuery] = useState("");
  const [categoryId, setCategoryId] = useState<string>();
  const activeCategoryId = categoryId ?? categories[0]?.id;
  const products = useMemo(() => {
    const q = normalize(query);
    if (q) return categories.flatMap((c) => c.products).filter((p) => normalize(p.name).includes(q));
    return (categories.find((c) => c.id === activeCategoryId) ?? categories[0])?.products ?? [];
  }, [categories, activeCategoryId, query]);
  return { query, setQuery, activeCategoryId, setCategoryId, products };
}

export function MenuFilters({ categories, filter }: { categories: MenuCategory[]; filter: ReturnType<typeof useProductFilter> }) {
  const t = useTranslations("pos");
  return (
    <>
      <div className="relative mt-2 px-2">
        <SearchIcon className="pointer-events-none absolute top-1/2 left-5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input type="search" value={filter.query} onChange={(e) => filter.setQuery(e.target.value)} placeholder={t("search")} aria-label={t("search")} className="pl-9" />
      </div>
      {!filter.query && (
        <nav aria-label={t("categories")} className="mt-2 flex gap-2 overflow-x-auto px-2 [scrollbar-width:none]">
          {categories.map((c) => {
            const active = c.id === filter.activeCategoryId;
            return (
              <button
                key={c.id}
                onClick={() => filter.setCategoryId(c.id)}
                aria-current={active ? "true" : undefined}
                className={cn("shrink-0 rounded-full border px-3.5 py-1.5 text-sm font-semibold", active ? "border-primary bg-primary text-primary-foreground" : "bg-card text-muted-foreground")}
              >
                {c.name}
              </button>
            );
          })}
        </nav>
      )}
    </>
  );
}

/** Product buttons; [counts] shows how many of each product are already on the order. */
export function ProductGrid({ products, loading, counts, onTap, className }: { products: MenuProduct[]; loading: boolean; counts: Map<string, number>; onTap: (p: MenuProduct) => void; className?: string }) {
  const t = useTranslations("pos");
  const locale = useLocale() as "ro" | "en";
  if (loading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-16 rounded-xl" />
        ))}
      </div>
    );
  }
  if (products.length === 0) return <p className="py-12 text-center text-muted-foreground">{t("noResults")}</p>;
  return (
    <ul className={cn("grid gap-2 sm:grid-cols-2", className)}>
      {products.map((p) => (
        <li key={p.id}>
          <button
            onClick={() => onTap(p)}
            disabled={!p.available}
            className={cn("flex min-h-16 w-full items-center gap-3 rounded-xl border bg-card p-3 text-left shadow-sm transition active:scale-[0.99] disabled:opacity-50", counts.get(p.id) && "border-primary")}
          >
            <span className="min-w-0 flex-1">
              <span className="block leading-snug font-semibold">{p.name}</span>
              <span className="block text-sm text-muted-foreground">
                {formatMoney(p.price, locale)}
                {p.modifierGroups.length > 0 && ` · ${t("hasOptions")}`}
                {!p.available && ` · ${t("soldOut")}`}
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
  );
}

/** Options, quantity and notes of one product, with the same rules as the customer menu. */
export function OptionsSheet({ product, onClose, onAdd }: { product: MenuProduct | null; onClose: () => void; onAdd: (item: NewCartItem) => void }) {
  return (
    <Sheet open={!!product} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="bottom" className="max-h-[92dvh] gap-0 overflow-hidden p-0">{product && <OptionsForm key={product.id} product={product} onClose={onClose} onAdd={onAdd} />}</SheetContent>
    </Sheet>
  );
}

function OptionsForm({ product, onClose, onAdd }: { product: MenuProduct; onClose: () => void; onAdd: (item: NewCartItem) => void }) {
  const t = useTranslations("pos");
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
              <Badge variant={group.minSelect > 0 ? "accent" : "muted"}>{group.minSelect > 0 ? t("required") : t("optional")}</Badge>
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
          {t("notes")}
        </label>
        <Textarea id="line-notes" rows={2} maxLength={200} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={t("notesPlaceholder")} />
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
          {t("add", { price: formatMoney({ amount: fromCents(unit * quantity), currency: product.price.currency }, locale) })}
        </Button>
      </SheetFooter>
    </>
  );
}

/** Case- and diacritics-insensitive search ("ciorba" finds "Ciorbă"). */
export function normalize(s: string) {
  return s.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase().trim();
}
