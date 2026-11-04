# Requirements
- Goal: The Intelligent Inventory Dashboard
- Domain: Supply
- Task: Build an Intelligent Inventory Dashboard to give dealership managers a real-time overview of their vehicle stock.

Feature requirements:

- [Vehicle inventory and actions](feature/vehicle/requirements.md)

## Local development infrastructure

Backend (9001) and frontend (3000) run on the host. Infrastructure-only Compose
provides persistent PostgreSQL (5432), Prometheus (9090), Grafana (3001), Loki
(3100), and internal Alloy. The vehicle service connects to PostgreSQL while
using PostgreSQL-backed business repositories. It exposes health/Prometheus endpoints
and writes correlated JSON logs to console and disk. Alloy ships file logs to
Loki; trace IDs remain queryable JSON fields, never indexed labels. Grafana
provisions data sources and an HTTP metrics dashboard. Tempo stores traces for
24 hours locally; Alloy receives OTLP/HTTP and forwards OTLP/gRPC. Automatic
HTTP/JDBC instrumentation and reusable operation observations expose nested
validation, business, repository, and response serialization timings. Loki and
Tempo provide bidirectional trace/log links. See
[setup and verification](../backend/infra/README.md).

## Mock account login

The login page offers an account dropdown with Tony and John so users do not
need to type their account name.

The web app requires a mock account login before showing inventory. `POST /login`
accepts an `account` (case-insensitive, trimmed): `tony` or `john`. Each returns a
stable UUID `userId`, distinct `accountId`, account name, display name, age, and
email. Login also requires choosing `dealershipId` from `DEALER_HCM_001` or
`DEALER_HN_002`. The backend validates and returns the selection in the profile;
it is saved in the browser session and shown in Settings. Older stored sessions
without a dealership require signing in again. Inventory, filter options, aging checks, pagination, and proposed actions are
scoped to the selected dealership. Query caches include dealership identity.
This mock selection is not production authentication or authorization. Unknown accounts are rejected with HTTP 401. No password is required;
this is a mock identity flow, not production authentication or authorization.
The complete profile is stored in local storage and restored on reload. Settings
shows the profile and provides logout, which removes the stored session, clears
cached data, and returns to login.

## HTTP correlation

Every backend response, including errors and actuator responses, includes
`X-Request-ID` and `X-Trace-ID` headers. Request IDs accept a valid incoming UUID
or generate a new one; trace IDs use the active server trace, with a generated
fallback when no span exists. Application response bodies use `{ requestId, traceId, data }`; actuator bodies
retain their standard formats. Request payload classes inherit `BaseRequest`,
and response envelopes inherit `BaseResponse<T>`.
Frontend API errors retain both IDs for troubleshooting. The frontend does not
use React Strict Mode's development remount, avoiding cancelled initial queries
caused by that remount; query abort signals remain enabled for normal lifecycle
and filter changes.

## Observability dashboard

The overview has Total requests, Success and Errors as top-level numeric cards,
RPS, and a request-outcomes pie chart (success, client error, server error).
Counts follow Grafana's time picker. There is no since-start request counter or
separate status charts. Success is HTTP 2xx/3xx; errors are HTTP 4xx/5xx.

The same dashboard contains a row per API below the overview (vehicles,
vehicle filters, vehicle action, login). Each row has independent P95/P99
charts comparing full HTTP duration and the API's Tempo business-logic span.
Login has no separate business-logic span, so only full HTTP latency is shown.
Tempo remote-writes span metrics to existing Prometheus; sampling affects span
percentiles. No new service is introduced. Actuator traffic is excluded from
the overview.

## Reusable business tracing

Services may define steps with standard Micrometer `@Observed(name = "...")`
on proxy-invoked bean methods or with Micrometer `Observation.createNotStarted(name, registry).observe(...)`
for synchronous inline work. No custom span annotation or wrapper is used. Child observations inherit the outermost API service
operation as `api.operation`, allowing each API's P95/P99 charts to display its
named steps and repository spans. Names must be bounded constants and attributes
must not contain user data. Same-class/private method calls use the lambda form;
asynchronous completion is outside the synchronous wrapper's scope.

Span lifecycle demo logs use the `<> ` message prefix and carry each span's own
traceId/spanId. Logging is enabled by default and can be disabled with
`SPAN_LOGS_ENABLED=false`; request payloads and exception messages are excluded.

## Local soak testing

`test/soak/soak.ts` runs 30 minutes at 20 requests/second using k6's
constant-arrival-rate executor, covering both dealerships and all business APIs.
It checks errors, P95/P99, and dropped iterations, and writes text/JSON summaries.
Concurrent stale-version action conflicts are tracked separately from unexpected
failures. Dedicated `soak` profile + explicit delay enable flag + test request
header allow occasional bounded 100–500 ms latency spans, disabled by default.
The existing dashboard includes application/host CPU, JVM memory, error rate,
and application database pool connections below the per-API latency rows.
Inventory and action calls execute real PostgreSQL queries, so the soak workload
exercises the database pool and produces JDBC child spans. Updates persist after
the test; there is no automatic data reset.
See [run and monitoring instructions](../test/soak/README.md).

## Java test isolation

JUnit/Mockito unit tests run without Docker through Gradle `test`. Spring Boot
integration tests use MockMvc and a disposable PostgreSQL 17.6 Testcontainers
instance through `integrationTest`, with real Flyway migrations, repositories,
and transactions. They never use the developer's local database or `.env`.
Gradle `check` runs both suites; unavailable Docker causes an explicit integration
failure rather than silently skipping tests. External telemetry export is disabled
for integration tests. See [the Java testing guide](../backend/services/sv_vehicle/TESTING.md).
