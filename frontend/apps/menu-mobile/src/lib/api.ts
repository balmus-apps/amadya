import { configureApi } from "@amadya/api-client";

export const apiUrl = process.env.EXPO_PUBLIC_API_URL ?? "http://localhost:8080/api/v1";
export const webUrl = process.env.EXPO_PUBLIC_WEB_URL ?? "http://localhost:3000";

let configured = false;

export function setupApi(getLocale: () => string) {
  if (configured) return;
  configureApi({ baseUrl: apiUrl, getLocale });
  configured = true;
}

export * from "@amadya/api-client";
