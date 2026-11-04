# Java tests

Requires Java 21. Run from the repository root; the Gradle wrapper supplies Gradle.
No application server, frontend, `.env`, or Compose stack is required.

## Unit tests (no Docker)

```sh
backend/services/sv_vehicle/gradlew -p backend/services/sv_vehicle test
```

JUnit Jupiter and Mockito cover:

- Inventory service forwarding of filters, independent page/count results, shared
  cutoff timestamp, and observation scope cleanup on failure.
- Pagination totals for empty, exact, and partial pages.
- Default and explicit action statuses, user/dealership attribution, and conflicts.
- Login normalization and unknown accounts.
- Request dealership validation and sanitized API errors.
- Existing tracing, context propagation, span logging, and opt-in soak delay rules.

The existing application-context smoke test excludes database auto-configuration
and mocks the JDBC template. It is not a database integration test.

## Integration tests (Docker required)

Start Docker Desktop or provide a Docker-compatible daemon, then run:

```sh
backend/services/sv_vehicle/gradlew -p backend/services/sv_vehicle integrationTest
```

`VehicleApiIntegrationTests` loads the actual Spring Boot application with
MockMvc (no listening HTTP server). Testcontainers starts PostgreSQL
`postgres:17.6-alpine`, matching Compose. `@ServiceConnection` connects the real
application datasource, Flyway, Hibernate validation, and transaction manager
to the disposable container. No mocks replace application services/repositories.

The test profile uses an intentionally invalid fallback datasource URL, preventing
accidental access to a local database if container wiring breaks. Credentials and
mapped ports come from Testcontainers. Tracing/log OTLP export is disabled and
span logs are suppressed, so Tempo/Alloy/Loki/Grafana are not required.

Flyway runs the production migration against the empty container. Each test
clears only this container's vehicle/action tables and creates its own fixtures.
Container reuse is disabled; Testcontainers removes containers after the suite.
The existing local 1,000 vehicles and local Flyway history are never accessed.

Coverage includes:

- Fresh-database migration and Hibernate schema validation.
- SQL filtering, sorting, paging, and separate matching COUNT queries.
- Dealership isolation for inventory, filter options, and action writes.
- HTTP response `data`/`pagination` layout and requestId/traceId header correlation.
- HTTP input validation, login, default action status, and 400/401/404/409 responses.
- Persisted latest action, stale versions, and history-insert rollback.
- Two simultaneous writers: exactly one succeeds and the other returns a conflict,
  leaving one history row and one version increment.

Missing Docker or image-pull failures fail the integration task; tests are not
silently skipped. The first run may download PostgreSQL and the Testcontainers
cleanup image. Standard Docker/Testcontainers environment settings are supported
for remote Docker, Colima, or Podman; machine-specific sockets are not hardcoded.

## All checks and reports

```sh
backend/services/sv_vehicle/gradlew -p backend/services/sv_vehicle check
```

`check` runs unit and integration tests. `*IntegrationTests` classes are excluded
from `test` and included by `integrationTest`.

HTML reports:

- `backend/services/sv_vehicle/build/reports/tests/test/index.html`
- `backend/services/sv_vehicle/build/reports/tests/integrationTest/index.html`

JUnit XML reports for CI are under `build/test-results/test/` and
`build/test-results/integrationTest/`. Both tasks return a failing exit status if
any assertion fails. Use `--rerun-tasks` to force a repeat run.
