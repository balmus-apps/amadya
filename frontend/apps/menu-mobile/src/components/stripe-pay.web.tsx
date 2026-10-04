import type { Order, PaymentIntent } from "@amadya/api-client";
import * as Linking from "expo-linking";
import { useTranslations } from "use-intl";
import { webUrl } from "@/lib/api";
import { Button } from "./ui";

/** The Stripe React Native SDK has no web support; hand over to the menu-web payment page. */
export function StripePay({ order, token }: { intent: PaymentIntent; order: Order; token: string }) {
  const t = useTranslations("payment");
  return <Button title={t("title")} onPress={() => Linking.openURL(`${webUrl}/order/${order.id}?t=${encodeURIComponent(token)}`)} />;
}
