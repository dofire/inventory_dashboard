# Vehicle feature requirements

## Core requirements

1. Inventory Visualization: Display a filterable list of all vehicles in a dealership's inventory (e.g., filter by make, model, age).
2. Aging Stock Identification: Automatically identify and prominently display "aging stock" (vehicles in inventory for >90 days).
3. Actionable Insights: Allow a manager to log and persist a status or proposed action for each aging vehicle (e.g., "Price Reduction Planned").

## Vehicle service API
The Spring service in `backend/services/sv_vehicle` exposes the following APIs.
All require a supported `dealershipId` (`DEALER_HCM_001` or `DEALER_HN_002`):
a query parameter for reads, a body field for actions. Missing or unsupported IDs
return 400. Vehicle responses include `dealershipId`. Filtering and sorting only
operate on vehicles owned by that dealership; filter options are scoped too.
An action against another dealership's vehicle returns 404 without mutation.


- `GET /vehicles`: returns a vehicle array under `data`, with a sibling
  `pagination` object containing zero-based `page`, page `size`, `totalItems`,
  and `totalPages` (zero for no matches). A separate SQL COUNT query uses the same
  dealership and filters as the page query, before pagination. Both queries share
  one repeatable-read transaction and one age cutoff timestamp. Totals remain
  accurate for empty or out-of-range pages. `page` is zero-based (default 0),
  `size` is 1–100 (default 20). Optional `make` and `model` accept repeated or
  comma-separated values, matched case-insensitively. `age` is a nonnegative
  minimum number of days in inventory. `aging=true` restricts results to stock
  older than 90 days; `aging=false` returns the complement. Omitted filters do
  not restrict results. Filters combine with AND; values within a filter use OR.
  `sortKey` accepts `make`, `model`, `price`, `status`, `stockedAt`,
  `daysInStock`, or `action`; `sortOrder` accepts `asc` or `desc` (case-insensitive).
  Defaults are `stockedAt` and `desc`. Sorting precedes pagination, with UUID as
  a deterministic tie-breaker. Vehicle sorts by make then model; price sorts by
  currency then minor-unit amount (no exchange-rate conversion). Missing actions
  sort first ascending. Days in stock reverses stocking timestamp order.
  Unsupported sort keys/orders return 400.
- `GET /vehicle-filters`: returns all distinct makes and their sorted models,
  with the `makes` object inside `data`.
- `POST /update-vehicle-status`: logs a proposed action with a JSON body containing
  UUID `userId`, UUID `vehicleId`, nonnegative `rowVersion`, and `status`
  (`PRICE_REDUCTION_PLANNED` or `MAINTENANCE_PLANNED`). Optional `actionStatus`
  is `CREATED` (default), `IN_PROGRESS`, `COMPLETED`, or `CANCELLED`.
  Here `status` means the proposed action, not the vehicle's inventory status.
  Returns 200 with `data: null` on success, 400 for invalid input, 404 for an unknown vehicle,
  or 409 for a stale version. Successful updates increment the vehicle's
  `rowVersion`, expose its latest action in subsequent reads, and append an
  action history entry. All vehicles may receive actions, including aging stock.

Example action request (first seeded vehicle, before any updates):

```json
{
  "dealershipId": "DEALER_HCM_001",
  "userId": "00000000-0000-0000-0000-000000000100",
  "vehicleId": "00000000-0000-0000-0000-000000000001",
  "rowVersion": 0,
  "status": "PRICE_REDUCTION_PLANNED"
}
```

`VehicleEntity` maps inventory fields (UUID, dealership ID, make, model, inventory status,
price, currency, stocking timestamp, optimistic version). Inventory statuses are `DRAFT`,
`AVAILABLE`, `RESERVED`, `SOLD`, and `UNAVAILABLE`. Price uses Java `Long` / SQL `BIGINT` and is expressed in the currency's minor
units. Currency is a required uppercase three-letter ISO 4217 code, stored as
`VARCHAR(3)` and returned as `currency` alongside `price` in API responses.
For example, `{"price":2200000,"currency":"USD"}` means USD 22,000.00.
`VehicleActionEntity` maps the vehicle UUID, action, action status, optimistic
version, and creation/update timestamps and user UUIDs. Both use the `public`
schema. Dealership IDs map to required `dealership_id VARCHAR(50)`. Vehicle indexes are
`(stocked_at DESC, id DESC)` and `(dealership_id, stocked_at DESC, id DESC)`;
the dealership index replaces the previous make index.

Business repositories use Spring JDBC with the configured PostgreSQL datasource.
Inventory queries apply dealership, make/model, minimum age, aging, sorting, and
pagination in SQL. Filter options use dealership-scoped distinct make/model
queries. Latest actions come from persisted action history, ordered by vehicle
revision, creation timestamp, then UUID. A version-conditional vehicle UPDATE
and action-history INSERT share one transaction. Failed inserts roll back the
version increment; concurrent stale writes return 409. Vehicle history survives
backend restarts. No mock inventory fallback or automatic data seeding exists.
The frontend supplies user and dealership IDs from the mock login profile;
there is no production manager authentication or dealership authorization.

Flyway is enabled. `V1__vehicle_persistence.sql` creates missing tables and indexes
without replacing existing rows. For an existing local schema without Flyway
history, baseline version 0 allows V1 to adopt it. Hibernate validates the schema
rather than generating it. These settings support the existing local database;
future schema changes require new versioned migrations.


## SQL table definitions

PostgreSQL reference DDL is documented in
[vehicle.sql](../../sql/table/vehicle.sql) and
[vehicle_action.sql](../../sql/table/vehicle_action.sql).
Create `vehicle` before `vehicle_action` because actions reference a vehicle.
UUIDs and audit values are application supplied; user UUIDs have no foreign key
until a manager table is defined. These files remain reference DDL; application
schema changes run from `src/main/resources/db/migration`.


