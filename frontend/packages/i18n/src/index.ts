import en from "./messages/en.json";
import ro from "./messages/ro.json";

export const locales = ["ro", "en"] as const;
export type AppLocale = (typeof locales)[number];
export const defaultLocale: AppLocale = "ro";

export const messages = { ro, en } as const;
export type Messages = typeof ro;

export function isLocale(value: string | undefined | null): value is AppLocale {
  return !!value && (locales as readonly string[]).includes(value);
}

const intlLocale: Record<AppLocale, string> = { ro: "ro-RO", en: "en-GB" };

/** Formats an API money value ("20.00", "RON") for display, e.g. "20,00 lei" / "RON 20.00". */
export function formatMoney(money: { amount: string; currency: string }, locale: AppLocale): string {
  return new Intl.NumberFormat(intlLocale[locale], { style: "currency", currency: money.currency }).format(Number(money.amount));
}

export function formatTime(iso: string | Date, locale: AppLocale, timeZone = "Europe/Bucharest"): string {
  return new Intl.DateTimeFormat(intlLocale[locale], { hour: "2-digit", minute: "2-digit", timeZone }).format(new Date(iso));
}

/**
 * Normalizes a Romanian phone number typed by a customer to E.164 (+40...).
 * Returns null when it cannot be a valid number.
 */
export function normalizePhone(input: string): string | null {
  const digits = input.replace(/[^\d+]/g, "");
  let e164: string;
  if (digits.startsWith("+")) e164 = digits;
  else if (digits.startsWith("00")) e164 = `+${digits.slice(2)}`;
  else if (digits.startsWith("0")) e164 = `+40${digits.slice(1)}`;
  else if (digits.startsWith("40")) e164 = `+${digits}`;
  else e164 = `+40${digits}`;
  return /^\+[1-9]\d{7,14}$/.test(e164) ? e164 : null;
}
