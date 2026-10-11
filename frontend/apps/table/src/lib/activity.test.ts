import type { TableActivity } from "@amadya/api-client";
import { describe, expect, it } from "vitest";
import en from "../messages/en.json";
import ro from "../messages/ro.json";
import { activityText, itemsText } from "./activity";

function translator(messages: Record<string, unknown>) {
  return (key: string, values: Record<string, string | number> = {}) => {
    const raw = key.split(".").reduce<unknown>((node, k) => (node as Record<string, unknown>)[k], messages.table) as string;
    return raw.replace(/\{(\w+)\}/g, (_, name: string) => String(values[name] ?? ""));
  };
}

const entry = (e: Partial<TableActivity>): TableActivity => ({ at: "2026-10-11T12:00:00Z", kind: "JOINED", actorKind: "GUEST", ...e });

describe("activity log", () => {
  const t = translator(ro);

  it("tells who joined, ordered and paid", () => {
    expect(activityText(entry({ actor: "Andrei" }), t, "ro")).toBe("Andrei s-a alăturat mesei");
    const items = [{ name: "Cafea", quantity: 2 }, { name: "Steak", quantity: 1 }];
    expect(activityText(entry({ kind: "ORDERED", actor: "Robert", items }), t, "ro")).toBe("Robert a comandat 2 × Cafea, 1 × Steak");
    const paid = activityText(entry({ kind: "PAID", actor: "Mircea", method: "ONLINE", amount: { amount: "28.00", currency: "RON" }, items: [{ name: "Înghețată", quantity: 2 }] }), t, "ro");
    expect(paid).toContain("Mircea a plătit");
    expect(paid).toContain("2 × Înghețată");
  });

  it("marks what was confirmed automatically", () => {
    expect(activityText(entry({ kind: "ROUND_CONFIRMED", actorKind: "SYSTEM", automatic: true, items: [{ name: "Limonadă", quantity: 1 }] }), t, "ro")).toContain("automat");
  });

  it("joins items", () => {
    expect(itemsText([{ name: "A", quantity: 1 }, { name: "B", quantity: 3 }])).toBe("1 × A, 3 × B");
  });

  it("has the same texts in both languages", () => {
    const keys = (o: object, p = ""): string[] => Object.entries(o).flatMap(([k, v]) => (typeof v === "object" && v ? keys(v, `${p}${k}.`) : [`${p}${k}`]));
    expect(keys(en).sort()).toEqual(keys(ro).sort());
  });
});
