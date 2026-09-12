from datetime import datetime, timezone

from fastapi import FastAPI

from app.api.scheduling import router as scheduling_router

app = FastAPI(title="Scheduling Platform AI Service")
app.include_router(scheduling_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "service": "ai-service",
        "status": "UP",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
