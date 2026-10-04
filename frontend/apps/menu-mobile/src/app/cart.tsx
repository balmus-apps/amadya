import { formatMoney } from "@amadya/i18n";
import { mix } from "@amadya/theme";
import { router } from "expo-router";
import { FlatList, StyleSheet, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useTranslations } from "use-intl";
import { Button, Stepper, Text } from "@/components/ui";
import { useApp } from "@/lib/app-context";
import { cartTotals, fromCents, lineCents, useCart } from "@/lib/stores";

export default function CartScreen() {
  const t = useTranslations("cart");
  const { theme, locale, settings } = useApp();
  const items = useCart((s) => s.items);
  const setQuantity = useCart((s) => s.setQuantity);
  const { count, totalCents, currency } = cartTotals(items);
  const border = mix(theme.background, theme.foreground, 0.12);

  if (items.length === 0) {
    return (
      <View style={styles.empty}>
        <Text style={{ fontSize: 48 }}>🛍️</Text>
        <Text variant="heading">{t("empty")}</Text>
        <Text muted>{t("emptyHint")}</Text>
      </View>
    );
  }

  return (
    <View style={{ flex: 1 }}>
      <FlatList
        data={items}
        keyExtractor={(i) => i.key}
        contentContainerStyle={{ padding: 16 }}
        ListHeaderComponent={<Text muted style={{ marginBottom: 8 }}>{t("items", { count })}</Text>}
        ItemSeparatorComponent={() => <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: border }} />}
        renderItem={({ item }) => (
          <View style={styles.line}>
            <View style={{ flex: 1, gap: 2 }}>
              <Text variant="label">{item.name}</Text>
              {item.options.length > 0 && (
                <Text variant="small" muted>
                  {item.options.map((o) => o.name).join(", ")}
                </Text>
              )}
              {item.notes && (
                <Text variant="small" muted>
                  “{item.notes}”
                </Text>
              )}
              <Text style={{ fontWeight: "800" }}>{formatMoney({ amount: fromCents(lineCents(item)), currency: item.currency }, locale)}</Text>
            </View>
            <Stepper value={item.quantity} min={0} onChange={(q) => setQuantity(item.key, q)} />
          </View>
        )}
      />
      <SafeAreaView edges={["bottom"]} style={[styles.footer, { borderColor: border }]}>
        <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
          <Text variant="heading">{t("total")}</Text>
          <Text variant="heading">{formatMoney({ amount: fromCents(totalCents), currency }, locale)}</Text>
        </View>
        <Button
          title={t("checkout")}
          disabled={!settings?.features.takeaway}
          onPress={() => {
            router.back();
            router.push("/checkout");
          }}
        />
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  empty: { flex: 1, alignItems: "center", justifyContent: "center", gap: 8, padding: 32 },
  line: { flexDirection: "row", alignItems: "center", gap: 12, paddingVertical: 14 },
  footer: { padding: 16, gap: 12, borderTopWidth: StyleSheet.hairlineWidth },
});
