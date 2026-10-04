/**
 * Design tokens. Each install brands the apps through `theme` in GET /settings/public (ADR 0004);
 * missing tokens fall back to these defaults and derived colours are computed for contrast.
 */
export interface ThemeTokens {
  primary: string;
  onPrimary: string;
  secondary: string;
  accent: string;
  background: string;
  foreground: string;
  radius: string;
  fontHeading: string;
  fontBody: string;
}

export const defaultTheme: ThemeTokens = {
  primary: "#B3261E",
  onPrimary: "#FFFFFF",
  secondary: "#2E7D32",
  accent: "#D62828",
  background: "#FFF8F0",
  foreground: "#1F1A17",
  radius: "12px",
  fontHeading: "Rubik",
  fontBody: "Inter",
};

const HEX = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i;

export function resolveTheme(input?: Record<string, string> | null): ThemeTokens {
  const merged = { ...defaultTheme } as Record<string, string>;
  for (const [key, value] of Object.entries(input ?? {})) {
    if (!(key in defaultTheme) || !value) continue;
    const isColour = !["radius", "fontHeading", "fontBody"].includes(key);
    if (isColour && !HEX.test(value)) continue;
    merged[key] = value;
  }
  // A theme that sets `primary` without `onPrimary` gets a readable text colour automatically.
  if (input?.primary && !input.onPrimary) merged.onPrimary = readableOn(merged.primary!);
  return merged as unknown as ThemeTokens;
}

/** CSS custom properties consumed by @amadya/ui (Tailwind 4 `@theme inline`). */
export function themeCssVars(theme: ThemeTokens): Record<string, string> {
  return {
    "--background": theme.background,
    "--foreground": theme.foreground,
    "--primary": theme.primary,
    "--primary-foreground": theme.onPrimary,
    "--secondary": theme.secondary,
    "--secondary-foreground": readableOn(theme.secondary),
    "--accent": theme.accent,
    "--accent-foreground": readableOn(theme.accent),
    "--card": mix(theme.background, "#FFFFFF", 0.6),
    "--card-foreground": theme.foreground,
    "--muted": mix(theme.background, theme.foreground, 0.06),
    "--muted-foreground": mix(theme.foreground, theme.background, 0.4),
    "--border": mix(theme.background, theme.foreground, 0.12),
    "--ring": theme.primary,
    "--radius": theme.radius,
  };
}

/** Black or white, whichever reads better on the given colour (WCAG relative luminance). */
export function readableOn(hex: string): string {
  const [r, g, b] = rgb(hex).map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  }) as [number, number, number];
  const luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  return luminance > 0.179 ? "#111111" : "#FFFFFF";
}

export function mix(a: string, b: string, weight: number): string {
  const ca = rgb(a);
  const cb = rgb(b);
  return `#${ca.map((c, i) => Math.round(c * (1 - weight) + cb[i]! * weight).toString(16).padStart(2, "0")).join("")}`;
}

function rgb(hex: string): [number, number, number] {
  let h = hex.replace("#", "");
  if (h.length === 3) h = h.split("").map((c) => c + c).join("");
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)) as [number, number, number];
}
