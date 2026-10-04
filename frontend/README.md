# Amadya frontends

pnpm workspace + Turborepo. TypeScript 6, React 19. Run the commands below from `frontend/`.

## Packages

| Package | What |
|---|---|
| `@amadya/api-client` | Generated from `../api/openapi/openapi.yaml` by hey-api: types, fetch SDK, TanStack Query hooks, SSE. Plus `configureApi` / `unwrap`. Regenerate with `pnpm gen:api`; `pnpm check:api` fails if the generated code is stale. |
| `@amadya/customer-core` | Cart, modifier rules, opening hours, recent orders. Framework-agnostic `zustand/vanilla` stores with injected storage. |
| `@amadya/i18n` | RO/EN messages (ICU), money/time formatting, Romanian phone normalization (E.164). |
| `@amadya/theme` | Install branding (`theme` from `/settings/public`) mapped to design tokens and CSS variables, with contrast-safe colours. |
| `@amadya/ui` | Web design system: shadcn/ui-style components on Tailwind CSS 4 + Radix. |
| `@amadya/config` | Shared tsconfig. |

## Apps

### menu-web — customer ordering (Next.js 16)

```bash
docker compose up -d postgres api          # from the repo root
pnpm install
pnpm --filter @amadya/menu-web dev         # http://localhost:3000 (RO) and /en
```

- **Menu:** promotions, sticky categories, a product sheet with sauce/extra rules, and a persistent cart.
- **Checkout:** guest checkout with name and phone required; pickup as soon as possible or at a chosen time.
- **Payment:** Stripe Payment Element plus Express Checkout (Apple Pay / Google Pay) when `PAYMENT_PROVIDER=stripe`. With the default `fake` provider, a "Simulate payment" button replaces it.
- **Tracking:** live updates over SSE, with polling when the stream drops; a browser notification when ready; recent orders kept on the device.
- **Dev vs production API calls:** in dev the browser calls the API directly (`.env.development` sets `NEXT_PUBLIC_API_URL`), because the Next dev proxy buffers SSE. In Docker, Caddy serves the site and `/api` on the same origin.

### menu-mobile — customer app (Expo SDK 57)

```bash
cp apps/menu-mobile/.env.example apps/menu-mobile/.env.local   # set EXPO_PUBLIC_API_URL for your device
pnpm --filter @amadya/menu-mobile dev       # Expo dev server (press i / a), or `web` for a browser preview
```

- **Screens:** the same flows as the web app — menu, product, cart, checkout, tracking, and order history.
- **Push notifications:** after payment, the app registers its Expo push token for the order, and the API sends "Comanda B-007 este gata!" when the kitchen marks it ready.
  - Push needs a development or store build and `EAS_PROJECT_ID`.
  - Expo Go on Android and simulators fall back to the live stream while the app is open.
- **Payments:** Stripe PaymentSheet with Apple Pay / Google Pay. The `@stripe/stripe-react-native` plugin requires a development build.
- **White-label:** `APP_NAME`, `APP_BUNDLE_ID` and `APP_PRIMARY_COLOR` produce one app per restaurant.

### admin — back office (Vite + React, TanStack Router/Query)

```bash
pnpm --filter @amadya/admin dev            # http://localhost:5173/admin (proxies /api to :8080)
```

ADMIN / MANAGER staff only. Sessions use a 15-minute access token kept in memory and a rotating refresh token in localStorage.

| Area | Screens |
|---|---|
| Overview | **Dashboard**: revenue, orders, average ticket, food cost from FIFO consumption, prep time, stock value, low stock, sales by day and by hour, best sellers. **Orders**: hand over, or cancel with a reason |
| Menu | **Products**: image upload, modifier groups, recipe tab with portion cost and margin. **Categories**. **Option groups**: each option can have its own recipe. **Promotions**: with an active period |
| Stock | **Stock on hand**: with FIFO lots. **Movements**. **Documents**: transfer, consumption note, waste, stock count. **Stock items**: with packagings. **Warehouses**. **Units**: unmapped units from invoices can be defined here |
| Purchasing | **Suppliers**. **Invoices**: e-Factura XML import, line matching remembered per supplier. **NIR**: draft, discrepancy reasons, post, reverse, printable legal layout |
| Settings | **Restaurant**: profile, branding with live preview, features, languages, opening hours. **VAT rates**, **kitchen stations**, **users and roles** |

### kitchen — kitchen & bar display (Vite + React, installable PWA)

```bash
pnpm --filter @amadya/kitchen dev          # http://localhost:5174/kitchen
```

- **Who uses it:** cooks and bartenders (role KITCHEN, or MANAGER / ADMIN).
- **Station:** each tablet or wall screen picks its station (Bucătărie, Grătar, Bar) and remembers it.
- **Board:** three columns, each with its own colour:

  | Column | Colour |
  |---|---|
  | **În așteptare / Pending** | light yellow |
  | **În preparare / In preparation** | orange |
  | **Gata / Done** | green |

  - Tap a card to move it to the next status.
  - Done cards keep an **Undo** button for 10 minutes.
  - Late tickets get a red border.
- **Card contents:** order number, channel, the takeaway customer's first name, elapsed time and ETA, items with sauces and extras. Notes and allergies are highlighted.
- **Live updates:** new orders arrive over SSE (`/kitchen/events`) with a chime. The board also reloads every 15 s as a safety net.
- **Customer side:** each status change reaches the customer's tracking page and the push notification.
- **Changing colours:** the status colours are CSS variables in `apps/kitchen/src/styles.css`. Swapping orange for blue is one line.

`@amadya/staff-auth` holds the session shared by the staff apps (admin, kitchen; waiter next).

## Checks

```bash
pnpm typecheck && pnpm test
pnpm --filter @amadya/menu-mobile export:check   # bundles iOS + Android with Metro
```
