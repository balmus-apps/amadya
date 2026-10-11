import { cartKey, type CartItem, type NewCartItem } from "@amadya/customer-core";
import { useStore } from "zustand";
import { createStore } from "zustand/vanilla";

/**
 * The round being taken at each table, kept while the waiter moves between screens (in memory only: a round not
 * sent yet is not worth restoring after a reload).
 */
interface DraftState {
  rounds: Record<string, { items: CartItem[]; notes: string }>;
  add: (sessionId: string, item: NewCartItem) => void;
  setQuantity: (sessionId: string, key: string, quantity: number) => void;
  setNotes: (sessionId: string, notes: string) => void;
  clear: (sessionId: string) => void;
}

const empty = { items: [] as CartItem[], notes: "" };

export const drafts = createStore<DraftState>((set) => ({
  rounds: {},
  add: (sessionId, item) =>
    set((s) => {
      const round = s.rounds[sessionId] ?? empty;
      const key = cartKey(item);
      const existing = round.items.find((i) => i.key === key);
      const items = existing
        ? round.items.map((i) => (i.key === key ? { ...i, quantity: Math.min(99, i.quantity + item.quantity) } : i))
        : [...round.items, { ...item, key }];
      return { rounds: { ...s.rounds, [sessionId]: { ...round, items } } };
    }),
  setQuantity: (sessionId, key, quantity) =>
    set((s) => {
      const round = s.rounds[sessionId] ?? empty;
      const items = quantity <= 0 ? round.items.filter((i) => i.key !== key) : round.items.map((i) => (i.key === key ? { ...i, quantity } : i));
      return { rounds: { ...s.rounds, [sessionId]: { ...round, items } } };
    }),
  setNotes: (sessionId, notes) => set((s) => ({ rounds: { ...s.rounds, [sessionId]: { ...(s.rounds[sessionId] ?? empty), notes } } })),
  clear: (sessionId) =>
    set((s) => {
      const rounds = { ...s.rounds };
      delete rounds[sessionId];
      return { rounds };
    }),
}));

export function useDraft(sessionId: string) {
  return useStore(drafts, (s) => s.rounds[sessionId] ?? empty);
}
