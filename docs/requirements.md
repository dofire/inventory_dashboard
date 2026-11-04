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
retaining its mock business repository. It exposes health/Prometheus endpoints
and writes correlated JSON logs to console and disk. Alloy ships file logs to
Loki; trace IDs remain queryable JSON fields, never indexed labels. Grafana
provisions data sources and an HTTP metrics dashboard. Tempo stores traces for
24 hours locally; Alloy receives OTLP/HTTP and forwards OTLP/gRPC. Automatic
HTTP/JDBC instrumentation and reusable operation observations expose nested
validation, business, repository, and response serialization timings. Loki and
Tempo provide bidirectional trace/log links. See
[setup and verification](../backend/infra/README.md).

## Mock account login

The web app requires a mock account login before showing inventory. `POST /login`
accepts an `account` (case-insensitive, trimmed): `tony` or `john`. Each returns a
stable UUID `userId`, distinct `accountId`, account name, display name, age, and
email. Unknown accounts are rejected with HTTP 401. No password is required;
this is a mock identity flow, not production authentication or authorization.
The complete profile is stored in local storage and restored on reload. Settings
shows the profile and provides logout, which removes the stored session, clears
cached data, and returns to login.

## HTTP correlation

Every backend response, including errors and actuator responses, includes
`X-Request-ID` and `X-Trace-ID` headers. Request IDs accept a valid incoming UUID
or generate a new one; trace IDs use the active server trace, with a generated
fallback when no span exists. Response bodies retain their existing formats.
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
