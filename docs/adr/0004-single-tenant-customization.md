# ADR 0004 — Single-tenant per install, customization via settings & branding

**Status:** Accepted — 2026-10-04

## Decision
- Each restaurant runs its own deployment (`docker-compose.yml` + `.env`). There is no `tenant_id` in the schema.
- Customization:
  - The `settings` module stores the restaurant profile (CUI, Reg. Com., address), locales, currency, VAT rates,
    opening hours, stations, printers and feature flags (tables, takeaway, online payments, KDS, queue display, invoice OCR).
  - Branding (name, logo, colors, fonts) is exposed through `GET /api/v1/settings/public` as theme tokens.
    Web apps apply them as CSS variables; the Expo app applies them as a theme.
  - `branding/` holds the defaults; an install can override them with a volume mount or from the admin UI.
- All secrets come from the environment. Nothing install-specific is committed.

## Consequences
- Simple data isolation and backup per client. Upgrades are rolled out per install, using tagged images.
