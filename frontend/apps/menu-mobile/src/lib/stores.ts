import { createCartStore, createRecentOrdersStore, createSavedContactStore, type CartState, type RecentOrdersState } from "@amadya/customer-core";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { useStore } from "zustand";

export * from "@amadya/customer-core";

const cartStore = createCartStore(() => AsyncStorage);
const recentOrdersStore = createRecentOrdersStore(() => AsyncStorage);
const savedContactStore = createSavedContactStore(() => AsyncStorage);

export function useCart<T>(selector: (state: CartState) => T): T {
  return useStore(cartStore, selector);
}

export function useRecentOrders<T>(selector: (state: RecentOrdersState) => T): T {
  return useStore(recentOrdersStore, selector);
}

export function useSavedContact() {
  return useStore(savedContactStore);
}
