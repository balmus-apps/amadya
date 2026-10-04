"use client";

import { Badge, Button, cn } from "@amadya/ui";
import { ClockIcon, ReceiptTextIcon } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { Link, usePathname } from "@/i18n/navigation";
import { isOpenNow, todaysHours } from "@amadya/customer-core";
import { useRecentOrders } from "@/lib/recent-orders";
import { useSettings } from "./providers";

export function Header() {
  const settings = useSettings();
  const t = useTranslations();
  const locale = useLocale();
  const pathname = usePathname();
  const recent = useRecentOrders((state) => state.orders);
  const hasOrders = useHydrated() && recent.length > 0;
  const open = isOpenNow(settings.openingHours);
  const today = todaysHours(settings.openingHours);

  return (
    <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur supports-[backdrop-filter]:bg-background/75">
      <div className="mx-auto flex h-16 max-w-5xl items-center gap-3 px-4">
        <Link href="/" className="flex min-w-0 items-center gap-3">
          {settings.logoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={settings.logoUrl} alt="" className="size-10 rounded-full object-cover" />
          ) : (
            <span className="flex size-10 items-center justify-center rounded-full bg-primary font-heading text-lg font-extrabold text-primary-foreground">
              {settings.name.charAt(0)}
            </span>
          )}
          <span className="min-w-0">
            <span className="block truncate font-heading text-lg leading-tight font-extrabold">{settings.name}</span>
            {settings.openingHours.length > 0 && (
              <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <Badge variant={open ? "success" : "muted"} className="px-1.5 py-0 text-[10px]">
                  {open ? t("common.open") : t("common.closed")}
                </Badge>
                {today && (
                  <span className="flex items-center gap-1">
                    <ClockIcon className="size-3" /> {today.opens}–{today.closes}
                  </span>
                )}
              </span>
            )}
          </span>
        </Link>

        <div className="ml-auto flex items-center gap-1">
          {hasOrders && (
            <Button asChild variant="ghost" size="sm">
              <Link href="/orders" aria-label={t("menu.myOrders")}>
                <ReceiptTextIcon />
                <span className="hidden sm:inline">{t("menu.myOrders")}</span>
              </Link>
            </Button>
          )}
          <nav aria-label={t("common.language")} className="flex rounded-full border bg-card p-0.5 text-xs font-semibold">
            {settings.locales.map((l) => (
              <Link
                key={l}
                href={pathname}
                locale={l}
                className={cn("rounded-full px-2.5 py-1 uppercase", l === locale ? "bg-primary text-primary-foreground" : "text-muted-foreground")}
              >
                {l}
              </Link>
            ))}
          </nav>
        </div>
      </div>
    </header>
  );
}

/** Avoids hydration mismatches for values read from localStorage. */
export function useHydrated() {
  const [hydrated, setHydrated] = useState(false);
  useEffect(() => setHydrated(true), []);
  return hydrated;
}
