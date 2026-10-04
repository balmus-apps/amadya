import { unwrap, type ApiProblem } from "@amadya/api-client";
import { toast } from "@amadya/ui";
import { useMutation, useQuery, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { useLocale } from "./i18n";

type Result<T> = Promise<{ data?: T; error?: unknown; response?: Response }>;

/** Query over a generated SDK call; the locale is part of the key because names come back localized. */
export function useApi<T>(key: QueryKey, call: () => Result<T>, options: { enabled?: boolean; refetchInterval?: number } = {}) {
  const { locale } = useLocale();
  return useQuery({ queryKey: [...key, locale], queryFn: () => unwrap(call()), ...options });
}

/** Mutation that shows the API's localized error, a success toast, and refreshes the given queries. */
export function useApiMutation<V, T>(call: (vars: V) => Result<T>, opts: { invalidate?: QueryKey[]; success?: string; onSuccess?: (data: T) => void } = {}) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (vars: V) => unwrap(call(vars)),
    onSuccess: async (data) => {
      await Promise.all((opts.invalidate ?? []).map((queryKey) => client.invalidateQueries({ queryKey })));
      if (opts.success) toast.success(opts.success);
      opts.onSuccess?.(data);
    },
    onError: (error) => {
      const problem = (error as { problem?: ApiProblem }).problem;
      const details = problem?.errors?.map((e) => `${e.field}: ${e.message}`).join("\n");
      toast.error(problem?.detail ?? String(error), { description: details });
    },
  });
}
