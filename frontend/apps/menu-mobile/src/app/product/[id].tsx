import { formatMoney } from "@amadya/i18n";
import { mix } from "@amadya/theme";
import { router, useLocalSearchParams } from "expo-router";
import { useMemo, useState } from "react";
import { Pressable, ScrollView, StyleSheet, TextInput, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useTranslations } from "use-intl";
import { Badge, Button, ProductArt, Stepper, Text } from "@/components/ui";
import { useApp } from "@/lib/app-context";
import { useProduct } from "@/lib/menu";
import { chosenOptions, fromCents, initialSelection, missingGroups, toCents, toggleOption, useCart, type Selection } from "@/lib/stores";

export default function ProductScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const product = useProduct(id);
  const t = useTranslations("menu");
  const { theme, locale, settings } = useApp();
  const add = useCart((s) => s.add);
  const [selection, setSelection] = useState<Selection>(() => (product ? initialSelection(product.modifierGroups) : {}));
  const [quantity, setQuantity] = useState(1);
  const [notes, setNotes] = useState("");
  const chosen = useMemo(() => (product ? chosenOptions(product.modifierGroups, selection) : []), [product, selection]);
  if (!product) return null;

  const missing = missingGroups(product.modifierGroups, selection);
  const unit = toCents(product.price.amount) + chosen.reduce((sum, o) => sum + toCents(o.priceDelta.amount), 0);
  const border = mix(theme.background, theme.foreground, 0.12);

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <ScrollView contentContainerStyle={{ paddingBottom: 24 }}>
        <ProductArt name={product.name} size="100%" />
        <View style={{ padding: 16, gap: 6 }}>
          <Text variant="title">{product.name}</Text>
          {product.description && <Text muted>{product.description}</Text>}
          <View style={{ flexDirection: "row", gap: 8, alignItems: "center" }}>
            <Text variant="heading">{formatMoney(product.price, locale)}</Text>
            {!!product.prepTimeSec && <Badge label={t("prepTime", { minutes: Math.max(1, Math.round(product.prepTimeSec / 60)) })} />}
          </View>
          {product.allergens.length > 0 && (
            <Text variant="small" muted>
              {t("allergens", { list: product.allergens.join(", ") })}
            </Text>
          )}
        </View>

        {product.modifierGroups.map((group) => (
          <View key={group.id} style={[styles.group, { borderColor: border }]}>
            <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
              <Text variant="heading">{group.name}</Text>
              <Badge
                tone={group.minSelect > 0 ? "accent" : "muted"}
                label={group.minSelect > 0 ? t("required") : group.maxSelect === 1 ? t("optional") : t("chooseUpTo", { count: group.maxSelect })}
              />
            </View>
            {group.options.map((option) => {
              const checked = selection[group.id]?.includes(option.id) ?? false;
              return (
                <Pressable
                  key={option.id}
                  accessibilityRole={group.maxSelect === 1 ? "radio" : "checkbox"}
                  accessibilityState={{ checked }}
                  onPress={() => setSelection((s) => toggleOption(s, group, option.id, !checked || group.maxSelect === 1))}
                  style={[styles.option, { borderColor: checked ? theme.primary : border, backgroundColor: checked ? mix(theme.background, theme.primary, 0.08) : "transparent" }]}
                >
                  <View
                    style={[
                      styles.mark,
                      { borderRadius: group.maxSelect === 1 ? 11 : 6, borderColor: checked ? theme.primary : border, backgroundColor: checked ? theme.primary : "transparent" },
                    ]}
                  >
                    {checked && <Text style={{ color: theme.onPrimary, fontSize: 12, fontWeight: "900" }}>✓</Text>}
                  </View>
                  <Text style={{ flex: 1, fontWeight: "600" }}>{option.name}</Text>
                  {Number(option.priceDelta.amount) !== 0 && <Text muted>+{formatMoney(option.priceDelta, locale)}</Text>}
                </Pressable>
              );
            })}
          </View>
        ))}

        <View style={[styles.group, { borderColor: border }]}>
          <Text variant="label" style={{ marginBottom: 8 }}>
            {t("notes")}
          </Text>
          <TextInput
            value={notes}
            onChangeText={setNotes}
            placeholder={t("notesPlaceholder")}
            maxLength={200}
            multiline
            style={[styles.input, { borderColor: border, color: theme.foreground }]}
          />
        </View>
      </ScrollView>

      {settings?.features.takeaway && (
        <SafeAreaView edges={["bottom"]} style={[styles.footer, { borderColor: border, backgroundColor: theme.background }]}>
          <Stepper value={quantity} onChange={setQuantity} />
          <Button
            style={{ flex: 1 }}
            disabled={missing.length > 0}
            title={t("addToCart", { price: formatMoney({ amount: fromCents(unit * quantity), currency: product.price.currency }, locale) })}
            onPress={() => {
              add({
                productId: product.id,
                name: product.name,
                unitPrice: product.price.amount,
                currency: product.price.currency,
                quantity,
                options: chosen.map((o) => ({ id: o.id, name: o.name, priceDelta: o.priceDelta.amount })),
                notes: notes.trim() || undefined,
              });
              router.back();
            }}
          />
        </SafeAreaView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  group: { borderTopWidth: StyleSheet.hairlineWidth, padding: 16 },
  option: { flexDirection: "row", alignItems: "center", gap: 12, minHeight: 50, paddingHorizontal: 12, borderWidth: 1, borderRadius: 12, marginBottom: 8 },
  mark: { width: 22, height: 22, borderWidth: 2, alignItems: "center", justifyContent: "center" },
  input: { borderWidth: 1, borderRadius: 12, padding: 12, minHeight: 60, fontSize: 15 },
  footer: { flexDirection: "row", alignItems: "center", gap: 12, paddingHorizontal: 16, paddingTop: 12, borderTopWidth: StyleSheet.hairlineWidth },
});
