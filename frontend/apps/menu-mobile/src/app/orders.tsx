import { formatMoney, formatTime } from "@amadya/i18n";
import { mix } from "@amadya/theme";
import { router } from "expo-router";
import { FlatList, Pressable, StyleSheet, View } from "react-native";
import { Text } from "@/components/ui";
import { useApp } from "@/lib/app-context";
import { useRecentOrders } from "@/lib/stores";

export default function RecentOrdersScreen() {
  const { theme, locale } = useApp();
  const orders = useRecentOrders((s) => s.orders);
  return (
    <FlatList
      data={orders}
      keyExtractor={(o) => o.id}
      contentContainerStyle={{ padding: 16 }}
      ItemSeparatorComponent={() => <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: mix(theme.background, theme.foreground, 0.12) }} />}
      renderItem={({ item }) => (
        <Pressable onPress={() => router.push({ pathname: "/order/[id]", params: { id: item.id, t: item.token } })} style={styles.row}>
          <Text variant="heading" style={{ fontSize: 22 }}>
            {item.number}
          </Text>
          <Text muted style={{ flex: 1 }}>
            {new Date(item.createdAt).toLocaleDateString(locale)} {formatTime(item.createdAt, locale)}
          </Text>
          <Text style={{ fontWeight: "700" }}>{formatMoney(item.total, locale)}</Text>
        </Pressable>
      )}
    />
  );
}

const styles = StyleSheet.create({ row: { flexDirection: "row", alignItems: "center", gap: 12, paddingVertical: 16 } });
