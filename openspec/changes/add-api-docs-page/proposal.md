# Proposal

## Why

The API (`/api/v1`) has more than 100 operations, but the only way to explore them today is to read the 3,600-line
`api/openapi/openapi.yaml` and call endpoints with `curl` and a hand-copied token. The owner wants a browsable,
try-it-out page. It must not publish a map of the whole API to the internet.

## What Changes

- **API docs page:** a new admin page, **Settings → API** (`/admin/api-docs`), visible only to the ADMIN. It shows
  every operation grouped by tag, with parameters, request and response formats, and who may call it.
- **Try it out:** sends real requests with the admin's current session token. No token is copied by hand.
- **Warning banner:** the page says that requests are real, act on this server's data, and are recorded in the
  activity log.
- **New endpoint:** `GET /api/v1/admin/openapi.yaml` returns the exact spec the running API was built from, ADMIN only.
- **Contract:** the spec gains a `docs` tag for this endpoint. It is hand-implemented, like `streams` and `webhooks`.

## Capabilities

### New Capabilities
- `api-docs`: Browsable, admin-only documentation of the running API: who can see it, where the spec comes from, and
  how "try it out" calls are authenticated.

### Modified Capabilities

(none)

## Impact

- **Backend:**
  - Gradle copies `api/openapi/openapi.yaml` into the jar. The Dockerfile already copies `api/` into the build stage.
  - One small controller in a new `docs` module.
  - An integration test.
- **Contract:** `api/openapi/openapi.yaml` gets the `docs` tag and the new path. The client is regenerated.
- **Frontend (admin only):**
  - new dependency `swagger-ui-react@5.33.0`, pinned
  - the page is lazy-loaded, so about 1 MB loads only when the page is opened
  - a route, an admin-only sidebar item and RO/EN labels
- **Security:** nothing new is public. The page sits behind the admin login, and the spec endpoint returns 401 without
  a login and 403 for non-admins.

## Out of scope

- A public developer portal or docs for third parties.
- Translating the endpoint descriptions. They stay in English, as written in the spec. Only the page's own labels are
  RO/EN.
- Generated client SDKs for outside use.

## Decisions for the owner

1. **Who sees the page.** *Recommended: ADMIN only.* The alternative also lets MANAGER see it, but managers would get
   403 on many admin-only calls they could see listed.
