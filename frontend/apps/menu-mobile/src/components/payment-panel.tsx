import { createPaymentIntent, simulatePaymentCapture, unwrap, type Order } from "@amadya/api-client";
import { formatMoney } from "@amadya/i18n";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { ActivityIndicator, Alert, View } from "react-native";
import { useTranslations } from "use-intl";
import { useApp } from "@/lib/app-context";
import { StripePay } from "./stripe-pay";
import { Button, Card, Text } from "./ui";

export function PaymentPanel({ order, token }: { order: Order; token: string }) {
  const t = useTranslations("payment");
  const { theme, locale } = useApp();
  const [busy, setBusy] = useState(false);
  const { data: intent, isError, refetch } = useQuery({
    queryKey: ["payment-intent", order.id],
    queryFn: () => unwrap(createPaymentIntent({ path: { orderId: order.id }, query: { token } })),
    staleTime: Infinity,
    retry: false,
  });

  return (
    <Card style={{ gap: 12 }}>
      <Text variant="heading">{t("title")}</Text>
      {isError && <Button variant="outline" title={t("failed")} onPress={() => refetch()} />}
      {!intent && !isError && <ActivityIndicator color={theme.primary} />}
      {intent?.provider === "fake" && (
        <View style={{ gap: 10 }}>
          <Text variant="small" muted>
            🧪 {t("simulateHint")}
          </Text>
          <Button
            loading={busy}
            title={`${t("simulate")} · ${formatMoney(intent.amount, locale)}`}
            onPress={async () => {
              setBusy(true);
              try {
                await unwrap(simulatePaymentCapture({ path: { paymentId: intent.paymentId } }));
              } catch {
                Alert.alert(t("failed"));
                setBusy(false);
              }
            }}
          />
        </View>
      )}
      {intent?.provider === "stripe" && <StripePay intent={intent} order={order} token={token} />}
      <Text variant="small" muted style={{ textAlign: "center" }}>
        🔒 {t("secure")}
      </Text>
    </Card>
  );
}
