from __future__ import annotations

import math
from collections import defaultdict
from datetime import timedelta

from ortools.sat.python import cp_model

from app.schemas.scheduling import (
    EmployeeInput,
    SchedulingRequest,
    StaffingAdvice,
    StaffingReason,
)
from app.solver.model_builder import build_model, week_key

MAX_CANDIDATES = 25
SEARCH_TIME_LIMIT_SECONDS = 2.0


def _ceil_div(total: int, per_employee: int) -> int:
    if per_employee <= 0:
        return total
    return math.ceil(total / per_employee)


def _busiest_week_bound(items, per_employee: int) -> tuple[int, int]:
    # Every weekly limit resets at each ISO week boundary, so one employee may be
    # counted in each week independently. The period requirement is therefore the
    # busiest single week, not a division over the whole period and not a sum.
    counts = defaultdict(int)
    for item in items:
        counts[week_key(item)] += 1
    if not counts:
        return 0, 0
    return (
        max(_ceil_div(count, per_employee) for count in counts.values()),
        len(counts),
    )


def _on_call_densest_window(dates: list, gap_days: int) -> int:
    ordered = sorted(set(dates))
    if not ordered:
        return 0
    best = 0
    start = 0
    for end in range(len(ordered)):
        while (ordered[end] - ordered[start]).days >= gap_days:
            start += 1
        best = max(best, end - start + 1)
    return best


def _same_day_rest_bound(shifts, rest_minutes: int) -> int:
    by_date = defaultdict(list)
    for shift in shifts:
        by_date[shift.work_date].append(shift)
    bound = 0
    for day_shifts in by_date.values():
        events = []
        for shift in day_shifts:
            rest = timedelta(minutes=rest_minutes)
            events.append((shift.starts_at - rest, 1))
            events.append((shift.ends_at + rest, -1))
        events.sort(key=lambda item: (item[0], -item[1]))
        current = 0
        for _, delta in events:
            current += delta
            bound = max(bound, current)
    return bound


def _closed_form_bound(request: SchedulingRequest) -> tuple[int, list[StaffingReason]]:
    shift_type_by_id = {item.shift_type_id: item for item in request.shift_types}
    shifts = request.work_shifts
    rest_minutes = request.rules.hard_rules.min_rest_hours_between_shifts * 60
    quotas = request.rules.quotas
    hard_rules = request.rules.hard_rules

    reasons: list[StaffingReason] = []
    # build_model applies max_shifts_per_week and the quota limits unconditionally, so a
    # limit of 0 forbids the shift outright instead of disabling the rule.
    weekly_limit = hard_rules.max_shifts_per_week
    required, weeks = _busiest_week_bound(
        (shift.work_date for shift in shifts), weekly_limit
    )
    reasons.append(
        StaffingReason(
            code="WEEKLY_CAPACITY",
            required_employees=required,
            detail=(
                f"No employee may take any of the {required} shift(s) in the busiest of the "
                f"{weeks} week(s) in the period, because each employee may take at most "
                f"{weekly_limit} shifts per week."
                if weekly_limit == 0
                else f"Each employee may take at most {weekly_limit} shifts per week, so the "
                f"busiest of the {weeks} week(s) in the period needs {required}."
            ),
        )
    )

    quota_limits = {
        "OVERTIME": quotas.overtime_shifts_per_week,
        "BATHING": quotas.bathing_shifts_per_week,
    }
    for category, limit in quota_limits.items():
        dates = {
            shift.work_date
            for shift in shifts
            if shift_type_by_id[shift.shift_type_id].quota_category == category
        }
        required, weeks = _busiest_week_bound(dates, limit)
        reasons.append(
            StaffingReason(
                code=f"QUOTA_{category}",
                required_employees=required,
                detail=(
                    f"No employee may cover any of the {required} {category.lower()} day(s) in "
                    f"the busiest of the {weeks} week(s) in the period, because each employee may "
                    f"cover at most {limit} {category.lower()} day(s) per week."
                    if limit == 0
                    else f"Each employee may cover at most {limit} {category.lower()} day(s) "
                    f"per week, so the busiest of the {weeks} week(s) in the period "
                    f"needs {required}."
                ),
            )
        )

    on_call_dates = [
        shift.work_date
        for shift in shifts
        if shift_type_by_id[shift.shift_type_id].quota_category == "ON_CALL"
    ]
    on_call_window = _on_call_densest_window(on_call_dates, quotas.on_call_every_n_days)
    if on_call_dates and on_call_window > 0:
        reasons.append(
            StaffingReason(
                code="ON_CALL_GAP",
                required_employees=on_call_window,
                detail=(
                    f"{on_call_window} on-call day(s) fall inside a single "
                    f"{quotas.on_call_every_n_days}-day window and one employee may hold at most one."
                ),
            )
        )

    rest_bound = _same_day_rest_bound(shifts, rest_minutes)
    if rest_bound > 0:
        reasons.append(
            StaffingReason(
                code="SAME_DAY_REST",
                required_employees=rest_bound,
                detail=(
                    f"Overlapping shifts on the same day need distinct employees because of the "
                    f"{hard_rules.min_rest_hours_between_shifts}h minimum rest."
                ),
            )
        )

    required = max((reason.required_employees for reason in reasons), default=0)
    return required, reasons


