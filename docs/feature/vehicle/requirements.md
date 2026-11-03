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
now, with no manager lookup or authentication. Database auto-configuration is
disabled for this mock mode. A database-backed `VehicleRepository` can replace
this implementation later; it must preserve atomic optimistic concurrency and
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
