import { configureApi, refreshToken as refreshCall, type TokenResponse } from "@amadya/api-client";
import { createStore } from "zustand/vanilla";

/**
 * Staff session shared by the back-office apps (admin, kitchen, waiter).
 * The access token (15 min) lives in memory; the refresh token in localStorage so a reload — or a kitchen tablet
 * that stays on all day — keeps the user signed in. Refresh tokens rotate on every use (the API revokes the old one).
 */
export interface Claims {
  sub: string;
  roles: string[];
  name: string;
  email: string;
  exp: number;
}

interface SessionState {
  accessToken?: string;
  claims?: Claims;
}

export const session = createStore<SessionState>(() => ({}));

let storageKey = "amadya-staff-refresh";
let locale = "ro";
let refreshing: Promise<string | undefined> | null = null;

function decode(token: string): Claims {
  const payload = token.split(".")[1]!.replace(/-/g, "+").replace(/_/g, "/");
  return JSON.parse(decodeURIComponent(escape(atob(payload)))) as Claims;
}

export function signIn(tokens: TokenResponse) {
  localStorage.setItem(storageKey, tokens.refreshToken);
  session.setState({ accessToken: tokens.accessToken, claims: decode(tokens.accessToken) });
}

export function signOut() {
  localStorage.removeItem(storageKey);
  session.setState({ accessToken: undefined, claims: undefined });
}

export function hasStoredSession() {
  return !!localStorage.getItem(storageKey);
}

export function hasAnyRole(...roles: string[]) {
  const mine = session.getState().claims?.roles ?? [];
  return roles.some((r) => mine.includes(r));
}

/** Returns a valid access token, refreshing it shortly before it expires. */
export async function accessToken(): Promise<string | undefined> {
  const { accessToken: token, claims } = session.getState();
  if (token && claims && claims.exp * 1000 - Date.now() > 30_000) return token;
  const stored = localStorage.getItem(storageKey);
  if (!stored) return undefined;
  refreshing ??= refreshCall({ body: { refreshToken: stored } })
    .then(({ data }) => {
      if (!data) {
        signOut();
        return undefined;
      }
      signIn(data);
      return data.accessToken;
    })
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

export function setApiLocale(next: string) {
  locale = next;
}

export function currentLocale() {
  return locale;
}

/** Wires the generated API client to this session; call once at app start. */
export function initStaffApi(options: { storageKey: string; baseUrl?: string }) {
  storageKey = options.storageKey;
  configureApi({ baseUrl: options.baseUrl ?? "/api/v1", getAccessToken: accessToken, getLocale: () => locale });
}
