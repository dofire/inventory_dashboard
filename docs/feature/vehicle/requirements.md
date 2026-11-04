# Vehicle feature requirements

## Core requirements

1. Inventory Visualization: Display a filterable list of all vehicles in a dealership's inventory (e.g., filter by make, model, age).
2. Aging Stock Identification: Automatically identify and prominently display "aging stock" (vehicles in inventory for >90 days).
3. Actionable Insights: Allow a manager to log and persist a status or proposed action for each aging vehicle (e.g., "Price Reduction Planned").

## Vehicle service API
The Spring service in `backend/services/sv_vehicle` exposes:

- `GET /vehicles`: returns a JSON vehicle array. `page` is zero-based (default 0),
  `size` is 1–100 (default 20). Optional `make` and `model` accept repeated or
  comma-separated values, matched case-insensitively. `age` is a nonnegative
  minimum number of days in inventory. `aging=true` restricts results to stock
  older than 90 days; `aging=false` returns the complement. Omitted filters do
  not restrict results. Filters combine with AND; values within a filter use OR.
  Results sort by `stockedAt DESC, id DESC` before pagination.
- `GET /vehicle-filters`: returns all distinct makes and their sorted models,
  e.g. `{"makes":{"Honda":["Accord","Civic"],"Toyota":["Camry","Corolla"]}}`.
- `POST /update-vehicle-status`: logs a proposed action with a JSON body containing
  UUID `userId`, UUID `vehicleId`, nonnegative `rowVersion`, and `status`
  (`PRICE_REDUCTION_PLANNED` or `MAINTENANCE_PLANNED`). Optional `actionStatus`
  is `CREATED` (default), `IN_PROGRESS`, `COMPLETED`, or `CANCELLED`.
  Here `status` means the proposed action, not the vehicle's inventory status.
  Returns 204 on success, 400 for invalid input, 404 for an unknown vehicle,
  or 409 for a stale version. Successful updates increment the vehicle's
  `rowVersion`, expose its latest action in subsequent reads, and append an
  action history entry. All vehicles may receive actions, including aging stock.

Example action request (first seeded vehicle, before any updates):

```json
{
  "userId": "00000000-0000-0000-0000-000000000100",
  "vehicleId": "00000000-0000-0000-0000-000000000001",
  "rowVersion": 0,
  "status": "PRICE_REDUCTION_PLANNED"
}
```

`VehicleEntity` maps inventory fields (UUID, make, model, inventory status,
price, currency, stocking timestamp, optimistic version). Inventory statuses are `DRAFT`,
`AVAILABLE`, `RESERVED`, `SOLD`, and `UNAVAILABLE`. Price uses Java `Long` / SQL `BIGINT` and is expressed in the currency's minor
units. Currency is a required uppercase three-letter ISO 4217 code, stored as
`VARCHAR(3)` and returned as `currency` alongside `price` in API responses.
For example, `{"price":2200000,"currency":"USD"}` means USD 22,000.00.
Mock vehicles use USD and preserve their original monetary values in cents.
`VehicleActionEntity` maps the vehicle UUID, action, action status, optimistic
version, and creation/update timestamps and user UUIDs. Both use the `public`
schema. Vehicle indexes support stocking date/ID ordering and make filtering.

The repository currently seeds six vehicles with stable UUIDs ending in 1–6
and stores updates/history in memory. It atomically checks the vehicle version
and records actions; data resets on restart. User IDs are caller supplied for
now, with no manager lookup or authentication. PostgreSQL connectivity is enabled for local infrastructure and health checks;
business reads and writes still use the mock repository. Hibernate schema
generation and Flyway migrations remain disabled. Database-backed endpoint repositories can replace
the shared `MockVehicleStore` later; it must preserve atomic optimistic concurrency and
transactional action history. No database migrations are included yet.


## SQL table definitions

PostgreSQL reference DDL is documented in
[vehicle.sql](../../sql/table/vehicle.sql) and
[vehicle_action.sql](../../sql/table/vehicle_action.sql).
Create `vehicle` before `vehicle_action` because actions reference a vehicle.
UUIDs and audit values are application supplied; user UUIDs have no foreign key
until a manager table is defined. These files are documentation, not automatic
migrations, and do not change the mocked repository.


## Local service port

The vehicle service listens on port `9001` by default. Use
`http://localhost:9001` for local API requests, as configured in
[vehicle.http](../../../backend/services/sv_vehicle/vehicle.http).


## Request correlation

Vehicle APIs accept an optional UUID in the `X-Request-ID` header. Missing or
invalid IDs are replaced with a generated UUID. The canonical ID is echoed in
`X-Request-ID` on the response and included as `requestId` in request-scoped JSON
logs. This is correlation metadata, not a JSON body field, trace ID, or
idempotency key. Actuator endpoints are excluded from this request logger.
The HTTP examples generate a fresh UUID for each request using IntelliJ's
`{{$uuid}}`; VS Code REST Client users should replace it with `{{$guid}}`.

## Inventory frontend

The React dashboard in `frontend/` runs on port 3000, with `/api` proxied to the
vehicle service on port 9001 during development. Filters and pagination are
server-side and synchronized to URL parameters. Make, model, and aging changes apply immediately and reset pagination.
Minimum stock age applies after a 600 ms typing pause, on blur, or on Enter,
without replacing the focused input. Empty means any age; invalid values show
inline guidance and do not trigger requests. Age changes also reset pagination. Model selection
is disabled until a make is selected; changing or clearing the make clears the
selected model. Model URL parameters without a make are ignored. Switching
inventory filters refreshes results even when that combination was recently cached. Global counts and percentages
are omitted because the API exposes no totals. Aging badges use membership in
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
with a reserved loading indicator above the filters and stable table column
widths. Previous rows cannot open a proposal while replacement results load;
aging and next-page queries wait for the current results.

## Backend endpoint packages

Each vehicle endpoint has its own package under `api`: `get_vehicles`,
`get_vehicle_filters`, and `set_vehicle_action`. Each contains one controller
with one endpoint, its service, and its repository. Request and response payloads live in their owning API package:
`UpdateStatusRequest` in `set_vehicle_action` and `VehicleView` in `get_vehicles`.
Shared enums live under `global/model`. The shared in-memory data store is
`global/mock/MockVehicleStore`; repositories belong only to their API packages.
The shared store keeps updates visible across APIs.
Auth and observability also remain under `global`. Existing HTTP paths are unchanged.
