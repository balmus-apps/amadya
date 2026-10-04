import type { Order, PaymentIntent } from "@amadya/api-client";
import { formatMoney } from "@amadya/i18n";
import { StripeProvider, useStripe } from "@stripe/stripe-react-native";
import * as Linking from "expo-linking";
import { useEffect, useState } from "react";
import { Alert } from "react-native";
import { useTranslations } from "use-intl";
import { useApp } from "@/lib/app-context";
import { Button } from "./ui";

/** Native Stripe PaymentSheet: Apple Pay / Google Pay first, card as fallback. */
export function StripePay(props: { intent: PaymentIntent; order: Order; token: string }) {
  if (!props.intent.publishableKey || !props.intent.clientSecret) return null;
  return (
    <StripeProvider publishableKey={props.intent.publishableKey} merchantIdentifier={undefined} urlScheme={Linking.createURL("/").split(":")[0]}>
      <Sheet {...props} />
    </StripeProvider>
  );
}

function Sheet({ intent }: { intent: PaymentIntent; order: Order; token: string }) {
  const t = useTranslations("payment");
  const { locale, settings } = useApp();
  const { initPaymentSheet, presentPaymentSheet } = useStripe();
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    initPaymentSheet({
      paymentIntentClientSecret: intent.clientSecret!,
      merchantDisplayName: settings?.name ?? "Amadya",
      applePay: { merchantCountryCode: "RO" },
      googlePay: { merchantCountryCode: "RO", currencyCode: intent.amount.currency, testEnv: __DEV__ },
      returnURL: Linking.createURL("stripe-redirect"),
    }).then(({ error }) => {
      if (error) Alert.alert(error.message);
      else setReady(true);
    });
  }, [initPaymentSheet, intent, settings?.name]);

  return (
    <Button
      disabled={!ready}
      loading={busy}
      title={t("payNow", { total: formatMoney(intent.amount, locale) })}
      onPress={async () => {
        setBusy(true);
        const { error } = await presentPaymentSheet();
        // Success is confirmed by the webhook; the live stream moves the screen forward.
        if (error && error.code !== "Canceled") Alert.alert(t("failed"), error.message);
        setBusy(false);
      }}
    />
  );
}
