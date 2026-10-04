import { Button, cn } from "@amadya/ui";
import { Link, Outlet, useNavigate } from "@tanstack/react-router";
import {
  BarChart3Icon,
  BoxesIcon,
  ClipboardListIcon,
  FileInputIcon,
  FileTextIcon,
  LayersIcon,
  LogOutIcon,
  MegaphoneIcon,
  PackageIcon,
  PercentIcon,
  ReceiptIcon,
  RulerIcon,
  SettingsIcon,
  ShoppingBagIcon,
  SlidersHorizontalIcon,
  StoreIcon,
  TruckIcon,
  UsersIcon,
  UtensilsIcon,
  WarehouseIcon,
  ChefHatIcon,
  MenuIcon,
  type LucideIcon,
} from "lucide-react";
import { useState } from "react";
import { useStore } from "zustand";
import { useLocale, useT } from "@/lib/i18n";
import { session, signOut } from "@/lib/session";

type Item = { to: string; label: string; icon: LucideIcon; admin?: boolean };

export function AppLayout() {
  const t = useT();
  const { locale, setLocale } = useLocale();
  const claims = useStore(session, (s) => s.claims);
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const isAdmin = claims?.roles.includes("ADMIN");

  const groups: { title?: string; items: Item[] }[] = [
    { items: [{ to: "/", label: t("nav.dashboard"), icon: BarChart3Icon }, { to: "/orders", label: t("nav.orders"), icon: ShoppingBagIcon }] },
    {
      title: t("nav.catalog"),
      items: [
        { to: "/products", label: t("nav.products"), icon: UtensilsIcon },
        { to: "/categories", label: t("nav.categories"), icon: LayersIcon },
        { to: "/modifiers", label: t("nav.modifiers"), icon: SlidersHorizontalIcon },
        { to: "/promotions", label: t("nav.promotions"), icon: MegaphoneIcon },
      ],
    },
    {
      title: t("nav.stock"),
      items: [
        { to: "/stock", label: t("nav.balances"), icon: BoxesIcon },
        { to: "/stock/movements", label: t("nav.movements"), icon: ClipboardListIcon },
        { to: "/stock/documents", label: t("nav.documents"), icon: FileTextIcon },
        { to: "/stock/items", label: t("nav.items"), icon: PackageIcon },
        { to: "/stock/warehouses", label: t("nav.warehouses"), icon: WarehouseIcon },
        { to: "/stock/units", label: t("nav.units"), icon: RulerIcon },
      ],
    },
    {
      title: t("nav.procurement"),
      items: [
        { to: "/suppliers", label: t("nav.suppliers"), icon: TruckIcon },
        { to: "/invoices", label: t("nav.invoices"), icon: ReceiptIcon },
        { to: "/nirs", label: t("nav.nirs"), icon: FileInputIcon },
      ],
    },
    {
      title: t("nav.settings"),
      items: [
        { to: "/settings", label: t("nav.restaurant"), icon: StoreIcon, admin: true },
        { to: "/settings/vat", label: t("nav.vat"), icon: PercentIcon },
        { to: "/settings/stations", label: t("nav.stations"), icon: ChefHatIcon },
        { to: "/users", label: t("nav.users"), icon: UsersIcon, admin: true },
      ],
    },
  ];

  const nav = (
    <nav className="flex flex-1 flex-col gap-5 overflow-y-auto p-3">
      {groups.map((g, i) => (
        <div key={i}>
          {g.title && <p className="mb-1 px-3 text-[11px] font-bold tracking-wider text-muted-foreground uppercase">{g.title}</p>}
          <ul className="space-y-0.5">
            {g.items
              .filter((it) => !it.admin || isAdmin)
              .map((it) => (
                <li key={it.to}>
                  <Link
                    to={it.to}
                    onClick={() => setOpen(false)}
                    activeOptions={{ exact: it.to === "/" || it.to === "/stock" || it.to === "/settings" }}
                    className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground transition hover:bg-muted hover:text-foreground data-[status=active]:bg-primary data-[status=active]:text-primary-foreground"
                  >
                    <it.icon className="size-4" /> {it.label}
                  </Link>
                </li>
              ))}
          </ul>
        </div>
      ))}
    </nav>
  );

  return (
    <div className="flex min-h-dvh">
      <aside className={cn("no-print fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r bg-card transition-transform lg:translate-x-0", open ? "translate-x-0" : "-translate-x-full")}>
        <div className="flex h-16 items-center gap-2 border-b px-5">
          <span className="flex size-8 items-center justify-center rounded-lg bg-primary font-extrabold text-primary-foreground">A</span>
          <span className="font-heading text-lg font-extrabold">Amadya</span>
        </div>
        {nav}
        <div className="border-t p-3">
          <p className="truncate px-3 text-sm font-semibold">{claims?.name}</p>
          <p className="truncate px-3 text-xs text-muted-foreground">{claims?.email}</p>
          <div className="mt-2 flex items-center gap-2 px-1">
            <div className="flex rounded-full border p-0.5 text-xs font-semibold" aria-label={t("nav.language")}>
              {(["ro", "en"] as const).map((l) => (
                <button key={l} onClick={() => setLocale(l)} className={cn("rounded-full px-2.5 py-1 uppercase", l === locale ? "bg-primary text-primary-foreground" : "text-muted-foreground")}>
                  {l}
                </button>
              ))}
            </div>
            <Button
              variant="ghost"
              size="sm"
              className="ml-auto"
              onClick={() => {
                signOut();
                void navigate({ to: "/login" });
              }}
            >
              <LogOutIcon /> {t("nav.logout")}
            </Button>
          </div>
        </div>
      </aside>
      {open && <div className="fixed inset-0 z-30 bg-black/40 lg:hidden" onClick={() => setOpen(false)} />}
      <div className="flex min-w-0 flex-1 flex-col lg:pl-64">
        <header className="no-print sticky top-0 z-20 flex h-14 items-center border-b bg-background/90 px-4 backdrop-blur lg:hidden">
          <Button variant="ghost" size="icon-sm" onClick={() => setOpen(true)} aria-label="Menu">
            <MenuIcon />
          </Button>
          <span className="ml-2 font-heading font-extrabold">Amadya</span>
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 p-4 sm:p-6 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
