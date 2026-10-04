import { createRootRoute, createRoute, createRouter, Outlet, redirect } from "@tanstack/react-router";
import { AppLayout } from "@/components/layout";
import { accessToken, hasStoredSession, session } from "@/lib/session";
import { CategoriesPage } from "@/pages/catalog/categories";
import { ModifiersPage } from "@/pages/catalog/modifiers";
import { ProductsPage } from "@/pages/catalog/products";
import { PromotionsPage } from "@/pages/catalog/promotions";
import { DashboardPage } from "@/pages/dashboard";
import { LoginPage } from "@/pages/login";
import { OrdersPage } from "@/pages/orders";
import { InvoiceDetailPage, InvoicesPage } from "@/pages/procurement/invoices";
import { NirDetailPage, NirPrintPage, NirsPage } from "@/pages/procurement/nirs";
import { SuppliersPage } from "@/pages/procurement/suppliers";
import { StationsPage, UsersPage, VatPage } from "@/pages/settings/lists";
import { RestaurantSettingsPage } from "@/pages/settings/restaurant";
import { BalancesPage } from "@/pages/stock/balances";
import { DocumentsPage } from "@/pages/stock/documents";
import { ItemsPage } from "@/pages/stock/items";
import { UnitsPage, WarehousesPage } from "@/pages/stock/masterdata";
import { MovementsPage } from "@/pages/stock/movements";

const root = createRootRoute({ component: Outlet });

const login = createRoute({ getParentRoute: () => root, path: "/login", component: LoginPage });

/** Every back-office route needs an ADMIN or MANAGER session (restored silently from the refresh token). */
const app = createRoute({
  getParentRoute: () => root,
  id: "app",
  component: AppLayout,
  beforeLoad: async () => {
    if (!hasStoredSession() || !(await accessToken())) throw redirect({ to: "/login" });
    const roles = session.getState().claims?.roles ?? [];
    if (!roles.includes("ADMIN") && !roles.includes("MANAGER")) throw redirect({ to: "/login" });
  },
});

const page = (path: string, component: () => React.ReactNode) => createRoute({ getParentRoute: () => app, path, component });

const routeTree = root.addChildren([
  login,
  app.addChildren([
    page("/", DashboardPage),
    page("/orders", OrdersPage),
    page("/products", ProductsPage),
    page("/categories", CategoriesPage),
    page("/modifiers", ModifiersPage),
    page("/promotions", PromotionsPage),
    createRoute({ getParentRoute: () => app, path: "/stock", component: BalancesPage, validateSearch: (s: Record<string, unknown>): { low?: boolean; warehouseId?: string } => ({ low: s.low === true || s.low === "true" ? true : undefined, warehouseId: typeof s.warehouseId === "string" ? s.warehouseId : undefined }) }),
    page("/stock/movements", MovementsPage),
    page("/stock/documents", DocumentsPage),
    page("/stock/items", ItemsPage),
    page("/stock/warehouses", WarehousesPage),
    page("/stock/units", UnitsPage),
    page("/suppliers", SuppliersPage),
    page("/invoices", InvoicesPage),
    page("/invoices/$id", InvoiceDetailPage),
    page("/nirs", NirsPage),
    page("/nirs/$id", NirDetailPage),
    page("/nirs/$id/print", NirPrintPage),
    page("/settings", RestaurantSettingsPage),
    page("/settings/vat", VatPage),
    page("/settings/stations", StationsPage),
    page("/users", UsersPage),
  ]),
]);

export const router = createRouter({ routeTree, basepath: "/admin", defaultPreload: "intent" });
