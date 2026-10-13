import { describe, expect, it } from "vitest";
import { posMessages } from "./messages";
import { normalize } from "./menu-picker";
import { paymentValid, quickNotes } from "./payment-panel";

describe("payment", () => {
  it("accepts an empty or covering cash amount, and any card payment", () => {
    expect(paymentValid("CASH", "", 4100)).toBe(true);
    expect(paymentValid("CASH", "50", 4100)).toBe(true);
    expect(paymentValid("CASH", "41,00", 4100)).toBe(true);
    expect(paymentValid("CASH", "40", 4100)).toBe(false);
    expect(paymentValid("CARD_POS", "", 4100)).toBe(true);
    expect(paymentValid("LATER", "", 4100)).toBe(true);
    expect(paymentValid("CASH", "", 0)).toBe(false);
  });

  it("offers the next two banknotes above the amount", () => {
    expect(quickNotes(4100)).toEqual([5000, 10_000]);
    expect(quickNotes(60_000)).toEqual([]);
  });
});

describe("menu search", () => {
  it("ignores case and diacritics", () => {
    expect(normalize(" Ciorbă ")).toBe("ciorba");
  });
});

describe("messages", () => {
  const keys = (o: object, prefix = ""): string[] =>
    Object.entries(o).flatMap(([k, v]) => (typeof v === "object" ? keys(v, `${prefix}${k}.`) : [`${prefix}${k}`]));
  it("has the same keys in Romanian and English", () => {
    expect(keys(posMessages.ro).sort()).toEqual(keys(posMessages.en).sort());
  });
});
