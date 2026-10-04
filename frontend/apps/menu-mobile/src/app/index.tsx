import { formatMoney } from "@amadya/i18n";
import { mix } from "@amadya/theme";
import { Link, router } from "expo-router";
import { useMemo, useRef, useState } from "react";
import { ActivityIndicator, Pressable, ScrollView, SectionList, StyleSheet, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useTranslations } from "use-intl";
import { Badge, Button, ProductArt, radius, Text } from "@/components/ui";
import { useApp } from "@/lib/app-context";
import { useMenu } from "@/lib/menu";
import { cartTotals, fromCents, isOpenNow, todaysHours, useCart, useRecentOrders } from "@/lib/stores";

export default function MenuScreen() {
  const t = useTranslations();
  const { settings, theme, locale, setLocale } = useApp();
  const { data: menu, isLoading, refetch, isRefetching } = useMenu();
  const items = useCart((s) => s.items);
  const recent = useRecentOrders((s) => s.orders);
  const add = useCart((s) => s.add);
  const list = useRef<SectionList>(null);
  const [active, setActive] = useState(0);
  const { count, totalCents, currency } = cartTotals(items);
  const sections = useMemo(() => (menu?.categories ?? []).map((c) => ({ key: c.id, title: c.name, data: c.products })), [menu]);
  const open = settings ? isOpenNow(settings.openingHours) : true;
  const today = settings ? todaysHours(settings.openingHours) : undefined;
  const border = mix(theme.background, theme.foreground, 0.12);

  if (isLoading || !menu) {
    return (
      <View style={[styles.center, { backgroundColor: theme.background }]}>
        <ActivityIndicator color={theme.primary} size="large" />
      </View>
    );
  }

  return (
    <SafeAreaView edges={["top"]} style={{ flex: 1, backgroundColor: theme.background }}>
      <View style={[styles.header, { borderColor: border }]}>
        <View style={[styles.logo, { backgroundColor: theme.primary }]}>
          <Text style={{ color: theme.onPrimary, fontWeight: "800", fontSize: 18 }}>{settings?.name.charAt(0)}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text variant="heading" numberOfLines={1}>
            {settings?.name}
          </Text>
          {today && (
            <View style={{ flexDirection: "row", gap: 6, alignItems: "center" }}>
              <Badge label={open ? t("common.open") : t("common.closed")} tone={open ? "success" : "muted"} />
              <Text variant="small" muted>
                {today.opens}–{today.closes}
              </Text>
            </View>
          )}
        </View>
        {recent.length > 0 && (
          <Link href="/orders" asChild>
            <Pressable accessibilityLabel={t("menu.myOrders")} hitSlop={8}>
              <Text style={{ fontSize: 22 }}>🧾</Text>
            </Pressable>
          </Link>
        )}
        <Pressable onPress={() => setLocale(locale === "ro" ? "en" : "ro")} style={[styles.lang, { borderColor: border }]} accessibilityLabel={t("common.language")}>
          <Text variant="label">{locale === "ro" ? "EN" : "RO"}</Text>
        </Pressable>
      </View>

      <SectionList
        ref={list}
        sections={sections}
        keyExtractor={(item) => item.id}
        stickySectionHeadersEnabled={false}
        onRefresh={refetch}
        refreshing={isRefetching}
        contentContainerStyle={{ paddingBottom: 120 }}
        onViewableItemsChanged={({ viewableItems }) => {
          const first = viewableItems.find((v) => v.section);
          if (first?.section) setActive(sections.findIndex((s) => s.key === (first.section as { key: string }).key));
        }}
        ListHeaderComponent={
          <View>
            {!settings?.features.takeaway && (
              <View style={[styles.notice, { borderColor: theme.accent }]}>
                <Text>{t("menu.takeawayOff")}</Text>
              </View>
            )}
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ padding: 16, gap: 12 }} snapToInterval={300} decelerationRate="fast">
              {menu.promotions.map((promo, i) => (
                <Pressable
                  key={promo.id}
                  onPress={() => promo.productId && router.push({ pathname: "/product/[id]", params: { id: promo.productId } })}
                  style={[styles.promo, { backgroundColor: i % 2 === 0 ? theme.foreground : theme.primary, borderRadius: radius(theme, 8) }]}
                >
                  {promo.badge && (
                    <View style={[styles.promoBadge, { backgroundColor: theme.accent }]}>
                      <Text style={{ color: "#fff", fontWeight: "900", fontSize: 18 }}>{promo.badge}</Text>
                    </View>
                  )}
                  <Text style={{ color: i % 2 === 0 ? theme.background : theme.onPrimary, fontSize: 22, lineHeight: 26, fontWeight: "900" }}>{promo.title}</Text>
                  {promo.subtitle && <Text style={{ color: i % 2 === 0 ? theme.background : theme.onPrimary, opacity: 0.85, marginTop: 4 }}>{promo.subtitle}</Text>}
                </Pressable>
              ))}
            </ScrollView>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 16, gap: 8, paddingBottom: 8 }}>
              {sections.map((s, i) => (
                <Pressable
                  key={s.key}
                  onPress={() => {
                    setActive(i);
                    list.current?.scrollToLocation({ sectionIndex: i, itemIndex: 0, viewOffset: 48 });
                  }}
                  style={[styles.chip, { borderColor: border }, active === i && { backgroundColor: theme.primary, borderColor: theme.primary }]}
                >
                  <Text variant="label" style={active === i ? { color: theme.onPrimary } : undefined}>
                    {s.title}
                  </Text>
                </Pressable>
              ))}
            </ScrollView>
          </View>
        }
        renderSectionHeader={({ section }) => (
          <Text variant="title" style={{ paddingHorizontal: 16, paddingTop: 20, paddingBottom: 8, fontSize: 24 }}>
            {section.title}
          </Text>
        )}
        renderItem={({ item }) => (
          <Pressable
            disabled={!item.available}
            onPress={() => router.push({ pathname: "/product/[id]", params: { id: item.id } })}
            style={[styles.product, { borderColor: border, borderRadius: radius(theme, 6), opacity: item.available ? 1 : 0.5 }]}
          >
            <View style={{ flex: 1, gap: 4 }}>
              <Text variant="label" style={{ fontSize: 16, fontWeight: "800" }}>
                {item.name}
              </Text>
              {item.description && (
                <Text variant="small" muted numberOfLines={2}>
                  {item.description}
                </Text>
              )}
              <View style={{ flexDirection: "row", gap: 8, alignItems: "center", marginTop: 4 }}>
                <Text style={{ fontWeight: "800" }}>{formatMoney(item.price, locale)}</Text>
                {!item.available && <Badge label={t("menu.soldOut")} />}
              </View>
            </View>
            <View>
              <ProductArt name={item.name} />
              {item.available && settings?.features.takeaway && (
                <Pressable
                  accessibilityLabel={`${t("menu.add")} ${item.name}`}
                  onPress={() =>
                    item.modifierGroups.length === 0
                      ? add({ productId: item.id, name: item.name, unitPrice: item.price.amount, currency: item.price.currency, quantity: 1, options: [] })
                      : router.push({ pathname: "/product/[id]", params: { id: item.id } })
                  }
                  style={[styles.add, { backgroundColor: theme.primary, borderColor: theme.background }]}
                >
                  <Text style={{ color: theme.onPrimary, fontSize: 20, fontWeight: "800" }}>+</Text>
                </Pressable>
              )}
            </View>
          </Pressable>
        )}
      />

      {count > 0 && (
        <SafeAreaView edges={["bottom"]} style={styles.cartBar}>
          <Button
            title={`${t("cart.view")} · ${count}`}
            onPress={() => router.push("/cart")}
            style={{ marginHorizontal: 16, shadowColor: "#000", shadowOpacity: 0.2, shadowRadius: 12, elevation: 6 }}
            right={<Text style={{ color: theme.onPrimary, fontWeight: "800" }}>{formatMoney({ amount: fromCents(totalCents), currency }, locale)}</Text>}
          />
        </SafeAreaView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: "center", justifyContent: "center" },
  header: { flexDirection: "row", alignItems: "center", gap: 12, paddingHorizontal: 16, paddingVertical: 10, borderBottomWidth: StyleSheet.hairlineWidth },
  logo: { width: 40, height: 40, borderRadius: 20, alignItems: "center", justifyContent: "center" },
  lang: { borderWidth: 1, borderRadius: 999, paddingHorizontal: 12, paddingVertical: 6 },
  notice: { margin: 16, marginBottom: 0, padding: 12, borderWidth: 1, borderRadius: 12 },
  promo: { width: 288, minHeight: 140, padding: 18, justifyContent: "flex-end" },
  promoBadge: { position: "absolute", top: 14, right: 14, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 10, transform: [{ rotate: "6deg" }] },
  chip: { borderWidth: 1, borderRadius: 999, paddingHorizontal: 14, paddingVertical: 8 },
  product: { flexDirection: "row", gap: 12, marginHorizontal: 16, marginBottom: 10, padding: 12, borderWidth: 1 },
  add: { position: "absolute", right: -6, bottom: -6, width: 34, height: 34, borderRadius: 17, alignItems: "center", justifyContent: "center", borderWidth: 3 },
  cartBar: { position: "absolute", left: 0, right: 0, bottom: 12 },
});
