from typing import Literal

from pydantic import Field

from app.schemas.scheduling import (
    ApiModel,
    DiagnosticOutput,
    PeriodInput,
    RulesInput,
    SolverStatistics,
)


class ExplainRequest(ApiModel):
    schema_version: Literal["1.0"] = "1.0"
    run_id: int
    period: PeriodInput
    status: Literal["SUCCESS", "INFEASIBLE", "FAILED"]
    solver_status: Literal[
        "OPTIMAL", "FEASIBLE", "INFEASIBLE", "UNKNOWN", "MODEL_INVALID"
    ]
    diagnostics: list[DiagnosticOutput] = Field(default_factory=list)
    statistics: SolverStatistics
    rules: RulesInput
    total_penalty: float | None = None
    penalty_details: dict[str, float] = Field(default_factory=dict)
    warnings: list[str] = Field(default_factory=list)
    error_message: str | None = None


class ExplainResponse(ApiModel):
    schema_version: Literal["1.0"] = "1.0"
    run_id: int
    explanation: str
    suggested_actions: list[str] = Field(default_factory=list)
    used_model: str | None = None
    fallback: bool
