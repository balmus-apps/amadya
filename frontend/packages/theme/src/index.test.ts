import { describe, expect, it } from "vitest";
import { defaultTheme, mix, readableOn, resolveTheme, themeCssVars } from "./index";

describe("theme", () => {
  it("uses defaults for missing or invalid tokens", () => {
    const theme = resolveTheme({ primary: "not-a-colour", radius: "8px", unknown: "x" });
    expect(theme.primary).toBe(defaultTheme.primary);
    expect(theme.radius).toBe("8px");
  });

  it("derives readable text on the primary colour", () => {
    expect(resolveTheme({ primary: "#FFC400" }).onPrimary).toBe("#111111");
    expect(resolveTheme({ primary: "#111111" }).onPrimary).toBe("#FFFFFF");
    expect(resolveTheme({ primary: "#FFC400", onPrimary: "#222222" }).onPrimary).toBe("#222222");
  });

  it("exposes css variables", () => {
    const vars = themeCssVars(resolveTheme({ primary: "#FFC400" }));
    expect(vars["--primary"]).toBe("#FFC400");
    expect(vars["--primary-foreground"]).toBe("#111111");
  });

  it("mixes colours", () => {
    expect(mix("#000000", "#ffffff", 0.5)).toBe("#808080");
    expect(readableOn("#fff")).toBe("#111111");
  });
});
