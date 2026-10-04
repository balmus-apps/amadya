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

## Checks

```bash
pnpm typecheck && pnpm test
pnpm --filter @amadya/menu-mobile export:check   # bundles iOS + Android with Metro
```
