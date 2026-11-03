# Keyloop inventory assessment

Vehicle service: Java 21 / Spring Boot 4.1.2, port 9001. The React frontend is
intended to run on the host at port 3000; no frontend source is present yet.
Docker Compose runs PostgreSQL and observability infrastructure only.

See [local infrastructure setup](backend/infra/README.md) for startup,
environment configuration, verification, and troubleshooting, and
[vehicle requirements](docs/feature/vehicle/requirements.md) for the API contract.
