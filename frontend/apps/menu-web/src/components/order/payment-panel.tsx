"use client";

import { createPaymentIntent, simulatePaymentCapture, unwrap, type ApiProblem, type Order, type PaymentIntent } from "@amadya/api-client";
import { formatMoney, type AppLocale } from "@amadya/i18n";
import { Button, Card, CardContent, Skeleton, toast } from "@amadya/ui";
import { Elements, ExpressCheckoutElement, PaymentElement, useElements, useStripe } from "@stripe/react-stripe-js";
import { loadStripe, type Stripe } from "@stripe/stripe-js";
import { useQuery } from "@tanstack/react-query";
import { FlaskConicalIcon, LockIcon } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useMemo, useState } from "react";

const stripeCache = new Map<string, Promise<Stripe | null>>();

export function PaymentPanel({ order, token }: { order: Order; token: string }) {
  const t = useTranslations("payment");
  const { data: intent, isError, refetch } = useQuery({
    queryKey: ["payment-intent", order.id],
    queryFn: () => unwrap(createPaymentIntent({ path: { orderId: order.id }, query: { token } })),
    staleTime: Infinity,
    retry: false,
  });

  return (
    <Card>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
          <h2 className="text-lg font-bold">{t("title")}</h2>
          <span className="flex items-center gap-1 text-xs text-muted-foreground">
            <LockIcon className="size-3" /> {t("secure")}
          </span>
        </div>
        {isError && (
          <Button variant="outline" onClick={() => refetch()}>
            {t("failed")}
          </Button>
        )}
        {!intent && !isError && <Skeleton className="h-24 w-full" />}
        {intent?.provider === "fake" && <FakePayment intent={intent} />}
        {intent?.provider === "stripe" && intent.clientSecret && intent.publishableKey && <StripePayment intent={intent} />}
      </CardContent>
    </Card>
  );
}

function FakePayment({ intent }: { intent: PaymentIntent }) {
  const t = useTranslations("payment");
  const locale = useLocale() as AppLocale;
  const [busy, setBusy] = useState(false);
  return (
    <div className="space-y-3">
      <p className="flex items-start gap-2 rounded-lg bg-muted p-3 text-sm text-muted-foreground">
        <FlaskConicalIcon className="mt-0.5 size-4 shrink-0" /> {t("simulateHint")}
      </p>
      <Button
        size="lg"
        className="h-auto min-h-12 w-full py-3 whitespace-normal"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          try {
            await unwrap(simulatePaymentCapture({ path: { paymentId: intent.paymentId } }));
          } catch {
            toast.error(t("failed"));
            setBusy(false);
          }
        }}
      >
        {busy ? t("paying") : `${t("simulate")} · ${formatMoney(intent.amount, locale)}`}
      </Button>
    </div>
  );
}

function StripePayment({ intent }: { intent: PaymentIntent }) {
  const locale = useLocale();
  const stripe = useMemo(() => {
    const key = intent.publishableKey!;
    if (!stripeCache.has(key)) stripeCache.set(key, loadStripe(key));
    return stripeCache.get(key)!;
  }, [intent.publishableKey]);

  const primary = typeof window !== "undefined" ? getComputedStyle(document.documentElement).getPropertyValue("--primary").trim() : undefined;
  return (
    <Elements
      stripe={stripe}
      options={{
        clientSecret: intent.clientSecret!,
        locale: locale === "en" ? "en" : "ro",
        appearance: { theme: "stripe", variables: { colorPrimary: primary || undefined, borderRadius: "10px" } },
      }}
    >
      <StripeForm intent={intent} />
    </Elements>
  );
}

function StripeForm({ intent }: { intent: PaymentIntent }) {
  const t = useTranslations("payment");
  const locale = useLocale() as AppLocale;
  const stripe = useStripe();
  const elements = useElements();
  const [busy, setBusy] = useState(false);

  async function confirm() {
    if (!stripe || !elements) return;
    setBusy(true);
    const { error } = await stripe.confirmPayment({
      elements,
      confirmParams: { return_url: window.location.href },
      redirect: "if_required",
    });
    if (error) {
      toast.error(error.message ?? t("failed"));
      setBusy(false);
    }
    // On success the webhook moves the order forward; the SSE stream updates this page.
  }

  return (
    <div className="space-y-4">
      <ExpressCheckoutElement onConfirm={confirm} options={{ buttonHeight: 48, paymentMethods: { applePay: "always", googlePay: "always" } }} />
      <PaymentElement options={{ layout: "tabs" }} />
      <Button size="lg" className="w-full" disabled={!stripe || busy} onClick={confirm}>
        {busy ? t("paying") : t("payNow", { total: formatMoney(intent.amount, locale) })}
      </Button>
      <p className="text-center text-xs text-muted-foreground">{t("wallets")}</p>
    </div>
  );
}

export type { ApiProblem };
