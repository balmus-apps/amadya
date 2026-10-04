import { mix, readableOn } from "@amadya/theme";
import * as Haptics from "expo-haptics";
import type { ReactNode } from "react";
import { ActivityIndicator, Platform, Pressable, StyleSheet, Text as RNText, View, type StyleProp, type TextProps, type ViewStyle } from "react-native";
import { useTheme } from "@/lib/app-context";

export function radius(theme: { radius: string }, extra = 0) {
  return (parseInt(theme.radius, 10) || 12) + extra;
}

export function Text({ style, variant = "body", muted, ...props }: TextProps & { variant?: "title" | "heading" | "body" | "small" | "label"; muted?: boolean }) {
  const theme = useTheme();
  return (
    <RNText
      style={[
        { color: muted ? mix(theme.foreground, theme.background, 0.4) : theme.foreground },
        styles[variant],
        style,
      ]}
      {...props}
    />
  );
}

export function Button({
  title,
  onPress,
  variant = "primary",
  disabled,
  loading,
  style,
  right,
}: {
  title: string;
  onPress: () => void;
  variant?: "primary" | "outline" | "dark";
  disabled?: boolean;
  loading?: boolean;
  style?: StyleProp<ViewStyle>;
  right?: ReactNode;
}) {
  const theme = useTheme();
  const bg = variant === "primary" ? theme.primary : variant === "dark" ? theme.foreground : "transparent";
  const fg = variant === "primary" ? theme.onPrimary : variant === "dark" ? readableOn(theme.foreground) : theme.foreground;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!disabled }}
      disabled={disabled || loading}
      onPress={() => {
        if (Platform.OS !== "web") void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
        onPress();
      }}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: bg, borderRadius: radius(theme, 2), opacity: disabled ? 0.45 : pressed ? 0.85 : 1 },
        variant === "outline" && { borderWidth: 1, borderColor: mix(theme.background, theme.foreground, 0.15) },
        style,
      ]}
    >
      {loading ? <ActivityIndicator color={fg} /> : <RNText style={[styles.buttonText, { color: fg }]}>{title}</RNText>}
      {right}
    </Pressable>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  const theme = useTheme();
  return (
    <View
      style={[
        { backgroundColor: mix(theme.background, "#FFFFFF", 0.6), borderRadius: radius(theme, 4), borderWidth: 1, borderColor: mix(theme.background, theme.foreground, 0.1), padding: 16 },
        style,
      ]}
    >
      {children}
    </View>
  );
}

export function Badge({ label, tone = "muted" }: { label: string; tone?: "muted" | "accent" | "success" }) {
  const theme = useTheme();
  const bg = tone === "accent" ? theme.accent : tone === "success" ? "#2E7D32" : mix(theme.background, theme.foreground, 0.08);
  const fg = tone === "muted" ? mix(theme.foreground, theme.background, 0.35) : readableOn(bg);
  return (
    <View style={{ backgroundColor: bg, borderRadius: 999, paddingHorizontal: 8, paddingVertical: 2, alignSelf: "flex-start" }}>
      <RNText style={{ color: fg, fontSize: 11, fontWeight: "700" }}>{label}</RNText>
    </View>
  );
}

export function Stepper({ value, onChange, min = 1, max = 99 }: { value: number; onChange: (v: number) => void; min?: number; max?: number }) {
  const theme = useTheme();
  const btn = (label: string, delta: number, disabled: boolean) => (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      disabled={disabled}
      onPress={() => onChange(value + delta)}
      style={[styles.stepperButton, { borderColor: mix(theme.background, theme.foreground, 0.15), opacity: disabled ? 0.35 : 1 }]}
    >
      <RNText style={{ fontSize: 20, fontWeight: "700", color: theme.foreground }}>{label}</RNText>
    </Pressable>
  );
  return (
    <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
      {btn("−", -1, value <= min)}
      <RNText style={{ minWidth: 22, textAlign: "center", fontSize: 17, fontWeight: "700", color: theme.foreground }}>{value}</RNText>
      {btn("+", 1, value >= max)}
    </View>
  );
}

const icons: [RegExp, string][] = [
  [/burger/i, "🍔"],
  [/lipie|wrap|sandv|sandwich|burrito|pita/i, "🌯"],
  [/cola|ap[aă]|water|suc|juice|drink/i, "🥤"],
  [/fasole|bean|ciorb|sup[aă]|soup/i, "🍲"],
  [/platou|platter|gr[aă]tar|grill/i, "🍖"],
  [/cartof|fries/i, "🍟"],
];

/** Placeholder art until the restaurant uploads product photos. */
export function ProductArt({ name, size = 96 }: { name: string; size?: number | "100%" }) {
  const theme = useTheme();
  const emoji = icons.find(([re]) => re.test(name))?.[1] ?? "🍽️";
  return (
    <View
      style={{
        width: size,
        height: size === "100%" ? 180 : size,
        borderRadius: size === "100%" ? 0 : radius(theme),
        backgroundColor: theme.primary,
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <RNText style={{ fontSize: size === "100%" ? 72 : 40 }}>{emoji}</RNText>
    </View>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: 28, lineHeight: 34, fontWeight: "800", letterSpacing: -0.5 },
  heading: { fontSize: 19, lineHeight: 24, fontWeight: "800" },
  body: { fontSize: 15, lineHeight: 21 },
  small: { fontSize: 13, lineHeight: 18 },
  label: { fontSize: 14, fontWeight: "600" },
  button: { minHeight: 52, paddingHorizontal: 18, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 10 },
  buttonText: { fontSize: 16, fontWeight: "700" },
  stepperButton: { width: 40, height: 40, borderRadius: 20, borderWidth: 1, alignItems: "center", justifyContent: "center" },
});
