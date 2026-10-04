"use client";

import { getOrder, orderEventsUrl, unwrap, type Order, type OrderStatus, type OrderStatusChanged } from "@amadya/api-client";
import { formatMoney, formatTime, type AppLocale } from "@amadya/i18n";
import { Badge, Button, Card, CardContent, cn, Separator, Skeleton } from "@amadya/ui";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BellIcon, BellRingIcon, CheckIcon, ChefHatIcon, PartyPopperIcon, ReceiptIcon, WifiIcon, WifiOffIcon, XCircleIcon } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";
import { Link } from "@/i18n/navigation";
import { useRecentOrders } from "@/lib/recent-orders";
import { useHydrated } from "../header";
import { PaymentPanel } from "./payment-panel";

const steps: OrderStatus[] = ["PLACED", "PREPARING", "READY", "COMPLETED"];

export function OrderScreen({ orderId, tokenFromUrl }: { orderId: string; tokenFromUrl?: string }) {
  const hydrated = useHydrated();
  const storedToken = useRecentOrders((s) => s.orders.find((o) => o.id === orderId)?.token);
  const token = tokenFromUrl ?? storedToken;
  if (!hydrated) return <OrderSkeleton />;
  if (!token) return <NotFound />;
  return <TrackedOrder orderId={orderId} token={token} />;
}

function TrackedOrder({ orderId, token }: { orderId: string; token: string }) {
  const t = useTranslations("order");
  const locale = useLocale() as AppLocale;
  const queryClient = useQueryClient();
  const queryKey = ["order", orderId, locale];
  const live = useOrderStream(orderId, token, (event) => {
    queryClient.setQueryData<Order>(queryKey, (prev) => (prev ? { ...prev, status: event.status, estimatedReadyAt: event.estimatedReadyAt ?? prev.estimatedReadyAt } : prev));
    // Payment and refund state are not part of the event, so reload the full order on status changes.
    void queryClient.invalidateQueries({ queryKey });
  });
  const { data: order, isError } = useQuery({
    queryKey,
    queryFn: () => unwrap(getOrder({ path: { orderId }, query: { token }, headers: { "Accept-Language": locale } })),
    // Fallback when the live stream is down (proxies, flaky mobile networks).
    refetchInterval: (query) => (live || ["COMPLETED", "CANCELLED"].includes(query.state.data?.status ?? "") ? false : 10_000),
  });
  const notifications = useReadyNotification(order);

  if (isError) return <NotFound />;
  if (!order) return <OrderSkeleton />;

  const stepIndex = steps.indexOf(order.status);
  const ready = order.status === "READY";

  return (
    <div className="mx-auto max-w-xl space-y-4 py-6">
      <Card className={cn("overflow-hidden text-center transition-colors", ready && "border-success")}>
        <div className={cn("px-6 py-8", ready ? "bg-success text-white" : "bg-foreground text-background")}>
          <p className="text-sm font-semibold tracking-wide uppercase opacity-80">{t("number")}</p>
          <p className="font-heading text-6xl font-extrabold tracking-tight tabular-nums">{order.number}</p>
          <p className="mt-1 text-sm opacity-80">{t("showAtPickup")}</p>
        </div>
        <CardContent className="space-y-1">
          <div className="flex items-center justify-center gap-2">
            <StatusIcon status={order.status} />
            <h1 className="text-2xl font-extrabold">{t(`status.${order.status}`)}</h1>
          </div>
          <p className="text-muted-foreground">{t(`statusHint.${order.status}`)}</p>
          {order.estimatedReadyAt && (order.status === "PLACED" || order.status === "PREPARING") && (
            <Eta at={order.estimatedReadyAt} locale={locale} />
          )}
          {order.pickupAt && order.status !== "COMPLETED" && order.status !== "CANCELLED" && (
            <p className="text-sm">{t("pickupAt", { time: formatTime(order.pickupAt, locale) })}</p>
          )}
          {order.paymentStatus === "REFUNDED" && <Badge variant="secondary">{t("refunded")}</Badge>}
        </CardContent>
      </Card>

      {order.status === "PENDING_PAYMENT" && <PaymentPanel order={order} token={token} />}

      {stepIndex >= 0 && (
        <ol className="grid grid-cols-4 gap-2" aria-label="progress">
          {steps.map((step, i) => (
            <li key={step} className="flex flex-col items-center gap-1.5 text-center">
              <span
                className={cn(
                  "flex size-9 items-center justify-center rounded-full border-2 text-sm font-bold transition",
                  i < stepIndex && "border-success bg-success text-white",
                  i === stepIndex && "border-primary bg-primary text-primary-foreground animate-pulse",
                  i > stepIndex && "text-muted-foreground",
                )}
              >
                {i < stepIndex ? <CheckIcon className="size-4" /> : i + 1}
              </span>
              <span className={cn("text-[11px] leading-tight", i === stepIndex ? "font-bold" : "text-muted-foreground")}>{t(`status.${step}`)}</span>
            </li>
          ))}
        </ol>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
        <span className="flex items-center gap-1.5">
          {live ? <WifiIcon className="size-3.5 text-success" /> : <WifiOffIcon className="size-3.5" />}
          {live ? t("live") : t("reconnecting")}
        </span>
        {notifications.supported && order.status !== "READY" && order.status !== "COMPLETED" && order.status !== "CANCELLED" && (
          <Button variant="outline" size="sm" onClick={notifications.enable} disabled={notifications.enabled}>
            {notifications.enabled ? <BellRingIcon /> : <BellIcon />}
            {notifications.enabled ? t("notifyOn") : t("notifyMe")}
          </Button>
        )}
      </div>

      <Card>
        <CardContent>
          <ul className="space-y-2 text-sm">
            {order.lines.map((line) => (
              <li key={line.id} className="flex justify-between gap-4">
                <span>
                  <span className="font-semibold">{line.quantity} ×</span> {line.productName}
                  {line.modifiers.length > 0 && <span className="text-muted-foreground"> · {line.modifiers.join(", ")}</span>}
                </span>
                <span className="shrink-0 tabular-nums">{formatMoney(line.total, locale)}</span>
              </li>
            ))}
          </ul>
          <Separator className="my-3" />
          <div className="flex justify-between font-bold">
            <span>Total</span>
            <span>{formatMoney(order.total, locale)}</span>
          </div>
        </CardContent>
      </Card>

      <Button asChild variant="outline" className="w-full">
        <Link href="/">{t("newOrder")}</Link>
      </Button>
    </div>
  );
}

function StatusIcon({ status }: { status: OrderStatus }) {
  const className = "size-6";
  if (status === "READY") return <PartyPopperIcon className={cn(className, "text-success")} />;
  if (status === "PREPARING") return <ChefHatIcon className={cn(className, "text-primary")} />;
  if (status === "CANCELLED") return <XCircleIcon className={cn(className, "text-destructive")} />;
  if (status === "COMPLETED") return <CheckIcon className={cn(className, "text-success")} />;
  return <ReceiptIcon className={cn(className, "text-muted-foreground")} />;
}

function Eta({ at, locale }: { at: string; locale: AppLocale }) {
  const t = useTranslations("order");
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(id);
  }, []);
  const minutes = Math.max(1, Math.round((new Date(at).getTime() - now) / 60_000));
  return (
    <p className="pt-2 text-lg font-semibold">
      {t("eta", { time: formatTime(at, locale) })} <span className="text-muted-foreground">· {t("etaIn", { minutes })}</span>
    </p>
  );
}

