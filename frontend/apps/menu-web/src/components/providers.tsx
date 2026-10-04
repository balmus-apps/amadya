"use client";

import type { PublicSettings } from "@amadya/api-client";
import type { AppLocale } from "@amadya/i18n";
import { Toaster } from "@amadya/ui";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createContext, useContext, useState, type ReactNode } from "react";
import { browserApiUrl, configureApi } from "@/lib/api";

const SettingsContext = createContext<PublicSettings | null>(null);

/** Restaurant settings loaded on the server for this request (name, theme, features, hours). */
export function useSettings(): PublicSettings {
  const settings = useContext(SettingsContext);
  if (!settings) throw new Error("useSettings outside <Providers>");
  return settings;
}

export function Providers({ settings, locale, children }: { settings: PublicSettings; locale: AppLocale; children: ReactNode }) {
  const [queryClient] = useState(() => {
    configureApi({ baseUrl: browserApiUrl, getLocale: () => locale });
    return new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: 1 } } });
  });
  return (
    <SettingsContext.Provider value={settings}>
      <QueryClientProvider client={queryClient}>
        {children}
        <Toaster />
      </QueryClientProvider>
    </SettingsContext.Provider>
  );
}
