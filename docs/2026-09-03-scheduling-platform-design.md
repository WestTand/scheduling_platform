# Scheduling Platform Monorepo Design

## Goal

Set up `scheduling_platform` as a single Git monorepo for a scheduling application. The repository should keep frontend, backend, AI service, infrastructure, and documentation together so local development and deployment are simpler than a Git submodule setup.

## Repository Structure

```txt
scheduling_platform/
├── frontend/
├── backend/
├── ai-service/
├── infra/
│   ├── docker-compose.dev.yml
│   └── .env.example
├── docs/
├── .github/
│   └── workflows/
├── README.md
└── .gitignore
```

Each top-level application folder is a normal directory inside the same Git repository, not a separate Git submodule.

## Components

`frontend/` contains the web client, expected to be a React app.

`backend/` contains the main API service, expected to be a Spring Boot application.

`ai-service/` contains the Python AI service, expected to use Flask or FastAPI.

`infra/` contains Docker Compose and deployment-related files.

`docs/` contains project documentation, requirements, API notes, and diagrams.

`.github/workflows/` contains CI/CD workflows when deployment automation is added.

## Local Database

Local development uses PostgreSQL from Docker instead of installing PostgreSQL directly on the machine.

Recommended database services:

```txt
PostgreSQL image: postgres:17-alpine
Database UI: adminer:5-standalone
PostgreSQL port: 5432
Adminer port: 8081
```

The backend connects to PostgreSQL using Docker's internal service name:

```txt
jdbc:postgresql://postgres:5432/scheduling_platform
```

Adminer should default to the `postgres` service.

## Local Development Flow

Run infrastructure services from the `infra` directory:

```bash
cd infra
docker compose -f docker-compose.dev.yml up -d
```

Open Adminer at:

```txt
http://localhost:8081
```

Expected Adminer login values:

```txt
System: PostgreSQL
Server: postgres
Username: scheduling_platform
Password: scheduling_platform_dev_password
Database: scheduling_platform
```

## Deployment Direction

The monorepo should support simple deployments where each service can still deploy independently:

```txt
frontend   -> Vercel, Netlify, or Docker
backend    -> Render, Railway, VPS, or Docker
ai-service -> Render, Railway, VPS, or Docker
database   -> managed PostgreSQL for production, Docker PostgreSQL for local/dev
```

Docker Compose in `infra/` is primarily for local development and optional VPS deployment.

## Constraints

The project should not use Git submodules.

The root repo should avoid pretending that all services share one build system. Each service owns its own dependencies and commands.

Secrets should not be committed. Use `.env.example` for sample values and `.env` for local private values.

## Success Criteria

The initial setup is successful when the repository has the agreed monorepo folder structure, local PostgreSQL runs with Docker, Adminer can connect to the database, and each service has a clear place for its source code and configuration.
