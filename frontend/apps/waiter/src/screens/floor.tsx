import { openTableSession, type FloorArea, type FloorTable } from "@amadya/api-client";
import { formatMoney } from "@amadya/i18n";
import { session, signOut } from "@amadya/staff-auth";
import { Button, cn, Dialog, DialogContent, DialogDescription, DialogTitle, QuantityStepper, Skeleton } from "@amadya/ui";
import { BellRingIcon, LogOutIcon, UsersIcon, Volume2Icon, VolumeXIcon, WifiIcon, WifiOffIcon } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useLocale, useTranslations } from "use-intl";
import { useStore } from "zustand";
import type { Prefs } from "../App";
import { sinceLabel, tableState } from "../lib/bill";
import { useFloor, useSessionMutation } from "../lib/data";
import { go } from "../lib/route";

const AREA_KEY = "amadya-waiter-area";
const MINE_KEY = "amadya-waiter-mine";

export function FloorScreen({ prefs }: { prefs: Prefs }) {
  const t = useTranslations();
  const floor = useFloor();
  const me = useStore(session, (s) => s.claims?.name);
  const [areaId, setAreaId] = useState(() => localStorage.getItem(AREA_KEY) ?? "");
  const [mineOnly, setMineOnly] = useState(() => localStorage.getItem(MINE_KEY) === "1");
  const [seating, setSeating] = useState<FloorTable | null>(null);
  const now = useNow(30_000);

  const areas = floor.data ?? [];
  const area = areas.find((a) => a.id === areaId) ?? areas[0];
  const counts = useMemo(() => summary(areas), [areas]);
  const tables = (area?.tables ?? []).filter((tb) => !mineOnly || !tb.session || tb.session.waiterName === me);

  return (
    <div className="flex min-h-dvh flex-col bg-muted/40">
      <header className="sticky top-0 z-20 border-b bg-background/95 backdrop-blur">
        <div className="flex items-center gap-2 px-4 pt-[max(0.5rem,env(safe-area-inset-top))] pb-2">
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-lg font-extrabold">{t("floor.title")}</h1>
            <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <span className={cn("flex items-center gap-1", prefs.live ? "text-green-700" : "text-amber-700")}>
                {prefs.live ? <WifiIcon className="size-3.5" /> : <WifiOffIcon className="size-3.5" />}
                {prefs.live ? t("live") : t("offline")}
              </span>
              <span aria-hidden>·</span>
              <span className="truncate">{me}</span>
            </p>
          </div>
          <div className="flex rounded-full border p-0.5 text-xs font-semibold" aria-label={t("language")}>
            {(["ro", "en"] as const).map((l) => (
              <button key={l} onClick={() => prefs.setLocale(l)} aria-pressed={l === prefs.locale} className={cn("rounded-full px-2.5 py-1 uppercase", l === prefs.locale ? "bg-primary text-primary-foreground" : "text-muted-foreground")}>
                {l}
              </button>
            ))}
          </div>
          <IconButton label={t("sound")} onClick={() => prefs.setSound(!prefs.sound)}>
            {prefs.sound ? <Volume2Icon /> : <VolumeXIcon />}
          </IconButton>
          <IconButton label={t("logout")} onClick={() => signOut()}>
            <LogOutIcon />
          </IconButton>
        </div>

        <nav aria-label={t("floor.areas")} className="flex gap-2 overflow-x-auto px-4 pb-2 [scrollbar-width:none]">
          {areas.map((a) => (
            <button
              key={a.id}
              onClick={() => (localStorage.setItem(AREA_KEY, a.id), setAreaId(a.id))}
              aria-current={a.id === area?.id ? "page" : undefined}
              className={cn("shrink-0 rounded-full border px-4 py-1.5 text-sm font-semibold", a.id === area?.id ? "border-primary bg-primary text-primary-foreground" : "bg-card text-muted-foreground")}
            >
              {a.name}
              {counts.get(a.id)!.ready > 0 && <span className="ml-1.5 rounded-full bg-green-600 px-1.5 text-xs text-white">{counts.get(a.id)!.ready}</span>}
            </button>
          ))}
          <button
            onClick={() => (localStorage.setItem(MINE_KEY, mineOnly ? "0" : "1"), setMineOnly(!mineOnly))}
            aria-pressed={mineOnly}
            className={cn("ml-auto shrink-0 rounded-full border px-3 py-1.5 text-sm font-semibold", mineOnly ? "border-foreground bg-foreground text-background" : "bg-card text-muted-foreground")}
          >
            {t("floor.mine")}
          </button>
        </nav>
      </header>

      <main className="flex-1 p-3 pb-[max(1rem,env(safe-area-inset-bottom))]">
        {floor.isPending ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-32 rounded-2xl" />
            ))}
          </div>
        ) : areas.length === 0 ? (
          <p className="py-16 text-center text-muted-foreground">{t("floor.noTables")}</p>
        ) : (
          <>
            <Legend />
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
              {tables.map((table) => (
                <li key={table.id}>
                  <TableCard table={table} now={now} onTap={() => (table.session ? go({ name: "table", sessionId: table.session.id }) : setSeating(table))} />
                </li>
              ))}
            </ul>
          </>
        )}
      </main>

      <SeatDialog table={seating} onClose={() => setSeating(null)} />
    </div>
  );
}

