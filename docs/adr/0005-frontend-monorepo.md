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
