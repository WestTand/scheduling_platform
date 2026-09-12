from fastapi import APIRouter, HTTPException

from app.schemas.scheduling import SchedulingRequest, SchedulingResponse
from app.solver.scheduler import InvalidSchedulingInput, solve_schedule

router = APIRouter(prefix="/api/v1/scheduling", tags=["scheduling"])


@router.post("/optimize", response_model=SchedulingResponse)
def optimize_schedule(request: SchedulingRequest) -> SchedulingResponse:
    try:
        return solve_schedule(request)
    except InvalidSchedulingInput as exception:
        raise HTTPException(status_code=422, detail=str(exception)) from exception
