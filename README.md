# Amadya — HoReCa Platform

Modular restaurant platform: customer to-go ordering (web + mobile), waiter table service,
kitchen queue with timers, warehouses with NIR, invoice import/scan, kitchen & fiscal printing.
UI in Romanian and English. Single-tenant per install, customizable via settings & branding.

## Repository layout

| Path | What |
|---|---|
| `docs/architecture` | C4 diagrams (Mermaid) |
| `docs/adr` | Architecture Decision Records |
| `docs/domain` | Domain rules: NIR, order lifecycle, warehouses, ERD |
| `api/openapi` | Contract-first OpenAPI spec — source of truth for backend & frontend clients |
| `backend/` | Kotlin + Spring Boot (Spring Modulith) core API *(Phase 1)* |
| `frontend/` | pnpm + Turborepo: menu-web, menu-mobile, admin, waiter, kitchen, queue-display *(Phases 2–5)* |
| `print-bridge/` | On-site Kotlin agent for ESC/POS & fiscal printers *(Phase 6)* |
| `infra/` | Reverse proxy config |
| `branding/` | Default logo/colors (overridable per install) |

## Run

```bash
cp .env.example .env
docker compose up -d
```

Services (Phase 0): PostgreSQL 17, MinIO, Caddy. Apps are added to `docker-compose.yml` as each phase lands.

## Delivery phases

0. Foundations (this) → 1. Backend core → 2. Customer menu (web, mobile) → 3. Admin dashboard
→ 4. Waiter app → 5. Kitchen display + queue screen → 6. Print bridge + fiscal → 7. Invoice scan.
