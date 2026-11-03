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
