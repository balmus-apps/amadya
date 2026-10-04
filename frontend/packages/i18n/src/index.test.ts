import { describe, expect, it } from "vitest";
import { formatMoney, messages, normalizePhone } from "./index";

function keys(obj: object, prefix = ""): string[] {
  return Object.entries(obj).flatMap(([k, v]) => (typeof v === "object" && v !== null ? keys(v, `${prefix}${k}.`) : [`${prefix}${k}`]));
}

describe("messages", () => {
  it("ro and en define the same keys", () => {
    expect(keys(messages.en).sort()).toEqual(keys(messages.ro).sort());
  });
});

describe("normalizePhone", () => {
  it.each([
    ["0722 123 456", "+40722123456"],
    ["+40 722-123-456", "+40722123456"],
    ["0040722123456", "+40722123456"],
    ["40722123456", "+40722123456"],
    ["722123456", "+40722123456"],
  ])("%s -> %s", (input, expected) => expect(normalizePhone(input)).toBe(expected));

  it("rejects garbage", () => {
    expect(normalizePhone("12")).toBeNull();
    expect(normalizePhone("")).toBeNull();
  });
});

describe("formatMoney", () => {
  it("formats RON per locale", () => {
    expect(formatMoney({ amount: "20.00", currency: "RON" }, "ro")).toMatch(/20,00\s?RON|20,00\s?lei/);
    expect(formatMoney({ amount: "20.00", currency: "RON" }, "en")).toContain("20.00");
  });
});
