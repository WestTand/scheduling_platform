# AI Service

FastAPI service for scheduling optimization with Google OR-Tools CP-SAT.

## Run Locally

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8001
```

Health check: `http://localhost:8001/health`

## Scheduling API

`POST /api/v1/scheduling/optimize` accepts a scheduling-period snapshot from the
backend and returns an optimal/feasible assignment or an infeasibility result.
OpenAPI documentation is available at `http://localhost:8001/docs` while the
service is running.

The backend is responsible for sending only active employees, active shift
types, approved leave requests, and accepted preferences. All work-shift
timestamps must include a UTC offset. Special quotas use the stable
`quotaCategory` values `OVERTIME`, `BATHING`, and `ON_CALL`; they must not be
inferred from a display name.

## LLM Explanation API

`POST /api/v1/scheduling/explain` receives the solver result (`status`,
`solverStatus`, diagnostics, statistics, period, and rules) and returns a
Vietnamese explanation plus suggested manager actions. It is an optional layer
and never makes assignment decisions or changes the CP-SAT result.

Configure Gemini through environment variables:

```bash
export GEMINI_API_KEY="..."
export GEMINI_MODEL="gemini-3.5-flash-lite"
export GEMINI_TIMEOUT_SECONDS="15" # optional
```

If no API key is configured, the endpoint returns a deterministic Vietnamese
explanation based only on the supplied diagnostics, with `fallback: true`.

The solver currently enforces:

- exact `requiredStaff` coverage;
- approved leave and locked assignments;
- shift overlap and minimum rest time;
- maximum work time per day and shifts per ISO week;
- overtime, bathing, and on-call quotas;
- fixed assignments immediately outside the period.

It minimizes weighted preference, shift-count fairness, weekend, consecutive
heavy-shift, and work-hour imbalance penalties. Durations are represented with
integers because CP-SAT requires integer constraints.
