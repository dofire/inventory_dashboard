# Vehicle Inventory Dashboard

A web application for managing dealership inventory. Users can filter and sort
vehicles, identify stock older than 90 days, and record proposed actions such as
price reductions or maintenance.

The frontend uses React and TypeScript. The backend uses Java 21 and Spring Boot,
with PostgreSQL for vehicle data and action history.

This implements **Scenario B: The Intelligent Inventory Dashboard** from the
Keyloop technical assessment. The backend is the primary service implementation;
the repository also includes a working frontend.

## Run

Prerequisite: Docker is installed and running.

1. From the repository root, run:

   ```sh
   docker compose up -d --build
   ```

   On a fresh checkout, first copy `.env.example` to `.env` and set
   `POSTGRES_PASSWORD` and `GRAFANA_ADMIN_PASSWORD`.

2. Open the [website](http://localhost:3000). Choose Tony or John and a dealership
   to sign in. No password is required.

3. Open [Grafana](http://localhost:3001/d/sv-vehicle). Sign in with
   `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` from `.env` to view the
   **Vehicle API performance** dashboard.

## Demo data

A new database starts empty. After the backend is healthy (`docker compose ps`),
load the sample inventory:

```sh
docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docs/sql/prepopulated_data/vehicles.sql
```

Refresh the website to see 1,000 vehicles across the two dealerships. Filter by
make, model, or age, and open a vehicle to save a proposal or review its history.
Changes persist across restarts.

## Apply code changes

Rebuild the app you changed:

```sh
docker compose up -d --build backend
docker compose up -d --build frontend
```

To rebuild both, run `docker compose up -d --build`. Database data is preserved.

## Stop

```sh
docker compose stop
```

## Build

To build both Docker images without starting services:

```sh
docker compose build backend frontend
```

The Docker builds package the apps; they do not run the test suites. Use
`docker compose up -d --build` to build and start the complete application.

## Test

Java tests require Java 21 on the host. The k6 test requires k6 0.57 or newer.

### Java tests

```sh
# Unit tests: Docker is not required.
backend/services/sv_vehicle/gradlew -p backend/services/sv_vehicle test

# Integration tests: Docker must be running.
backend/services/sv_vehicle/gradlew -p backend/services/sv_vehicle integrationTest

# Run both suites.
backend/services/sv_vehicle/gradlew -p backend/services/sv_vehicle check
```

Integration tests start a disposable PostgreSQL container with Testcontainers
and exercise the application through MockMvc. They do not require a running
backend or Compose stack, and do not access your local database. If Docker is
unavailable, the integration task fails rather than skipping the tests.

The test suites check the core business rules:

| Business rule | What is verified |
| --- | --- |
| Dealership isolation | Reads, filter options, and updates stay within the selected dealership. |
| Inventory search | Make/model filters, stock age, sorting, and pagination work together. |
| Accurate totals | The count query matches the filters, including empty pages. |
| Aging stock | Vehicles older than 90 days are separated from newer stock. |
| Proposed actions | Default/explicit statuses and the latest saved action are correct. |
| Safe updates | Stale versions return 409; simultaneous updates produce one successful write. |
| Transaction integrity | A failed history insert rolls back the vehicle version change. |
| API contracts | Validation, response envelopes, pagination, and correlation IDs are checked. |

HTML reports are available under
`backend/services/sv_vehicle/build/reports/tests/test/` and
`backend/services/sv_vehicle/build/reports/tests/integrationTest/`.
See the [Java testing guide](backend/services/sv_vehicle/TESTING.md) for details.

### k6 performance test

With the Compose stack running and demo data loaded, run from the repository root:

```sh
k6 run test/soak/soak.ts
```

This runs for 30 minutes at 20 requests per second across the inventory, filter,
login, and action APIs. Watch latency, errors, and resource usage in
[Grafana](http://localhost:3001/d/sv-vehicle) during the run.

Reports are saved to `test/soak/results/summary.txt` and `summary.json`.
The test saves real action updates; they remain in the database afterward.
See the [k6 guide](test/soak/README.md) for thresholds and configuration.

## Monitoring

Open [Grafana](http://localhost:3001) and sign in with the `GRAFANA_ADMIN_USER`
and `GRAFANA_ADMIN_PASSWORD` from your root `.env` file. The default username is
`admin`.

Open the [Vehicle API performance dashboard](http://localhost:3001/d/sv-vehicle)
to see request counts, errors, response times, CPU, memory, and database
connections. Everything is configured automatically.

Try filtering vehicles and saving a proposal on the website, then wait about
30 seconds and refresh Grafana. Select the last 15 minutes as the time range.
A fresh stack may show empty charts until you use the application.

To inspect logs, open **Explore**, choose **Loki**, and run
`{app="sv_vehicle"} | json`. Expand a log entry and use **View trace**, when
available, to see the request's trace. You can also choose **Tempo** in Explore
and search for service `sv_vehicle` to inspect recent requests.

See the [observability guide](backend/infra/README.md) for more detail.
