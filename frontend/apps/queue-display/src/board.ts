import type { QueueBoard } from "@amadya/api-client";

export type ReadyEntry = QueueBoard["readyOrders"][number];

/** How long a number just ready is highlighted on screen. */
export const FRESH_MS = 60_000;

/** Ready orders still within the visible window at [now]; the API already filters, this keeps a frozen screen honest. */
export function visibleReady(board: QueueBoard, now: number): ReadyEntry[] {
  return board.readyOrders.filter((o) => now - Date.parse(o.readyAt) < board.readyVisibleSeconds * 1000);
}

/** Numbers that became ready since the previous board, oldest first so they are announced in order. */
export function newlyReady(previous: QueueBoard | undefined, next: QueueBoard): string[] {
  if (!previous) return [];
  const before = new Set(previous.readyOrders.map((o) => o.number));
  return next.readyOrders
    .filter((o) => !before.has(o.number))
    .sort((a, b) => Date.parse(a.readyAt) - Date.parse(b.readyAt))
    .map((o) => o.number);
}

export function isFresh(entry: ReadyEntry, now: number) {
  return now - Date.parse(entry.readyAt) < FRESH_MS;
}

/** Shows at most [max] and how many did not fit. */
export function fit<T>(items: T[], max: number): { shown: T[]; more: number } {
  return items.length > max ? { shown: items.slice(0, max), more: items.length - max } : { shown: items, more: 0 };
}

/** "B-007" read aloud as "B 7" in both languages. */
export function spokenNumber(number: string): string {
  return number.replace(/^([A-Za-z]+)-0*(\d+)$/, "$1 $2");
}
