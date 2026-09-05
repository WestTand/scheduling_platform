# AI Service

FastAPI service for scheduling optimization. This scaffold currently exposes only a health endpoint.

## Run Locally

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8001
```

Health check: `http://localhost:8001/health`
