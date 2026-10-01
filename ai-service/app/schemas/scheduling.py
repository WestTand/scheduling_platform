from datetime import date
from decimal import Decimal
from typing import Literal

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, model_validator


def to_camel(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        serialize_by_alias=True,
    )


class PeriodInput(ApiModel):
    period_id: int
    start_date: date
    end_date: date
    version_no: int = Field(ge=1)
    timezone: str = "Asia/Ho_Chi_Minh"

    @model_validator(mode="after")
    def validate_range(self) -> "PeriodInput":
        if self.end_date < self.start_date:
            raise ValueError("period.endDate must be on or after startDate")
        return self


class EmployeeInput(ApiModel):
    employee_id: int


class ShiftTypeInput(ApiModel):
    shift_type_id: int
    duration_minutes: int = Field(gt=0)
    overnight: bool = False
    heavy: bool = False
    min_staff: int = Field(gt=0)
    max_staff: int = Field(gt=0)
    quota_category: Literal["OVERTIME", "BATHING", "ON_CALL"] | None = None

    @model_validator(mode="after")
    def validate_staff_range(self) -> "ShiftTypeInput":
        if self.max_staff < self.min_staff:
            raise ValueError("shift type maxStaff must be at least minStaff")
        return self


class WorkShiftInput(ApiModel):
    work_shift_id: int
    shift_type_id: int
    work_date: date
    starts_at: AwareDatetime
    ends_at: AwareDatetime
    required_staff: int = Field(gt=0)

    @model_validator(mode="after")
    def validate_time_range(self) -> "WorkShiftInput":
        if self.ends_at <= self.starts_at:
            raise ValueError("work shift endsAt must be after startsAt")
        return self


class LeaveInput(ApiModel):
    leave_request_id: int
    employee_id: int
    start_date: date
    end_date: date

    @model_validator(mode="after")
    def validate_range(self) -> "LeaveInput":
        if self.end_date < self.start_date:
            raise ValueError("leave endDate must be on or after startDate")
        return self


class PreferenceInput(ApiModel):
    preference_id: int
    employee_id: int
    shift_type_id: int | None = None
    preference_date: date
    preference_type: Literal["WANT", "AVOID"]
    priority: int = Field(default=1, ge=1)


class LockedAssignmentInput(ApiModel):
    assignment_id: int
    employee_id: int
    work_shift_id: int


class BoundaryAssignmentInput(ApiModel):
    employee_id: int
    shift_type_id: int
    starts_at: AwareDatetime
    ends_at: AwareDatetime

    @model_validator(mode="after")
    def validate_time_range(self) -> "BoundaryAssignmentInput":
        if self.ends_at <= self.starts_at:
            raise ValueError("boundary assignment endsAt must be after startsAt")
        return self


class HardRulesInput(ApiModel):
    min_rest_hours_between_shifts: int = Field(ge=0, le=24)
    max_work_hours_per_day: int = Field(gt=0, le=24)
    max_shifts_per_week: int = Field(ge=0, le=14)


class QuotaRulesInput(ApiModel):
    overtime_shifts_per_week: int = Field(ge=0, le=14)
    bathing_shifts_per_week: int = Field(ge=0, le=14)
    on_call_every_n_days: int = Field(ge=1, le=31)


class SoftWeightsInput(ApiModel):
    fairness: Decimal = Field(ge=0, le=999.99)
    preference: Decimal = Field(ge=0, le=999.99)
    weekend: Decimal = Field(ge=0, le=999.99)
    heavy_shift: Decimal = Field(ge=0, le=999.99)
    work_hour_balance: Decimal = Field(ge=0, le=999.99)


class RulesInput(ApiModel):
    hard_rules: HardRulesInput
    quotas: QuotaRulesInput
    soft_weights: SoftWeightsInput


class SolverOptionsInput(ApiModel):
    time_limit_seconds: float = Field(default=30, gt=0, le=300)
    random_seed: int = Field(default=1, ge=0)
    num_workers: int = Field(default=8, ge=1, le=64)


class SchedulingRequest(ApiModel):
    schema_version: Literal["1.0"] = "1.0"
    run_id: int
    period: PeriodInput
    employees: list[EmployeeInput]
    shift_types: list[ShiftTypeInput]
    work_shifts: list[WorkShiftInput]
    approved_leaves: list[LeaveInput] = Field(default_factory=list)
    preferences: list[PreferenceInput] = Field(default_factory=list)
    locked_assignments: list[LockedAssignmentInput] = Field(default_factory=list)
    boundary_assignments: list[BoundaryAssignmentInput] = Field(default_factory=list)
    rules: RulesInput
    solver_options: SolverOptionsInput = Field(default_factory=SolverOptionsInput)


class AssignmentOutput(ApiModel):
    employee_id: int
    work_shift_id: int


class DiagnosticOutput(ApiModel):
    code: str
    message: str
    employee_id: int | None = None
    work_shift_id: int | None = None


class SolverStatistics(ApiModel):
    wall_time_seconds: float
    conflicts: int
    branches: int


class StaffingReason(ApiModel):
    code: str
    required_employees: int
    detail: str


class StaffingAdvice(ApiModel):
    required_employees: int
    available_employees: int
    total_shifts: int
    exact: bool = True
    unreachable: bool = False
    reasons: list[StaffingReason] = Field(default_factory=list)


class SchedulingResponse(ApiModel):
    schema_version: Literal["1.0"] = "1.0"
    run_id: int
    period_id: int
    status: Literal["SUCCESS", "INFEASIBLE", "FAILED"]
    solver_status: Literal[
        "OPTIMAL", "FEASIBLE", "INFEASIBLE", "UNKNOWN", "MODEL_INVALID"
    ]
    assignments: list[AssignmentOutput] = Field(default_factory=list)
    total_penalty: float | None = None
    penalty_details: dict[str, float] = Field(default_factory=dict)
    warnings: list[str] = Field(default_factory=list)
    diagnostics: list[DiagnosticOutput] = Field(default_factory=list)
    staffing_advice: StaffingAdvice | None = None
    error_message: str | None = None
    statistics: SolverStatistics

