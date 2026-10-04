"use client";

import { createCartStore, type CartState } from "@amadya/customer-core";
import { useStore } from "zustand";

export * from "@amadya/customer-core";

export const cartStore = createCartStore(() => localStorage);

export function useCart<T>(selector: (state: CartState) => T): T {
  return useStore(cartStore, selector);
}
