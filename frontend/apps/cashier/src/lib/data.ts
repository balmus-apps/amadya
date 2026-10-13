import { getMenu, getPublicSettings, listOrders, unwrap, type ApiProblem, type Order } from "@amadya/api-client";
import { toast } from "@amadya/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useLocale } from "use-intl";
import { FOLLOWED } from "./lists";

export const keys = {
  orders: ["orders"] as const,
  menu: ["menu"] as const,
  settings: ["settings"] as const,
};

/** Orders the counter follows; refreshed by the live feed, polled every 30 s as a safety net. */
export function useOrders() {
  const locale = useLocale();
  return useQuery({
    queryKey: [...keys.orders, locale],
    queryFn: () => unwrap(listOrders({ query: { status: [...FOLLOWED], limit: 200 } })),
    refetchInterval: 30_000,
  });
}

export function useMenu() {
  const locale = useLocale();
  return useQuery({ queryKey: [...keys.menu, locale], queryFn: () => unwrap(getMenu()), staleTime: 5 * 60_000 });
}

/** Public settings: the minutes a phone order waits before it is confirmed automatically. */
export function useSettings() {
  return useQuery({ queryKey: keys.settings, queryFn: () => unwrap(getPublicSettings()), staleTime: 60_000 });
}

/** Message from an API error (problem+json `detail`, already localized by the API). */
export function problemMessage(error: unknown): string {
  return (error as { problem?: ApiProblem }).problem?.detail ?? String(error);
}

/** An action on one order: the lists are refreshed after it, errors are shown as a toast. */
export function useOrderAction<V>(call: (vars: V) => Promise<{ data?: Order; error?: unknown; response?: Response }>, onDone?: (order: Order) => void) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (vars: V) => unwrap(call(vars)),
    onSuccess: async (order) => {
      await client.invalidateQueries({ queryKey: keys.orders });
      onDone?.(order);
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
}
