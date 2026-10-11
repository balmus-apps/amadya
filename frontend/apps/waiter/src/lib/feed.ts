import { streamTableEvents, type FloorArea, type TableEvent } from "@amadya/api-client";
import { accessToken } from "@amadya/staff-auth";
import { toast } from "@amadya/ui";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useTranslations } from "use-intl";
import { keys } from "./data";

/**
 * Live table events: refreshes the floor and the affected table, and announces rounds ready to serve with a sound,
 * a vibration and a toast. A fresh token is sent on every (re)connect.
 */
export function useTableFeed(soundOn: boolean) {
  const client = useQueryClient();
  const t = useTranslations();
  const [live, setLive] = useState(false);
  const sound = useRef(soundOn);
  sound.current = soundOn;

  useEffect(() => {
    const abort = new AbortController();
    void (async () => {
      const { stream } = await streamTableEvents({
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
      for await (const raw of stream) {
        const event = raw as unknown as TableEvent;
        void client.invalidateQueries({ queryKey: keys.floor });
        void client.invalidateQueries({ queryKey: keys.session(event.sessionId) });
        if (event.kind === "SESSION_TO_CONFIRM" || event.kind === "ROUND_TO_CONFIRM" || event.kind === "JOIN_REQUEST") {
          await client.refetchQueries({ queryKey: keys.floor });
          const label = tableLabel(client.getQueriesData<FloorArea[]>({ queryKey: keys.floor }), event.sessionId);
          toast.info(t(`feed.${event.kind}`, { table: label ?? "?" }), { duration: 8000 });
          if (sound.current) chime();
          navigator.vibrate?.([80, 40, 80, 40, 80]);
        }
        if (event.kind === "ROUND_READY") {
          const label = tableLabel(client.getQueriesData<FloorArea[]>({ queryKey: keys.floor }), event.sessionId);
          toast.success(t("feed.roundReady", { table: label ?? "?", number: event.orderNumber ?? "" }), { duration: 8000 });
          if (sound.current) chime();
          navigator.vibrate?.([120, 60, 120]);
        }
      }
    })().catch(() => setLive(false));
    return () => abort.abort();
  }, [client, t]);

  return live;
}

function tableLabel(cached: [unknown, FloorArea[] | undefined][], sessionId: string): string | undefined {
  for (const [, areas] of cached) {
    for (const area of areas ?? []) {
      const table = area.tables.find((tb) => tb.session?.id === sessionId);
      if (table) return table.label;
    }
  }
  return undefined;
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
