# Vehicle Inventory Dashboard

A web application for managing dealership inventory. Users can filter and sort
vehicles, identify stock older than 90 days, and record proposed actions such as
price reductions or maintenance.

The frontend uses React and TypeScript. The backend uses Java 21 and Spring Boot,
with PostgreSQL for vehicle data and action history.

## Before you start

Install:

- **Java 21**
- **Node.js 22.14 or newer** and npm
- **Docker Desktop** (or Docker Engine with Compose v2), running

Gradle is included through the wrapper; you do not need to install it separately.
Run the commands below from the repository root unless instructed otherwise.

## Run locally

### 1. Configure the application

For a fresh checkout:

```sh
cp .env.example .env
cp frontend/.env.example frontend/.env
```

Keep existing `.env` files if you already configured the project. In the root
`.env`, replace `POSTGRES_PASSWORD` and `GRAFANA_ADMIN_PASSWORD` with your own
local passwords. Do not commit these files. The backend launcher reads the root
`.env` as shell configuration, so quote values containing shell special characters.

### 2. Start the database and monitoring tools

```sh
mkdir -p backend/services/sv_vehicle/logs
docker compose up -d
```

Compose starts PostgreSQL, Prometheus, Loki, Tempo, Alloy, and Grafana. The backend
and frontend run separately in the next two steps.

### 3. Start the backend

In one terminal:

```sh
./tools/run-local.sh
```

The backend runs at **http://localhost:9001**. Flyway creates missing database
tables, and existing data is preserved. Check readiness at
http://localhost:9001/actuator/health.

### 4. Start the frontend

In another terminal, from the repository root:

```sh
npm --prefix frontend ci
npm --prefix frontend run dev
```

Open **http://localhost:3000**. Sign in with `tony` or `john`, then choose
`DEALER_HCM_001` or `DEALER_HN_002`.

These are mock login accounts; inventory and action history use real PostgreSQL
data. A new database starts empty—there is no automatic inventory seed. Existing
vehicle data must use the selected dealership ID. See the
[vehicle table definition](docs/sql/table/vehicle.sql) when importing data.

Select a vehicle to view its details and save a proposal. Changes survive backend
restarts. If another user updated the same vehicle, refresh before saving again.

To stop the infrastructure while keeping its data:

```sh
docker compose stop
```

## Build

Build the backend JAR and frontend bundle:

```sh
backend/services/sv_vehicle/gradlew -p backend/services/sv_vehicle bootJar
npm --prefix frontend ci
npm --prefix frontend run build
```

Outputs are in `backend/services/sv_vehicle/build/libs/` and `frontend/dist/`.
These commands build the application; run the tests separately as shown below.

To run the built backend instead of the development launcher, use a new terminal
from the repository root with the database already running:

```sh
set -a
. ./.env
set +a
cd backend/services/sv_vehicle
java -jar build/libs/sv_vehicle-0.0.1-SNAPSHOT.jar
```

For deployment, serve `frontend/dist/` with a web server that proxies `/api/*` to
the backend and removes the `/api` prefix. Vite's development proxy is only used
by `npm run dev`. See the [frontend guide](frontend/README.md) for configuration.

## Test

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

### Frontend tests

```sh
npm --prefix frontend ci
npm --prefix frontend run typecheck
npm --prefix frontend test
```

Frontend tests cover filtering, sorting, pagination, proposal submission,
conflicts, and keyboard interaction with dropdowns.

For the optional **30-minute, 20 RPS** performance test, follow the
[k6 soak test guide](test/soak/README.md).

## Monitoring

Open **http://localhost:3001** and sign into Grafana with the credentials from
`.env`. The **Vehicle API performance** dashboard shows request rates, outcomes,
API P95/P99 latency, CPU, memory, and database connections.

Tempo shows request traces with business and JDBC spans. Logs include request
and trace IDs for troubleshooting. See the
[observability guide](backend/infra/README.md) for setup and verification.

## AI Collaboration Narrative

### Strategy

I used AI as an implementation partner, guided by concrete requirements and small,
reviewable changes. I specified the API contracts, dealership behavior, project
structure, and preference for simple, readable code. Existing project conventions
and documentation guided implementation choices.

### Verification and refinement

I refined the output through specific feedback on both behavior and usability:
separate API packages, automatic user attribution, stable filters, simpler dialogs,
and consistent dropdowns. When a gap appeared—such as both dealerships showing
the same vehicles—I clarified the expected behavior and extended the fix through
the frontend, backend, and persistence layer.

Verification combined type checking, compilation, unit tests, frontend interaction
tests, and integration tests against real PostgreSQL through Testcontainers.
Tests checked failure paths as well as successful requests, including stale
versions, concurrent writes, and transaction rollback. Integration tests were
rerun successfully once Docker was available; compilation alone was not treated
as proof that database integration worked.

### Final quality

I favored established tools, including Micrometer for tracing and Radix for
accessible controls, over unnecessary custom implementations. Database tests use
isolated containers, SQL values use bound parameters, and API errors avoid
exposing internal details. Documentation was updated when behavior changed.

The result is a tested local-development application. Mock login remains a known
limitation and must be replaced before treating this as a production authentication
system.
