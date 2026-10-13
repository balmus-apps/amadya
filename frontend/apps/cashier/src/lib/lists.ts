import type { Order } from "@amadya/api-client";

export type ListName = "confirm" | "progress" | "ready";

/** The statuses the counter follows; finished and table orders are left out. */
export const FOLLOWED = ["AWAITING_CONFIRMATION", "PLACED", "PREPARING", "READY"] as const;

/** Which counter list an order belongs to: only to-go and counter orders, never table rounds. */
export function listOf(order: Pick<Order, "status" | "channel">): ListName | null {
  if (order.channel === "DINE_IN") return null;
  switch (order.status) {
    case "AWAITING_CONFIRMATION":
      return "confirm";
    case "PLACED":
    case "PREPARING":
      return "progress";
    case "READY":
      return "ready";
    default:
      return null;
  }
}

/** Oldest first: the customer who has waited longest is served first. */
export function groupOrders(orders: Order[]): Record<ListName, Order[]> {
  const lists: Record<ListName, Order[]> = { confirm: [], progress: [], ready: [] };
  [...orders].sort((a, b) => a.createdAt.localeCompare(b.createdAt)).forEach((o) => {
    const list = listOf(o);
    if (list) lists[list].push(o);
  });
  return lists;
}

/** Seconds until a waiting order is confirmed automatically (never negative). */
export function secondsUntilAuto(createdAt: string, confirmMinutes: number, now: number): number {
  return Math.max(0, Math.round((Date.parse(createdAt) + confirmMinutes * 60_000 - now) / 1000));
}

/** Cancelling food already being prepared is for managers and admins, as the API enforces. */
export function canCancel(status: Order["status"], isManager: boolean): boolean {
  if (status === "PLACED") return true;
  return isManager && (status === "PREPARING" || status === "READY");
}
