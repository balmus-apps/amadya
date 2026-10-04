import type { OrderStatus } from "@amadya/api-client";
import { Badge } from "@amadya/ui";
import { useT } from "@/lib/i18n";

const tone: Record<OrderStatus, "muted" | "default" | "accent" | "success" | "secondary" | "outline"> = {
  PENDING_PAYMENT: "outline",
  PLACED: "default",
  PREPARING: "accent",
  READY: "success",
  COMPLETED: "muted",
  CANCELLED: "muted",
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const t = useT();
  return <Badge variant={tone[status]}>{t(`orders.statuses.${status}`)}</Badge>;
}
