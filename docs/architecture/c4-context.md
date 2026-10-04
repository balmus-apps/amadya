# C4 Level 1 — System Context

```mermaid
C4Context
title Amadya HoReCa – System Context

Person(customer, "Customer", "Orders to-go via web or mobile app, pays with Apple/Google Pay")
Person(waiter, "Waiter", "Opens tables, takes orders, splits bills")
Person(kitchen, "Kitchen staff", "Prepares orders from the queue")
Person(admin, "Manager / Admin", "Menu, warehouses, NIR, invoices, reports, settings")

System(amadya, "Amadya Platform", "Ordering, kitchen queue, inventory & NIR, printing, fiscal receipts")

System_Ext(psp, "Payment provider", "Stripe (Apple Pay / Google Pay / card)")
System_Ext(anaf, "ANAF e-Factura", "Supplier invoices as UBL XML")
System_Ext(ocr, "LLM / OCR service", "Extracts data from invoice photos/PDFs")
System_Ext(push, "Expo Push / Web Push", "Order-ready notifications")
System_Ext(hw, "Printers & AMEF", "Kitchen ESC/POS printers, fiscal printer")

Rel(customer, amadya, "Browses menu, orders, pays, tracks")
Rel(waiter, amadya, "Table orders, bills")
Rel(kitchen, amadya, "Sees queue, marks ready")
Rel(admin, amadya, "Manages")
Rel(amadya, psp, "Creates payments, receives webhooks")
Rel(amadya, anaf, "Imports invoices (XML)")
Rel(amadya, ocr, "Sends invoice images")
Rel(amadya, push, "Sends notifications")
Rel(amadya, hw, "Print jobs via on-site Print Bridge")
```

Notes
- Single-tenant per install: one restaurant (possibly with several stations/printers) per deployment.
- The Print Bridge sits on-site; the platform may run on-site or in the cloud.
