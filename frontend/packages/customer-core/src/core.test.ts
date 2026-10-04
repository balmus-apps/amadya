import { describe, expect, it } from "vitest";
import { cartKey, cartTotals, fromCents, unitCents, type CartItem } from "./cart";
import { initialSelection, missingGroups, toggleOption } from "./modifiers";
import { isOpenNow } from "./opening-hours";

const pita: CartItem = {
  key: "",
  productId: "p1",
  name: "Lipie cu pui",
  unitPrice: "20.00",
  currency: "RON",
  quantity: 2,
  options: [
    { id: "o2", name: "Bacon", priceDelta: "5.00" },
    { id: "o1", name: "Sos cheese", priceDelta: "0.00" },
  ],
};

describe("cart", () => {
  it("prices options in cents", () => {
    expect(unitCents(pita)).toBe(2500);
    expect(fromCents(cartTotals([pita]).totalCents)).toBe("50.00");
    expect(cartTotals([pita]).count).toBe(2);
  });

  it("merges lines with the same options regardless of order", () => {
    const reordered = { ...pita, options: [...pita.options].reverse() };
    expect(cartKey(pita)).toBe(cartKey(reordered));
    expect(cartKey(pita)).not.toBe(cartKey({ ...pita, notes: "fără ceapă" }));
  });
});

describe("opening hours", () => {
  // 2026-10-05 is a Monday; 09:30 UTC = 12:30 in Bucharest (UTC+3)
  const monday1230 = new Date("2026-10-05T09:30:00Z");

  it("is open inside today's hours", () => {
    expect(isOpenNow([{ dayOfWeek: 1, opens: "11:00", closes: "22:00" }], monday1230)).toBe(true);
    expect(isOpenNow([{ dayOfWeek: 1, opens: "13:00", closes: "22:00" }], monday1230)).toBe(false);
  });

  it("handles hours past midnight", () => {
    const monday0100 = new Date("2026-10-04T22:00:00Z"); // Monday 01:00 local
    expect(isOpenNow([{ dayOfWeek: 7, opens: "18:00", closes: "02:00" }], monday0100)).toBe(true);
  });

  it("is always open without a schedule", () => {
    expect(isOpenNow([], monday1230)).toBe(true);
  });
});

describe("modifiers", () => {
  const sauce = { id: "g1", name: "Sos", minSelect: 1, maxSelect: 1, options: [{ id: "a", name: "Cheese", priceDelta: { amount: "0", currency: "RON" } }, { id: "b", name: "Usturoi", priceDelta: { amount: "0", currency: "RON" } }] };
  const extras = { id: "g2", name: "Extra", minSelect: 0, maxSelect: 2, options: ["x", "y", "z"].map((id) => ({ id, name: id, priceDelta: { amount: "1", currency: "RON" } })) };

  it("preselects required single choices", () => {
    expect(initialSelection([sauce, extras])).toEqual({ g1: ["a"] });
  });

  it("enforces the maximum", () => {
    let s = toggleOption({}, extras, "x", true);
    s = toggleOption(s, extras, "y", true);
    s = toggleOption(s, extras, "z", true);
    expect(s.g2).toEqual(["x", "y"]);
    expect(toggleOption(s, sauce, "b", true).g1).toEqual(["b"]);
  });

  it("reports missing required groups", () => {
    expect(missingGroups([sauce], {}).map((g) => g.id)).toEqual(["g1"]);
    expect(missingGroups([sauce], { g1: ["a"] })).toEqual([]);
  });
});
