import { configureApi } from "@amadya/api-client";

/** Server components call the Core API directly; the browser goes through the same-origin /api/v1. */
export const browserApiUrl = process.env.NEXT_PUBLIC_API_URL ?? "/api/v1";

const baseUrl =
  typeof window === "undefined" ? `${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1` : browserApiUrl;

configureApi({ baseUrl });

export * from "@amadya/api-client";
