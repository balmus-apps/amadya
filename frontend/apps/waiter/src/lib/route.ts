import { useSyncExternalStore } from "react";

/**
 * Hash routes, so the phone's back button walks back through the screens:
 * `#/` floor, `#/s/<sessionId>` a table, `#/s/<sessionId>/order` adding a round.
 */
export type Route = { name: "floor" } | { name: "table"; sessionId: string } | { name: "order"; sessionId: string };

function parse(hash: string): Route {
  const parts = hash.replace(/^#\/?/, "").split("/").filter(Boolean);
  if (parts[0] === "s" && parts[1]) return parts[2] === "order" ? { name: "order", sessionId: parts[1] } : { name: "table", sessionId: parts[1] };
  return { name: "floor" };
}

function subscribe(onChange: () => void) {
  window.addEventListener("hashchange", onChange);
  return () => window.removeEventListener("hashchange", onChange);
}

export function useRoute(): Route {
  const hash = useSyncExternalStore(subscribe, () => window.location.hash);
  return parse(hash);
}

export function go(route: Route) {
  window.location.hash = route.name === "floor" ? "#/" : route.name === "table" ? `#/s/${route.sessionId}` : `#/s/${route.sessionId}/order`;
}

