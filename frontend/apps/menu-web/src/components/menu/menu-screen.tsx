"use client";

import type { Menu, MenuProduct } from "@amadya/api-client";
import { formatMoney, type AppLocale } from "@amadya/i18n";
import { Badge, Button, cn, toast } from "@amadya/ui";
import { PlusIcon, StoreIcon } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useEffect, useMemo, useRef, useState } from "react";
import { useCart } from "@/lib/cart";
import { useSettings } from "../providers";
import { CartBar } from "./cart";
import { ProductArt } from "./product-art";
import { ProductSheet } from "./product-sheet";

export function MenuScreen({ menu }: { menu: Menu }) {
  const t = useTranslations("menu");
  const locale = useLocale() as AppLocale;
  const settings = useSettings();
  const add = useCart((s) => s.add);
  const [selected, setSelected] = useState<MenuProduct | null>(null);
  const [activeCategory, setActiveCategory] = useState(menu.categories[0]?.id);
  const navRef = useRef<HTMLDivElement>(null);
  const products = useMemo(() => new Map(menu.categories.flatMap((c) => c.products).map((p) => [p.id, p])), [menu]);
  const orderingOn = settings.features.takeaway;

  // Highlight the category currently on screen.
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0];
        if (visible) setActiveCategory(visible.target.id.replace("cat-", ""));
      },
      { rootMargin: "-140px 0px -60% 0px" },
    );
    menu.categories.forEach((c) => {
      const el = document.getElementById(`cat-${c.id}`);
      if (el) observer.observe(el);
    });
    return () => observer.disconnect();
  }, [menu]);

  useEffect(() => {
    navRef.current?.querySelector(`[data-cat="${activeCategory}"]`)?.scrollIntoView({ inline: "center", block: "nearest", behavior: "smooth" });
  }, [activeCategory]);

  function quickAdd(product: MenuProduct) {
    add({ productId: product.id, name: product.name, unitPrice: product.price.amount, currency: product.price.currency, quantity: 1, options: [] });
    toast.success(product.name, { description: formatMoney(product.price, locale), duration: 1500 });
  }

  if (menu.categories.length === 0) {
    return <p className="py-24 text-center text-muted-foreground">{t("empty")}</p>;
  }

  return (
    <>
      {!orderingOn && (
        <div className="mt-4 flex items-center gap-3 rounded-xl border border-accent/40 bg-accent/10 p-4 text-sm">
          <StoreIcon className="size-5 shrink-0 text-accent" /> {t("takeawayOff")}
        </div>
      )}

      {menu.promotions.length > 0 && (
        <section aria-label="Promo" className="-mx-4 mt-4 flex snap-x snap-mandatory scroll-px-4 gap-3 overflow-x-auto px-4 pb-2 [scrollbar-width:none]">
          {menu.promotions.map((promo, i) => {
            const product = promo.productId ? products.get(promo.productId) : undefined;
            return (
              <button
                key={promo.id}
                type="button"
                onClick={() => product && setSelected(product)}
                disabled={!product}
                className={cn(
                  "relative flex min-h-36 w-[85%] max-w-md shrink-0 snap-start flex-col justify-end overflow-hidden rounded-2xl p-5 text-left shadow-sm transition active:scale-[0.99] sm:w-[48%]",
                  i % 2 === 0 ? "bg-foreground text-background" : "bg-primary text-primary-foreground",
                )}
              >
                {promo.imageUrl && (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={promo.imageUrl} alt="" className="absolute inset-0 size-full object-cover opacity-40" />
                )}
                {promo.badge && (
                  <span className="absolute top-4 right-4 rotate-6 rounded-xl bg-accent px-3 py-1.5 font-heading text-lg font-extrabold text-accent-foreground shadow-md">
                    {promo.badge}
                  </span>
                )}
                <span className="relative font-heading text-2xl leading-tight font-extrabold">{promo.title}</span>
                {promo.subtitle && <span className="relative mt-1 text-sm opacity-85">{promo.subtitle}</span>}
              </button>
            );
          })}
        </section>
      )}

      <div ref={navRef} className="sticky top-16 z-30 -mx-4 mt-2 flex gap-2 overflow-x-auto border-b bg-background/95 px-4 py-3 backdrop-blur [scrollbar-width:none]">
        {menu.categories.map((c) => (
          <a
            key={c.id}
            data-cat={c.id}
            href={`#cat-${c.id}`}
            className={cn(
              "shrink-0 rounded-full border px-4 py-2 text-sm font-semibold transition",
              activeCategory === c.id ? "border-primary bg-primary text-primary-foreground" : "bg-card text-muted-foreground hover:text-foreground",
            )}
          >
            {c.name}
          </a>
        ))}
      </div>

      {menu.categories.map((category) => (
        <section key={category.id} id={`cat-${category.id}`} className="pt-6">
          <h2 className="mb-3 text-2xl font-extrabold">{category.name}</h2>
          <ul className="grid gap-3 sm:grid-cols-2">
            {category.products.map((product) => (
              <li key={product.id}>
                <div
                  role="button"
                  tabIndex={0}
                  onClick={() => product.available && setSelected(product)}
                  onKeyDown={(e) => e.key === "Enter" && product.available && setSelected(product)}
                  aria-disabled={!product.available}
                  className={cn(
                    "group flex h-full cursor-pointer gap-3 overflow-hidden rounded-2xl border bg-card p-3 transition hover:shadow-md",
                    !product.available && "cursor-not-allowed opacity-60",
                  )}
                >
                  <div className="min-w-0 flex-1 py-1">
                    <h3 className="font-heading text-base leading-snug font-bold">{product.name}</h3>
                    {product.description && <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{product.description}</p>}
                    <div className="mt-2 flex items-center gap-2">
                      <span className="font-bold">{formatMoney(product.price, locale)}</span>
                      {!product.available && <Badge variant="muted">{t("soldOut")}</Badge>}
                    </div>
                  </div>
                  <div className="relative shrink-0">
                    <ProductArt name={product.name} imageUrl={product.imageUrl} className="size-28 rounded-xl" />
                    {product.available && orderingOn && (
                      <Button
                        size="icon-sm"
                        className="absolute -right-1 -bottom-1 rounded-full shadow-md ring-4 ring-card"
                        aria-label={`${t("add")} ${product.name}`}
                        onClick={(e) => {
                          e.stopPropagation();
                          if (product.modifierGroups.length === 0) quickAdd(product);
                          else setSelected(product);
                        }}
                      >
                        <PlusIcon />
                      </Button>
                    )}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </section>
      ))}

      <ProductSheet product={selected} onClose={() => setSelected(null)} canOrder={orderingOn} />
      <CartBar />
    </>
  );
}
