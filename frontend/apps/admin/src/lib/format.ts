import { formatMoney as money, type AppLocale } from "@amadya/i18n";

export const formatMoney = money;

export function formatDate(value: string | Date, locale: AppLocale) {
  return new Intl.DateTimeFormat(locale === "ro" ? "ro-RO" : "en-GB", { dateStyle: "medium", timeZone: "Europe/Bucharest" }).format(new Date(value));
}

export function formatDateTime(value: string | Date, locale: AppLocale) {
  return new Intl.DateTimeFormat(locale === "ro" ? "ro-RO" : "en-GB", { dateStyle: "short", timeStyle: "short", timeZone: "Europe/Bucharest" }).format(new Date(value));
}

/** "12500.000" g -> "12,5 kg"-style readable quantity; keeps the unit when no larger unit applies. */
export function formatQty(quantity: string, unit: string, locale: AppLocale) {
  const n = Number(quantity);
  const fmt = (v: number) => new Intl.NumberFormat(locale === "ro" ? "ro-RO" : "en-GB", { maximumFractionDigits: 3 }).format(v);
  if ((unit === "g" || unit === "ml") && Math.abs(n) >= 1000) return `${fmt(n / 1000)} ${unit === "g" ? "kg" : "l"}`;
  return `${fmt(n)} ${unit}`;
}

/** Localized text from the API's {ro, en} objects. */
export function lt(text: { ro: string; en?: string } | undefined, locale: AppLocale) {
  if (!text) return "";
  return locale === "en" && text.en ? text.en : text.ro;
}

export function today() {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Bucharest" }).format(new Date());
}

export function daysAgo(days: number) {
  const d = new Date(Date.now() - days * 86_400_000);
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Bucharest" }).format(d);
}
