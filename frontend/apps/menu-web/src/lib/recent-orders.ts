"use client";

import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

/** Guest orders are remembered on this device only, with their tracking token (no account needed). */
export interface RecentOrder {
  id: string;
  number: string;
  token: string;
  createdAt: string;
  total: { amount: string; currency: string };
}

interface RecentOrdersState {
  orders: RecentOrder[];
  remember: (order: RecentOrder) => void;
  tokenFor: (id: string) => string | undefined;
}

export const useRecentOrders = create<RecentOrdersState>()(
  persist(
    (set, get) => ({
      orders: [],
      remember: (order) => set((s) => ({ orders: [order, ...s.orders.filter((o) => o.id !== order.id)].slice(0, 20) })),
      tokenFor: (id) => get().orders.find((o) => o.id === id)?.token,
    }),
    { name: "amadya-recent-orders", storage: createJSONStorage(() => localStorage), version: 1 },
  ),
);

/** Last checkout contact details, to prefill the next order. */
export interface SavedContact {
  name: string;
  phone: string;
  email?: string;
}

export const useSavedContact = create<{ contact?: SavedContact; save: (c: SavedContact) => void }>()(
  persist((set) => ({ contact: undefined, save: (contact) => set({ contact }) }), {
    name: "amadya-contact",
    storage: createJSONStorage(() => localStorage),
  }),
);
