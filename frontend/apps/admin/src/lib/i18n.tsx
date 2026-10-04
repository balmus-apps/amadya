import { isLocale, type AppLocale } from "@amadya/i18n";
import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { IntlProvider, useTranslations } from "use-intl";
import en from "../messages/en.json";
import ro from "../messages/ro.json";
import { setApiLocale } from "./session";

export const adminMessages = { ro, en };

const LocaleContext = createContext<{ locale: AppLocale; setLocale: (l: AppLocale) => void }>({ locale: "ro", setLocale: () => {} });

export function useLocale() {
  return useContext(LocaleContext);
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const saved = localStorage.getItem("amadya-admin-locale");
  const [locale, setLocaleState] = useState<AppLocale>(isLocale(saved) ? saved : "ro");
  setApiLocale(locale);
  const value = useMemo(
    () => ({
      locale,
      setLocale: (l: AppLocale) => {
        localStorage.setItem("amadya-admin-locale", l);
        document.documentElement.lang = l;
        setLocaleState(l);
      },
    }),
    [locale],
  );
  return (
    <LocaleContext.Provider value={value}>
      <IntlProvider locale={locale} messages={adminMessages[locale]} timeZone="Europe/Bucharest">
        {children}
      </IntlProvider>
    </LocaleContext.Provider>
  );
}

export { useTranslations as useT };
