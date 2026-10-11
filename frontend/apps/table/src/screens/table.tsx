import { ownerDecideGuest, unwrap, type GuestView } from "@amadya/api-client";
import { formatTime, type AppLocale } from "@amadya/i18n";
import { Button, cn, toast } from "@amadya/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ClipboardListIcon, ReceiptTextIcon, ShieldCheckIcon, UserPlusIcon, UtensilsIcon } from "lucide-react";
import { useState } from "react";
import { useLocale, useTranslations } from "use-intl";
import type { SetLocale } from "../App";
import { activityText } from "../lib/activity";
import { guestHeaders, keys, problemMessage, tableCode } from "../lib/guest";
import { BillTab } from "./bill";
import { MenuTab } from "./menu";

type Tab = "menu" | "bill" | "activity";

export function TableScreen({ view, token, setLocale }: { view: GuestView; token: string; setLocale: SetLocale }) {
  const t = useTranslations("table");
  const locale = useLocale() as AppLocale;
  const [tab, setTab] = useState<Tab>(view.rounds.length > 0 || view.pendingRounds.length > 0 ? "bill" : "menu");

  return (
    <div className="mx-auto flex min-h-dvh max-w-xl flex-col">
      <header className="sticky top-0 z-20 border-b bg-background/95 px-4 pt-[max(0.75rem,env(safe-area-inset-top))] pb-3 backdrop-blur">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-xl font-black">
              {t("title", { label: view.tableLabel })}
              {view.areaName && <span className="font-medium text-muted-foreground"> · {view.areaName}</span>}
            </h1>
            <p className="truncate text-xs text-muted-foreground">{view.me.name}</p>
          </div>
          <div className="flex rounded-full border p-0.5 text-xs font-semibold" aria-label={t("language")}>
            {(["ro", "en"] as const).map((l) => (
              <button key={l} onClick={() => setLocale(l)} aria-pressed={l === locale} className={cn("rounded-full px-2.5 py-1 uppercase", l === locale ? "bg-primary text-primary-foreground" : "text-muted-foreground")}>
                {l}
              </button>
            ))}
          </div>
        </div>
        {view.status === "AWAITING_CONFIRMATION" && view.confirmDeadline && (
          <p role="status" className="mt-3 flex gap-2 rounded-xl bg-[var(--status-pending)] p-3 text-sm text-[var(--status-pending-strong)]">
            <ShieldCheckIcon className="size-4 shrink-0" /> {t("awaiting", { time: formatTime(view.confirmDeadline, locale) })}
          </p>
        )}
        <JoinRequests view={view} token={token} />
      </header>

      <main className="flex-1 p-4 pb-28">
        {tab === "menu" && <MenuTab view={view} token={token} onSent={() => setTab("bill")} />}
        {tab === "bill" && <BillTab view={view} token={token} />}
        {tab === "activity" && <Activity view={view} />}
      </main>

      <nav aria-label="Tabs" className="fixed inset-x-0 bottom-0 z-20 border-t bg-background/95 pb-[env(safe-area-inset-bottom)] backdrop-blur">
        <div className="mx-auto grid max-w-xl grid-cols-3">
          {(
            [
              ["menu", t("tabs.menu"), <UtensilsIcon key="m" />],
              ["bill", t("tabs.bill"), <ReceiptTextIcon key="b" />],
              ["activity", t("tabs.activity"), <ClipboardListIcon key="a" />],
            ] as const
          ).map(([id, label, icon]) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              aria-current={tab === id ? "page" : undefined}
              className={cn("relative flex flex-col items-center gap-0.5 py-2.5 text-xs font-semibold [&_svg]:size-5", tab === id ? "text-primary" : "text-muted-foreground")}
            >
              {icon}
              {label}
              {id === "bill" && view.pendingRounds.some((r) => r.status === "PENDING") && <span className="absolute top-2 right-[calc(50%-1.1rem)] size-2 rounded-full bg-amber-500" />}
            </button>
          ))}
        </div>
      </nav>
    </div>
  );
}

/** The owner lets people in (the waiter can too, from the waiter app). */
function JoinRequests({ view, token }: { view: GuestView; token: string }) {
  const t = useTranslations("table");
  const client = useQueryClient();
  const decide = useMutation({
    mutationFn: ({ id, approve }: { id: string; approve: boolean }) =>
      unwrap(ownerDecideGuest({ path: { code: tableCode, guestId: id, action: approve ? "approve" : "reject" }, headers: guestHeaders(token) })),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.view }),
    onError: (e) => toast.error(problemMessage(e)),
  });
  if (view.joinRequests.length === 0) return null;
  return (
    <ul className="mt-3 space-y-2">
      {view.joinRequests.map((g) => (
        <li key={g.id} className="flex items-center gap-2 rounded-xl border bg-card p-2 pl-3 text-sm">
          <UserPlusIcon className="size-4 shrink-0 text-primary" />
          <span className="min-w-0 flex-1 font-semibold">{t("requests.wants", { name: g.name })}</span>
          <Button size="sm" variant="ghost" disabled={decide.isPending} onClick={() => decide.mutate({ id: g.id, approve: false })}>
            {t("requests.reject")}
          </Button>
          <Button size="sm" disabled={decide.isPending} onClick={() => decide.mutate({ id: g.id, approve: true })}>
            {t("requests.approve")}
          </Button>
        </li>
      ))}
    </ul>
  );
}

function Activity({ view }: { view: GuestView }) {
  const t = useTranslations("table");
  const locale = useLocale() as AppLocale;
  if (view.activity.length === 0) return <p className="py-12 text-center text-muted-foreground">{t("activity.empty")}</p>;
  return (
    <ol className="space-y-3">
      {[...view.activity].reverse().map((a, i) => (
        <li key={i} className="flex gap-3 text-sm">
          <time className="w-12 shrink-0 pt-0.5 text-xs text-muted-foreground tabular-nums">{formatTime(a.at, locale)}</time>
          <span className={cn(a.kind === "PAID" && "font-semibold", a.kind === "ROUND_REJECTED" && "text-destructive")}>
            {activityText(a, (key, values) => t(key as never, values as never), locale)}
          </span>
        </li>
      ))}
    </ol>
  );
}
