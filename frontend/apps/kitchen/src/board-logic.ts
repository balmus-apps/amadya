import type { KitchenTicket, KitchenTicketEvent, KitchenTicketStatus } from "@amadya/api-client";

export const columns: KitchenTicketStatus[] = ["QUEUED", "IN_PROGRESS", "READY"];

/** Done tickets stay on screen this long (with Undo), then disappear. */
export const DONE_VISIBLE_MS = 10 * 60_000;

export type Board = Map<string, KitchenTicket>;

export function applyEvent(board: Board, event: KitchenTicketEvent): Board {
  const next = new Map(board);
  if (event.type === "REMOVED" || !event.ticket) next.delete(event.ticketId);
  else next.set(event.ticketId, event.ticket);
  return next;
}

/** Oldest first in the working columns; most recent first in Done. */
export function group(board: Board, now: number): Record<KitchenTicketStatus, KitchenTicket[]> {
  const result: Record<KitchenTicketStatus, KitchenTicket[]> = { QUEUED: [], IN_PROGRESS: [], READY: [] };
  for (const t of board.values()) {
    if (t.status === "READY" && t.readyAt && now - Date.parse(t.readyAt) > DONE_VISIBLE_MS) continue;
    result[t.status].push(t);
  }
  result.QUEUED.sort((a, b) => Date.parse(a.queuedAt) - Date.parse(b.queuedAt));
  result.IN_PROGRESS.sort((a, b) => Date.parse(a.queuedAt) - Date.parse(b.queuedAt));
  result.READY.sort((a, b) => Date.parse(b.readyAt ?? b.queuedAt) - Date.parse(a.readyAt ?? a.queuedAt));
  return result;
}

export function isLate(t: KitchenTicket, now: number) {
  return t.status !== "READY" && now > Date.parse(t.estimatedReadyAt);
}

/** "4:07" since the ticket arrived (or was finished, for Done); "4h 56m" past an hour. */
export function elapsed(t: KitchenTicket, now: number) {
  const from = Date.parse(t.queuedAt);
  const to = t.status === "READY" && t.readyAt ? Date.parse(t.readyAt) : now;
  const s = Math.max(0, Math.floor((to - from) / 1000));
  if (s >= 3600) return `${Math.floor(s / 3600)}h ${String(Math.floor((s % 3600) / 60)).padStart(2, "0")}m`;
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

export function nextAction(status: KitchenTicketStatus): "start" | "ready" | null {
  return status === "QUEUED" ? "start" : status === "IN_PROGRESS" ? "ready" : null;
}
