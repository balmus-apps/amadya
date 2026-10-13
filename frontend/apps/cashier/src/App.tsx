import { login, unwrap } from "@amadya/api-client";
import { posMessages } from "@amadya/staff-pos";
import { accessToken, hasAnyRole, hasStoredSession, session, setApiLocale, signIn, signOut } from "@amadya/staff-auth";
import { Button, cn, Input, Toaster } from "@amadya/ui";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BellIcon, BellOffIcon, LogOutIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { IntlProvider, useTranslations } from "use-intl";
import { useStore } from "zustand";
import { useOrders } from "./lib/data";
import { useOrderFeed } from "./lib/feed";
import { groupOrders, type ListName } from "./lib/lists";
import en from "./messages/en.json";
import ro from "./messages/ro.json";
import { NewOrderScreen } from "./screens/new-order";
import { OrderList } from "./screens/orders";

const LOCALE_KEY = "amadya-cashier-locale";
const SOUND_KEY = "amadya-cashier-sound";
export const CASHIER_ROLES = ["CASHIER", "MANAGER", "ADMIN"];

const queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: 5_000, retry: 1, refetchOnWindowFocus: true } } });

type Locale = "ro" | "en";
type Tab = "new" | ListName;

export function App() {
  const [locale, setLocaleState] = useState<Locale>(() => (localStorage.getItem(LOCALE_KEY) === "en" ? "en" : "ro"));
  setApiLocale(locale);
  const setLocale = (l: Locale) => {
    localStorage.setItem(LOCALE_KEY, l);
    document.documentElement.lang = l;
    setLocaleState(l);
  };
  return (
    <IntlProvider locale={locale} messages={locale === "en" ? { ...en, ...posMessages.en } : { ...ro, ...posMessages.ro }} timeZone="Europe/Bucharest">
      <QueryClientProvider client={queryClient}>
        <Gate locale={locale} setLocale={setLocale} />
        <Toaster position="top-center" />
      </QueryClientProvider>
    </IntlProvider>
  );
}

function Gate({ locale, setLocale }: { locale: Locale; setLocale: (l: Locale) => void }) {
  const claims = useStore(session, (s) => s.claims);
  const [checking, setChecking] = useState(hasStoredSession());

  useEffect(() => {
    if (hasStoredSession()) void accessToken().finally(() => setChecking(false));
  }, []);

  if (checking) return null;
  if (!claims || !hasAnyRole(...CASHIER_ROLES)) return <Login />;
  return <Shell locale={locale} setLocale={setLocale} />;
}

/** One screen for the counter: a new order, and the three live lists. */
function Shell({ locale, setLocale }: { locale: Locale; setLocale: (l: Locale) => void }) {
  const t = useTranslations();
  const [tab, setTab] = useState<Tab>("new");
  const [sound, setSound] = useState(() => localStorage.getItem(SOUND_KEY) !== "off");
  const live = useOrderFeed(sound);
  const orders = useOrders();
  const lists = groupOrders(orders.data ?? []);
  const tabs: { id: Tab; count?: number }[] = [
    { id: "new" },
    { id: "confirm", count: lists.confirm.length },
    { id: "progress", count: lists.progress.length },
    { id: "ready", count: lists.ready.length },
  ];

  return (
    <div className="flex h-dvh flex-col bg-muted/40">
      <header className="flex flex-wrap items-center gap-2 border-b bg-background px-3 py-2">
        <nav className="flex flex-1 gap-1 overflow-x-auto [scrollbar-width:none]" aria-label="Cashier">
          {tabs.map(({ id, count }) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              aria-current={tab === id ? "page" : undefined}
              className={cn(
                "flex h-11 shrink-0 items-center gap-2 rounded-xl px-4 text-sm font-bold transition",
                tab === id ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted",
              )}
            >
              {t(`tabs.${id}`)}
              {count !== undefined && count > 0 && (
                <span
                  className={cn(
                    "min-w-6 rounded-full px-1.5 py-0.5 text-xs tabular-nums",
                    id === "confirm" ? "bg-[var(--status-attention-strong)] text-white" : tab === id ? "bg-primary-foreground/20" : "bg-muted",
                  )}
                >
                  {count}
                </span>
              )}
            </button>
          ))}
        </nav>
        <span className={cn("flex items-center gap-1.5 text-xs font-semibold", live ? "text-[var(--status-done-strong)]" : "text-destructive")} role="status">
          <span className={cn("size-2 rounded-full", live ? "bg-[var(--status-done-strong)]" : "animate-pulse bg-destructive")} />
          {live ? t("shell.live") : t("shell.offline")}
        </span>
        <Button
          variant="ghost"
          size="icon"
          aria-label={sound ? t("shell.soundOn") : t("shell.soundOff")}
          onClick={() => {
            localStorage.setItem(SOUND_KEY, sound ? "off" : "on");
            setSound(!sound);
          }}
        >
          {sound ? <BellIcon /> : <BellOffIcon />}
        </Button>
        <Button variant="ghost" size="sm" onClick={() => setLocale(locale === "ro" ? "en" : "ro")}>
          {locale === "ro" ? "EN" : "RO"}
        </Button>
        <Button variant="ghost" size="icon" aria-label={t("shell.logout")} onClick={() => signOut()}>
          <LogOutIcon />
        </Button>
      </header>

      <main className="min-h-0 flex-1">
        {tab === "new" ? <NewOrderScreen live={live} /> : <OrderList list={tab} orders={lists[tab]} loading={orders.isPending} />}
      </main>
    </div>
  );
}

function Login() {
  const t = useTranslations("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(undefined);
    setBusy(true);
    try {
      signIn(await unwrap(login({ body: { email, password } })));
      if (!hasAnyRole(...CASHIER_ROLES)) {
        signOut();
        setError(t("notAllowed"));
      }
    } catch {
      setError(t("error"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center bg-muted/40 p-4">
      <form onSubmit={submit} className="w-full max-w-sm space-y-4 rounded-2xl border bg-card p-6 shadow-sm">
        <div>
          <h1 className="text-2xl font-extrabold">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
        <Input id="email" type="email" inputMode="email" placeholder={t("email")} autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <Input id="password" type="password" placeholder={t("password")} autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button type="submit" size="lg" className="w-full" disabled={busy}>
          {t("submit")}
        </Button>
      </form>
    </div>
  );
}
