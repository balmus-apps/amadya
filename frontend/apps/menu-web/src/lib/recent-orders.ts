"use client";

import { createRecentOrdersStore, createSavedContactStore, type RecentOrdersState } from "@amadya/customer-core";
import { useStore } from "zustand";

export type { RecentOrder, SavedContact } from "@amadya/customer-core";

const recentOrdersStore = createRecentOrdersStore(() => localStorage);
const savedContactStore = createSavedContactStore(() => localStorage);

export function useRecentOrders<T>(selector: (state: RecentOrdersState) => T): T {
  return useStore(recentOrdersStore, selector);
}

export function useSavedContact() {
  return useStore(savedContactStore);
}
