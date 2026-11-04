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
Loki at http://localhost:3100, Tempo at http://localhost:3200, and Alloy OTLP/HTTP
at localhost:4318. Only Alloy exposes OTLP to the host; Tempo ingestion stays internal. Sign into Grafana
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
Spring exports OTLP/HTTP to Alloy, which batches and forwards traces to Tempo
over OTLP/gRPC. Tempo retains 24 hours locally by default (`TEMPO_RETENTION`),
with persistent WAL/blocks, a 512 MiB memory limit and one CPU. Alloy has a
384 MiB limit and a bounded 128 MiB trace processor. Metrics/log pipelines remain unchanged.

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

## Tempo traces and waterfall verification

Configuration uses Spring Boot 4's OpenTelemetry starter and OTLP properties,
Micrometer observations, and Datasource Micrometer 2.x for JDBC. References:
[Spring tracing](https://docs.spring.io/spring-boot/4.1/reference/actuator/tracing.html),
[Datasource Micrometer](https://jdbc-observations.github.io/datasource-micrometer/docs/current/docs/html/),
and [Alloy to Tempo](https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/set-up-collector/grafana-alloy/).

1. Follow the startup commands above, then check Tempo and the existing backends:

```sh
docker compose ps
curl -fsS http://localhost:3200/ready
curl -fsS http://localhost:3100/ready
curl -fsS http://localhost:9090/-/ready
docker compose logs --tail=50 alloy tempo
```

Tempo's minimal image has no HTTP health probe configured; `/ready` is the live
readiness check. Allow startup time before testing. Alloy retries export if Tempo
is not yet ready. The OTLP listener is not an HTTP health endpoint.

2. Start the host application using `./tools/run-local.sh`, then issue a real API call:

```sh
curl -i -H 'traceparent: 00-0123456789abcdef0123456789abcdef-0123456789abcdef-01' \
  'http://localhost:9001/vehicles?page=0&size=10'
# Allow several seconds for SDK export and Alloy batching, then:
curl -fsS http://localhost:3200/api/traces/0123456789abcdef0123456789abcdef
```

The response headers include the same `X-Trace-ID`. A trace lookup that returns
spans confirms the application -> Alloy -> Tempo path. If missing, inspect
application exporter warnings and `docker compose logs alloy tempo` for refused,
failed or dropped spans. Make new requests after fixing connection errors.

3. In Grafana at http://localhost:3001, open **Explore -> Tempo -> Search**.
Choose service `sv_vehicle`, set the last 15 minutes, and select a trace to see
its waterfall and individual durations. Use TraceQL for precise searches:

```traceql
{ resource.service.name = "sv_vehicle" && span.http.route = "/vehicles" }
```

```traceql
{ resource.service.name = "sv_vehicle" && duration > 100ms }
```

The first is an endpoint filter; combine it with duration as needed. Search for
`business.logic`, `repository.mock`, or JDBC spans to inspect slow operations.
The expected hierarchy for the current read API is:

```text
GET /vehicles (automatic server span)
├── business.logic (operation=GetVehiclesService.findVehicles)
│   └── repository.mock (operation=GetVehiclesRepository.findAll)
└── response.mapping (actual HTTP converter serialization/output writes)
```

`request.validation` appears when MVC invokes Bean Validation, including
`@Valid` request payloads and constrained controller parameters. It does not
pretend to measure validation on endpoints with no validation. A 204 action
response has no response body and therefore no serialization span. Response
mapping includes output writing, not only CPU serialization time. Services and
repositories use one reusable aspect; no controller duplicates the server span.

JDBC connection/query observations are automatic for Spring-managed datasources.
Current vehicle APIs use the shared mock store and intentionally have **no fake
SQL spans**. To verify real JDBC spans, request `/actuator/health` (its database
health indicator uses PostgreSQL), then inspect its trace for JDBC operations.
Business SQL will appear under service/repository spans once real persistence
is implemented. No schema or persistence behavior was changed.

4. In **Explore -> Loki**, run:

```logql
{app="sv_vehicle"} | json | traceId="0123456789abcdef0123456789abcdef"
```

Expand a log line and click **View trace** on its TraceID derived field. In
Tempo, use the span's logs link to return to Loki. Confirm the trace ID matches
and `spanId` identifies the logged operation. The final HTTP request log belongs
to the server span; logs emitted inside an operation carry its child span ID.
Trace IDs remain JSON fields rather than Loki stream labels.

5. Confirm existing metrics at `/actuator/prometheus` and the provisioned HTTP
metrics dashboard still show requests. Confirm ordinary logs still arrive in
Loki. Trace export is independent from metrics scraping and file log delivery.

### Configuration and context propagation

- `TRACING_SAMPLE_RATE` defaults to `1.0` locally; lower it for production.
- `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` defaults to `http://localhost:4318/v1/traces`.
  Changing `OTLP_HTTP_PORT` requires changing this endpoint too. Containerized
  applications should target `http://alloy:4318/v1/traces` instead of localhost.
- `TEMPO_RETENTION` defaults to `24h`; `TEMPO_PORT` defaults to `3200`.
- W3C `traceparent` is accepted by automatic HTTP instrumentation. Build outbound
  clients from Spring's configured builders to propagate trace context.
- Boot task execution context propagation is enabled for the managed executor.
  Raw threads, common-pool futures, or custom executors must use
  `ContextPropagatingTaskDecorator`/context snapshots explicitly. No asynchronous
  business operations currently exist, and none were introduced.
- Span operation attributes contain class/method names only. JDBC high-cardinality
  fields are removed before export; bind values, result sets, SQL text and query
  logging are disabled/redacted. Do not add request bodies, email addresses,
  credentials or account IDs as span attributes.

### Verification performed during implementation

Compose configuration validation, Alloy's own `validate` command, Tempo's
`-config.verify=true` check, Gradle Java
compilation, existing context tests, and tracing observation/privacy tests passed.
Tests verify nested service/repository and validation/serialization observation parents, scope cleanup,
and JDBC query-field removal. These checks do not establish live delivery.
Infrastructure and host servers were not started per the repository instruction;
Tempo readiness, live Alloy forwarding, Grafana waterfalls and Loki/Tempo links
must be checked using the steps above.

## Grafana API dashboards

**Vehicle API performance** shows Total requests, Success and Errors as numbers at
the top, followed by RPS and a request-outcomes pie chart. There is no raw counter
since startup or separate status-rate charts. Counts use Grafana's time picker;
success means HTTP 2xx/3xx and errors mean HTTP 4xx/5xx. Prometheus estimates
counter increases and handles resets; displayed counts are rounded to integers.

Below the overview, the same dashboard contains an expandable row for each API:

- GET vehicles (`/vehicles`)
- GET vehicle filters (`/vehicle-filters`)
- POST vehicle action (`/update-vehicle-status`)
- POST login (`/login`)

Each API row has separate P95 and P99 charts. Summary cards, RPS and the
outcome pie appear once at the top of the dashboard. Each percentile chart compares full HTTP request duration
(blue) with that API's Tempo business-logic span (green). Login currently has
no service/business-logic span, so its charts show full request duration only.
All rows share the dashboard time picker.

Tempo's metrics-generator writes `traces_spanmetrics_latency_bucket` to the
existing Prometheus remote-write receiver. Span charts select the exact API
operation; no other API's service spans are included. Durations include nested
repository/database work and must not be added together. Trace sampling affects
span aggregates; HTTP metrics cover all scraped requests. Keep sampling at
100% locally for comparison. The generator caps active series at 10,000 and
keeps its WAL in the existing Tempo volume. Histogram buckets cover 1 ms to 30 s.

Apply configuration updates using:

```sh
docker compose up -d tempo prometheus grafana
# Grafana provisioning settings changed; restart to load folder provisioning:
docker compose restart grafana
```

Generate API traffic over at least a minute, then open **Vehicle API performance** and scroll to the API rows. Rates and percentiles need multiple samples; the generator
collects every 15 seconds. Empty ranges can have no latency data. No historical
trace backfill occurs. Check generator/remote-write logs if spans remain absent:

```promql
sum by (span_name, operation) (traces_spanmetrics_latency_count{service="sv_vehicle"})
```

Exemplar dots on percentile charts link to Tempo via `traceID`. An exemplar is
one actual request and is not necessarily the request at exactly P95 or P99.

Validation: JSON, dashboard queries via `promtool check rules`, and whitespace
checks pass. Live rendering and data delivery remain runtime verification steps.

## Named business steps in services

Use standard Micrometer directly; no custom tracing wrapper is required.
Annotation scanning is enabled in application configuration:

```java
import io.micrometer.observation.annotation.Observed;

@Observed(name = "vehicles.validate-proposal")
public void validateProposal() {
    // business logic
}
```

Annotations require calls through a Spring-managed bean proxy. For inline logic
or same-class calls, inject `ObservationRegistry` and use the standard lambda API:

```java
Observation.createNotStarted("vehicles.taskA", observationRegistry)
    .observe(() -> {
        // business logic
    });

var result = Observation.createNotStarted("vehicles.taskB", observationRegistry)
    .observe(() -> calculateResult());
```

`GetVehiclesService` demonstrates this directly for `vehicles.filter-and-sort`.
Both APIs create child spans through the existing Micrometer tracing bridge.
They preserve return values and exceptions and restore the parent scope.
Annotations do not intercept private/final methods or calls through `this`.
Synchronous observations do not measure asynchronous completion: propagate
context and observe asynchronous work separately when needed.

Keep names as stable constants and never attach sensitive arguments or results.
The existing `ApiSpanContext` observation filter supplies the API attribution
needed by the per-API P95/P99 charts; named child steps appear automatically.
It also redacts exception messages before export. This is dashboard attribution,
not a replacement for Micrometer's span API. Restart the application to use the
updated instrumentation; generate new traffic to populate the step curves.

## Span logs for demos

Traced Micrometer observations emit readable messages such as
`<> Starting vehicle inventory search.` and
`<> Finished filtering and sorting inventory results.` Failures say
`<> Could not complete ...`. Span name, operation and outcome remain structured fields. This includes named steps,
service/repository operations and traced framework observations. Structured logs
carry the observation's own `traceId` and `spanId`, including at completion after
parent scope restoration. No payloads or exception messages are logged.
Set `SPAN_LOGS_ENABLED=false` to disable this extra demo logging.

After making an API request, copy its `X-Trace-ID` header into this Loki query:

```logql
{app="sv_vehicle"} | json | traceId="YOUR_TRACE_ID" | line_format "{{.message}}"
```

Look for messages beginning with `<> `; all steps share the trace ID and nested
steps have different span IDs. Use **View trace** to open the Tempo waterfall.

Business logs also report useful outcomes: how many vehicles were checked and
returned, how many make/model options were built, and when a proposal was saved.
All custom messages retain the `<> ` prefix and existing trace/span correlation.
