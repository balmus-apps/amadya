import type { ConfigContext, ExpoConfig } from "expo/config";

/**
 * White-label app config: every restaurant install builds its own app from these env vars
 * (single-tenant, ADR 0004). The API URL decides which restaurant the app talks to.
 */
export default ({ config }: ConfigContext): ExpoConfig => {
  const name = process.env.APP_NAME ?? "Amadya";
  const bundleId = process.env.APP_BUNDLE_ID ?? "ro.amadya.menu";
  const primary = process.env.APP_PRIMARY_COLOR ?? "#FFC400";
  return {
    ...config,
    name,
    slug: process.env.APP_SLUG ?? "amadya-menu",
    scheme: process.env.APP_SCHEME ?? "amadya",
    version: "0.1.0",
    orientation: "portrait",
    icon: "./assets/icon.png",
    userInterfaceStyle: "light",
    ios: { supportsTablet: false, bundleIdentifier: bundleId },
    android: {
      package: bundleId,
      adaptiveIcon: {
        backgroundColor: primary,
        foregroundImage: "./assets/android-icon-foreground.png",
        backgroundImage: "./assets/android-icon-background.png",
        monochromeImage: "./assets/android-icon-monochrome.png",
      },
    },
    web: { favicon: "./assets/favicon.png", bundler: "metro" },
    plugins: [
      "expo-router",
      "expo-localization",
      ["expo-notifications", { color: primary }],
      ["@stripe/stripe-react-native", { merchantIdentifier: process.env.APPLE_MERCHANT_ID ?? `merchant.${bundleId}`, enableGooglePay: true }],
    ],
    experiments: { typedRoutes: true },
    extra: {
      eas: { projectId: process.env.EAS_PROJECT_ID },
    },
  };
};
