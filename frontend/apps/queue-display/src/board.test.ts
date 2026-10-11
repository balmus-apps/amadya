import type { QueueBoard } from "@amadya/api-client";
import { describe, expect, it } from "vitest";
import { fit, isFresh, newlyReady, spokenNumber, visibleReady } from "./board";

const at = (s: number) => new Date(Date.parse("2026-10-11T12:00:00Z") + s * 1000).toISOString();
const board = (ready: [string, number][], windowSec = 1200): QueueBoard => ({
  preparing: [],
  ready: ready.map(([n]) => n),
  readyOrders: ready.map(([number, s]) => ({ number, readyAt: at(s) })),
  readyVisibleSeconds: windowSec,
});

describe("queue board", () => {
  it("announces only numbers that just became ready, oldest first", () => {
    const before = board([["B-001", 0]]);
    const after = board([["B-003", 20], ["B-002", 10], ["B-001", 0]]);
    expect(newlyReady(undefined, after)).toEqual([]);
    expect(newlyReady(before, after)).toEqual(["B-002", "B-003"]);
  });

  it("drops numbers past the visible window", () => {
    const b = board([["B-002", 100], ["B-001", 0]], 60);
    expect(visibleReady(b, Date.parse(at(90))).map((o) => o.number)).toEqual(["B-002"]);
  });

  it("highlights fresh numbers for a minute", () => {
    const entry = { number: "B-001", readyAt: at(0) };
    expect(isFresh(entry, Date.parse(at(30)))).toBe(true);
    expect(isFresh(entry, Date.parse(at(61)))).toBe(false);
  });

  it("caps what fits and counts the rest", () => {
    expect(fit([1, 2, 3, 4], 3)).toEqual({ shown: [1, 2, 3], more: 1 });
    expect(fit([1], 3)).toEqual({ shown: [1], more: 0 });
  });

  it("reads numbers naturally", () => {
    expect(spokenNumber("B-007")).toBe("B 7");
    expect(spokenNumber("B-120")).toBe("B 120");
  });
});
