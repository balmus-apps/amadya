"use client";

import type { MenuProduct, MenuPromotion } from "@amadya/api-client";
import { cn } from "@amadya/ui";
import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";

/**
 * Horizontally scrolling promo cards. Touch screens swipe; with a mouse there is no horizontal wheel and the
 * scrollbar is hidden, so previous/next buttons appear on hover-capable devices whenever there is more to see.
 */
export function PromoStrip({
  promotions,
  products,
  onSelect,
}: {
  promotions: MenuPromotion[];
  products: Map<string, MenuProduct>;
  onSelect: (product: MenuProduct) => void;
}) {
  const t = useTranslations("menu");
  const stripRef = useRef<HTMLElement>(null);
  const [edges, setEdges] = useState({ start: true, end: true });

  useEffect(() => {
    const strip = stripRef.current;
    if (!strip) return;
    const update = () =>
      setEdges({ start: strip.scrollLeft <= 4, end: strip.scrollLeft + strip.clientWidth >= strip.scrollWidth - 4 });
    update();
    strip.addEventListener("scroll", update, { passive: true });
    const resize = new ResizeObserver(update);
    resize.observe(strip);
    return () => {
      strip.removeEventListener("scroll", update);
      resize.disconnect();
    };
  }, [promotions]);

  function page(direction: 1 | -1) {
    const strip = stripRef.current;
    if (!strip) return;
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    // Snap points (snap-start) settle the cards after the page-sized move.
    strip.scrollBy({ left: direction * strip.clientWidth * 0.9, behavior: reduced ? "auto" : "smooth" });
  }

  return (
    <div className="group/promos relative -mx-4 mt-4">
      <section
        ref={stripRef}
        aria-label={t("promotions")}
        className="flex snap-x snap-mandatory scroll-px-4 gap-3 overflow-x-auto px-4 pb-2 [scrollbar-width:none]"
      >
        {promotions.map((promo, i) => {
          const product = promo.productId ? products.get(promo.productId) : undefined;
          return (
            <button
              key={promo.id}
              type="button"
              onClick={() => product && onSelect(product)}
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

      <StripButton side="left" hidden={edges.start} label={t("previous")} onClick={() => page(-1)}>
        <ChevronLeftIcon />
      </StripButton>
      <StripButton side="right" hidden={edges.end} label={t("next")} onClick={() => page(1)}>
        <ChevronRightIcon />
      </StripButton>
    </div>
  );
}

function StripButton({
  side,
  hidden,
  label,
  onClick,
  children,
}: {
  side: "left" | "right";
  hidden: boolean;
  label: string;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      tabIndex={hidden ? -1 : 0}
      className={cn(
        // Only on devices with a mouse: touch screens swipe.
        "absolute top-[calc(50%-0.25rem)] hidden size-10 -translate-y-1/2 items-center justify-center rounded-full border bg-card text-foreground shadow-md transition hover:bg-muted [@media(hover:hover)]:flex [&_svg]:size-5",
        side === "left" ? "left-2" : "right-2",
        hidden && "pointer-events-none opacity-0",
      )}
    >
      {children}
    </button>
  );
}
