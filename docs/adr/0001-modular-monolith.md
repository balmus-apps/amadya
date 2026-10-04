# ADR 0001 — Modular monolith with Spring Modulith (Kotlin)

**Status:** Accepted — 2026-10-04

## Context
We need many features (ordering, kitchen, inventory, NIR, fiscal, printing), we want strong modularity,
and each install serves a single restaurant. A small team will build it, one app at a time.

## Decision
- One deployable Core API in Kotlin 2.x on Spring Boot 3.5, structured with **Spring Modulith**.
- One module per bounded context (see `docs/architecture/c4-component-backend.md`).
- Modules talk through domain events, persisted in the event publication registry (outbox).
- Boundaries are verified in CI with `ApplicationModules.verify()`.
- Persistence: Spring Data JPA on PostgreSQL 17. Each module owns its tables; no cross-module foreign keys except to ids.

## Consequences
- One container to build and run. Transactions stay simple.
- A module can later be extracted into a service, because its contracts are already events plus API packages.
- Discipline is needed: no shortcuts through another module's internals (enforced by tests).
