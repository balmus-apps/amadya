import { configureApi, refreshToken as refreshCall, type TokenResponse } from "@amadya/api-client";
import { createStore } from "zustand/vanilla";

/**
 * Staff session. The access token (15 min) lives in memory; the refresh token in localStorage so a page reload
 * keeps the user signed in. Refresh tokens rotate on every use (the API revokes the old one).
 */
interface Claims {
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

const REFRESH_KEY = "amadya-admin-refresh";
export const session = createStore<SessionState>(() => ({}));
let refreshing: Promise<string | undefined> | null = null;

function decode(token: string): Claims {
  const payload = token.split(".")[1]!.replace(/-/g, "+").replace(/_/g, "/");
  return JSON.parse(decodeURIComponent(escape(atob(payload)))) as Claims;
}

export function signIn(tokens: TokenResponse) {
  localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
  session.setState({ accessToken: tokens.accessToken, claims: decode(tokens.accessToken) });
}

export function signOut() {
  localStorage.removeItem(REFRESH_KEY);
  session.setState({ accessToken: undefined, claims: undefined });
}

export function hasStoredSession() {
  return !!localStorage.getItem(REFRESH_KEY);
}

/** Returns a valid access token, refreshing it shortly before it expires. */
export async function accessToken(): Promise<string | undefined> {
  const { accessToken: token, claims } = session.getState();
  if (token && claims && claims.exp * 1000 - Date.now() > 30_000) return token;
  const stored = localStorage.getItem(REFRESH_KEY);
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

let locale = "ro";
export function setApiLocale(next: string) {
  locale = next;
}

configureApi({ baseUrl: "/api/v1", getAccessToken: accessToken, getLocale: () => locale });
