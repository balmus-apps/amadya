import { isLocale, type AppLocale } from "@amadya/i18n";
import { resolveTheme, themeCssVars } from "@amadya/theme";
import type { Metadata, Viewport } from "next";
import { hasLocale, NextIntlClientProvider } from "next-intl";
import { setRequestLocale } from "next-intl/server";
import { Inter, Rubik } from "next/font/google";
import { notFound } from "next/navigation";
import type { CSSProperties, ReactNode } from "react";
import { Header } from "@/components/header";
import { Providers } from "@/components/providers";
import { routing } from "@/i18n/routing";
import { loadSettings } from "@/lib/server-data";
import "../globals.css";

const body = Inter({ subsets: ["latin", "latin-ext"], variable: "--font-body" });
const heading = Rubik({ subsets: ["latin", "latin-ext"], weight: ["500", "700", "800"], variable: "--font-heading-family" });

// Settings and menu are edited live from the admin app, so every page renders per request.
export const dynamic = "force-dynamic";

export async function generateMetadata(): Promise<Metadata> {
  const settings = await loadSettings();
  return { title: { default: settings.name, template: `%s · ${settings.name}` }, description: settings.address };
}

export async function generateViewport(): Promise<Viewport> {
  const settings = await loadSettings();
  return { themeColor: resolveTheme(settings.theme).primary, width: "device-width", initialScale: 1, viewportFit: "cover" };
}

export default async function LocaleLayout({ children, params }: { children: ReactNode; params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!hasLocale(routing.locales, locale) || !isLocale(locale)) notFound();
  setRequestLocale(locale);

  const settings = await loadSettings();
  const theme = resolveTheme(settings.theme);

  return (
    <html lang={locale} style={themeCssVars(theme) as CSSProperties} className={`${body.variable} ${heading.variable}`}>
      <body className="min-h-dvh">
        <NextIntlClientProvider>
          <Providers settings={settings} locale={locale as AppLocale}>
            <Header />
            <main className="mx-auto w-full max-w-5xl px-4 pb-32">{children}</main>
          </Providers>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
