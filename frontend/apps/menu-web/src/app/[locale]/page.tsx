import { isLocale } from "@amadya/i18n";
import { setRequestLocale } from "next-intl/server";
import { MenuScreen } from "@/components/menu/menu-screen";
import { loadMenu } from "@/lib/server-data";

export default async function MenuPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  setRequestLocale(locale);
  const menu = await loadMenu(isLocale(locale) ? locale : "ro");
  return <MenuScreen menu={menu} />;
}
