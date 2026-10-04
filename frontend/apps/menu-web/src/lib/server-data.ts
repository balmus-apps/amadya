import "server-only";
import { type AppLocale } from "@amadya/i18n";
import { getMenu, getPublicSettings, unwrap } from "./api";

export async function loadSettings() {
  return unwrap(getPublicSettings({ cache: "no-store" }));
}

export async function loadMenu(locale: AppLocale) {
  return unwrap(getMenu({ headers: { "Accept-Language": locale }, cache: "no-store" }));
}
