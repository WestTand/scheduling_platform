from datetime import datetime, timezone

from fastapi import FastAPI

app = FastAPI(title="Scheduling Platform AI Service")


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "service": "ai-service",
        "status": "UP",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
