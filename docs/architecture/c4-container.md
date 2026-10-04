# C4 Level 2 — Containers

```mermaid
C4Container
title Amadya – Containers

Person(customer, "Customer")
Person(staff, "Staff", "Admin, waiter, kitchen")

System_Boundary(s, "Amadya (docker-compose)") {
  Container(menuWeb, "Menu Web", "Next.js", "Customer menu, cart, checkout, order tracking")
  Container(admin, "Admin Dashboard", "React + Vite", "Back-office: catalog, stock, NIR, reports, settings")
  Container(waiter, "Waiter App", "React PWA", "Floor plan, tables, orders, bill split")
  Container(kds, "Kitchen Display", "React PWA", "Queue per station, timers, bump")
  Container(queue, "Queue Display", "React", "TV screen: preparing / ready order numbers")
  Container(proxy, "Reverse proxy", "Caddy", "TLS, path routing")
  Container(api, "Core API", "Kotlin, Spring Boot, Spring Modulith", "REST (OpenAPI), WebSocket/STOMP, SSE")
  ContainerDb(db, "Database", "PostgreSQL 17", "Schema managed by Flyway")
  ContainerDb(s3, "Object storage", "MinIO (S3)", "Images, invoice scans, generated PDFs")
}

Container(menuMobile, "Menu Mobile", "Expo / React Native", "Customer app with push notifications")
Container(bridge, "Print Bridge", "Kotlin agent, on-site", "ESC/POS + fiscal (AMEF) drivers")

System_Ext(psp, "Payment provider", "Stripe")
System_Ext(hw, "Printers / AMEF", "USB, Bluetooth, LAN, Serial")
System_Ext(ocr, "LLM / OCR")
System_Ext(push, "Expo / Web Push")

Rel(customer, menuWeb, "HTTPS")
Rel(customer, menuMobile, "Uses")
Rel(staff, admin, "HTTPS")
Rel(staff, waiter, "HTTPS")
Rel(staff, kds, "HTTPS")
Rel(menuWeb, proxy, "HTTPS")
Rel(menuMobile, proxy, "HTTPS")
Rel(admin, proxy, "HTTPS")
Rel(waiter, proxy, "HTTPS / WSS")
Rel(kds, proxy, "HTTPS / WSS")
Rel(queue, proxy, "SSE")
Rel(proxy, api, "REST / WS / SSE")
Rel(api, db, "JDBC")
Rel(api, s3, "S3 API")
Rel(api, psp, "HTTPS + webhooks")
Rel(api, ocr, "HTTPS")
Rel(api, push, "HTTPS")
Rel(bridge, proxy, "STOMP over WSS (outbound only)")
Rel(bridge, hw, "ESC/POS, fiscal protocol")
```

All frontends use the same generated TypeScript client built from `api/openapi`.
The Print Bridge only opens outbound connections, so the restaurant needs no inbound ports.
