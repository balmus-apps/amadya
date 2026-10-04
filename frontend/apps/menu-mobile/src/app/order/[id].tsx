import { getOrder, unwrap, type Order, type OrderStatus } from "@amadya/api-client";
import { formatMoney, formatTime } from "@amadya/i18n";
import { mix } from "@amadya/theme";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { router, useLocalSearchParams } from "expo-router";
import { useEffect, useRef, useState } from "react";
import { ActivityIndicator, Platform, ScrollView, StyleSheet, Vibration, View } from "react-native";
import { useTranslations } from "use-intl";
import { PaymentPanel } from "@/components/payment-panel";
import { Badge, Button, Card, Text } from "@/components/ui";
import { useApp } from "@/lib/app-context";
import { enableOrderPush, type PushResult } from "@/lib/push";
import { useRecentOrders } from "@/lib/stores";
import { useOrderStream } from "@/lib/use-order-stream";

const steps: OrderStatus[] = ["PLACED", "PREPARING", "READY", "COMPLETED"];

export default function OrderScreen() {
  const params = useLocalSearchParams<{ id: string; t?: string }>();
  const stored = useRecentOrders((s) => s.orders.find((o) => o.id === params.id)?.token);
  const token = params.t ?? stored;
  const t = useTranslations("order");
  if (!token) {
    return (
      <View style={styles.center}>
        <Text>{t("notFound")}</Text>
      </View>
    );
  }
  return <TrackedOrder orderId={params.id} token={token} />;
}

function TrackedOrder({ orderId, token }: { orderId: string; token: string }) {
  const t = useTranslations("order");
  const { theme, locale } = useApp();
  const queryClient = useQueryClient();
  const queryKey = ["order", orderId, locale];
  const live = useOrderStream(orderId, token, (event) => {
    queryClient.setQueryData<Order>(queryKey, (prev) => (prev ? { ...prev, status: event.status, estimatedReadyAt: event.estimatedReadyAt ?? prev.estimatedReadyAt } : prev));
    void queryClient.invalidateQueries({ queryKey });
  });
  const { data: order, isError } = useQuery({
    queryKey,
    queryFn: () => unwrap(getOrder({ path: { orderId }, query: { token }, headers: { "Accept-Language": locale } })),
    refetchInterval: (q) => (live || ["COMPLETED", "CANCELLED"].includes(q.state.data?.status ?? "") ? false : 10_000),
  });
  const [push, setPush] = useState<PushResult | null>(null);
  const previous = useRef<OrderStatus | undefined>(undefined);

  // Ask for push once the order is in the kitchen, so the customer can lock the phone.
  useEffect(() => {
    if (order && push === null && order.status !== "PENDING_PAYMENT" && order.status !== "COMPLETED" && order.status !== "CANCELLED") {
      enableOrderPush(orderId, token, locale).then(setPush);
    }
  }, [order, push, orderId, token, locale]);

  useEffect(() => {
    if (order?.status === "READY" && previous.current && previous.current !== "READY" && Platform.OS !== "web") Vibration.vibrate([0, 300, 150, 300]);
    previous.current = order?.status;
  }, [order?.status]);

  if (isError) {
    return (
      <View style={styles.center}>
        <Text>{t("notFound")}</Text>
      </View>
    );
  }
  if (!order) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={theme.primary} size="large" />
      </View>
    );
  }

  const ready = order.status === "READY";
  const stepIndex = steps.indexOf(order.status);
  const heroBg = ready ? "#2E7D32" : theme.foreground;
  const heroFg = ready ? "#FFFFFF" : theme.background;

  return (
    <ScrollView contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: 40 }}>
      <View style={[styles.hero, { backgroundColor: heroBg }]}>
        <Text style={{ color: heroFg, opacity: 0.8, fontWeight: "700", letterSpacing: 1 }}>{t("number").toUpperCase()}</Text>
        <Text style={{ color: heroFg, fontSize: 64, lineHeight: 72, fontWeight: "900", letterSpacing: -1 }}>{order.number}</Text>
        <Text style={{ color: heroFg, opacity: 0.8 }}>{t("showAtPickup")}</Text>
      </View>

      <Card style={{ alignItems: "center", gap: 4 }}>
        <Text variant="title" style={{ fontSize: 24, lineHeight: 30, textAlign: "center" }}>
          {t(`status.${order.status}`)}
        </Text>
        <Text muted style={{ textAlign: "center" }}>
          {t(`statusHint.${order.status}`)}
        </Text>
        {order.estimatedReadyAt && (order.status === "PLACED" || order.status === "PREPARING") && (
          <Text variant="label" style={{ marginTop: 6 }}>
            {t("eta", { time: formatTime(order.estimatedReadyAt, locale) })}
          </Text>
        )}
        {order.paymentStatus === "REFUNDED" && <Badge label={t("refunded")} />}
      </Card>

      {order.status === "PENDING_PAYMENT" && <PaymentPanel order={order} token={token} />}

      {stepIndex >= 0 && (
        <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
          {steps.map((step, i) => {
            const done = i < stepIndex;
            const current = i === stepIndex;
            return (
              <View key={step} style={{ flex: 1, alignItems: "center", gap: 6 }}>
                <View
                  style={[
                    styles.dot,
                    { borderColor: done ? "#2E7D32" : current ? theme.primary : mix(theme.background, theme.foreground, 0.2) },
                    done && { backgroundColor: "#2E7D32" },
                    current && { backgroundColor: theme.primary },
                  ]}
                >
                  <Text style={{ color: done ? "#fff" : current ? theme.onPrimary : theme.foreground, fontWeight: "800" }}>{done ? "✓" : i + 1}</Text>
                </View>
                <Text variant="small" style={{ textAlign: "center", fontWeight: current ? "800" : "400" }}>
                  {t(`status.${step}`)}
                </Text>
              </View>
            );
          })}
        </View>
      )}

      <Text variant="small" muted>
        {live ? `● ${t("live")}` : `○ ${t("reconnecting")}`}
        {push === "registered" ? ` · 🔔 ${t("notifyOn")}` : ""}
      </Text>

      <Card style={{ gap: 8 }}>
        {order.lines.map((line) => (
          <View key={line.id} style={{ flexDirection: "row", justifyContent: "space-between", gap: 12 }}>
            <Text style={{ flex: 1 }}>
              <Text style={{ fontWeight: "800" }}>{line.quantity} × </Text>
              {line.productName}
              {line.modifiers.length > 0 ? <Text muted> · {line.modifiers.join(", ")}</Text> : null}
            </Text>
            <Text>{formatMoney(line.total, locale)}</Text>
          </View>
        ))}
        <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: mix(theme.background, theme.foreground, 0.15), marginVertical: 4 }} />
        <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
          <Text variant="heading">Total</Text>
          <Text variant="heading">{formatMoney(order.total, locale)}</Text>
        </View>
      </Card>

      <Button variant="outline" title={t("newOrder")} onPress={() => router.dismissTo("/")} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: "center", justifyContent: "center", padding: 24 },
  hero: { borderRadius: 20, paddingVertical: 28, alignItems: "center" },
  dot: { width: 36, height: 36, borderRadius: 18, borderWidth: 2, alignItems: "center", justifyContent: "center" },
});
