# Vehicle API soak test

Run commands from the repository root. Uses the existing Spring Boot service,
PostgreSQL, Prometheus, Alloy, Tempo, Loki, and Grafana. No new Docker service.
Requires k6 0.57+ (native TypeScript). Node/npm are only needed for type checking.

## Prepare the local test environment

Start the existing infrastructure as described in [the infrastructure guide](../../backend/infra/README.md).
Restart Grafana after pulling the updated dashboard provisioning:

```sh
docker compose restart grafana
```

Start the backend with the dedicated test profile and explicit delay flag:

```sh
SOAK_DELAYS_ENABLED=true TRACING_SAMPLE_RATE=1.0 \
  ./tools/run-local.sh --args='--spring.profiles.active=soak'
```

The `soak` profile, `SOAK_DELAYS_ENABLED=true`, and request header
`X-Soak-Test: true` are all required for simulated latency. Regular requests are
unaffected. By default, 8% of marked API calls pause for a random 100–500 ms.
Configure frequency using `SOAK_DELAY_PROBABILITY` (0–1). Delay duration is bounded
in code. Restart without the flag/profile after testing. Do not enable this
profile in production. Application delays are disabled by default.

The span `soak.simulated-delay` uses Micrometer Observation inside the API
business span (inside the HTTP span for login). It inherits trace context,
appears in Tempo waterfalls and per-API P95/P99 charts, and emits existing
`<> ` lifecycle logs with traceId/spanId. No payloads or identifiers are added to
span attributes.

## Check and run

```sh
npm --prefix test/soak ci
npm --prefix test/soak run typecheck
k6 inspect test/soak/soak.ts
k6 run test/soak/soak.ts
```

Default: **30 minutes, 20 RPS**, using `constant-arrival-rate`, with 30
preallocated VUs and a maximum of 100. Exactly one HTTP request per iteration;
there are no setup requests, redirects, retries, or sleep calls. The executor
paces traffic. A 15-second grace period lets in-flight requests finish.
This schedules 36,000 requests if the generator sustains the rate. A
`dropped_iterations` failure means the target was not maintained.

Optional short smoke run or another local service address:

```sh
k6 run -e DURATION=30s -e RATE=20 -e BASE_URL=http://localhost:9001 test/soak/soak.ts
```

`BASE_URL` has no `/api` suffix when targeting Spring Boot directly. Use
`http://localhost:3000/api` only when intentionally testing the frontend proxy.
Scripts must run from the repository root so summary paths resolve correctly.

The approximate workload is 65% inventory (with aging/make/sort variations),
20% filter options, 10% login, and 5% action updates. Both dealerships and both
mock accounts participate. VUs without an inventory snapshot substitute a read
for an action. Calls validate correlation IDs, response envelopes, status codes,
and returned vehicle/profile dealership IDs. Updates modify the in-memory action
history; restarting the backend resets this data. Concurrent users can submit
stale row versions: HTTP 409 is an expected concurrency outcome, counted as
`action_conflicts`, and the stale vehicle is discarded until a later read.
Other non-200 statuses fail checks. Grafana's existing outcome pie still counts
409 as an HTTP client error; k6's error threshold intentionally excludes it.

## Thresholds and summary

- Aggregate and per-endpoint P95 < 800 ms, P99 < 1,200 ms.
- Unexpected failure rate and k6 HTTP failure rate < 1%.
- Checks pass > 99%; zero dropped iterations.

Latency thresholds leave room for the intentional 500 ms delay; they are local
baseline defaults, not production SLOs. k6 exits nonzero on threshold failures.
It prints the target/achieved RPS, counts, P95/P99, failures, conflicts, dropped
iterations, and every threshold result. Reports are written to:

- `test/soak/results/summary.txt`
- `test/soak/results/summary.json` (all metric aggregates and thresholds)

Reports and node_modules are ignored by Git. No full per-request output is
written, keeping the generator's overhead small.

## Monitor in Grafana

Open http://localhost:3001/d/sv-vehicle and use Last 30 minutes with auto refresh.
The existing dashboard supplies RPS, total/success/error numbers, and outcome pie.
Each API row has independent P95 and P99 charts with full request, business, and
child-span lines. The bottom **Soak test · application resources** row adds:

- Unexpected 5xx and 4xx error rates (409 excluded from this rate panel).
- Application process and host CPU usage.
- Used JVM heap/nonheap memory and heap limit.
- Hikari active, idle, pending, and maximum application database connections.

Prometheus already scrapes these metrics every 15 seconds. Missing series show
no data rather than a fabricated zero. CPU is application/host CPU, not Docker
infrastructure CPU; connection metrics cover the application pool, not every
PostgreSQL session. Watch for rising memory, pending connections, dropped load,
and latency drift over the full run. JVM allocation/GC creates a normal sawtooth;
sustained growth after collections needs investigation.

In Explore → Tempo, search service `sv_vehicle`, span name
`soak.simulated-delay`, and duration >100 ms. Open a trace to see its parent API
and delay duration; use the existing trace-to-Loki link for correlated logs.
The child delay lines already appear in vehicle/filter/action percentile panels;
login has its own delay-span line. Allow a few collection/scrape intervals before
expecting span metrics. A delay-only P95 summarizes delayed calls, whereas full
request P95 includes delayed and undelayed calls; these are different populations.

Business repositories still use the mock store. This test exercises application
concurrency and observability; it is **not a PostgreSQL query endurance test**.
Database pool usage may remain idle. It does not fabricate query traffic or
change persistence behavior.

## References

- [k6 constant arrival rate](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/)
- [k6 native TypeScript](https://grafana.com/docs/k6/latest/using-k6/javascript-typescript-compatibility-mode/)
- [Spring Boot datasource metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
