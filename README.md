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
| `backend/` | Kotlin + Spring Boot (Spring Modulith) core API — see [backend/README.md](backend/README.md) |
| `frontend/` | pnpm + Turborepo: menu-web, menu-mobile (Phase 2 ✅), admin (Phase 3 ✅), kitchen & bar display ✅, waiter, queue-display — see [frontend/README.md](frontend/README.md) |
| `print-bridge/` | On-site Kotlin agent for ESC/POS & fiscal printers *(Phase 6)* |
| `infra/` | Reverse proxy config |
| `branding/` | Default logo/colors (overridable per install) |

## Run

```bash
cp .env.example .env
docker compose up -d
```

Services: PostgreSQL 17, Caddy, the Core API (`https://localhost/api/v1`, or `http://localhost:8080/api/v1` directly) the customer menu (`https://localhost`) the admin (`https://localhost/admin`) and the kitchen & bar display (`https://localhost/kitchen`).
With `SPRING_PROFILES_ACTIVE=dev` the demo restaurant (BurRegescu) is loaded. Apps are added to `docker-compose.yml` as each phase lands.

## Delivery phases

0. Foundations ✅ → 1. Backend core ✅ → 2. Customer menu (web, mobile) ✅ → 3. Admin dashboard ✅
→ 4. Waiter app → 5. Kitchen display + queue screen → 6. Print bridge + fiscal → 7. Invoice scan.
