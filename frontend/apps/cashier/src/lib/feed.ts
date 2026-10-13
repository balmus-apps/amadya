import { streamStaffOrderEvents, type OrderStatusChanged } from "@amadya/api-client";
import { accessToken } from "@amadya/staff-auth";
import { toast } from "@amadya/ui";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useTranslations } from "use-intl";
import { keys } from "./data";

/**
 * Live order events (GET /orders/events): every change refreshes the lists; a new phone order to confirm is announced
 * with a chime and a toast. A fresh token is sent on every (re)connect; the lists reload when the feed is back.
 */
export function useOrderFeed(soundOn: boolean) {
  const client = useQueryClient();
  const t = useTranslations("orders");
  const [live, setLive] = useState(false);
  const sound = useRef(soundOn);
  sound.current = soundOn;

  useEffect(() => {
    const abort = new AbortController();
    void (async () => {
      const { stream } = await streamStaffOrderEvents({
        signal: abort.signal,
        sseDefaultRetryDelay: 2000,
        sseMaxRetryDelay: 15_000,
        onRequest: async (url, init) => {
          const headers = new Headers(init.headers);
          headers.set("Authorization", `Bearer ${await accessToken()}`);
          return new Request(url, { ...init, headers });
        },
        onSseError: () => setLive(false),
        onSseEvent: () => setLive(true),
      });
      setLive(true);
      void client.invalidateQueries({ queryKey: keys.orders });
      for await (const raw of stream) {
        const event = raw as unknown as OrderStatusChanged;
        if (event.channel === "DINE_IN") continue;
        void client.invalidateQueries({ queryKey: keys.orders });
        if (event.status === "AWAITING_CONFIRMATION") {
          toast.info(t("newToConfirm", { number: event.number }), { duration: 8000 });
          if (sound.current) chime();
        }
      }
    })().catch(() => setLive(false));
    return () => abort.abort();
  }, [client, t]);

  return live;
}

/** Short two-tone chime (Web Audio, no asset needed). */
export function chime() {
  try {
    const ctx = new AudioContext();
    [660, 990].forEach((freq, i) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0.0001, ctx.currentTime + i * 0.16);
      gain.gain.exponentialRampToValueAtTime(0.35, ctx.currentTime + i * 0.16 + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + i * 0.16 + 0.22);
      osc.connect(gain).connect(ctx.destination);
      osc.start(ctx.currentTime + i * 0.16);
      osc.stop(ctx.currentTime + i * 0.16 + 0.25);
    });
  } catch {
    /* audio blocked until the first tap */
  }
}
