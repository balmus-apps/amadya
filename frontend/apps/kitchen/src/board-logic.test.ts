import type { KitchenTicket } from "@amadya/api-client";
import { describe, expect, it } from "vitest";
import { applyEvent, elapsed, group, isLate, nextAction } from "./board-logic";

const t = (id: string, status: KitchenTicket["status"], queuedAt: string, extra: Partial<KitchenTicket> = {}): KitchenTicket => ({
  id,
  orderId: "o" + id,
  orderNumber: "B-00" + id,
  channel: "COUNTER",
  stationId: "s",
  status,
  queuedAt,
  estimatedReadyAt: "2026-10-05T10:10:00Z",
  lines: [],
  ...extra,
});

describe("kitchen board", () => {
  const now = Date.parse("2026-10-05T10:12:00Z");

  it("groups by status, oldest first, and hides done tickets after 10 minutes", () => {
    let board = new Map<string, KitchenTicket>();
    board = applyEvent(board, { type: "UPSERT", ticketId: "2", stationId: "s", ticket: t("2", "QUEUED", "2026-10-05T10:05:00Z") });
    board = applyEvent(board, { type: "UPSERT", ticketId: "1", stationId: "s", ticket: t("1", "QUEUED", "2026-10-05T10:01:00Z") });
    board = applyEvent(board, { type: "UPSERT", ticketId: "3", stationId: "s", ticket: t("3", "READY", "2026-10-05T09:40:00Z", { readyAt: "2026-10-05T09:55:00Z" }) });
    board = applyEvent(board, { type: "UPSERT", ticketId: "4", stationId: "s", ticket: t("4", "READY", "2026-10-05T10:00:00Z", { readyAt: "2026-10-05T10:08:00Z" }) });
    const g = group(board, now);
    expect(g.QUEUED.map((x) => x.id)).toEqual(["1", "2"]);
    expect(g.READY.map((x) => x.id)).toEqual(["4"]);
  });

  it("removes tickets of cancelled orders", () => {
    const board = applyEvent(new Map([["1", t("1", "QUEUED", "2026-10-05T10:01:00Z")]]), { type: "REMOVED", ticketId: "1", stationId: "s" });
    expect(board.size).toBe(0);
  });

  it("flags late tickets and formats elapsed time", () => {
    expect(isLate(t("1", "IN_PROGRESS", "2026-10-05T10:00:00Z"), now)).toBe(true);
    expect(isLate(t("1", "READY", "2026-10-05T10:00:00Z", { readyAt: "2026-10-05T10:11:00Z" }), now)).toBe(false);
    expect(elapsed(t("1", "QUEUED", "2026-10-05T10:07:53Z"), now)).toBe("4:07");
    expect(elapsed(t("1", "QUEUED", "2026-10-05T05:15:00Z"), now)).toBe("4h 57m");
  });

  it("tap moves pending to in preparation to done", () => {
    expect(nextAction("QUEUED")).toBe("start");
    expect(nextAction("IN_PROGRESS")).toBe("ready");
    expect(nextAction("READY")).toBeNull();
  });
});
