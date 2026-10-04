import { createOrder, unwrap, type ApiProblem } from "@amadya/api-client";
import { formatMoney, normalizePhone } from "@amadya/i18n";
import { mix } from "@amadya/theme";
import { router } from "expo-router";
import { useEffect, useMemo, useState } from "react";
import { Alert, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from "react-native";
import { useTranslations } from "use-intl";
import { Button, Card, Text } from "@/components/ui";
import { useApp } from "@/lib/app-context";
import { cartTotals, fromCents, toOrderLines, useCart, useRecentOrders, useSavedContact } from "@/lib/stores";

function slots(now = new Date()): string[] {
  const start = new Date(now.getTime() + 30 * 60_000);
  start.setMinutes(Math.ceil(start.getMinutes() / 15) * 15, 0, 0);
  return Array.from({ length: 11 }, (_, i) => {
    const d = new Date(start.getTime() + i * 15 * 60_000);
    return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  });
}

function pickupIso(time: string) {
  const [h, m] = time.split(":").map(Number);
  const d = new Date();
  d.setHours(h!, m!, 0, 0);
  if (d.getTime() < Date.now()) d.setDate(d.getDate() + 1);
  return d.toISOString();
}

export default function CheckoutScreen() {
  const t = useTranslations("checkout");
  const { theme, locale } = useApp();
  const items = useCart((s) => s.items);
  const clear = useCart((s) => s.clear);
  const remember = useRecentOrders((s) => s.remember);
  const { contact, save } = useSavedContact();
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [notes, setNotes] = useState("");
  const [pickup, setPickup] = useState<"asap" | string>("asap");
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const times = useMemo(() => slots(), []);
  const { totalCents, currency } = cartTotals(items);
  const total = formatMoney({ amount: fromCents(totalCents), currency }, locale);
  const border = mix(theme.background, theme.foreground, 0.15);

  useEffect(() => {
    if (contact) {
      setName(contact.name);
      setPhone(contact.phone);
      setEmail(contact.email ?? "");
    }
  }, [contact]);

  const errors = {
    name: name.trim().length < 2 ? t("invalidName") : undefined,
    phone: normalizePhone(phone) === null ? t("invalidPhone") : undefined,
    email: email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? t("invalidEmail") : undefined,
  };
  const valid = !errors.name && !errors.phone && !errors.email;

  async function submit() {
    setSubmitted(true);
    if (!valid || items.length === 0) return;
    setBusy(true);
    try {
      const normalized = normalizePhone(phone)!;
      const order = await unwrap(
        createOrder({
          headers: { "Accept-Language": locale },
          body: {
            channel: "TAKEAWAY",
            customer: { name: name.trim(), phone: normalized, email: email || undefined },
            pickupAt: pickup === "asap" ? undefined : pickupIso(pickup),
            notes: notes.trim() || undefined,
            lines: toOrderLines(items),
          },
        }),
      );
      save({ name: name.trim(), phone: normalized, email: email || undefined });
      remember({ id: order.id, number: order.number, token: order.trackingToken!, createdAt: order.createdAt, total: order.total });
      clear();
      router.replace({ pathname: "/order/[id]", params: { id: order.id, t: order.trackingToken! } });
    } catch (error) {
      const problem = (error as { problem?: ApiProblem }).problem;
      Alert.alert(problem?.detail ?? String(error));
      setBusy(false);
    }
  }

  const field = (label: string, value: string, onChange: (v: string) => void, error: string | undefined, props: Partial<React.ComponentProps<typeof TextInput>> = {}) => (
    <View style={{ gap: 6 }}>
      <Text variant="label">{label}</Text>
      <TextInput
        value={value}
        onChangeText={onChange}
        style={[styles.input, { borderColor: submitted && error ? "#C62828" : border, color: theme.foreground }]}
        placeholderTextColor={mix(theme.foreground, theme.background, 0.5)}
        {...props}
      />
      {submitted && error && <Text style={{ color: "#C62828" }}>{error}</Text>}
    </View>
  );

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: 40 }} keyboardShouldPersistTaps="handled">
        <Card style={{ gap: 14 }}>
          <Text variant="heading">{t("contact")}</Text>
          {field(t("name"), name, setName, errors.name, { autoComplete: "name", textContentType: "name" })}
          {field(t("phone"), phone, setPhone, errors.phone, { keyboardType: "phone-pad", autoComplete: "tel", textContentType: "telephoneNumber", placeholder: "07xx xxx xxx" })}
          {field(t("email"), email, setEmail, errors.email, { keyboardType: "email-address", autoCapitalize: "none", autoComplete: "email" })}
        </Card>

        <Card style={{ gap: 12 }}>
          <Text variant="heading">{t("pickup")}</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
            {["asap", ...times].map((slot) => (
              <Pressable
                key={slot}
                onPress={() => setPickup(slot)}
                style={[styles.chip, { borderColor: pickup === slot ? theme.primary : border, backgroundColor: pickup === slot ? theme.primary : "transparent" }]}
              >
                <Text variant="label" style={pickup === slot ? { color: theme.onPrimary } : undefined}>
                  {slot === "asap" ? t("asap") : slot}
                </Text>
              </Pressable>
            ))}
          </View>
          {field(t("notes"), notes, setNotes, undefined, { multiline: true, maxLength: 500 })}
        </Card>

        <View style={{ flexDirection: "row", justifyContent: "space-between", paddingHorizontal: 4 }}>
          <Text variant="heading">Total</Text>
          <Text variant="heading">{total}</Text>
        </View>
        <Text variant="small" muted style={{ textAlign: "right", marginTop: -8, paddingHorizontal: 4 }}>
          {t("vatIncluded")}
        </Text>
        <Button title={busy ? t("placing") : t("placeOrder", { total })} loading={busy} onPress={submit} />
        <Text variant="small" muted style={{ textAlign: "center" }}>
          {t("privacy")}
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  input: { borderWidth: 1, borderRadius: 12, paddingHorizontal: 12, paddingVertical: 12, fontSize: 16 },
  chip: { borderWidth: 1, borderRadius: 999, paddingHorizontal: 14, paddingVertical: 8 },
});