/** Subscribes to the order's SSE stream; EventSource reconnects by itself after network drops. */
function useOrderStream(orderId: string, token: string, onEvent: (event: OrderStatusChanged) => void): boolean {
  const [live, setLive] = useState(false);
  const handler = useRef(onEvent);
  handler.current = onEvent;
  useEffect(() => {
    const source = new EventSource(orderEventsUrl(orderId, token));
    source.onopen = () => setLive(true);
    source.onerror = () => setLive(false);
    source.addEventListener("order-status", (e) => {
      setLive(true);
      handler.current(JSON.parse((e as MessageEvent<string>).data) as OrderStatusChanged);
    });
    return () => source.close();
  }, [orderId, token]);
  return live;
}

/** Browser notification + vibration when the order becomes READY while the page is open (push comes with the mobile app). */
function useReadyNotification(order: Order | undefined) {
  const t = useTranslations("order");
  const supported = typeof window !== "undefined" && "Notification" in window;
  const [enabled, setEnabled] = useState(() => supported && Notification.permission === "granted");
  const previous = useRef<OrderStatus | undefined>(undefined);

  useEffect(() => {
    if (!order) return;
    if (previous.current && previous.current !== "READY" && order.status === "READY") {
      navigator.vibrate?.([200, 100, 200]);
      if (enabled) new Notification(t("readyNotification", { number: order.number }), { tag: order.id });
    }
    previous.current = order.status;
  }, [order, enabled, t]);

  return {
    supported,
    enabled,
    enable: async () => setEnabled((await Notification.requestPermission()) === "granted"),
  };
}

function OrderSkeleton() {
  return (
    <div className="mx-auto max-w-xl space-y-4 py-6">
      <Skeleton className="h-64 w-full" />
      <Skeleton className="h-10 w-full" />
      <Skeleton className="h-40 w-full" />
    </div>
  );
}

function NotFound() {
  const t = useTranslations("order");
  return (
    <div className="py-24 text-center">
      <p className="mb-4 text-muted-foreground">{t("notFound")}</p>
      <Button asChild>
        <Link href="/">{t("newOrder")}</Link>
      </Button>
    </div>
  );
}
