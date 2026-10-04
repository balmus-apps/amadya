import { setRequestLocale } from "next-intl/server";
import { OrderScreen } from "@/components/order/order-screen";

export default async function OrderPage({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string; orderId: string }>;
  searchParams: Promise<{ t?: string }>;
}) {
  const { locale, orderId } = await params;
  setRequestLocale(locale);
  return <OrderScreen orderId={orderId} tokenFromUrl={(await searchParams).t} />;
}
