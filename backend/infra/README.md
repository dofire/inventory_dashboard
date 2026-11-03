# Local development infrastructure

Requirements: Docker Engine/Desktop with Compose v2, Java 21. Run commands from
the repository root unless stated otherwise. Nothing here starts host apps.

## Configure and start

```sh
cp .env.example .env
# Edit .env: replace POSTGRES_PASSWORD and GRAFANA_ADMIN_PASSWORD with local secrets.
mkdir -p backend/services/sv_vehicle/logs
docker compose config --quiet
docker compose up -d
docker compose ps
```

All published ports bind to loopback. PostgreSQL is at localhost:5432,
Prometheus at http://localhost:9090, Grafana at http://localhost:3001, and
Loki at http://localhost:3100. Alloy has no published port. Sign into Grafana
with the credentials in .env. Named volumes retain database data, metrics,
Grafana state, Loki logs, and Alloy file positions. Metrics and logs retain seven
days. Loki's health check validates configuration only (the official minimal
image has no HTTP client); check `/ready` explicitly for live readiness.
Other services have readiness checks. Alloy retries Loki delivery during startup.

For local startup, use the launcher that loads the root `.env` and sets the
service working directory automatically:

```sh
./tools/run-local.sh
```

The launcher sources `.env` as trusted shell configuration; use shell-safe values
and quote values containing spaces or shell metacharacters.

Compose reads .env automatically; Spring Boot does not. Export the same values
into the shell that starts the application (the following assumes shell-safe
.env values; alternatively use your IDE environment configuration):

```sh
set -a
. ./.env
set +a
cd backend/services/sv_vehicle
./gradlew bootRun
```

Default log path is relative to the service working directory:
`backend/services/sv_vehicle/logs/sv_vehicle.json`. Run Gradle from that directory.
If launching elsewhere, set APP_LOG_FILE to an absolute path and APP_LOG_DIR to
the same directory for Compose. Restart Alloy after changing the bind mount.
Logs rotate at 10 MB; Alloy follows active .json files and retains its offsets.
JSON uses Boot's Logback Logstash format: @timestamp, level, logger_name, message,
and traceId/spanId from MDC when a request span exists. Startup logs have no
request span. Sampling defaults to 100% for this local assessment.
There is no trace exporter or OTLP endpoint: correlation works locally without
Tempo, an OpenTelemetry Collector, or another tracing server.

PostgreSQL connectivity and its Actuator health check are enabled. Hibernate
schema generation and Flyway are disabled; inventory and action history still
use the existing mock repository. SQL reference files in docs/sql/table are not
run automatically. Database connectivity does not imply business persistence.

The frontend should run separately on port 3000 when its source is added.

## Verify after startup

```sh
curl -f http://localhost:9001/actuator/health
curl -f http://localhost:9001/actuator/prometheus
curl -f http://localhost:3100/ready
curl -f http://localhost:3001/api/health
curl -f http://localhost:9090/api/v1/targets
docker compose exec postgres sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
# Deterministic trace ID for testing correlation. Repeat to populate HTTP metrics.
curl -f -H 'traceparent: 00-0123456789abcdef0123456789abcdef-0123456789abcdef-01' \
  http://localhost:9001/vehicles
```

Health should be UP, including its database contributor (details are hidden).
The Prometheus Targets page should show sv_vehicle UP. Query
`up{job="sv_vehicle"}` (expect 1). The provisioned **Vehicle HTTP metrics**
dashboard shows request rate, 5xx rate in requests/second, and p95 latency.
Generate traffic and wait at least two scrape intervals; no traffic means gaps
or zero rates. Histogram buckets are explicitly enabled.

Find the request in `logs/sv_vehicle.json`; traceId should be the 32-character
value above and spanId a 16-character hex value. In Grafana Explore select Loki:

```logql
{app="sv_vehicle"} | json | traceId="0123456789abcdef0123456789abcdef"
```

Or verify Loki directly after waiting for file discovery/batching:

```sh
curl -f -G http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={app="sv_vehicle"} | json | traceId="0123456789abcdef0123456789abcdef"'
docker compose logs alloy
```

Only app, level, and the source filename are stream labels. traceId/spanId remain
JSON fields parsed at query time, preventing high-cardinality indexed labels.
A successful Loki query and the same result in Explore verify the complete path.

Run backend tests independently of PostgreSQL:

```sh
cd backend/services/sv_vehicle
./gradlew test
```

The existing context test explicitly excludes database auto-configuration;
it does not verify database connectivity. Use the running health endpoint for that.

## Network switching and final demonstration

For host development use DB_HOST=localhost and
PROMETHEUS_TARGET=host.docker.internal:9001. Compose supplies Linux's host-gateway
mapping as well as Docker Desktop support. Ports are configurable in .env.example.

For a future fully containerized demo, add backend/frontend services and build
files, set DB_HOST=postgres, DB_PORT=5432, SERVER_PORT=9001,
PROMETHEUS_TARGET=sv_vehicle:9001, and pass the database environment variables
explicitly into the backend container. Mount a shared log volume into the backend
and Alloy and set APP_LOG_FILE accordingly. Keep Grafana's internal data source
URLs unchanged. Browser frontend API URLs must still use published host URLs or
a reverse proxy; container DNS names do not resolve in the browser. No backend
or frontend image/build configuration is included in this task.

## Troubleshooting and cleanup

- Host port 5432 already in use: keep the existing PostgreSQL instance running
  and set both `POSTGRES_PORT=5433` and `DB_PORT=5433` in your local `.env`
  (or choose another free host port). Rerun `docker compose up -d` and re-export
  `.env` before restarting Spring Boot. PostgreSQL inside Docker still uses
  port 5432; a future containerized backend should use `DB_PORT=5432`.

- `FATAL: role "vehicle" does not exist`: check that Spring uses the Docker
  database host port, rather than a separate PostgreSQL instance on 5432. Use
  `tools/run-local.sh` to load the root `.env` (including DB_PORT), or export it manually.
  Do not add a Hibernate dialect to hide a database connection failure.
- Database authentication: check the exported environment. Changing .env passwords
  does not change users in an existing database volume. Use SQL to change the
  password or deliberately reset disposable volumes.
- Prometheus DOWN: ensure host app is listening on 9001, health works, and host
  firewall allows Docker access. Avoid binding Spring to loopback only on Linux.
- Empty logs: check APP_LOG_FILE vs APP_LOG_DIR, file permissions, Docker Desktop
  file sharing, `docker compose logs alloy`, and the Explore time window. Generate
  an API request; startup messages do not contain trace IDs.
- Grafana credentials are initialized once. Updating the environment does not
  reset an existing Grafana admin password; use its admin password reset command.
- `docker compose down` retains volumes. `docker compose down -v` deletes local
  database, dashboards/state, metrics, and logs stored in volumes permanently.
- Services have no production authentication/TLS beyond Grafana login; ports are
  intentionally restricted to the local machine. Do not expose them publicly.

## Verification performed during implementation

Compose configuration, Java syntax parsing, shell script syntax, dashboard JSON, and whitespace checks
passed. Gradle tests were attempted but blocked by sandbox cache-lock/socket
permissions. Infrastructure and host applications were not started, following
the repository instruction. Database connectivity, live scraping, trace-bearing
logs, Alloy delivery, Grafana trace lookup, and passing backend tests therefore
remain unverified; use the commands above on the development host.
