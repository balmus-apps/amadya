import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useState } from "react";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { useTranslations } from "use-intl";
import { AppProvider, useTheme } from "@/lib/app-context";

export default function RootLayout() {
  const [queryClient] = useState(() => new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: 1 } } }));
  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <AppProvider>
          <Navigator />
        </AppProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}

function Navigator() {
  const theme = useTheme();
  const t = useTranslations();
  return (
    <>
      <StatusBar style="dark" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: theme.background },
          headerTintColor: theme.foreground,
          headerTitleStyle: { fontWeight: "800" },
          headerShadowVisible: false,
          contentStyle: { backgroundColor: theme.background },
        }}
      >
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen name="product/[id]" options={{ presentation: "modal", headerShown: false }} />
        <Stack.Screen name="cart" options={{ presentation: "modal", title: t("cart.title") }} />
        <Stack.Screen name="checkout" options={{ title: t("checkout.title") }} />
        <Stack.Screen name="order/[id]" options={{ title: "", headerBackVisible: false }} />
        <Stack.Screen name="orders" options={{ title: t("order.recent") }} />
      </Stack>
    </>
  );
}