def _forbidden_by_zero_limit(request: SchedulingRequest) -> bool:
    # A limit of 0 forbids the shift outright for every employee, so no headcount in any
    # range makes the period schedulable and climbing candidates cannot find one.
    if request.rules.hard_rules.max_shifts_per_week == 0 and request.work_shifts:
        return True
    quotas = request.rules.quotas
    blocked = {
        category
        for category, limit in (
            ("OVERTIME", quotas.overtime_shifts_per_week),
            ("BATHING", quotas.bathing_shifts_per_week),
        )
        if limit == 0
    }
    if not blocked:
        return False
    shift_type_by_id = {item.shift_type_id: item for item in request.shift_types}
    return any(
        shift_type_by_id[shift.shift_type_id].quota_category in blocked
        for shift in request.work_shifts
    )


def _structural_request(request: SchedulingRequest, employee_count: int) -> SchedulingRequest:
    return request.model_copy(
        update={
            "employees": [
                EmployeeInput(employee_id=index)
                for index in range(1, employee_count + 1)
            ],
            "approved_leaves": [],
            "preferences": [],
            "locked_assignments": [],
            "boundary_assignments": [],
        }
    )


def find_minimum_feasible_employees(
    request: SchedulingRequest, lower_bound: int | None = None
) -> tuple[int, bool, bool]:
    if lower_bound is None:
        lower_bound, _reasons = _closed_form_bound(request)
    if _forbidden_by_zero_limit(request):
        return max(1, lower_bound), False, True
    available = len(request.employees)
    start = max(1, min(lower_bound, available + 1))
    # A start below the closed-form bound means the bound overstated the minimum,
    # so a feasible solve at start proves feasibility but not minimality.
    proves_minimality = lower_bound <= max(1, available + 1)

    for offset in range(MAX_CANDIDATES):
        employee_count = start + offset
        structural = _structural_request(request, employee_count)
        employees = [item.employee_id for item in structural.employees]
        model, _assigned = build_model(structural, employees)
        solver = cp_model.CpSolver()
        solver.parameters.max_time_in_seconds = SEARCH_TIME_LIMIT_SECONDS
        solver.parameters.num_search_workers = 1
        solver.parameters.random_seed = 1
        result = solver.solve(model)
        if result in (cp_model.OPTIMAL, cp_model.FEASIBLE):
            # Every value below start is ruled out by the closed-form bound, and the
            # climb ruled out everything from start up to this candidate.
            return employee_count, proves_minimality, False
        if result != cp_model.INFEASIBLE:
            # Time limit or invalid model: everything the climb ruled out stays
            # ruled out, but this candidate itself is unresolved.
            return employee_count, False, False

    # Nothing inside the searched budget was feasible, so the bound is not a target any
    # manager can hit by hiring; it is only the best number known for logs.
    return max(1, lower_bound), False, True


def estimate_required_employees(request: SchedulingRequest) -> StaffingAdvice:
    available = len(request.employees)
    lower_bound, reasons = _closed_form_bound(request)
    required, exact, unreachable = find_minimum_feasible_employees(request, lower_bound)
    # Every reason is derived from the closed-form bound, so it can only justify that
    # bound; once the climb raises the minimum, quoting a reason would contradict it.
    return StaffingAdvice(
        required_employees=required,
        available_employees=available,
        total_shifts=len(request.work_shifts),
        exact=exact,
        unreachable=unreachable,
        reasons=[
            reason for reason in reasons if reason.required_employees == lower_bound
        ]
        if required == lower_bound
        else [],
    )
