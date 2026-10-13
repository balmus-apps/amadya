import type { Order } from "@amadya/api-client";
import { describe, expect, it } from "vitest";
import { canCancel, groupOrders, listOf, secondsUntilAuto } from "./lists";

const order = (id: string, status: Order["status"], channel: Order["channel"], createdAt: string) => ({ id, status, channel, createdAt }) as Order;

describe("counter lists", () => {
  it("puts each to-go and counter order in its list, oldest first", () => {
    const lists = groupOrders([
      order("b", "AWAITING_CONFIRMATION", "TAKEAWAY", "2026-10-13T10:05:00Z"),
      order("a", "AWAITING_CONFIRMATION", "TAKEAWAY", "2026-10-13T10:01:00Z"),
      order("c", "PREPARING", "COUNTER", "2026-10-13T10:00:00Z"),
      order("d", "READY", "TAKEAWAY", "2026-10-13T09:00:00Z"),
      order("e", "READY", "DINE_IN", "2026-10-13T09:00:00Z"),
      order("f", "COMPLETED", "COUNTER", "2026-10-13T09:00:00Z"),
    ]);
    expect(lists.confirm.map((o) => o.id)).toEqual(["a", "b"]);
    expect(lists.progress.map((o) => o.id)).toEqual(["c"]);
    expect(lists.ready.map((o) => o.id)).toEqual(["d"]);
  });

  it("never shows table rounds", () => {
    expect(listOf({ status: "PLACED", channel: "DINE_IN" })).toBeNull();
  });

  it("counts down to the automatic confirmation", () => {
    const created = "2026-10-13T10:00:00Z";
    expect(secondsUntilAuto(created, 5, Date.parse("2026-10-13T10:01:00Z"))).toBe(240);
    expect(secondsUntilAuto(created, 5, Date.parse("2026-10-13T10:09:00Z"))).toBe(0);
  });

  it("lets only managers cancel food being prepared", () => {
    expect(canCancel("PLACED", false)).toBe(true);
    expect(canCancel("PREPARING", false)).toBe(false);
    expect(canCancel("PREPARING", true)).toBe(true);
    expect(canCancel("AWAITING_CONFIRMATION", true)).toBe(false);
  });
});
