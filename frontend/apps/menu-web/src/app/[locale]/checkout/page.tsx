import { getTranslations, setRequestLocale } from "next-intl/server";
import { CheckoutScreen } from "@/components/checkout/checkout-screen";

export async function generateMetadata() {
  const t = await getTranslations("checkout");
  return { title: t("title") };
}

export default async function CheckoutPage({ params }: { params: Promise<{ locale: string }> }) {
  setRequestLocale((await params).locale);
  return <CheckoutScreen />;
}