function TableCard({ table, now, onTap }: { table: FloorTable; now: number; onTap: () => void }) {
  const t = useTranslations();
  const locale = useLocale() as "ro" | "en";
  const state = tableState(table);
  const s = table.session;
  return (
    <button
      onClick={onTap}
      aria-label={`${t("table.label", { label: table.label })} – ${t(`floor.state.${state}`)}`}
      className={cn(
        "flex h-32 w-full flex-col rounded-2xl border-2 p-3 text-left shadow-sm transition active:scale-[0.98]",
        state === "free" && "border-dashed border-border bg-card",
        state === "occupied" && "border-[var(--status-progress-strong)] bg-[var(--status-occupied)]",
        state === "ready" && "ready-pulse border-[var(--status-done-strong)] bg-[var(--status-done)]",
      )}
    >
      <div className="flex w-full items-start justify-between gap-2">
        <span className="text-3xl leading-none font-black tabular-nums">{table.label}</span>
        {s ? (
          <span className="flex items-center gap-1 text-sm font-semibold">
            <UsersIcon className="size-4" /> {s.guests}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">{t("floor.seats", { count: table.seats })}</span>
        )}
      </div>
      {s ? (
        <div className="mt-auto space-y-0.5 text-sm">
          {s.roundsReady > 0 && (
            <p className="flex items-center gap-1 font-bold text-[var(--status-done-strong)]">
              <BellRingIcon className="size-4" /> {t("floor.readyToServe", { count: s.roundsReady })}
            </p>
          )}
          {s.roundsInKitchen > 0 && <p className="font-semibold text-[var(--status-progress-strong)]">{t("floor.inKitchen", { count: s.roundsInKitchen })}</p>}
          <p className="flex justify-between gap-2 text-xs text-muted-foreground">
            <span>{sinceLabel(s.openedAt, now)}</span>
            <span className="font-semibold text-foreground tabular-nums">{formatMoney(s.remaining, locale)}</span>
          </p>
        </div>
      ) : (
        <p className="mt-auto text-sm font-semibold text-muted-foreground">{t("floor.state.free")}</p>
      )}
    </button>
  );
}

function SeatDialog({ table, onClose }: { table: FloorTable | null; onClose: () => void }) {
  const t = useTranslations();
  const [guests, setGuests] = useState(2);
  useEffect(() => {
    if (table) setGuests(Math.min(2, table.seats));
  }, [table]);
  const open = useSessionMutation((id: string) => openTableSession({ path: { id }, body: { guests } }), (s) => {
    onClose();
    go({ name: "table", sessionId: s.id });
  });
  return (
    <Dialog open={!!table} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogTitle>{table && t("seat.title", { label: table.label })}</DialogTitle>
        <DialogDescription>{t("seat.question")}</DialogDescription>
        <div className="grid grid-cols-4 gap-2">
          {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => (
            <button key={n} onClick={() => setGuests(n)} aria-pressed={guests === n} className={cn("h-12 rounded-xl border text-lg font-bold", guests === n ? "border-primary bg-primary text-primary-foreground" : "bg-card")}>
              {n}
            </button>
          ))}
        </div>
        <div className="flex items-center justify-between gap-3">
          <QuantityStepper value={guests} onChange={(v) => setGuests(Math.max(1, Math.min(50, v)))} decreaseLabel="−" increaseLabel="+" />
          <Button size="lg" className="flex-1" disabled={open.isPending || !table} onClick={() => table && open.mutate(table.id)}>
            {t("seat.open")}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function Legend() {
  const t = useTranslations();
  const items = [
    { key: "free", className: "border-2 border-dashed bg-card" },
    { key: "occupied", className: "border-2 border-[var(--status-progress-strong)] bg-[var(--status-occupied)]" },
    { key: "ready", className: "border-2 border-[var(--status-done-strong)] bg-[var(--status-done)]" },
  ] as const;
  return (
    <ul className="mb-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
      {items.map((i) => (
        <li key={i.key} className="flex items-center gap-1.5">
          <span className={cn("size-3.5 rounded", i.className)} aria-hidden /> {t(`floor.state.${i.key}`)}
        </li>
      ))}
    </ul>
  );
}

function summary(areas: FloorArea[]) {
  return new Map(areas.map((a) => [a.id, { ready: a.tables.filter((tb) => (tb.session?.roundsReady ?? 0) > 0).length }]));
}

export function useNow(intervalMs: number) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

export function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick} aria-label={label} title={label} className="rounded-lg p-2 text-muted-foreground hover:bg-muted hover:text-foreground [&_svg]:size-5">
      {children}
    </button>
  );
}
