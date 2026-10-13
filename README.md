# Amadya

**The whole restaurant on one platform:** orders, tables, kitchen, payments and stock, from the guest's phone to the
warehouse.

**[Română](#română) · [English](#english) · [For developers](#for-developers)**

---

## Română

### Ce este Amadya
Amadya este o platformă care leagă **toate părțile unui restaurant**. Clientul comandă de pe telefon, ospătarul servește
mesele, bucătăria pregătește, iar managerul vede totul în timp real. Fiecare are ecranul lui, potrivit cu ce face, și
toate vorbesc între ele **instantaneu**. Nu mai sunt bilețele pierdute, comenzi strigate peste tejghea sau stocuri
socotite seara pe hârtie.

Totul funcționează în **română și engleză** și poartă **numele, logo-ul și culorile restaurantului**.

### O zi într-un restaurant cu Amadya

1. **Clientul comandă de acasă.** Deschide meniul pe telefon, cu poze, prețuri și opțiuni („cu sos de usturoi sau
   cheese?”), comandă la pachet și plătește la ridicare. Când restaurantul activează plățile online, plătește direct cu
   Apple Pay sau Google Pay.
2. **La tejghea, casierul ține ritmul.** Pe ecranul casei ia comanda clientului din fața lui și încasează pe loc, numerar
   sau card, cu restul calculat. Comanda pleacă la bucătărie deja plătită. Tot aici apar comenzile venite de pe telefon:
   casierul le confirmă sau le refuză cu un motiv pe care clientul îl vede. Dacă nu are timp, ele pleacă singure la
   bucătărie după câteva minute. La ridicare, predă comanda și încasează ce nu e plătit.
3. **La masă, comandă singur.** Scanează codul QR de pe masă, își scrie numele și comandă. Prietenii care vin după el
   scanează același cod și cer să se alăture. Cine a deschis masa sau ospătarul îi primește, ca nimeni din afară să nu
   poată comanda în numele mesei.
4. **Ospătarul rămâne stăpân pe sală.** Vede pe telefon toate mesele: libere, ocupate sau cu mâncarea gata de dus.
   Confirmă comenzile venite din QR. Dacă e prea ocupat, ele pleacă singure spre bucătărie după 5 minute, iar timpul se
   poate schimba în zilele aglomerate. Ia comenzi, mută sau unește mese și încasează.
5. **Bucătăria și barul primesc doar ce le privește.** Pe ecranul lor apar comenzile cu culori clare: **galben** în
   așteptare, **portocaliu** în preparare, **verde** gata. Cu o atingere comanda trece mai departe, iar ospătarul și
   clientul află imediat că mâncarea e gata.
6. **Clienții la pachet își văd numărul pe televizor.** Un ecran deasupra tejghelei arată ce comenzi se pregătesc și pe
   care le pot ridica, cu anunț sonor.
7. **Plata e simplă, oricum ar vrea clienții.** Toată nota sau împărțită egal. Fiecare își plătește doar ce a comandat,
   de pe telefon sau la ospătar, numerar ori card. Amadya ține socoteala: nimic nu se plătește de două ori și nimic nu
   rămâne neplătit.
8. **Totul rămâne scris.** La fiecare masă se vede cine a venit, ce a comandat și ce a plătit, de exemplu: „Andrei a
   comandat 2 cafele”, „Mircea a plătit limonada de pe telefon”.
9. **Stocul se scade singur.** Fiecare preparat are rețeta lui. Când se vinde un burger, din stoc ies chifla, carnea și
   sosul. Marfa intră pe baza facturii de la furnizor, iar NIR-ul se face din câteva clicuri.
10. **Managerul vede tot.** Vânzări pe zile și ore, ce se vinde cel mai bine, costul ingredientelor, stocuri pe gestiuni
   și alerte când ceva se termină.

### Ce primește fiecare

| Cine | Ce primește |
|---|---|
| **Clientul** | Meniu pe telefon, comandă fără așteptare, plată rapidă, notificare când comanda e gata |
| **Casierul** | Comenzi la tejghea cu încasare pe loc, comenzile de pe telefon de confirmat, predarea comenzilor gata |
| **Ospătarul** | Toate mesele pe telefon, alerte când mâncarea e gata, încasare și împărțirea notei în câteva secunde |
| **Bucătăria și barul** | Ecran clar, cu fiecare comandă la stația ei, cronometre și semnal când întârzie ceva |
| **Managerul** | Meniu, prețuri, promoții, stocuri, facturi, NIR, rapoarte și personal, într-un singur loc |

### Paginile principale
Pe calculatorul pe care rulează Amadya (datele de test vin deja încărcate):

| Pagina | Adresa | Cont de test |
|---|---|---|
| Meniul pentru clienți | <https://localhost> | fără cont |
| Comandă de la masă (QR) | <https://localhost/t/demo-5> | doar un prenume |
| Aplicația ospătarului | <https://localhost/waiter> | `ospatar@amadya.local` / `Ospatar12345!` |
| Casa (comenzi la tejghea, confirmarea comenzilor de pe telefon) | <https://localhost/cashier> | `admin@amadya.local` / `Admin12345!` |
| Ecranul bucătăriei și al barului | <https://localhost/kitchen> | `admin@amadya.local` / `Admin12345!` |
| Ecranul de comenzi (TV) | <https://localhost/queue> | fără cont |
| Administrare | <https://localhost/admin> | `admin@amadya.local` / `Admin12345!` |

Puteți deschide paginile și de pe telefon, în aceeași rețea Wi-Fi (vezi [For developers](#for-developers)).

### Ce urmează
- **e-Factura (ANAF):** facturile de la furnizori intră singure în Amadya, iar facturile către firme pleacă automat
  ([propunere](docs/adr/0009-efactura-integration.md)).
- **Imprimante și casa de marcat:** bonuri în bucătărie și bon fiscal la plată.
- **Plăți reale cu cardul:** după conectarea procesatorului de plăți ([pași](docs/todo/payments.md)).

---

## English

### What Amadya is
Amadya is a platform that connects **every part of a restaurant**. Guests order from their phones, waiters serve the
tables, the kitchen cooks, and the manager sees everything as it happens. Everyone gets a screen made for their job, and
all of them talk to each other **instantly**. No more lost paper tickets, orders shouted across the counter, or stock
counted on paper at night.

Everything works in **Romanian and English** and carries the **restaurant's own name, logo and colours**.

### A day in a restaurant with Amadya

1. **Guests order from home.** They open the menu on their phone, with photos, prices and choices ("garlic or cheese
   sauce?"), order takeaway and pay at pickup. When the restaurant turns online payments on, they pay right away with
   Apple Pay or Google Pay.
2. **At the counter, the cashier keeps things moving.** On the cashier screen they take the order of the customer in
   front of them and the money at once, in cash or by card, with the change worked out. The order reaches the kitchen
   already paid. Orders from phones appear here too: the cashier confirms them, or refuses them with a reason the
   customer sees. If the cashier has no time, they go to the kitchen on their own after a few minutes. At pickup, the
   cashier hands the order over and takes any payment still due.
3. **At the table, they order themselves.** A guest scans the QR code on the table, types a first name and orders.
   Friends who arrive later scan the same code and ask to join. Whoever opened the table, or the waiter, lets them in, so
   nobody outside can order on the table's bill.
4. **The waiter stays in charge of the floor.** Every table is on the waiter's phone: free, occupied, or with food
   ready to serve. Orders from the QR code wait for the waiter's OK. If the waiter is too busy, they go to the kitchen on
   their own after 5 minutes, a time that can be changed on busy days. Waiters take orders, move or join tables, and
   take payments.
5. **The kitchen and the bar get only what concerns them.** Orders appear on their screen in clear colours:
   **yellow** waiting, **orange** being prepared, **green** done. One tap moves an order along, and the waiter and the
   guest know at once that the food is ready.
6. **Takeaway guests see their number on a TV.** A screen above the counter shows which orders are being prepared and
   which are ready, with a sound announcement.
7. **Paying is simple, however guests want to pay.** The whole bill or split equally. Each guest pays only for what
   they ordered, from their phone or to the waiter, in cash or by card. Amadya keeps track: nothing is paid twice and
   nothing is left unpaid.
8. **Everything is on record.** Each table shows who came, what they ordered and what they paid, for example "Andrei
   ordered 2 coffees", "Mircea paid for the lemonade from his phone".
9. **Stock updates itself.** Every dish has its recipe. When a burger is sold, the bun, the meat and the sauce come out
   of stock. Goods come in from the supplier's invoice, and the goods receipt note (NIR) takes a few clicks.
10. **The manager sees everything.** Sales by day and hour, best sellers, food cost, stock per warehouse, and alerts when
   something runs low.

### What each person gets

| Who | What they get |
|---|---|
| **Guests** | The menu on their phone, ordering without waiting, quick payment, a notice when the food is ready |
| **Cashier** | Counter orders paid on the spot, phone orders to confirm, ready orders to hand over |
| **Waiters** | Every table on their phone, alerts when food is ready, payments and split bills in seconds |
| **Kitchen and bar** | A clear screen with each order at its station, timers, and a signal when something runs late |
| **Manager** | Menu, prices, promotions, stock, invoices, NIR, reports and staff, all in one place |

### The main pages
On the computer running Amadya (demo data is already loaded):

| Page | Address | Demo login |
|---|---|---|
| Customer menu | <https://localhost> | none |
| Ordering at the table (QR) | <https://localhost/t/demo-5> | just a first name |
| Waiter app | <https://localhost/waiter> | `ospatar@amadya.local` / `Ospatar12345!` |
| Cashier (counter orders, confirming phone orders) | <https://localhost/cashier> | `admin@amadya.local` / `Admin12345!` |
| Kitchen and bar display | <https://localhost/kitchen> | `admin@amadya.local` / `Admin12345!` |
| Order screen (TV) | <https://localhost/queue> | none |
| Admin | <https://localhost/admin> | `admin@amadya.local` / `Admin12345!` |

The pages also open from a phone on the same Wi-Fi network (see [For developers](#for-developers)).

### What comes next
- **e-Factura (ANAF):** supplier invoices arrive in Amadya on their own, and invoices to companies are sent
  automatically ([proposal](docs/adr/0009-efactura-integration.md)).
- **Printers and the fiscal printer:** kitchen tickets and the fiscal receipt at payment.
- **Real card payments:** once the payment provider is connected ([steps](docs/todo/payments.md)).

---

## For developers

Modular restaurant platform, single-tenant per install, customizable through settings and branding.

### Repository layout

| Path | What |
|---|---|
| `docs/architecture` | C4 diagrams (Mermaid) |
| `docs/adr` | Architecture Decision Records |
| `docs/domain` | Domain rules: NIR, order lifecycle, warehouses, tables, e-Factura, ERD |
| `docs/todo` | Open work lists (payments, waiter app) |
| `api/openapi` | Contract-first OpenAPI spec, the source of truth for the backend and the frontend clients |
| `backend/` | Kotlin + Spring Boot (Spring Modulith) core API, see [backend/README.md](backend/README.md) |
| `frontend/` | pnpm + Turborepo: menu-web, menu-mobile, admin, kitchen, waiter, cashier, table (QR), queue-display, see [frontend/README.md](frontend/README.md) |
| `print-bridge/` | On-site Kotlin agent for ESC/POS and fiscal printers *(Phase 6)* |
| `infra/` | Reverse proxy config |
| `branding/` | Default logo and colours (overridable per install) |

### Run

```bash
cp .env.example .env
docker compose up -d
```

- **Services:** PostgreSQL 17, Caddy, the core API (`https://localhost/api/v1`, or `http://localhost:8080/api/v1`
  directly) and the apps listed in the tables above.
- **Demo data:** with `SPRING_PROFILES_ACTIVE=dev` the Amadya demo restaurant is loaded, with tables, a waiter
  account and QR codes `demo-1` … `demo-6`, `demo-t1` … `demo-t4`.
- **Certificate:** locally Caddy uses its own certificate, so the browser asks you to accept it once.
- **Phones on the same Wi-Fi:** set `AMADYA_LAN_SITE=http://<this machine's IP>` in `.env` and run
  `docker compose up -d caddy`.

Production (amadya.alphatech.ro, behind the shared proxy, images from GHCR): see [docs/deploy.md](docs/deploy.md).

### Delivery phases

0. Foundations ✅ → 1. Backend core ✅ → 2. Customer menu (web, mobile) ✅ → 3. Admin dashboard ✅
→ 4. Waiter app ✅ → 5. Kitchen display ✅ + queue screen ✅ → QR ordering at the table ✅ → Cashier app ✅
→ e-Factura *(proposed, [ADR 0009](docs/adr/0009-efactura-integration.md))* → 6. Print bridge + fiscal.
