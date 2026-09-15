from fastapi import APIRouter

from app.schemas.explain import ExplainRequest, ExplainResponse
from app.services.llm import explain_schedule

router = APIRouter(prefix="/api/v1/scheduling", tags=["scheduling"])


@router.post("/explain", response_model=ExplainResponse)
def explain_solver_result(request: ExplainRequest) -> ExplainResponse:
    return explain_schedule(request)
