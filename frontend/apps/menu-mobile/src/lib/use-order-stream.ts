import { orderEventsUrl, type OrderStatusChanged } from "@amadya/api-client";
import { useEffect, useRef, useState } from "react";
import EventSource from "react-native-sse";

/** Live order status over Server-Sent Events (react-native-sse works on iOS, Android and web). */
export function useOrderStream(orderId: string, token: string, onEvent: (event: OrderStatusChanged) => void): boolean {
  const [live, setLive] = useState(false);
  const handler = useRef(onEvent);
  handler.current = onEvent;

  useEffect(() => {
    const source = new EventSource<"order-status">(orderEventsUrl(orderId, token), { pollingInterval: 3000 });
    source.addEventListener("open", () => setLive(true));
    source.addEventListener("error", () => setLive(false));
    source.addEventListener("order-status", (event) => {
      setLive(true);
      if (event.data) handler.current(JSON.parse(event.data) as OrderStatusChanged);
    });
    return () => {
      source.removeAllEventListeners();
      source.close();
    };
  }, [orderId, token]);

  return live;
}
