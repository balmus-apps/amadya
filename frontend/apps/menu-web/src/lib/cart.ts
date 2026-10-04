"use client";

import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

export interface CartOption {
  id: string;
  name: string;
  priceDelta: string;
}

export interface CartItem {
  /** Same product + options + notes merge into one line. */
  key: string;
  productId: string;
  name: string;
  unitPrice: string;
  currency: string;
  quantity: number;
  options: CartOption[];
  notes?: string;
}

export type NewCartItem = Omit<CartItem, "key">;

export function cartKey(item: Pick<CartItem, "productId" | "options" | "notes">): string {
  return [item.productId, ...item.options.map((o) => o.id).sort(), (item.notes ?? "").trim().toLowerCase()].join("|");
}

/** Gross price of one unit with its options, as cents to avoid float drift. */
export function unitCents(item: Pick<CartItem, "unitPrice" | "options">): number {
  return toCents(item.unitPrice) + item.options.reduce((sum, o) => sum + toCents(o.priceDelta), 0);
}

export function lineCents(item: CartItem): number {
  return unitCents(item) * item.quantity;
}

export function toCents(amount: string): number {
  return Math.round(Number(amount) * 100);
}

export function fromCents(cents: number): string {
  return (cents / 100).toFixed(2);
}

interface CartState {
  items: CartItem[];
  add: (item: NewCartItem) => void;
  setQuantity: (key: string, quantity: number) => void;
  remove: (key: string) => void;
  clear: () => void;
}

export const useCart = create<CartState>()(
  persist(
    (set) => ({
      items: [],
      add: (item) =>
        set((state) => {
          const key = cartKey(item);
          const existing = state.items.find((i) => i.key === key);
          if (existing) {
            return { items: state.items.map((i) => (i.key === key ? { ...i, quantity: Math.min(99, i.quantity + item.quantity) } : i)) };
          }
          return { items: [...state.items, { ...item, key }] };
        }),
      setQuantity: (key, quantity) =>
        set((state) => ({
          items: quantity <= 0 ? state.items.filter((i) => i.key !== key) : state.items.map((i) => (i.key === key ? { ...i, quantity } : i)),
        })),
      remove: (key) => set((state) => ({ items: state.items.filter((i) => i.key !== key) })),
      clear: () => set({ items: [] }),
    }),
    { name: "amadya-cart", storage: createJSONStorage(() => localStorage), version: 1 },
  ),
);

export function cartTotals(items: CartItem[]) {
  return {
    count: items.reduce((n, i) => n + i.quantity, 0),
    totalCents: items.reduce((sum, i) => sum + lineCents(i), 0),
    currency: items[0]?.currency ?? "RON",
  };
}
