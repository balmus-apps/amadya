import { getActivityFilters, getRestaurantSettings, listActivity, unwrap, type ActivityApp, type ActivityEntry, type ActivityOutcome, type Role } from "@amadya/api-client";
import { Badge, Button, Input, NativeSelect, Sheet, SheetContent, SheetHeader, SheetTitle, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@amadya/ui";
import { useInfiniteQuery } from "@tanstack/react-query";
import { useNavigate, useSearch } from "@tanstack/react-router";
import { RefreshCwIcon, XIcon } from "lucide-react";
import { useState } from "react";
import { useStore } from "zustand";
import { Empty, PageHeader } from "@/components/kit";
import { useApi } from "@/lib/data";
import { formatDateTime } from "@/lib/format";
import { useLocale, useT } from "@/lib/i18n";
import { session } from "@/lib/session";

export type ActivitySearch = { actorId?: string; role?: Role; app?: ActivityApp; action?: string; outcome?: ActivityOutcome; from?: string; to?: string };

const apps: ActivityApp[] = ["ADMIN", "WAITER", "KITCHEN", "CASHIER", "OTHER"];
const outcomes: ActivityOutcome[] = ["OK", "DENIED", "FAILED"];

export function validateActivitySearch(s: Record<string, unknown>): ActivitySearch {
  const str = (v: unknown) => (typeof v === "string" && v ? v : undefined);
  return { actorId: str(s.actorId), role: str(s.role) as Role | undefined, app: str(s.app) as ActivityApp | undefined, action: str(s.action), outcome: str(s.outcome) as ActivityOutcome | undefined, from: str(s.from), to: str(s.to) };
}

/** "updateProduct" -> "Update product", for actions without a translation yet. */
function humanize(action: string) {
  const words = action.replace(/([A-Z])/g, " $1").toLowerCase().trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function useActionLabel() {
  const t = useT();
  return (action: string) => (t.has(`activity.actions.${action}`) ? t(`activity.actions.${action}`) : humanize(action));
}

export function ActivityPage() {
  const t = useT();
  const { locale } = useLocale();
  const actionLabel = useActionLabel();
  const search = useSearch({ strict: false }) as ActivitySearch;
  const navigate = useNavigate();
  const isAdmin = useStore(session, (s) => s.claims?.roles.includes("ADMIN"));
  const [selected, setSelected] = useState<ActivityEntry | null>(null);
  const set = (patch: Partial<ActivitySearch>) => navigate({ to: ".", search: { ...search, ...patch } });

  const { data: filters } = useApi(["activity-filters"], () => getActivityFilters());
  const { data: settings } = useApi(["settings"], () => getRestaurantSettings());
  const roles: Role[] = isAdmin ? ["ADMIN", "MANAGER", "CASHIER", "WAITER", "KITCHEN"] : ["CASHIER", "WAITER", "KITCHEN"];

  const log = useInfiniteQuery({
    queryKey: ["activity", search, locale],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => unwrap(listActivity({ query: { ...search, before: pageParam, limit: 100 } })),
    getNextPageParam: (page) => page.nextCursor,
  });
  const entries = log.data?.pages.flatMap((p) => p.items) ?? [];
  const filtered = Object.values(search).some(Boolean);

  return (
    <>
      <PageHeader
        title={t("activity.title")}
        description={t(isAdmin ? "activity.descriptionAdmin" : "activity.descriptionManager", { days: settings?.activityRetentionDays ?? 30 })}
        actions={
          <Button variant="outline" onClick={() => log.refetch()} disabled={log.isFetching}>
            <RefreshCwIcon className={log.isFetching ? "animate-spin" : undefined} /> {t("activity.refresh")}
          </Button>
        }
      />
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <NativeSelect className="w-48" value={search.actorId ?? ""} onChange={(e) => set({ actorId: e.target.value || undefined })} aria-label={t("activity.who")}>
          <option value="">{t("activity.everyone")}</option>
          {filters?.actors.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </NativeSelect>
        <NativeSelect className="w-40" value={search.role ?? ""} onChange={(e) => set({ role: (e.target.value || undefined) as Role | undefined })} aria-label={t("users.roles")}>
          <option value="">{t("activity.allRoles")}</option>
          {roles.map((r) => (
            <option key={r} value={r}>
              {t(`users.roleNames.${r}`)}
            </option>
          ))}
        </NativeSelect>
        <NativeSelect className="w-36" value={search.app ?? ""} onChange={(e) => set({ app: (e.target.value || undefined) as ActivityApp | undefined })} aria-label={t("activity.app")}>
          <option value="">{t("activity.allApps")}</option>
          {apps.map((a) => (
            <option key={a} value={a}>
              {t(`activity.apps.${a}`)}
            </option>
          ))}
        </NativeSelect>
        <NativeSelect className="w-52" value={search.action ?? ""} onChange={(e) => set({ action: e.target.value || undefined })} aria-label={t("activity.action")}>
          <option value="">{t("activity.allActions")}</option>
          {filters?.actions
            .map((a) => ({ a, label: actionLabel(a) }))
            .sort((x, y) => x.label.localeCompare(y.label, locale))
            .map(({ a, label }) => (
              <option key={a} value={a}>
                {label}
              </option>
            ))}
        </NativeSelect>
        <NativeSelect className="w-36" value={search.outcome ?? ""} onChange={(e) => set({ outcome: (e.target.value || undefined) as ActivityOutcome | undefined })} aria-label={t("activity.result")}>
          <option value="">{t("activity.allResults")}</option>
          {outcomes.map((o) => (
            <option key={o} value={o}>
              {t(`activity.outcomes.${o}`)}
            </option>
          ))}
        </NativeSelect>
        <Input type="date" className="w-40" value={search.from ?? ""} max={search.to} onChange={(e) => set({ from: e.target.value || undefined })} aria-label={t("common.from")} />
        <Input type="date" className="w-40" value={search.to ?? ""} min={search.from} onChange={(e) => set({ to: e.target.value || undefined })} aria-label={t("common.to")} />
        {filtered && (
          <Button variant="ghost" onClick={() => navigate({ to: ".", search: {} })}>
            <XIcon /> {t("activity.clear")}
          </Button>
        )}
      </div>

      {entries.length === 0 ? (
        <Empty loading={log.isLoading}>{t("activity.empty")}</Empty>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("activity.when")}</TableHead>
                <TableHead>{t("activity.who")}</TableHead>
                <TableHead>{t("activity.app")}</TableHead>
                <TableHead>{t("activity.action")}</TableHead>
                <TableHead>{t("activity.target")}</TableHead>
                <TableHead>{t("activity.result")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {entries.map((e) => (
                <TableRow key={e.id} className="cursor-pointer" onClick={() => setSelected(e)}>
                  <TableCell className="whitespace-nowrap tabular-nums">{formatDateTime(e.at, locale)}</TableCell>
                  <TableCell>
                    <span className="font-medium">{e.actor.name}</span>
                    <span className="block text-xs text-muted-foreground">{e.actor.roles.map((r) => t(`users.roleNames.${r}`)).join(", ")}</span>
                  </TableCell>
                  <TableCell>{t(`activity.apps.${e.app}`)}</TableCell>
                  <TableCell>{actionLabel(e.action)}</TableCell>
                  <TableCell className="max-w-72">
                    <span className="block truncate">{e.target ?? "—"}</span>
                    {e.detail && <span className="block truncate text-xs text-muted-foreground">{e.detail}</span>}
                  </TableCell>
                  <TableCell>
                    <OutcomeBadge outcome={e.outcome} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {log.hasNextPage && (
            <div className="mt-4 flex justify-center">
              <Button variant="outline" onClick={() => log.fetchNextPage()} disabled={log.isFetchingNextPage}>
                {t("activity.more")}
              </Button>
            </div>
          )}
        </>
      )}
      <EntrySheet entry={selected} onClose={() => setSelected(null)} />
    </>
  );
}

function OutcomeBadge({ outcome }: { outcome: ActivityOutcome }) {
  const t = useT();
  const variant = outcome === "OK" ? "success" : outcome === "DENIED" ? "default" : "accent";
  return <Badge variant={variant}>{t(`activity.outcomes.${outcome}`)}</Badge>;
}

function EntrySheet({ entry, onClose }: { entry: ActivityEntry | null; onClose: () => void }) {
  const t = useT();
  const { locale } = useLocale();
  const actionLabel = useActionLabel();
  if (!entry) return <Sheet open={false} />;
  const rows: [string, string | undefined][] = [
    [t("activity.when"), formatDateTime(entry.at, locale)],
    [t("activity.who"), `${entry.actor.name} (${entry.actor.roles.map((r) => t(`users.roleNames.${r}`)).join(", ") || "—"})`],
    [t("activity.app"), t(`activity.apps.${entry.app}`)],
    [t("activity.target"), entry.target],
    [t("activity.detail"), entry.detail],
    [t("activity.error"), entry.error],
    [t("activity.request"), `${entry.method} ${entry.path}`],
    [t("activity.status"), String(entry.status)],
    [t("activity.recordId"), entry.targetId],
    [t("activity.ip"), entry.ip],
  ];
  return (
    <Sheet open onOpenChange={(o) => !o && onClose()}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            {actionLabel(entry.action)} <OutcomeBadge outcome={entry.outcome} />
          </SheetTitle>
        </SheetHeader>
        <dl className="space-y-3 overflow-y-auto px-5 pb-5 text-sm">
          {rows
            .filter(([, value]) => value)
            .map(([label, value]) => (
              <div key={label}>
                <dt className="text-xs font-semibold text-muted-foreground uppercase">{label}</dt>
                <dd className="break-all">{value}</dd>
              </div>
            ))}
        </dl>
      </SheetContent>
    </Sheet>
  );
}
