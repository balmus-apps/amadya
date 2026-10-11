# ADR 0010 — Guests order and pay from the table (QR)

**Status:** Accepted — 2026-10-11. Implemented on branch `qr-table-ordering`.
**Builds on:** table sessions, rounds and item payments (Phase 4, [tables.md](../domain/tables.md)), and online payments
(ADR 0007).

## Story
A guest sits down, scans the QR code on the table and orders from their phone. The waiter keeps taking orders for the
same table: guests and waiter share **one session and one bill**. Any guest at the table can pay the whole bill or only
selected items, with Apple Pay, Google Pay or a card. Other guests pay the waiter in cash or by card terminal as before,
all on the same bill. A log records what happened at the table: who joined, who ordered what, who paid for what.

The old `horeca-webapp` (`RestaurantTableController`, `table-order.tsx`) did a first version of this. It had no
protection against ordering from outside the restaurant, no record of who did what, and computed totals in the browser.
This design keeps the idea and fixes those.

## Decisions (agreed 2026-10-11)
1. **Starting a session:**
   - A guest can scan a **free** table and start its session; that guest becomes the **owner**.
   - **Nothing reaches the kitchen until the waiter confirms the session.** The waiter app shows "Table 5: new guests
     (QR)" with Confirm or Reject.
   - If the waiter already seated the table, the first guest who scans becomes the owner.
2. **Guest rounds:** each round a guest sends **waits for the waiter's OK**. The waiter confirms it (it goes to the
   kitchen) or rejects it (with a reason the guest sees).
   - **If nobody answers in time, it is confirmed automatically** and goes to the kitchen.
   - The time limit is **5 minutes by default**. A manager can change it in Admin → Setări at any time, e.g. longer on
     crowded days. It is a restaurant setting, not a deploy setting.
   - **The same applies to a guest-started table:** it is confirmed automatically when the time runs out.
3. **Joining:**
   - Anyone else who scans **asks to join**. The **owner or any waiter** can let them in or refuse.
   - The waiter can also remove a guest.
   - **Any member** can pay the whole bill or only part of it.
4. **Identity:** only a **first name**, asked when joining. No account, no phone number.
5. **Paying from the phone** works exactly like takeaway: the same payment provider, wallet sheet and webhook
   (ADR 0007). It runs on the fake provider until Stripe or Netopia keys exist.
6. **Mixed payments:** online payments and the waiter's cash or card payments go on the same bill.
7. **What guests see:**
   - **The bill as usual, without names.**
   - **An activity log**, for example "Andrei a intrat la masă", "Robert a comandat 2 cafele și un steak",
     "Mircea a plătit 2 înghețate".
8. **A separate app** (`apps/table`), served at `/t/<code>`, as its own module. It reuses the shared packages: menu,
   cart and modifier rules, UI and i18n.

## Design

**QR code**
- Each table gets a random, revocable code; the QR holds `https://<domain>/t/<code>`, never the table number.
- Admin → Mese prints the codes and can **regenerate** one if a photo of it leaks.
- QR ordering can be switched off per restaurant.

**Guests**
- A `table_guest` belongs to one session: first name, role `OWNER` or `MEMBER`, status `PENDING`, `ACTIVE`, `REJECTED`
  or `REMOVED`, and the hash of a device token.
- The token is created when the guest joins and kept on that phone. It only works for this session, and stops working
  when the table closes or the guest is removed.
- Guests never get a staff login.

**Session states**
- A session started by a guest is `PENDING_CONFIRMATION` until a waiter confirms it. A rejected one is closed.
- Sessions opened by the waiter work as today.

**Guest rounds**
- They are stored as **pending rounds** (the items, who sent them, when) and only become an order (`placeRound`) when
  the waiter confirms, so the kitchen never sees unconfirmed food.
- They show in the waiter app with a countdown. A scheduled job confirms whatever is still waiting when the time is up,
  and the activity log says it was confirmed automatically.
- The time limit (`guestConfirmMinutes`, default 5) is stored with the restaurant settings, next to the QR ordering
  switch. Changing it applies to new requests.

**Who did what**
- Rounds record who placed them (guest or waiter), and payments record who paid (guest or waiter).
- An activity log per session (`table_activity`: time, who, what, details) feeds the guests' log section and the waiter
  app.

**Paying from the phone**
- `POST /t/{code}/payments` with "the whole rest" or the selected units. The server computes the amount, as for waiter
  payments.
- It creates the provider payment (intent → wallet sheet → webhook), linked to the session, and the selected units are
  **reserved while the payment is pending**:
  - another guest can't select them;
  - the reservation is released if the payment fails or expires (10 minutes).
- When the webhook confirms the payment, it becomes a captured session payment with its allocations. If nothing is left
  to pay, the table closes as today.

**Live updates**
- A guest stream (`/t/{code}/events`, authorized by the device token) carries join requests (for the owner), approvals,
  round status, payments and the activity log.
- The waiter stream gets new event kinds: session to confirm, round to confirm, guest asking to join.

**Limits**
- Rate limits per device and per table on join requests and rounds.
- Join requests expire after a few minutes.
- A table can have at most a set number of guests (default: its seats + 2).

## Tasks
**Backend**
1. QR codes per table (create, regenerate) and the public endpoint that resolves a code to a table.
2. Guests: start or join a session, the device token, approve, reject or remove (owner or waiter), owner handover by the
   waiter.
3. Waiter confirmation of guest-started sessions, and pending guest rounds with confirm or reject. Both are confirmed
   automatically when the time runs out (default 5 minutes, changeable in the settings).
4. Recording who placed each round and who made each payment, plus the activity log.
5. Online payments from the table: whole or selected units, reservations, webhook → session payment.
6. Guest event stream and the new waiter events. Rate limits and expiry jobs.
7. Integration tests:
   - start → confirm → join → approve;
   - guest round → confirm, → reject, and → confirmed automatically when the time runs out;
   - two guests paying overlapping items (reservation);
   - the fake-provider webhook closing the table;
   - a removed guest's token being refused.

**Guest app `apps/table`** (`/t/<code>`)

8. The join screen (first name) and the waiting state ("the waiter is confirming your table", "the owner is letting you
   in").
9. The owner sees join requests and accepts or refuses.
10. Menu and cart (shared with menu-web), and sending a round with its status: waiting for the waiter, in the kitchen,
    ready, served, refused.
11. The bill, with no names; pay all or pay selected items through the wallet sheet; the activity log section.

**Waiter app**

12. Confirm new QR tables, confirm or reject guest rounds before their time runs out, approve or remove guests, and see
    the guests and the log on the table screen.

**Admin and infrastructure**

13. Print or download the QR codes, the QR ordering switch, and the confirmation time limit (default 5 minutes,
    changeable at any time).
14. A Caddy route for `/t/*`, a compose service, the production image and the release script.
15. Docs: `tables.md`, plus the waiter todo (the QR item is done).
