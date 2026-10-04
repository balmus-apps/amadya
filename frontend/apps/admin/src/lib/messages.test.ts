import { describe, expect, it } from "vitest";
import en from "../messages/en.json";
import ro from "../messages/ro.json";

function keys(obj: object, prefix = ""): string[] {
  return Object.entries(obj).flatMap(([k, v]) => (typeof v === "object" && v !== null ? keys(v, `${prefix}${k}.`) : [`${prefix}${k}`]));
}

describe("admin messages", () => {
  it("ro and en define the same keys", () => {
    expect(keys(en).sort()).toEqual(keys(ro).sort());
  });
});
