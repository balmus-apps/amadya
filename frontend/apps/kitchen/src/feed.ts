import { listKitchenTickets, streamKitchenEvents, unwrap, type KitchenTicket, type KitchenTicketEvent } from "@amadya/api-client";
import { accessToken } from "@amadya/staff-auth";
import { useCallback, useEffect, useRef, useState } from "react";
import { applyEvent, type Board } from "./board-logic";

/**
 * Tickets of one station: initial load, then live Server-Sent Events. A fresh token is sent on every (re)connect,
 * and the full list is reloaded every 15 s as a safety net for missed events.
 */
export function useKitchenFeed(stationId: string, locale: string, onNewTicket: (t: KitchenTicket) => void) {
  const [board, setBoard] = useState<Board>(new Map());
  const [live, setLive] = useState(false);
  const known = useRef(new Set<string>());
  const notify = useRef(onNewTicket);
  notify.current = onNewTicket;

  const reload = useCallback(async () => {
    const list = await unwrap(listKitchenTickets({ query: { stationId }, headers: { "Accept-Language": locale } }));
    list.forEach((t) => known.current.add(t.id));
    setBoard(new Map(list.map((t) => [t.id, t])));
  }, [stationId, locale]);

  useEffect(() => {
    known.current = new Set();
    void reload().catch(() => {});
    const poll = setInterval(() => void reload().catch(() => {}), 15_000);
    const abort = new AbortController();

    void (async () => {
      const { stream } = await streamKitchenEvents({
        query: { stationId },
        headers: { "Accept-Language": locale },
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
        const event = raw as unknown as KitchenTicketEvent;
        if (event.type === "UPSERT" && event.ticket && !known.current.has(event.ticketId)) {
          known.current.add(event.ticketId);
          notify.current(event.ticket);
        }
        setBoard((b) => applyEvent(b, event));
      }
    })().catch(() => setLive(false));

    return () => {
      abort.abort();
      clearInterval(poll);
    };
  }, [stationId, locale, reload]);

  const replace = useCallback((t: KitchenTicket) => setBoard((b) => new Map(b).set(t.id, t)), []);
  return { board, live, replace };
}

/** Short two-tone chime (Web Audio, no asset needed). */
export function chime() {
  try {
    const ctx = new AudioContext();
    [880, 1320].forEach((freq, i) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0.0001, ctx.currentTime + i * 0.18);
      gain.gain.exponentialRampToValueAtTime(0.4, ctx.currentTime + i * 0.18 + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + i * 0.18 + 0.25);
      osc.connect(gain).connect(ctx.destination);
      osc.start(ctx.currentTime + i * 0.18);
      osc.stop(ctx.currentTime + i * 0.18 + 0.3);
    });
  } catch {
    /* audio blocked until the first tap */
  }
}
