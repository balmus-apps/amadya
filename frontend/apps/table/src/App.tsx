import { getPublicSettings, joinTable, unwrap, type GuestTable } from "@amadya/api-client";
import { formatMoney, messages as shared, type AppLocale } from "@amadya/i18n";
import { resolveTheme, themeCssVars } from "@amadya/theme";
import { Button, cn, Input, Skeleton, Toaster } from "@amadya/ui";
import { QueryClient, QueryClientProvider, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { HourglassIcon, UtensilsCrossedIcon } from "lucide-react";
import { useEffect, useState, type CSSProperties } from "react";
import { IntlProvider, useLocale, useTranslations } from "use-intl";
import { keys, problem, problemMessage, storedToken, storeToken, tableCode, useGuestFeed, useGuestView, useTableInfo } from "./lib/guest";
import en from "./messages/en.json";
import ro from "./messages/ro.json";
import { TableScreen } from "./screens/table";

const LOCALE_KEY = "amadya-table-locale";
let locale: AppLocale = (() => {
  try {
    const saved = localStorage.getItem(LOCALE_KEY);
    if (saved === "ro" || saved === "en") return saved;
  } catch {
    /* storage blocked */
  }
  return navigator.language.toLowerCase().startsWith("ro") ? "ro" : "en";
})();

export const currentLocale = () => locale;

const queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: 3_000, refetchOnWindowFocus: true } } });

export function App() {
  const [current, setCurrent] = useState<AppLocale>(locale);
  const setLocale = (l: AppLocale) => {
    locale = l;
    try {
      localStorage.setItem(LOCALE_KEY, l);
    } catch {
      /* storage blocked */
    }
    document.documentElement.lang = l;
    setCurrent(l);
  };
  return (
    <IntlProvider locale={current} messages={{ ...shared[current], ...(current === "en" ? en : ro) }} timeZone="Europe/Bucharest">
      <QueryClientProvider client={queryClient}>
        <Branded>
          <Gate setLocale={setLocale} />
        </Branded>
        <Toaster position="top-center" />
      </QueryClientProvider>
    </IntlProvider>
  );
}

/** The restaurant's colours, as on the customer menu. */
function Branded({ children }: { children: React.ReactNode }) {
  const settings = useQuery({ queryKey: ["settings"], queryFn: () => unwrap(getPublicSettings()), staleTime: Infinity });
  const theme = resolveTheme(settings.data?.theme);
  return (
    <div className="min-h-dvh bg-background text-foreground" style={themeCssVars(theme) as CSSProperties}>
      {children}
    </div>
  );
}

export type SetLocale = (l: AppLocale) => void;

function Gate({ setLocale }: { setLocale: SetLocale }) {
  const t = useTranslations("table");
  const info = useTableInfo();
  const [token, setToken] = useState(storedToken);
  const view = useGuestView(token);
  const client = useQueryClient();
  useGuestFeed(token, () => void client.invalidateQueries({ queryKey: keys.view }));

  // A token that no longer works (refused, removed, table closed long ago): start again.
  const forbidden = view.isError && problem(view.error)?.status === 403;
  useEffect(() => {
    if (forbidden && view.data === undefined) {
      storeToken(null);
      setToken(null);
    }
  }, [forbidden, view.data]);

  if (!tableCode || (info.isError && problem(info.error)?.status === 404)) return <Message icon={<UtensilsCrossedIcon />} text={t("notFound")} />;
  if (info.isPending) return <Loading />;
  if (info.data && !info.data.enabled) return <Message icon={<UtensilsCrossedIcon />} text={t("disabled")} />;
  if (!token || forbidden) {
    return (
      <Join
        info={info.data!}
        rejected={forbidden}
        onJoined={(tok) => {
          storeToken(tok);
          setToken(tok);
        }}
      />
    );
  }
  if (view.isPending || !view.data) return <Loading />;
  if (view.data.me.status === "PENDING") return <Waiting />;
  if (view.data.status === "CLOSED") return <Closed amount={view.data.paid} onAgain={() => (storeToken(null), setToken(null))} />;
  return <TableScreen view={view.data} token={token} setLocale={setLocale} />;
}

function Join({ info, rejected, onJoined }: { info: GuestTable; rejected: boolean; onJoined: (token: string) => void }) {
  const t = useTranslations("table");
  const [name, setName] = useState("");
  const client = useQueryClient();
  const join = useMutation({
    mutationFn: () => unwrap(joinTable({ path: { code: tableCode }, body: { name: name.trim() } })),
    onSuccess: (res) => {
      client.setQueryData([...keys.view, res.token, locale], res.view);
      onJoined(res.token);
    },
  });
  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col justify-center gap-6 p-6">
      <div className="space-y-2 text-center">
        {info.logoUrl && <img src={info.logoUrl} alt="" className="mx-auto size-16 rounded-full object-cover" />}
        <p className="text-sm text-muted-foreground">{t("join.welcome", { restaurant: info.restaurantName })}</p>
        <h1 className="text-4xl font-black">{t("title", { label: info.tableLabel })}</h1>
        {info.areaName && <p className="text-sm text-muted-foreground">{info.areaName}</p>}
      </div>
      <p className="text-center text-muted-foreground">{info.occupied ? t("join.occupied") : t("join.start")}</p>
      {rejected && (
        <p role="alert" className="rounded-xl bg-muted p-3 text-center text-sm">
          {t("waiting.rejected")}
        </p>
      )}
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (name.trim()) join.mutate();
        }}
      >
        <label htmlFor="guest-name" className="text-sm font-semibold">
          {t("join.name")}
        </label>
        <Input id="guest-name" autoComplete="given-name" maxLength={40} placeholder={t("join.namePlaceholder")} value={name} onChange={(e) => setName(e.target.value)} className="h-12 text-lg" />
        {join.isError && (
          <p role="alert" className="text-sm text-destructive">
            {problemMessage(join.error)}
          </p>
        )}
        <Button type="submit" size="lg" className="w-full" disabled={!name.trim() || join.isPending}>
          {info.occupied ? t("join.ask") : t("join.submit")}
        </Button>
      </form>
    </div>
  );
}

function Waiting() {
  const t = useTranslations("table");
  return <Message icon={<HourglassIcon className="animate-pulse" />} title={t("waiting.title")} text={t("waiting.text")} />;
}

function Closed({ amount, onAgain }: { amount: { amount: string; currency: string }; onAgain: () => void }) {
  const t = useTranslations("table");
  const locale = useLocale() as AppLocale;
  return (
    <Message icon={<UtensilsCrossedIcon />} title={t("closed.title")} text={t("closed.text")}>
      {Number(amount.amount) > 0 && <p className="font-semibold">{t("closed.paid", { amount: formatMoney(amount, locale) })}</p>}
      <Button variant="outline" onClick={onAgain}>
        {t("waiting.again")}
      </Button>
    </Message>
  );
}

function Message({ icon, title, text, children }: { icon: React.ReactNode; title?: string; text: string; children?: React.ReactNode }) {
  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-4 p-6 text-center [&>svg]:size-10">
      <span className={cn("text-primary [&_svg]:size-12")}>{icon}</span>
      {title && <h1 className="text-2xl font-extrabold">{title}</h1>}
      <p className="text-muted-foreground">{text}</p>
      {children}
    </div>
  );
}

function Loading() {
  return (
    <div className="mx-auto max-w-md space-y-3 p-6">
      <Skeleton className="h-10 rounded-xl" />
      <Skeleton className="h-40 rounded-2xl" />
      <Skeleton className="h-40 rounded-2xl" />
    </div>
  );
}
