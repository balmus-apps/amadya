import type { TableActivity } from "@amadya/api-client";
import { formatMoney, type AppLocale } from "@amadya/i18n";

/** "2 × Espresso, 1 × Limonadă" */
export function itemsText(items: TableActivity["items"]): string {
  return (items ?? []).map((i) => `${i.quantity} × ${i.name}`).join(", ");
}

type Translate = (key: string, values?: Record<string, string | number>) => string;

/** One line of the table's log, e.g. "Robert a comandat 2 × Cafea, 1 × Steak" or "Mircea a plătit 28,00 lei pentru 2 × Înghețată". */
export function activityText(a: TableActivity, t: Translate, locale: AppLocale): string {
  const actor = a.actor ?? t("activity.someone");
  const items = itemsText(a.items);
  switch (a.kind) {
    case "JOINED":
      return t("activity.joined", { actor });
    case "REMOVED":
      return t("activity.removed", { actor });
    case "ORDERED":
      return a.actorKind === "WAITER" ? t("activity.orderedByWaiter", { actor, items }) : t("activity.ordered", { actor, items });
    case "ROUND_CONFIRMED":
      return a.automatic ? t("activity.roundAuto", { items }) : t("activity.roundConfirmed", { actor, items });
    case "ROUND_REJECTED":
      return t("activity.roundRejected", { items, reason: a.reason ? ` (${a.reason})` : "" });
    case "TABLE_CONFIRMED":
      return a.automatic ? t("activity.tableAuto") : t("activity.tableConfirmed", { actor });
    case "PAID": {
      const amount = a.amount ? formatMoney(a.amount, locale) : "";
      const how = t(`activity.method.${a.method ?? "ONLINE"}`);
      return items ? t("activity.paidItems", { actor, amount, items, how }) : t("activity.paid", { actor, amount, how });
    }
  }
}
