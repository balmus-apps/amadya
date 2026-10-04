"use client";

import { formatMoney, formatTime, type AppLocale } from "@amadya/i18n";
import { ChevronRightIcon } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useRecentOrders } from "@/lib/recent-orders";
import { useHydrated } from "../header";

export function RecentOrders() {
  const t = useTranslations("order");
  const locale = useLocale() as AppLocale;
  const orders = useRecentOrders((s) => s.orders);
  const hydrated = useHydrated();
  if (!hydrated) return null;
  return (
    <div className="mx-auto max-w-xl py-6">
      <h1 className="mb-4 text-3xl font-extrabold">{t("recent")}</h1>
      <ul className="divide-y rounded-2xl border bg-card">
        {orders.map((o) => (
          <li key={o.id}>
            <Link href={`/order/${o.id}?t=${encodeURIComponent(o.token)}`} className="flex items-center gap-4 p-4 hover:bg-muted/50">
              <span className="font-heading text-xl font-extrabold tabular-nums">{o.number}</span>
              <span className="flex-1 text-sm text-muted-foreground">
                {new Date(o.createdAt).toLocaleDateString(locale)} {formatTime(o.createdAt, locale)}
              </span>
              <span className="font-semibold">{formatMoney(o.total, locale)}</span>
              <ChevronRightIcon className="size-4 text-muted-foreground" />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
