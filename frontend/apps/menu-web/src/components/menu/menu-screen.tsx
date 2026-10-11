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
import { PromoStrip } from "./promo-strip";

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

  // While a tapped category is being scrolled to, the highlight stays on it instead of following the sections passing by.
  const jumpingTo = useRef<string | null>(null);

  // Highlight the category whose section is under the sticky bar. A passive, frame-throttled scroll listener:
  // it never scrolls anything itself, so it cannot fight the finger or a jump in progress.
  useEffect(() => {
    let frame = 0;
    const update = () => {
      frame = 0;
      if (jumpingTo.current) return;
      const navBottom = navRef.current?.getBoundingClientRect().bottom ?? 0;
      const atEnd = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2;
      let current = menu.categories[0]?.id;
      for (const c of menu.categories) {
        const top = document.getElementById(`cat-${c.id}`)?.getBoundingClientRect().top;
        if (top !== undefined && top <= navBottom + 8) current = c.id;
      }
      setActiveCategory(atEnd ? menu.categories.at(-1)?.id : current);
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      cancelAnimationFrame(frame);
    };
  }, [menu]);

  // Keep the highlighted chip in view by scrolling the chip bar sideways only. scrollIntoView would also scroll
  // the page, which interrupts the user's scroll and cancels a jump.
  useEffect(() => {
    const nav = navRef.current;
    const chip = nav?.querySelector<HTMLElement>(`[data-cat="${activeCategory}"]`);
    if (!nav || !chip) return;
    nav.scrollTo({ left: chip.offsetLeft - (nav.clientWidth - chip.offsetWidth) / 2, behavior: scrollBehavior() });
  }, [activeCategory]);

  function jumpTo(categoryId: string) {
    const section = document.getElementById(`cat-${categoryId}`);
    const nav = navRef.current;
    if (!section || !nav) return;
    jumpingTo.current = categoryId;
    setActiveCategory(categoryId);
    // Where the bar's bottom will be once stuck; before the page has scrolled past the promos it still sits lower.
    const stuckBottom = parseFloat(getComputedStyle(nav).top) + nav.offsetHeight;
    const top = section.getBoundingClientRect().top + window.scrollY - stuckBottom;
    window.scrollTo({ top, behavior: scrollBehavior() });
    history.replaceState(null, "", `#cat-${categoryId}`);
    // scrollend is not available everywhere; the timeout releases the highlight in any case.
    const release = () => {
      jumpingTo.current = null;
      window.removeEventListener("scrollend", release);
      window.dispatchEvent(new Event("scroll"));
    };
    window.addEventListener("scrollend", release, { once: true });
    setTimeout(release, 1200);
  }

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

      {menu.promotions.length > 0 && <PromoStrip promotions={menu.promotions} products={products} onSelect={setSelected} />}

      <nav ref={navRef} aria-label={t("categories")} className="sticky top-16 z-30 -mx-4 mt-2 flex gap-2 overflow-x-auto border-b bg-background px-4 py-3 [scrollbar-width:none]">
        {menu.categories.map((c) => (
          <a
            key={c.id}
            data-cat={c.id}
            href={`#cat-${c.id}`}
            onClick={(e) => {
              e.preventDefault();
              jumpTo(c.id);
            }}
            aria-current={activeCategory === c.id ? "true" : undefined}
            className={cn(
              "shrink-0 rounded-full border px-4 py-2 text-sm font-semibold transition",
              activeCategory === c.id ? "border-primary bg-primary text-primary-foreground" : "bg-card text-muted-foreground hover:text-foreground",
            )}
          >
            {c.name}
          </a>
        ))}
      </nav>

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

function scrollBehavior(): ScrollBehavior {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
}
