# ADR 0005 — Frontend monorepo (pnpm + Turborepo), shadcn/ui, RO/EN

**Status:** Accepted — 2026-10-04

## Decision
- `frontend/` is a pnpm workspace orchestrated by Turborepo.
  - apps: `menu-web` (Next.js, SSR), `menu-mobile` (Expo + NativeWind), `admin`, `waiter`, `kitchen`,
    `queue-display` (Vite + React 19 + TanStack Router; the staff apps are installable PWAs).
  - packages: `api-client` (generated), `ui` (shadcn/ui on Tailwind CSS 4), `i18n` (shared `ro`/`en` messages,
    i18next; `next-intl` adapter for menu-web), `theme` (design tokens from branding), `config` (tsconfig, eslint).
- TypeScript strict. Builds fail on type or lint errors.
- State: TanStack Query for server state, Zustand for small local stores (cart), react-hook-form + zod for forms.
- Localized content from the API arrives already resolved to the request locale; admin screens edit both languages side by side.

## Consequences
- One shared design system and one API client across all apps, with no copy-paste drift.

## Amendment — 2026-10-04 (Phase 2)
- Versions: Next.js 16, React 19, TypeScript 6.0 (TS 7, the native compiler, is not supported by Next yet), Tailwind CSS 4,
  Expo SDK 57 (React Native 0.86, React 19.2).
- **`@amadya/customer-core`** holds the logic shared by menu-web and menu-mobile: cart, modifier rules, opening hours,
  recent orders, saved contact. Its stores use `zustand/vanilla` with injected storage (localStorage or AsyncStorage). Each app binds
  them to its own React with `useStore`, so web (React 19.3) and mobile (React 19.2) never share a React copy.
- **i18n on mobile** uses `use-intl` (the framework-agnostic core of next-intl) with the same `@amadya/i18n` messages.
- **Mobile styling** uses React Native `StyleSheet` with `@amadya/theme` tokens, *not* NativeWind: NativeWind 4 requires Tailwind 3,
  which would clash with the Tailwind 4 used by the web packages in the same workspace. To revisit when NativeWind supports Tailwind 4.
- The mobile app is **white-label**: name, bundle id, colours and API URL come from environment variables (`app.config.ts`), one build per restaurant.
