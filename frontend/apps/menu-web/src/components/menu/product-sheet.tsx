"use client";

import type { MenuModifierGroup, MenuProduct } from "@amadya/api-client";
import { formatMoney, type AppLocale } from "@amadya/i18n";
import {
  Badge,
  Button,
  Checkbox,
  cn,
  Label,
  QuantityStepper,
  RadioGroup,
  RadioGroupItem,
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetTitle,
  Textarea,
  toast,
} from "@amadya/ui";
import { ClockIcon } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { chosenOptions, fromCents, initialSelection, missingGroups, toCents, toggleOption, useCart, type Selection } from "@/lib/cart";
import { ProductArt } from "./product-art";

export function ProductSheet({ product, onClose, canOrder }: { product: MenuProduct | null; onClose: () => void; canOrder: boolean }) {
  return (
    <Sheet open={!!product} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="bottom" className="overflow-hidden p-0">
        {product && <ProductForm key={product.id} product={product} onDone={onClose} canOrder={canOrder} />}
      </SheetContent>
    </Sheet>
  );
}

function ProductForm({ product, onDone, canOrder }: { product: MenuProduct; onDone: () => void; canOrder: boolean }) {
  const t = useTranslations("menu");
  const locale = useLocale() as AppLocale;
  const add = useCart((s) => s.add);
  const [selected, setSelected] = useState<Selection>(() => initialSelection(product.modifierGroups));
  const [quantity, setQuantity] = useState(1);
  const [notes, setNotes] = useState("");

  const chosen = useMemo(() => chosenOptions(product.modifierGroups, selected), [product, selected]);
  const missing = missingGroups(product.modifierGroups, selected);
  const unitCents = toCents(product.price.amount) + chosen.reduce((sum, o) => sum + toCents(o.priceDelta.amount), 0);
  const total = { amount: fromCents(unitCents * quantity), currency: product.price.currency };

  function toggle(group: MenuModifierGroup, optionId: string, checked: boolean) {
    setSelected((prev) => toggleOption(prev, group, optionId, checked));
  }

  function submit() {
    add({
      productId: product.id,
      name: product.name,
      unitPrice: product.price.amount,
      currency: product.price.currency,
      quantity,
      options: chosen.map((o) => ({ id: o.id, name: o.name, priceDelta: o.priceDelta.amount })),
      notes: notes.trim() || undefined,
    });
    toast.success(product.name, { description: `${quantity} × ${formatMoney({ amount: fromCents(unitCents), currency: total.currency }, locale)}`, duration: 1500 });
    onDone();
  }

  return (
    <>
      <div className="overflow-y-auto">
        <ProductArt name={product.name} imageUrl={product.imageUrl} className="h-44 w-full sm:h-56" iconClassName="size-16" />
        <div className="space-y-2 p-5">
          <SheetTitle className="text-2xl">{product.name}</SheetTitle>
          {product.description && <SheetDescription className="text-base">{product.description}</SheetDescription>}
          <div className="flex flex-wrap items-center gap-2 pt-1 text-sm">
            <span className="text-lg font-bold">{formatMoney(product.price, locale)}</span>
            {!!product.prepTimeSec && (
              <Badge variant="muted">
                <ClockIcon /> {t("prepTime", { minutes: Math.max(1, Math.round(product.prepTimeSec / 60)) })}
              </Badge>
            )}
          </div>
          {product.allergens.length > 0 && <p className="text-xs text-muted-foreground">{t("allergens", { list: product.allergens.join(", ") })}</p>}
        </div>

        {product.modifierGroups.map((group) => (
          <fieldset key={group.id} className="border-t px-5 py-4">
            <legend className="sr-only">{group.name}</legend>
            <div className="mb-3 flex items-baseline justify-between gap-2">
              <span className="font-heading text-lg font-bold">{group.name}</span>
              <Badge variant={group.minSelect > 0 ? "accent" : "muted"}>
                {group.minSelect > 0
                  ? t("required")
                  : group.maxSelect === 1
                    ? t("optional")
                    : t("chooseUpTo", { count: group.maxSelect })}
              </Badge>
            </div>
            {group.maxSelect === 1 ? (
              <RadioGroup value={selected[group.id]?.[0] ?? ""} onValueChange={(v) => toggle(group, v, true)}>
                {group.options.map((option) => (
                  <OptionRow key={option.id} id={`${group.id}-${option.id}`} name={option.name} delta={option.priceDelta} locale={locale}>
                    <RadioGroupItem id={`${group.id}-${option.id}`} value={option.id} />
                  </OptionRow>
                ))}
              </RadioGroup>
            ) : (
              <div className="grid gap-2">
                {group.options.map((option) => {
                  const checked = selected[group.id]?.includes(option.id) ?? false;
                  const full = !checked && (selected[group.id]?.length ?? 0) >= group.maxSelect;
                  return (
                    <OptionRow key={option.id} id={`${group.id}-${option.id}`} name={option.name} delta={option.priceDelta} locale={locale} disabled={full}>
                      <Checkbox id={`${group.id}-${option.id}`} checked={checked} disabled={full} onCheckedChange={(c) => toggle(group, option.id, c === true)} />
                    </OptionRow>
                  );
                })}
              </div>
            )}
          </fieldset>
        ))}

        <div className="space-y-2 border-t px-5 py-4">
          <Label htmlFor="notes">{t("notes")}</Label>
          <Textarea id="notes" maxLength={200} rows={2} placeholder={t("notesPlaceholder")} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </div>
      </div>

      {canOrder && (
        <SheetFooter className="flex-row items-center gap-3 bg-card pb-[max(1rem,env(safe-area-inset-bottom))]">
          <QuantityStepper value={quantity} onChange={setQuantity} decreaseLabel="−" increaseLabel="+" />
          <Button size="lg" className="min-w-0 flex-1 overflow-hidden" disabled={missing.length > 0} onClick={submit}>
            {t("addToCart", { price: formatMoney(total, locale) })}
          </Button>
        </SheetFooter>
      )}
    </>
  );
}

function OptionRow({
  id,
  name,
  delta,
  locale,
  disabled,
  children,
}: {
  id: string;
  name: string;
  delta: { amount: string; currency: string };
  locale: AppLocale;
  disabled?: boolean;
  children: React.ReactNode;
}) {
  return (
    <label
      htmlFor={id}
      className={cn("flex min-h-12 cursor-pointer items-center gap-3 rounded-xl border bg-card px-3 py-2 transition has-[[data-state=checked]]:border-primary has-[[data-state=checked]]:bg-primary/5", disabled && "cursor-not-allowed opacity-50")}
    >
      {children}
      <span className="flex-1 font-medium">{name}</span>
      {Number(delta.amount) !== 0 && <span className="text-sm text-muted-foreground">+{formatMoney(delta, locale)}</span>}
    </label>
  );
}
