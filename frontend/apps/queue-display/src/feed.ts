import { getQueueBoard, streamQueueEvents, unwrap, type QueueBoard } from "@amadya/api-client";
import { useEffect, useRef, useState } from "react";
import { newlyReady } from "./board";

/**
 * The queue board: initial load, then live Server-Sent Events (public, no login). Reloaded every 30 s as a safety
 * net; [onReady] gets the numbers that just became ready.
 */
export function useQueue(onReady: (numbers: string[]) => void) {
  const [board, setBoard] = useState<QueueBoard>();
  const [live, setLive] = useState(false);
  const previous = useRef<QueueBoard | undefined>(undefined);
  const notify = useRef(onReady);
  notify.current = onReady;

  useEffect(() => {
    const apply = (next: QueueBoard) => {
      const fresh = newlyReady(previous.current, next);
      previous.current = next;
      setBoard(next);
      if (fresh.length > 0) notify.current(fresh);
    };
    const reload = () => void unwrap(getQueueBoard()).then(apply).catch(() => setLive(false));
    reload();
    const poll = setInterval(reload, 30_000);
    const abort = new AbortController();
    void (async () => {
      const { stream } = await streamQueueEvents({
        signal: abort.signal,
        sseDefaultRetryDelay: 2000,
        sseMaxRetryDelay: 15_000,
        onSseError: () => setLive(false),
        onSseEvent: () => setLive(true),
      });
      for await (const event of stream) apply(event as unknown as QueueBoard);
    })().catch(() => setLive(false));
    return () => {
      abort.abort();
      clearInterval(poll);
    };
  }, []);

  return { board, live };
}
