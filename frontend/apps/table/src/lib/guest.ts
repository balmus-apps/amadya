import {
  getGuestTable,
  getGuestView,
  getMenu,
  streamGuestEvents,
  unwrap,
  type ApiProblem,
  type GuestView,
  type TableEvent,
} from "@amadya/api-client";
import { createCartStore, type CartState } from "@amadya/customer-core";
import { toast } from "@amadya/ui";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "use-intl";
import { useStore } from "zustand";

/** The table's code, from the QR URL: https://<domain>/t/<code>. */
export const tableCode = decodeURIComponent(window.location.pathname.replace(/^\/t\/?/, "").split("/")[0] ?? "");

const TOKEN_KEY = `amadya-table:${tableCode}:token`;

/** The device token received on joining; it only works for this table's current session. */
export function storedToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function storeToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage blocked: the guest joins again after a reload */
  }
}

/** The cart of this table only (the customer menu on the same site has its own). */
export const cartStore = createCartStore(() => ({
  getItem: (k: string) => sessionStorage.getItem(`table:${tableCode}:${k}`),
  setItem: (k: string, v: string) => sessionStorage.setItem(`table:${tableCode}:${k}`, v),
  removeItem: (k: string) => sessionStorage.removeItem(`table:${tableCode}:${k}`),
}));

export function useCart<T>(selector: (s: CartState) => T): T {
  return useStore(cartStore, selector);
}

export const keys = { table: ["table", tableCode] as const, view: ["view", tableCode] as const, menu: ["menu"] as const };

export function useTableInfo() {
  const locale = useLocale();
  return useQuery({ queryKey: [...keys.table, locale], queryFn: () => unwrap(getGuestTable({ path: { code: tableCode } })), retry: false });
}

export function guestHeaders(token: string) {
  return { "X-Table-Guest": token };
}

/** The table as this guest sees it; refreshed by the live feed and every 20 s as a safety net. */
export function useGuestView(token: string | null) {
  const locale = useLocale();
  return useQuery({
    queryKey: [...keys.view, token, locale],
    queryFn: () => unwrap(getGuestView({ path: { code: tableCode }, headers: guestHeaders(token!) })),
    enabled: !!token,
    refetchInterval: 20_000,
    retry: false,
  });
}

export function useMenu() {
  const locale = useLocale();
  return useQuery({ queryKey: [...keys.menu, locale], queryFn: () => unwrap(getMenu()), staleTime: 5 * 60_000 });
}

export function problem(error: unknown): ApiProblem | undefined {
  return (error as { problem?: ApiProblem }).problem;
}

export function problemMessage(error: unknown): string {
  return problem(error)?.detail ?? String(error);
}

/** Live table events for this guest: refresh the view; a ready round is announced. */
export function useGuestFeed(token: string | null, onClosed: () => void) {
  const client = useQueryClient();
  const t = useTranslations("table");
  const [generation, setGeneration] = useState(0);
  useEffect(() => {
    if (!token) return;
    const abort = new AbortController();
    void (async () => {
      const { stream } = await streamGuestEvents({
        path: { code: tableCode },
        headers: guestHeaders(token),
        signal: abort.signal,
        sseDefaultRetryDelay: 3000,
        sseMaxRetryDelay: 20_000,
      });
      for await (const raw of stream) {
        const event = raw as unknown as TableEvent;
        await client.invalidateQueries({ queryKey: keys.view });
        if (event.kind === "ROUND_READY") {
          toast.success(t("feed.ready"), { duration: 8000 });
          navigator.vibrate?.([100, 50, 100]);
        }
        if (event.kind === "CLOSED") {
          onClosed();
          // A merged table moves its guests to another session: follow it.
          setGeneration((g) => g + 1);
          return;
        }
      }
    })().catch(() => {});
    return () => abort.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, generation, client]);
}

export type { GuestView };
