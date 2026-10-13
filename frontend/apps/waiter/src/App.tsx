import { login, unwrap } from "@amadya/api-client";
import { accessToken, hasAnyRole, hasStoredSession, session, setApiLocale, signIn, signOut } from "@amadya/staff-auth";
import { posMessages } from "@amadya/staff-pos";
import { Button, Input, Toaster } from "@amadya/ui";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { IntlProvider, useTranslations } from "use-intl";
import { useStore } from "zustand";
import { useTableFeed } from "./lib/feed";
import { useRoute } from "./lib/route";
import en from "./messages/en.json";
import ro from "./messages/ro.json";
import { FloorScreen } from "./screens/floor";
import { OrderScreen } from "./screens/order";
import { TableScreen } from "./screens/table";

const LOCALE_KEY = "amadya-waiter-locale";
const SOUND_KEY = "amadya-waiter-sound";
export const FLOOR_ROLES = ["WAITER", "CASHIER", "MANAGER", "ADMIN"];

const queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: 5_000, retry: 1, refetchOnWindowFocus: true } } });

export type Locale = "ro" | "en";

export interface Prefs {
  locale: Locale;
  setLocale: (l: Locale) => void;
  sound: boolean;
  setSound: (on: boolean) => void;
  live: boolean;
}

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
  if (!claims || !hasAnyRole(...FLOOR_ROLES)) return <Login />;
  return <Screens locale={locale} setLocale={setLocale} />;
}

function Screens({ locale, setLocale }: { locale: Locale; setLocale: (l: Locale) => void }) {
  const route = useRoute();
  const [sound, setSoundState] = useState(() => localStorage.getItem(SOUND_KEY) !== "off");
  const live = useTableFeed(sound);
  const prefs: Prefs = {
    locale,
    setLocale,
    sound,
    setSound: (on) => {
      localStorage.setItem(SOUND_KEY, on ? "on" : "off");
      setSoundState(on);
    },
    live,
  };

  switch (route.name) {
    case "table":
      return <TableScreen key={route.sessionId} sessionId={route.sessionId} />;
    case "order":
      return <OrderScreen key={route.sessionId} sessionId={route.sessionId} />;
    default:
      return <FloorScreen prefs={prefs} />;
  }
}

function Login() {
  const t = useTranslations();
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
      if (!hasAnyRole(...FLOOR_ROLES)) {
        signOut();
        setError(t("login.notAllowed"));
      }
    } catch {
      setError(t("login.error"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center bg-muted/40 p-4">
      <form onSubmit={submit} className="w-full max-w-sm space-y-4 rounded-2xl border bg-card p-6 shadow-sm">
        <div>
          <h1 className="text-2xl font-extrabold">{t("login.title")}</h1>
          <p className="text-sm text-muted-foreground">{t("login.subtitle")}</p>
        </div>
        <Input id="email" type="email" inputMode="email" placeholder={t("login.email")} autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <Input id="password" type="password" placeholder={t("login.password")} autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button type="submit" size="lg" className="w-full" disabled={busy}>
          {t("login.submit")}
        </Button>
      </form>
    </div>
  );
}
