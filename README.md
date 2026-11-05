# Vehicle Inventory Dashboard

A dealership inventory dashboard for **Scenario B** of the Keyloop technical
assessment. Filter vehicles, identify stock older than 90 days, and save proposed
actions with an audit history.

Built with React, TypeScript, Java 21, Spring Boot, and PostgreSQL.

## Run

Prerequisite: Docker is installed and running. Run commands from the repository root.

1. Start the application:

   ```sh
   docker compose up -d --build
   ```

   For a fresh checkout, first copy `.env.example` to `.env` and set
   `POSTGRES_PASSWORD` and `GRAFANA_ADMIN_PASSWORD`.

2. Open the [website](http://localhost:3000). Choose Tony or John and a dealership.
   No password is required.

3. Open [Grafana](http://localhost:3001/d/sv-vehicle). Sign in with
   `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` from `.env`.

## Demo data

A new database starts empty. Once the backend is healthy (`docker compose ps`),
load 1,000 sample vehicles:

```sh
docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docs/sql/prepopulated_data/vehicles.sql
```

Refresh the website. Filter by make, model, or age, then open a vehicle to save a
proposal or view its history. Data persists across restarts.

## Monitoring

The [Vehicle API performance dashboard](http://localhost:3001/d/sv-vehicle) shows
request counts, errors, response times, CPU, memory, and database connections.
Use the website to generate traffic, then refresh Grafana after about 30 seconds.

For logs, open **Explore**, select **Loki**, and run `{app="sv_vehicle"} | json`.
Expand a log entry and click **View trace** when available. To browse traces,
select **Tempo** and search for service `sv_vehicle`.

To investigate a specific request, copy its `X-Trace-ID` response header from
your browser's Network tab. In **Explore → Loki**, filter logs by that ID:

```logql
{app="sv_vehicle"} | json | traceId="0123456789abcdef0123456789abcdef"
```

Replace the example ID with your request's trace ID. In **Explore → Tempo**,
select the **Trace ID** query type and paste the same ID to open the request's
span waterfall. Allow a few seconds for logs and traces to arrive.

See the [observability guide](backend/infra/README.md) for details.

## Build and rebuild

Build images without starting services:

```sh
docker compose build backend frontend
```

After code changes, rebuild and restart the app you changed:

```sh
# Backend only
docker compose up -d --build backend

# Frontend only
docker compose up -d --build frontend
```

Run `docker compose up -d --build` to rebuild both. Database data is preserved.

## Stop

```sh
docker compose stop
```

## Test

### Java tests

Requires Java 21. Integration tests also require Docker and use a disposable
PostgreSQL container, separate from the application database.

```sh
# Unit tests
backend/services/sv_vehicle/gradlew -p backend/services/sv_vehicle test

# Integration tests
backend/services/sv_vehicle/gradlew -p backend/services/sv_vehicle integrationTest

# Both suites
backend/services/sv_vehicle/gradlew -p backend/services/sv_vehicle check
```

HTML reports are in `backend/services/sv_vehicle/build/reports/tests/`.
See the [Java testing guide](backend/services/sv_vehicle/TESTING.md) for details.

### k6 performance test

Requires k6 0.57 or newer, the Compose stack running, and demo data loaded.

```sh
k6 run test/soak/soak.ts
```

Runs for 30 minutes at 20 requests per second across inventory, filter, login,
and action APIs. Monitor the results in [Grafana](http://localhost:3001/d/sv-vehicle).

Reports are saved to `test/soak/results/summary.txt` and `summary.json`.
Action updates made by the test remain in the database.
See the [k6 guide](test/soak/README.md) for thresholds and configuration.
