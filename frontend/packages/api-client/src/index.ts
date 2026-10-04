import { client } from "./generated/client.gen";

export * from "./generated/types.gen";
export * from "./generated/sdk.gen";
export { client } from "./generated/client.gen";

export interface ApiConfig {
  /** Absolute API base (".../api/v1") or the relative "/api/v1" when served behind the same origin. */
  baseUrl: string;
  /** Returns the current bearer token for staff apps; customer apps can omit it. */
  getAccessToken?: () => string | undefined | Promise<string | undefined>;
  /** Locale sent as Accept-Language so the API localizes names and error messages. */
  getLocale?: () => string;
}

let currentBaseUrl = "/api/v1";

/** Configures the shared generated client once per app (or per request on the server). */
export function configureApi({ baseUrl, getAccessToken, getLocale }: ApiConfig) {
  currentBaseUrl = baseUrl.replace(/\/$/, "");
  client.setConfig({
    baseUrl: currentBaseUrl,
    auth: getAccessToken ? async () => (await getAccessToken()) ?? undefined : undefined,
  });
  if (getLocale) {
    client.interceptors.request.use((request) => {
      if (!request.headers.has("Accept-Language")) request.headers.set("Accept-Language", getLocale());
      return request;
    });
  }
}

export function apiBaseUrl() {
  return currentBaseUrl;
}

/** URL of the Server-Sent Events stream for one order (used with EventSource). */
export function orderEventsUrl(orderId: string, trackingToken: string) {
  return `${currentBaseUrl}/orders/${encodeURIComponent(orderId)}/events?token=${encodeURIComponent(trackingToken)}`;
}

export function queueEventsUrl() {
  return `${currentBaseUrl}/queue/events`;
}

/** RFC 9457 problem returned by the API; `code` is stable and `detail` is already localized. */
export interface ApiProblem {
  status?: number;
  code?: string;
  detail?: string;
  errors?: { field?: string; message?: string }[];
}

export function isApiProblem(value: unknown): value is ApiProblem {
  return typeof value === "object" && value !== null && ("code" in value || "detail" in value);
}

/** Unwraps a generated-SDK result: returns data or throws the problem. */
export async function unwrap<T>(promise: Promise<{ data?: T; error?: unknown; response?: Response }>): Promise<T> {
  const { data, error, response } = await promise;
  if (error !== undefined || data === undefined) {
    const problem: ApiProblem = isApiProblem(error) ? error : { detail: String(error ?? "Request failed") };
    throw Object.assign(new Error(problem.detail ?? problem.code ?? "Request failed"), { problem, status: response?.status });
  }
  return data;
}
