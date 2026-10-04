# ADR 0001 — Modular monolith with Spring Modulith (Kotlin)

**Status:** Accepted — 2026-10-04

## Context
We need many features (ordering, kitchen, inventory, NIR, fiscal, printing), we want strong modularity,
and each install serves a single restaurant. A small team will build it, one app at a time.

## Decision
- One deployable Core API in Kotlin 2.3 on **Spring Boot 4.1** (Java 25, Jackson 3, Hibernate 7), structured with **Spring Modulith 2.1**.
  (The plan said Boot 3.5; its open-source support ended in mid-2026, so Phase 1 started on 4.1.)
- One module per bounded context (see `docs/architecture/c4-component-backend.md`).
- Modules talk through domain events, persisted in the event publication registry (outbox).
- Boundaries are verified in CI with `ApplicationModules.verify()`.
- Persistence: Spring Data JPA on PostgreSQL 17. Each module owns its tables; no cross-module foreign keys except to ids.
- Layout: `ro.amadya.<module>` holds the module's public API (interfaces, DTOs, events); `ro.amadya.<module>.internal` is hidden.
  `shared` and the generated `contract` are open modules that every module may use.

## Consequences
- One container to build and run. Transactions stay simple.
- A module can later be extracted into a service, because its contracts are already events plus API packages.
- Discipline is needed: no shortcuts through another module's internals (enforced by tests).
