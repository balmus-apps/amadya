# TODO — Waiter app

Status on 2026-10-11: Phase 4 has shipped. It covers the floor, rounds, live "ready" alerts, payments (whole, equal split,
by item; cash or card terminal), transfer, merge and pre-bill. The rules are in
[docs/domain/tables.md](../domain/tables.md). These items are not built yet.

## 1. Floor plan drawn to scale
- [ ] Today tables are a grid per area, ordered by "Ordine" in Admin → Mese.
- [ ] Let the admin drag tables to their real position and shape (round or square, size). The waiter app then shows the
      room as it looks.
- [ ] The database needs a position (x, y), a shape and a rotation per table. The waiter app needs a plan view, with the
      grid kept as a fallback on small phones.

## 2. Removing a single item from a round
- [ ] Today the waiter cancels the whole round and sends a new one.
- [ ] Add "remove item" (or reduce its quantity) on a round. The rule is the same as for cancelling a round: the waiter
      while the kitchen has not started, a manager after that.
- [ ] The kitchen ticket must update live (the item crossed out), and the bill and stock consumption must follow.

## 3. QR codes for guests to order from the table
- [ ] Each table gets a QR code (printable from Admin → Mese). Scanning it opens the customer menu tied to that table.
- [ ] Decide whether a guest's order goes straight to the kitchen or waits for the waiter to confirm it. Confirming is
      safer against pranks.
- [ ] The guest's orders appear as rounds of the table session, and the waiter sees them live.
- [ ] Optional: guests pay their share online from their phone (needs Stripe, see [payments.md](payments.md)).

## 4. Tips
- [ ] Record a tip with a payment (cash or card), separate from the bill.
- [ ] Report tips per waiter and per day. Check with the accountant how card tips are booked.

## 5. Fiscal receipt (Phase 6)
- [ ] Today the pre-bill is only written to the log and is not a fiscal document.
- [ ] With the fiscal printer, every payment at the table must print the bon fiscal. The payment method (cash or card)
      and the VAT groups come from the bill.
- [ ] Print the pre-bill on the real printer of the area or the bar.
- [ ] Depends on choosing the printer hardware (ADR 0003).
