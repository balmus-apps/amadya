import type { FloorTable, OrderStatus, TableRoundLine } from "@amadya/api-client";
import { fromCents, toCents } from "@amadya/customer-core";

export { fromCents, toCents };

/**
 * What one person pays when the rest of the bill is split equally among [people]: the floor of the share, so the
 * last person (people = 1) pays whatever is left and the cents always add up.
 */
export function equalShareCents(remainingCents: number, people: number): number {
  if (people <= 1) return remainingCents;
  return Math.floor(remainingCents / people);
}

export function unpaidUnits(line: Pick<TableRoundLine, "quantity" | "paidQuantity">): number {
  return Math.max(0, line.quantity - line.paidQuantity);
}

/**
 * Amount for the selected units, mirroring the API: units are charged their share of the line total, and paying the
 * last unpaid units takes whatever is left of the line. Display only; the API computes the payment.
 */
export function itemsAmountCents(lines: TableRoundLine[], selection: Record<string, number>): number {
  return lines.reduce((sum, line) => {
    const qty = selection[line.lineId] ?? 0;
    if (qty <= 0) return sum;
    const total = toCents(line.total.amount);
    const unpaid = unpaidUnits(line);
    if (qty >= unpaid) return sum + total - Math.round((total * line.paidQuantity) / line.quantity);
    return sum + Math.round((total * qty) / line.quantity);
  }, 0);
}

export type RoundState = "pending" | "preparing" | "ready" | "served" | "cancelled";

export function roundState(status: OrderStatus): RoundState {
  switch (status) {
    case "PLACED":
    case "PENDING_PAYMENT":
      return "pending";
    case "PREPARING":
      return "preparing";
    case "READY":
      return "ready";
    case "COMPLETED":
      return "served";
    case "CANCELLED":
      return "cancelled";
  }
}

export type TableState = "free" | "occupied" | "ready";

export function tableState(table: FloorTable): TableState {
  if (!table.session) return "free";
  return table.session.roundsReady > 0 ? "ready" : "occupied";
}

/** "12 min" since the guests sat down; "1h 05" past an hour. */
export function sinceLabel(iso: string, now: number): string {
  const minutes = Math.max(0, Math.floor((now - Date.parse(iso)) / 60_000));
  if (minutes < 60) return `${minutes} min`;
  return `${Math.floor(minutes / 60)}h ${String(minutes % 60).padStart(2, "0")}`;
}
