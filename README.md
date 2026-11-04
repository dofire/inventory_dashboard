# Inventory assessment

Vehicle service: Java 21 / Spring Boot 4.1.2, port 9001. The React frontend is
in `frontend/` runs on the host at port 3000. See the
[frontend guide](frontend/README.md) for setup, checks, and API limitations.
Docker Compose runs PostgreSQL and observability infrastructure only.

See [local infrastructure setup](backend/infra/README.md) for startup,
environment configuration, verification, and troubleshooting, and
[vehicle requirements](docs/feature/vehicle/requirements.md) for the API contract.
