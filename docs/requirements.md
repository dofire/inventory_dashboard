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
provisions data sources and an HTTP metrics dashboard. See
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
