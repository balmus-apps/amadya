# api-docs Specification

## Purpose
Gives the restaurant's administrator browsable documentation of the running Amadya API, with the ability to try
requests using their own session, without exposing the API description publicly.

## Requirements

### Requirement: The running API's spec is available to admins only
The API SHALL serve the OpenAPI document it was built from at `GET /api/v1/admin/openapi.yaml`. Only an authenticated
ADMIN SHALL receive it.

#### Scenario: Admin fetches the spec
- **WHEN** an ADMIN requests `GET /api/v1/admin/openapi.yaml`
- **THEN** the response is 200 with the YAML document, whose `info.version` and paths match the deployed release

#### Scenario: Not signed in
- **WHEN** the spec is requested without a token
- **THEN** the response is 401 and no part of the document is returned

#### Scenario: Other staff
- **WHEN** a MANAGER, WAITER, KITCHEN or CASHIER requests the spec
- **THEN** the response is 403

### Requirement: Admin API documentation page
The admin app SHALL offer an "API" page under Settings, shown in the navigation only to ADMIN users. It SHALL list
every operation of the spec grouped by tag, with parameters, request and response schemas.

#### Scenario: Admin opens the page
- **WHEN** an ADMIN opens Settings → API
- **THEN** every tag of the spec is listed, and expanding an operation shows its parameters and schemas

#### Scenario: Manager
- **WHEN** a MANAGER is signed in to the admin app
- **THEN** the API item is not in the navigation
- **AND** opening `/admin/api-docs` directly shows a "not allowed" message instead of the documentation

### Requirement: Try-it-out uses the admin's session
Requests sent from the page SHALL be authenticated with the admin's current session, refreshed like any other admin
request, without the admin entering a token. They SHALL go to the same server that serves the page.

#### Scenario: Calling an endpoint
- **WHEN** the admin uses "Try it out" on `GET /admin/activity`
- **THEN** the request carries the admin's bearer token and the real response is shown

#### Scenario: Session refreshed
- **WHEN** the access token expired while the page was open
- **THEN** the next try-it-out request uses a refreshed token and succeeds

### Requirement: Real requests are clearly flagged
The page SHALL state, above the documentation, that requests are real, act on this server's data, and are recorded in
the activity log.

#### Scenario: Warning shown
- **WHEN** the page is opened
- **THEN** the warning is visible in the admin's language (Romanian or English) before any operation
