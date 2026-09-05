# Scheduling Platform

Monorepo for a staff scheduling platform with a React frontend, Spring Boot backend, FastAPI AI service, and local PostgreSQL infrastructure.

## Project Structure

- `frontend/`: React + TypeScript + Vite web client.
- `backend/`: Spring Boot API service.
- `ai-service/`: Python FastAPI optimization service placeholder.
- `infra/`: Docker Compose files for local development.
- `docs/`: project analysis, design, and use-case documentation.

## Docker Development

From the repository root:

```powershell
docker compose -f infra/docker-compose.dev.yml up --build
```

If your Docker Compose version supports watch mode, use it to restart the backend when Java sources change and rebuild when `backend/pom.xml` changes:

```powershell
docker compose -f infra/docker-compose.dev.yml up --build --watch
```

The plain `up --build` command remains the fallback if `--watch` is unavailable.

Local URLs:

- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/api/health`
- AI health: `http://localhost:8001/health`
- Adminer: `http://localhost:8081`
- PostgreSQL: `localhost:5432`

Adminer login values:

- System: `PostgreSQL`
- Server: `postgres`
- Username: `scheduling_platform`
- Password: `scheduling_platform_dev_password`
- Database: `scheduling_platform`

Optional environment overrides are listed in `infra/.env.example`.

Stop the development stack:

```powershell
docker compose -f infra/docker-compose.dev.yml down
```

Stop the stack and remove Docker volumes, including the PostgreSQL data volume:

```powershell
docker compose -f infra/docker-compose.dev.yml down -v
```

## Manual Backend Fallback

Start PostgreSQL first, either with Docker Compose or another local PostgreSQL instance using the values above.

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Health check: `http://localhost:8080/api/health`

## Manual AI Service Fallback

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8001
```

Health check: `http://localhost:8001/health`

## Manual Frontend Fallback

Use `npm.cmd` on Windows PowerShell if script execution blocks `npm.ps1`.

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Frontend URL: `http://localhost:5173`
