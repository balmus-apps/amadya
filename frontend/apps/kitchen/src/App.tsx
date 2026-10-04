import { bumpKitchenTicket, listKitchenStations, login, recallKitchenTicket, startKitchenTicket, unwrap, type KitchenTicket, type Station } from "@amadya/api-client";
import { accessToken, hasAnyRole, hasStoredSession, session, setApiLocale, signIn, signOut } from "@amadya/staff-auth";
import { Button, cn, Input, toast, Toaster } from "@amadya/ui";
import { LogOutIcon, MaximizeIcon, Undo2Icon, Volume2Icon, VolumeXIcon, WifiIcon, WifiOffIcon } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { IntlProvider, useTranslations } from "use-intl";
import { useStore } from "zustand";
import { columns, elapsed, group, isLate, nextAction } from "./board-logic";
import { chime, useKitchenFeed } from "./feed";
import en from "./messages/en.json";
import ro from "./messages/ro.json";

const STATION_KEY = "amadya-kitchen-station";
const LOCALE_KEY = "amadya-kitchen-locale";
const SOUND_KEY = "amadya-kitchen-sound";

export function App() {
  const [locale, setLocale] = useState<"ro" | "en">(() => (localStorage.getItem(LOCALE_KEY) === "en" ? "en" : "ro"));
  setApiLocale(locale);
  const changeLocale = (l: "ro" | "en") => {
    localStorage.setItem(LOCALE_KEY, l);
    setLocale(l);
  };
  return (
    <IntlProvider locale={locale} messages={locale === "en" ? en : ro} timeZone="Europe/Bucharest">
      <Gate locale={locale} setLocale={changeLocale} />
      <Toaster />
    </IntlProvider>
  );
}

function Gate({ locale, setLocale }: { locale: "ro" | "en"; setLocale: (l: "ro" | "en") => void }) {
  const claims = useStore(session, (s) => s.claims);
  const [checking, setChecking] = useState(hasStoredSession());
  const [stationId, setStationId] = useState(() => localStorage.getItem(STATION_KEY) ?? "");

  useEffect(() => {
    if (hasStoredSession()) void accessToken().finally(() => setChecking(false));
  }, []);

  if (checking) return null;
  if (!claims || !hasAnyRole("KITCHEN", "MANAGER", "ADMIN")) return <Login />;
  if (!stationId) return <StationPicker onPick={(id) => (localStorage.setItem(STATION_KEY, id), setStationId(id))} />;
  return (
    <KitchenBoard
      stationId={stationId}
      locale={locale}
      setLocale={setLocale}
      onChangeStation={() => (localStorage.removeItem(STATION_KEY), setStationId(""))}
    />
  );
}

function Login() {
  const t = useTranslations();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string>();
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(undefined);
    try {
      signIn(await unwrap(login({ body: { email, password } })));
      if (!hasAnyRole("KITCHEN", "MANAGER", "ADMIN")) {
        signOut();
        setError(t("login.notAllowed"));
      }
    } catch {
      setError(t("login.error"));
    }
  }
  return (
    <div className="flex min-h-dvh items-center justify-center p-4">
      <form onSubmit={submit} className="w-full max-w-sm space-y-4 rounded-2xl bg-card p-6">
        <h1 className="text-2xl font-extrabold">{t("login.title")}</h1>
        <Input id="email" type="email" placeholder={t("login.email")} autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <Input id="password" type="password" placeholder={t("login.password")} autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error && <p className="text-sm text-destructive">{error}</p>}
        <Button type="submit" size="lg" className="w-full">
          {t("login.submit")}
        </Button>
      </form>
    </div>
  );
}

function StationPicker({ onPick }: { onPick: (id: string) => void }) {
  const t = useTranslations();
  const [stations, setStations] = useState<Station[]>([]);
  useEffect(() => {
    void unwrap(listKitchenStations()).then(setStations);
  }, []);
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-6 text-white">
      <h1 className="text-3xl font-extrabold">{t("station.choose")}</h1>
      <div className="grid w-full max-w-3xl gap-4 sm:grid-cols-3">
        {stations.map((s) => (
          <button key={s.id} onClick={() => onPick(s.id)} className="rounded-2xl bg-card p-8 text-2xl font-extrabold text-foreground transition hover:scale-[1.02] active:scale-95">
            {s.name.ro}
            {s.name.en && s.name.en !== s.name.ro && <span className="block text-base font-medium text-muted-foreground">{s.name.en}</span>}
          </button>
        ))}
      </div>
    </div>
  );
}

