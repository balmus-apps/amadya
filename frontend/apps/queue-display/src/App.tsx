import { getMenu, getPublicSettings, unwrap, type MenuPromotion, type PublicSettings } from "@amadya/api-client";
import { resolveTheme, themeCssVars } from "@amadya/theme";
import { cn } from "@amadya/ui";
import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { announce, unlockAudio } from "./announce";
import { fit, isFresh, visibleReady } from "./board";
import { useQueue } from "./feed";

/** Labels in the restaurant's main language, with the other one underneath for tourists. */
const labels = {
  preparing: { ro: "Se pregătește", en: "Preparing" },
  ready: { ro: "Gata de ridicare", en: "Ready for pickup" },
  nothingReady: { ro: "Comenzile gata apar aici", en: "Ready orders appear here" },
  nothingPreparing: { ro: "Nicio comandă în lucru", en: "No orders in progress" },
  more: { ro: "și încă {n}", en: "and {n} more" },
  enableSound: { ro: "Atingeți pentru sunet", en: "Tap to enable sound" },
  offline: { ro: "Reconectare…", en: "Reconnecting…" },
  disabled: { ro: "Ecranul de comenzi este dezactivat din setări.", en: "The order screen is turned off in the settings." },
} as const;

type Locale = "ro" | "en";
const params = new URLSearchParams(window.location.search);
const SOUND_KEY = "amadya-queue-sound";

