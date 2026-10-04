import { createStore } from "zustand/vanilla";
import { createJSONStorage, persist, type StateStorage } from "zustand/middleware";

/** Guest orders are remembered on this device only, with their tracking token (no account needed). */
export interface RecentOrder {
  id: string;
  number: string;
  token: string;
  createdAt: string;
  total: { amount: string; currency: string };
}

export interface RecentOrdersState {
  orders: RecentOrder[];
  remember: (order: RecentOrder) => void;
}

export function createRecentOrdersStore(storage: () => StateStorage) {
  return createStore<RecentOrdersState>()(
    persist(
      (set) => ({
        orders: [],
        remember: (order) => set((s) => ({ orders: [order, ...s.orders.filter((o) => o.id !== order.id)].slice(0, 20) })),
      }),
      { name: "amadya-recent-orders", storage: createJSONStorage(storage), version: 1 },
    ),
  );
}

/** Last checkout contact details, to prefill the next order. */
export interface SavedContact {
  name: string;
  phone: string;
  email?: string;
}

export function createSavedContactStore(storage: () => StateStorage) {
  return createStore<{ contact?: SavedContact; save: (c: SavedContact) => void }>()(
    persist((set) => ({ contact: undefined, save: (contact) => set({ contact }) }), {
      name: "amadya-contact",
      storage: createJSONStorage(storage),
    }),
  );
}
