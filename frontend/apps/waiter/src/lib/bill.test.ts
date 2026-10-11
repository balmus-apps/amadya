import type { TableRoundLine } from "@amadya/api-client";
import { describe, expect, it } from "vitest";
import { equalShareCents, itemsAmountCents, roundState, sinceLabel, tableState, unpaidUnits } from "./bill";

const line = (lineId: string, quantity: number, total: string, paidQuantity = 0): TableRoundLine => ({
  lineId,
  productName: lineId,
  modifiers: [],
  quantity,
  paidQuantity,
  unitPrice: { amount: (Number(total) / quantity).toFixed(2), currency: "RON" },
  total: { amount: total, currency: "RON" },
});

describe("equal split", () => {
  it("rounds shares down and lets the last person pay the rest", () => {
    // 100.00 between 3: 33.33 + 33.33 + 33.34
    const first = equalShareCents(10_000, 3);
    const second = equalShareCents(10_000 - first, 2);
    const last = equalShareCents(10_000 - first - second, 1);
    expect([first, second, last]).toEqual([3333, 3333, 3334]);
    expect(first + second + last).toBe(10_000);
  });
});

describe("pay by items", () => {
  it("charges each unit its share and the last units the rest of the line", () => {
    const lines = [line("a", 3, "10.00"), line("b", 1, "14.00")];
    expect(itemsAmountCents(lines, { a: 1 })).toBe(333);
    expect(itemsAmountCents(lines, { a: 3, b: 1 })).toBe(2400);
    // after one unit of "a" was paid (3.33), the other two cost 6.67
    expect(itemsAmountCents([line("a", 3, "10.00", 1)], { a: 2 })).toBe(667);
    expect(unpaidUnits(line("a", 3, "10.00", 1))).toBe(2);
  });
});

describe("states", () => {
  it("maps order statuses to round states", () => {
    expect(roundState("PLACED")).toBe("pending");
    expect(roundState("PREPARING")).toBe("preparing");
    expect(roundState("READY")).toBe("ready");
    expect(roundState("COMPLETED")).toBe("served");
  });

  it("marks tables with a ready round", () => {
    expect(tableState({ id: "t", label: "1", seats: 4 })).toBe("free");
    const session = { id: "s", guests: 2, openedAt: "", total: { amount: "0", currency: "RON" }, remaining: { amount: "0", currency: "RON" }, roundsInKitchen: 1, roundsReady: 0 };
    expect(tableState({ id: "t", label: "1", seats: 4, session })).toBe("occupied");
    expect(tableState({ id: "t", label: "1", seats: 4, session: { ...session, roundsReady: 1 } })).toBe("ready");
  });

  it("formats time at the table", () => {
    const now = Date.parse("2026-10-11T12:00:00Z");
    expect(sinceLabel("2026-10-11T11:48:00Z", now)).toBe("12 min");
    expect(sinceLabel("2026-10-11T10:55:00Z", now)).toBe("1h 05");
  });
});