## Local service port

The vehicle service listens on port `9001` by default. Use
`http://localhost:9001` for local API requests, as configured in
[vehicle.http](../../../backend/services/sv_vehicle/vehicle.http).


## Request correlation

Vehicle APIs accept an optional UUID in the `X-Request-ID` header. Missing or
invalid IDs are replaced with a generated UUID. The canonical ID is echoed in
`X-Request-ID` on the response and included as `requestId` in request-scoped JSON
logs. Application JSON responses also include `requestId` and `traceId` alongside
`data`; these IDs are correlation metadata, not idempotency keys. Actuator endpoints are excluded from this request logger.
The HTTP examples generate a fresh UUID for each request using IntelliJ's
`{{$uuid}}`; VS Code REST Client users should replace it with `{{$guid}}`.

## Inventory frontend

The React dashboard in `frontend/` runs on port 3000, with `/api` proxied to the
vehicle service on port 9001 during development. Filters and pagination are
server-side and synchronized to URL parameters. Clicking a table column header
cycles through ascending, descending, and reset, resetting pagination each time.
Reset removes both sort parameters from the URL and request, restoring backend
default ordering. The active header has an accent background and direction arrow,
and exposes its sort direction to assistive technology. Make, model, and aging changes apply immediately and reset pagination.
Minimum stock age applies after a 600 ms typing pause, on blur, or on Enter,
without replacing the focused input. Empty means any age; invalid values show
inline guidance and do not trigger requests. Age changes also reset pagination. Model selection
is disabled until a make is selected; changing or clearing the make clears the
selected model. Model URL parameters without a make are ignored. Switching
inventory filters refreshes results even when that combination was recently cached. The table footer shows the visible range and total matching vehicles, plus
current/total pages. Next-page availability comes from response pagination; no
extra next-page request is needed. Aging badges use membership in
backend aging-filter results; displayed days use local date formatting.

Managers can inspect the latest proposed action, create proposals, and record
status changes using the existing endpoint. Action history/timestamps and vehicle
status filtering are not exposed by the API and are not fabricated. The signed-in mock profile supplies the user UUID for attribution automatically;
managers see their name and email rather than entering a UUID. Clicking a vehicle
row or its keyboard-accessible vehicle/Manage button opens the detail dialog. Version conflicts require refresh
and review before another submission. See the
[frontend guide](../../../frontend/README.md) for configuration and limitations.

### Appearance

The dashboard uses a single inventory view with filters, table, and action dialog.
Decorative navigation, workspace labels, promotional banners, and repeated
explanations are omitted. Light/dark mode follows the system preference initially;
an explicit toggle persists the chosen mode locally. Theme colors cover controls,
tables, badges, notifications, and dialogs.

### Filter loading behavior

Filter and page transitions retain the previous vehicle rows while fetching,
with stable table column widths. A circular progress indicator centered in the
viewport appears over a dimmed overlay during inventory, filter, aging, and
pagination requests. Saving a proposal or refreshing from the vehicle dialog
shows the same indicator centered over the dialog. Previous rows cannot open a proposal while replacement results load;
aging queries wait for the current results.

## Backend endpoint packages

Each vehicle endpoint has its own package under `api`: `get_vehicles`,
`get_vehicle_filters`, and `set_vehicle_action`. Each contains one controller
with one endpoint, its service, and its repository. Request and response payloads live in their owning API package:
`UpdateStatusRequest` in `set_vehicle_action` and `VehicleView` in `get_vehicles`.
Shared enums live under `global/model`. Repositories remain in their API
packages and use the same PostgreSQL datasource. `MockVehicleStore` is removed.
Auth and observability also remain under `global`. Existing HTTP paths are unchanged.

## Shared payload contract

Application request DTOs extend `global/api/BaseRequest`. Endpoint response DTOs
extend `BaseResponse<T>`, with `{ "requestId": "...", "traceId": "...", "data": ... }`.
Nested data models such as vehicles and profiles are domain DTOs rather than
response envelopes. Successful actions return HTTP 200 with `data: null`.
Application controller errors use the same envelope with a generic message in
`data` and their original HTTP status. Actuator keeps its standard response format.

Example paginated response shape:

```json
{
  "requestId": "...",
  "traceId": "...",
  "data": [],
  "pagination": { "page": 0, "size": 20, "totalItems": 0, "totalPages": 0 }
}
```

## Vehicle action history

A vehicle has many immutable history entries through `vehicle_action.vehicle_id`.
The inventory row shows the latest current proposal; the detail dialog shows the
full history with action/status, timestamp, and author, newest revision first.
`GET /vehicles/{vehicleId}/actions` requires `dealershipId` and accepts zero-based
`page` and `size` (default 20, maximum 100). It returns the entry array in `data`
and matching totals in sibling `pagination`. Unknown or other-dealership vehicles
return 404. Each entry includes id, action, actionStatus, removed, rowVersion,
createdAt, and createdBy. Earlier history entries remain visible after updates.

`POST /remove-vehicle-action` accepts dealershipId, userId, vehicleId, and
rowVersion. It clears both current action and actionStatus and increments the
vehicle version. It appends an audited removal event rather than deleting any
history. Removal entries expose `removed: true` with null action/status. The UI
asks for confirmation before removal. Stale versions return 409; no current
proposal or an inaccessible vehicle returns 404. Failed removals roll back the
version increment. A new proposal may be created afterward.

Flyway V2 adds a `removed` flag with default false to existing history rows;
existing actions and statuses are preserved. These endpoints follow the same
per-API controller/service/repository/payload package layout as other APIs.
