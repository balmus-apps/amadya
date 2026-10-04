import { defaultLocale, locales } from "@amadya/i18n";
import { defineRouting } from "next-intl/routing";

// Romanian at "/", English at "/en".
export const routing = defineRouting({ locales, defaultLocale, localePrefix: "as-needed" });