const columnStyle = {
  QUEUED: { bg: "var(--status-pending)", strong: "var(--status-pending-strong)" },
  IN_PROGRESS: { bg: "var(--status-progress)", strong: "var(--status-progress-strong)" },
  READY: { bg: "var(--status-done)", strong: "var(--status-done-strong)" },
} as const;

function KitchenBoard({ stationId, locale, setLocale, onChangeStation }: { stationId: string; locale: "ro" | "en"; setLocale: (l: "ro" | "en") => void; onChangeStation: () => void }) {
  const t = useTranslations();
  const [sound, setSound] = useState(() => localStorage.getItem(SOUND_KEY) !== "off");
  const [fresh, setFresh] = useState<Set<string>>(new Set());
  const [now, setNow] = useState(Date.now());
  const [stations, setStations] = useState<Station[]>([]);
  const { board, live, replace } = useKitchenFeed(stationId, locale, (ticket) => {
    if (sound) chime();
    setFresh((f) => new Set(f).add(ticket.id));
    setTimeout(() => setFresh((f) => (f.delete(ticket.id), new Set(f))), 4000);
  });

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    void unwrap(listKitchenStations()).then(setStations).catch(() => {});
    return () => clearInterval(id);
  }, []);

  const grouped = useMemo(() => group(board, now), [board, now]);
  const station = stations.find((s) => s.id === stationId);

  async function advance(ticket: KitchenTicket) {
    const action = nextAction(ticket.status);
    if (!action) return;
    try {
      const call = action === "start" ? startKitchenTicket : bumpKitchenTicket;
      replace(await unwrap(call({ path: { ticketId: ticket.id } })));
    } catch (e) {
      toast.error((e as Error).message);
    }
  }

  async function undo(ticket: KitchenTicket) {
    try {
      replace(await unwrap(recallKitchenTicket({ path: { ticketId: ticket.id } })));
    } catch (e) {
      toast.error((e as Error).message);
    }
  }

  return (
    <div className="flex h-dvh flex-col text-white">
      <header className="flex items-center gap-3 border-b border-white/10 px-4 py-2">
        <button onClick={onChangeStation} className="rounded-lg px-2 py-1 text-xl font-extrabold hover:bg-white/10" title={t("station.change")}>
          {station ? (locale === "en" && station.name.en ? station.name.en : station.name.ro) : "…"}
        </button>
        <span className={cn("flex items-center gap-1 text-sm", live ? "text-green-400" : "text-amber-400")}>
          {live ? <WifiIcon className="size-4" /> : <WifiOffIcon className="size-4" />} {live ? t("live") : t("offline")}
        </span>
        <span className="ml-auto font-mono text-2xl tabular-nums">
          {new Intl.DateTimeFormat("ro-RO", { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Bucharest" }).format(now)}
        </span>
        <div className="flex rounded-full border border-white/20 p-0.5 text-xs font-semibold" aria-label={t("language")}>
          {(["ro", "en"] as const).map((l) => (
            <button key={l} onClick={() => setLocale(l)} className={cn("rounded-full px-2.5 py-1 uppercase", l === locale ? "bg-white text-black" : "text-white/70")}>
              {l}
            </button>
          ))}
        </div>
        <IconButton label={t("sound")} onClick={() => (localStorage.setItem(SOUND_KEY, sound ? "off" : "on"), setSound(!sound), !sound && chime())}>
          {sound ? <Volume2Icon /> : <VolumeXIcon />}
        </IconButton>
        <IconButton label={t("fullscreen")} onClick={() => void document.documentElement.requestFullscreen?.()}>
          <MaximizeIcon />
        </IconButton>
        <IconButton label={t("logout")} onClick={() => signOut()}>
          <LogOutIcon />
        </IconButton>
      </header>

      <main className="grid flex-1 grid-cols-3 gap-3 overflow-hidden p-3">
        {columns.map((status) => (
          <section key={status} className="flex min-h-0 flex-col rounded-2xl bg-white/5">
            <h2 className="flex items-center justify-between rounded-t-2xl px-4 py-2.5 text-lg font-extrabold text-black" style={{ background: columnStyle[status].bg }}>
              {t(`status.${status}`)}
              <span className="rounded-full bg-black/80 px-2.5 py-0.5 text-sm text-white tabular-nums">{grouped[status].length}</span>
            </h2>
            <div className="flex-1 space-y-3 overflow-y-auto p-3">
              {grouped[status].length === 0 && <p className="py-8 text-center text-white/40">{t("empty")}</p>}
              {grouped[status].map((ticket) => (
                <TicketCard key={ticket.id} ticket={ticket} now={now} fresh={fresh.has(ticket.id)} onTap={() => advance(ticket)} onUndo={() => undo(ticket)} />
              ))}
            </div>
          </section>
        ))}
      </main>
    </div>
  );
}

function TicketCard({ ticket, now, fresh, onTap, onUndo }: { ticket: KitchenTicket; now: number; fresh: boolean; onTap: () => void; onUndo: () => void }) {
  const t = useTranslations();
  const late = isLate(ticket, now);
  const style = columnStyle[ticket.status];
  const time = (iso: string) => new Intl.DateTimeFormat("ro-RO", { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Bucharest" }).format(new Date(iso));
  const action = nextAction(ticket.status);
  return (
    <article
      role="button"
      tabIndex={0}
      onClick={action ? onTap : undefined}
      onKeyDown={(e) => e.key === "Enter" && action && onTap()}
      aria-label={`${ticket.orderNumber} – ${t(`status.${ticket.status}`)}`}
      className={cn("rounded-xl border-4 p-3 text-black transition select-none", action && "cursor-pointer active:scale-[0.98]", fresh && "kds-new")}
      style={{ background: style.bg, borderColor: late ? "var(--status-late)" : style.strong }}
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-mono text-3xl leading-none font-black tabular-nums">{ticket.orderNumber}</p>
          <p className="mt-1 text-sm font-semibold">
            {t(`channel.${ticket.channel}`)}
            {ticket.customerName && ` · ${ticket.customerName}`}
            {ticket.tableLabel && ` · ${t("card.table", { label: ticket.tableLabel })}`}
          </p>
        </div>
        <div className="text-right">
          <p className={cn("font-mono text-2xl font-bold tabular-nums", late && "text-[var(--status-late)]")}>{elapsed(ticket, now)}</p>
          {late ? (
            <span className="rounded bg-[var(--status-late)] px-1.5 py-0.5 text-xs font-bold text-white uppercase">{t("card.late")}</span>
          ) : (
            ticket.status !== "READY" && <p className="text-xs">{t("card.eta", { time: time(ticket.estimatedReadyAt) })}</p>
          )}
        </div>
      </div>
      <ul className="mt-3 space-y-1.5 border-t border-black/15 pt-2">
        {ticket.lines.map((l, i) => (
          <li key={i} className="text-lg leading-snug">
            <span className="font-black">{l.quantity} ×</span> <span className="font-semibold">{l.productName}</span>
            {l.modifiers.length > 0 && <span className="block pl-6 text-base font-bold">+ {l.modifiers.join(", ")}</span>}
            {l.notes && <span className="block pl-6 text-base font-bold text-red-700">⚠ {l.notes}</span>}
          </li>
        ))}
      </ul>
      {ticket.notes && <p className="mt-2 rounded bg-black/10 px-2 py-1 text-sm font-bold">⚠ {ticket.notes}</p>}
      <div className="mt-3 flex items-center justify-between">
        <span className="text-xs font-semibold tracking-wide uppercase">{t(`status.${ticket.status}`)}</span>
        {action ? (
          <span className="rounded-lg px-3 py-1.5 text-sm font-extrabold text-white" style={{ background: style.strong }}>
            {t(`action.${ticket.status}`)} →
          </span>
        ) : (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onUndo();
            }}
            className="flex items-center gap-1 rounded-lg bg-black/80 px-3 py-1.5 text-sm font-bold text-white"
          >
            <Undo2Icon className="size-4" /> {t("action.undo")}
          </button>
        )}
      </div>
    </article>
  );
}

function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick} aria-label={label} title={label} className="rounded-lg p-2 text-white/80 hover:bg-white/10 hover:text-white [&_svg]:size-5">
      {children}
    </button>
  );
}
