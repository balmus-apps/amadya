import { getFloor, getMenu, getTableSession, unwrap, type ApiProblem, type TableSession } from "@amadya/api-client";
import { toast } from "@amadya/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useLocale } from "use-intl";

export const keys = {
  floor: ["floor"] as const,
  session: (id: string) => ["session", id] as const,
  menu: ["menu"] as const,
};

/** Floor plan with open sessions; refreshed by the live feed, polled as a safety net. */
export function useFloor() {
  const locale = useLocale();
  return useQuery({ queryKey: [...keys.floor, locale], queryFn: () => unwrap(getFloor()), refetchInterval: 20_000 });
}

export function useSession(id: string) {
  const locale = useLocale();
  return useQuery({ queryKey: [...keys.session(id), locale], queryFn: () => unwrap(getTableSession({ path: { id } })), refetchInterval: 20_000 });
}

export function useMenu() {
  const locale = useLocale();
  return useQuery({ queryKey: [...keys.menu, locale], queryFn: () => unwrap(getMenu()), staleTime: 5 * 60_000 });
}

/** Message from an API error (problem+json `detail`, already localized by the API). */
export function problemMessage(error: unknown): string {
  return (error as { problem?: ApiProblem }).problem?.detail ?? String(error);
}

/**
 * Mutation on a table session: the API answers with the updated session, which replaces the cached one; the floor is
 * refreshed too. Errors are shown as a toast.
 */
export function useSessionMutation<V>(call: (vars: V) => Promise<{ data?: TableSession; error?: unknown; response?: Response }>, onDone?: (session: TableSession) => void) {
  const client = useQueryClient();
  const locale = useLocale();
  return useMutation({
    mutationFn: (vars: V) => unwrap(call(vars)),
    onSuccess: async (session) => {
      client.setQueryData([...keys.session(session.id), locale], session);
      await client.invalidateQueries({ queryKey: keys.floor });
      onDone?.(session);
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
}
