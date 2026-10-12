# ADR 0011 — Activity log of staff actions

**Status:** Accepted — 2026-10-12. Implemented on branch `activity-log`.

## Context
The owner creates accounts for managers, waiters, kitchen staff and cashiers, and needs to see what each of them did:
who changed a price, who cancelled an order, who voided a payment, who tried something they are not allowed to do.
Nothing in the system recorded this across apps. Some modules keep a user id on their own records (NIR `posted_by`,
stock movements), and the table log (ADR 0010) covers one table session. None of these can answer "what did Ana do
today?".

## Decisions (agreed 2026-10-12)
1. **What is logged:** every **write** request (POST/PUT/PATCH/DELETE) made by **staff** (ADMIN, MANAGER, WAITER,
   KITCHEN, CASHIER), in any app, **including refused and failed ones**, plus logins (also failed ones) and logouts.
   Reads are not logged (too noisy), nor are guests and customers, nor token refreshes.
2. **Who sees it:**
   - **ADMIN** sees everything.
   - A **MANAGER** sees only the staff below them (waiters, kitchen, cashiers). They do not see other managers, admins,
     or login attempts with unknown emails.
   - Other roles get 403.
3. **Retention:** set by the admin in **Restaurant settings** (`activityRetentionDays`), 30 days by default and up to
   3650. An hourly job deletes older entries. Each restaurant chooses how much history it wants to keep and has room
   for.
4. **Updates:** the page updates when the user refreshes it, with no live feed yet. A live view would build on events
   (see Consequences).

## How it works
- **Automatic capture:** an MVC `HandlerInterceptor` in the new `audit` module (`afterCompletion`) records each write
  request that reached a controller.
  - The action is the handler method name, which is the OpenAPI `operationId` (ADR 0002). A new endpoint is logged with
    no extra code.
  - The row is inserted in its own transaction, after the request's own work has committed or rolled back. If the
    insert fails, only a warning is logged; the response is never affected.
- **What was acted on:** a `ResponseBodyAdvice` reads the DTO that the action returned and keeps its best-known label:
  order or document number, table, name, title, code or email. For an error response it keeps the problem `code`
  instead.
- **Readable changes:** services can add to the entry through `shared.Activity`, a request-scoped note. Most actions
  don't need it. These ones use it:
  - price changes
  - names of deleted records
  - cancel reasons
  - table payments
  - role, activation and password changes on users

  `Activity.actingAs` names the person on login and logout, which carry no token.
- **Which app:** the staff apps send `X-Amadya-App: admin | waiter | kitchen` through the shared API client. Without it,
  the app is inferred from the path: `/admin` means admin, `/kitchen` kitchen, `/tables` waiter, anything else other.
  The header is self-declared. It is used for filtering only, never for authorization.
- **Storage:** one append-only table, `activity_log`. Ids are UUIDv7, so `id DESC` is newest first and also serves as
  the page cursor. The API has no endpoint to edit or delete entries.

## Consequences
- **Coverage is broad with little code,** but entries are only as readable as the returned DTO, or the note a service
  adds.
- **Requests refused by the URL rules in `SecurityConfig`** (e.g. a waiter calling `/admin/**`) never reach a
  controller, so they are not logged. A 403 from `@PreAuthorize` is logged.
- **Request bodies are not stored,** so passwords and personal data never land in the log.
- **Live view later:** the audit module can publish an `ActivityRecorded` event after each insert and serve it over SSE,
  like the kitchen and tables feeds. The table and the API stay as they are.
