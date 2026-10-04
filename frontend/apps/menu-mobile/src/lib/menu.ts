import { getMenu, unwrap, type Menu, type MenuProduct } from "@amadya/api-client";
import { useQuery } from "@tanstack/react-query";
import { useApp } from "./app-context";

export function useMenu() {
  const { locale } = useApp();
  return useQuery<Menu>({ queryKey: ["menu", locale], queryFn: () => unwrap(getMenu({ headers: { "Accept-Language": locale } })) });
}

export function useProduct(id: string): MenuProduct | undefined {
  const { data } = useMenu();
  return data?.categories.flatMap((c) => c.products).find((p) => p.id === id);
}
