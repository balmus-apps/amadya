import { setRequestLocale } from "next-intl/server";
import { RecentOrders } from "@/components/order/recent-orders";

export default async function OrdersPage({ params }: { params: Promise<{ locale: string }> }) {
  setRequestLocale((await params).locale);
  return <RecentOrders />;
}
