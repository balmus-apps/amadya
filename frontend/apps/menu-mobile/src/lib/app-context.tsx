import { getPublicSettings, unwrap, type PublicSettings } from "@amadya/api-client";
import { defaultLocale, isLocale, messages, type AppLocale } from "@amadya/i18n";
import { resolveTheme, type ThemeTokens } from "@amadya/theme";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { useQuery } from "@tanstack/react-query";
import { getLocales } from "expo-localization";
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { IntlProvider } from "use-intl";
import { setupApi } from "./api";

interface AppContextValue {
  settings?: PublicSettings;
  theme: ThemeTokens;
  locale: AppLocale;
  setLocale: (locale: AppLocale) => void;
}

const AppContext = createContext<AppContextValue | null>(null);

export function useApp(): AppContextValue {
  const value = useContext(AppContext);
  if (!value) throw new Error("useApp outside <AppProvider>");
  return value;
}

export function useTheme(): ThemeTokens {
  return useApp().theme;
}

const LOCALE_KEY = "amadya-locale";
let currentLocale: AppLocale = defaultLocale;

/** Settings, theme and language for the whole app; the phone's language is the default. */
export function AppProvider({ children }: { children: ReactNode }) {
  const deviceLocale = getLocales()[0]?.languageCode;
  const [locale, setLocaleState] = useState<AppLocale>(isLocale(deviceLocale) ? deviceLocale : defaultLocale);
  currentLocale = locale;
  setupApi(() => currentLocale);

  useEffect(() => {
    AsyncStorage.getItem(LOCALE_KEY).then((saved) => isLocale(saved) && setLocaleState(saved));
  }, []);

  const { data: settings } = useQuery({ queryKey: ["settings"], queryFn: () => unwrap(getPublicSettings()), staleTime: 5 * 60_000 });
  const theme = useMemo(() => resolveTheme(settings?.theme), [settings]);

  const value = useMemo<AppContextValue>(
    () => ({
      settings,
      theme,
      locale,
      setLocale: (next) => {
        setLocaleState(next);
        void AsyncStorage.setItem(LOCALE_KEY, next);
      },
    }),
    [settings, theme, locale],
  );

  return (
    <AppContext.Provider value={value}>
      <IntlProvider locale={locale} messages={messages[locale]} timeZone="Europe/Bucharest">
        {children}
      </IntlProvider>
    </AppContext.Provider>
  );
}