export function App() {
  const [settings, setSettings] = useState<PublicSettings>();
  const [promos, setPromos] = useState<MenuPromotion[]>([]);
  const [now, setNow] = useState(Date.now());
  const [audioOn, setAudioOn] = useState(false);

  const main: Locale = (params.get("lang") as Locale) ?? (settings?.defaultLocale === "en" ? "en" : "ro");
  const second: Locale | undefined = settings?.locales.includes(main === "ro" ? "en" : "ro") ? (main === "ro" ? "en" : "ro") : undefined;
  const soundWanted = params.get("sound") !== "off";
  const voiceWanted = params.get("voice") !== "off";

  const { board, live } = useQueue((numbers) => announce(numbers, { sound: soundWanted && audioOn, voice: voiceWanted && audioOn, locale: main }));

  useEffect(() => {
    void unwrap(getPublicSettings()).then(setSettings).catch(() => {});
    void unwrap(getMenu())
      .then((m) => setPromos(m.promotions))
      .catch(() => {});
    const tick = setInterval(() => setNow(Date.now()), 5_000);
    // TVs and tablets must not go to sleep while the restaurant is open.
    void (navigator as Navigator & { wakeLock?: { request: (t: "screen") => Promise<unknown> } }).wakeLock?.request("screen").catch(() => {});
    try {
      if (localStorage.getItem(SOUND_KEY) === "on") setAudioOn(true);
    } catch {
      /* storage blocked */
    }
    return () => clearInterval(tick);
  }, []);

  const theme = useMemo(() => resolveTheme(settings?.theme), [settings]);
  const ready = board ? visibleReady(board, now) : [];
  const readyFit = fit(ready, 12);
  const preparingFit = fit(board?.preparing ?? [], 24);
  const t = (key: keyof typeof labels, n?: number) => ({
    main: labels[key][main].replace("{n}", String(n ?? "")),
    second: second ? labels[key][second].replace("{n}", String(n ?? "")) : undefined,
  });

  if (settings && !settings.features.queueDisplay) {
    return <div className="flex h-dvh items-center justify-center p-8 text-center text-2xl text-white/70">{t("disabled").main}</div>;
  }

  return (
    <div className="flex h-dvh flex-col overflow-hidden text-white" style={themeCssVars(theme) as CSSProperties}>
      <header className="flex items-center gap-4 border-b-4 px-[2.5vmin] py-[1.4vmin]" style={{ borderColor: theme.primary }} onDoubleClick={() => void document.documentElement.requestFullscreen?.()}>
        {settings?.logoUrl ? (
          <img src={settings.logoUrl} alt="" className="size-[6vmin] rounded-full object-cover" />
        ) : (
          <span className="flex size-[6vmin] items-center justify-center rounded-full text-[3vmin] font-black" style={{ background: theme.primary, color: theme.onPrimary }}>
            {settings?.name.charAt(0)}
          </span>
        )}
        <h1 className="truncate text-[3.6vmin] font-extrabold">{settings?.name}</h1>
        {!live && (
          <span role="status" className="rounded-full bg-amber-500/20 px-3 py-1 text-[1.6vmin] font-semibold text-amber-300">
            {t("offline").main}
          </span>
        )}
        <span className="ml-auto font-mono text-[4vmin] font-bold tabular-nums">
          {new Intl.DateTimeFormat("ro-RO", { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Bucharest" }).format(now)}
        </span>
      </header>

      <main className="grid min-h-0 flex-1 gap-[1.5vmin] p-[1.5vmin] landscape:grid-cols-[2fr_3fr] portrait:grid-rows-[2fr_3fr]">
        <Panel title={t("preparing")} tone="preparing" count={board?.preparing.length ?? 0}>
          {preparingFit.shown.length === 0 ? (
            <Empty text={t("nothingPreparing").main} />
          ) : (
            <ul className="grid content-start gap-[1vmin] [grid-template-columns:repeat(auto-fill,minmax(11vw,1fr))] portrait:[grid-template-columns:repeat(auto-fill,minmax(22vw,1fr))]">
              {preparingFit.shown.map((n) => (
                <li key={n} className="rounded-xl bg-white/8 py-[1.2vmin] text-center font-mono text-[4.4vmin] font-bold whitespace-nowrap text-white/85 tabular-nums">
                  {n}
                </li>
              ))}
            </ul>
          )}
          {preparingFit.more > 0 && <More text={t("more", preparingFit.more).main} />}
        </Panel>

        <Panel title={t("ready")} tone="ready" count={ready.length}>
          {readyFit.shown.length === 0 ? (
            <Empty text={t("nothingReady").main} />
          ) : (
            <ul className="grid content-start gap-[1.4vmin] [grid-template-columns:repeat(auto-fill,minmax(16vw,1fr))] portrait:[grid-template-columns:repeat(auto-fill,minmax(30vw,1fr))]">
              {readyFit.shown.map((o, i) => (
                <li
                  key={o.number}
                  className={cn(
                    "rounded-2xl py-[2vmin] text-center font-mono font-black whitespace-nowrap tabular-nums transition",
                    isFresh(o, now) ? "queue-fresh bg-[#16a34a] text-white" : "bg-[#14532d] text-[#bbf7d0]",
                    i === 0 ? "text-[8vmin]" : "text-[6.4vmin]",
                  )}
                >
                  {o.number}
                </li>
              ))}
            </ul>
          )}
          {readyFit.more > 0 && <More text={t("more", readyFit.more).main} />}
        </Panel>
      </main>

      {promos.length > 0 && <PromoTicker promos={promos} color={theme.primary} onColor={theme.onPrimary} />}

      {soundWanted && !audioOn && (
        <button
          onClick={() => {
            unlockAudio();
            setAudioOn(true);
            try {
              localStorage.setItem(SOUND_KEY, "on");
            } catch {
              /* storage blocked */
            }
          }}
          className="fixed right-[2vmin] bottom-[2vmin] rounded-full bg-white px-5 py-3 text-[1.8vmin] font-bold text-black shadow-lg"
        >
          🔊 {t("enableSound").main}
        </button>
      )}
    </div>
  );
}

function Panel({ title, tone, count, children }: { title: { main: string; second?: string }; tone: "preparing" | "ready"; count: number; children: React.ReactNode }) {
  return (
    <section aria-label={title.main} className={cn("flex min-h-0 flex-col overflow-hidden rounded-3xl p-[1.4vmin]", tone === "ready" ? "bg-[#052e16]" : "bg-white/5")}>
      <h2 className="mb-[1.6vmin] flex flex-wrap items-baseline gap-x-3">
        <span className={cn("text-[4.4vmin] font-extrabold", tone === "ready" ? "text-[#4ade80]" : "text-[#fdba74]")}>{title.main}</span>
        {title.second && <span className="text-[2.4vmin] font-semibold text-white/50">{title.second}</span>}
        <span className="ml-auto rounded-full bg-white/10 px-3 text-[2.4vmin] font-bold tabular-nums">{count}</span>
      </h2>
      <div className="min-h-0 flex-1 overflow-hidden">{children}</div>
    </section>
  );
}

function Empty({ text }: { text: string }) {
  return <p className="flex h-full items-center justify-center text-center text-[2.6vmin] text-white/35">{text}</p>;
}

function More({ text }: { text: string }) {
  return <p className="mt-[1vmin] text-center text-[2.2vmin] font-semibold text-white/60">{text}</p>;
}

/** One promotion at a time along the bottom, changing every 10 s. */
function PromoTicker({ promos, color, onColor }: { promos: MenuPromotion[]; color: string; onColor: string }) {
  const [index, setIndex] = useState(0);
  useEffect(() => {
    if (promos.length < 2) return;
    const id = setInterval(() => setIndex((i) => (i + 1) % promos.length), 10_000);
    return () => clearInterval(id);
  }, [promos.length]);
  const promo = promos[index % promos.length]!;
  return (
    <footer key={promo.id} className="queue-promo flex items-center gap-4 px-[2.5vmin] py-[1.6vmin]" style={{ background: color, color: onColor }}>
      {promo.badge && <span className="shrink-0 whitespace-nowrap rounded-lg bg-black/80 px-3 py-1 text-[2.6vmin] font-black text-white">{promo.badge}</span>}
      <span className="min-w-0 truncate text-[3vmin] font-extrabold">{promo.title}</span>
      {promo.subtitle && <span className="min-w-0 truncate text-[2.2vmin] opacity-80 portrait:hidden">{promo.subtitle}</span>}
    </footer>
  );
}
