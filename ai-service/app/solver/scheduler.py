from __future__ import annotations

from collections import defaultdict
from datetime import timedelta
from decimal import Decimal, ROUND_HALF_UP

from ortools.sat.python import cp_model

from app.schemas.scheduling import (
    AssignmentOutput,
    DiagnosticOutput,
    SchedulingRequest,
    SchedulingResponse,
    SolverStatistics,
)

WEIGHT_SCALE = Decimal("100")


class InvalidSchedulingInput(ValueError):
    pass


def _weight(value: Decimal) -> int:
    return int((value * WEIGHT_SCALE).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def _week_key(moment) -> tuple[int, int]:
    iso = moment.isocalendar()
    return iso.year, iso.week


def _conflict(first_start, first_end, second_start, second_end, rest_minutes: int) -> bool:
    if second_start < first_start:
        first_start, second_start = second_start, first_start
        first_end, second_end = second_end, first_end
    return second_start < first_end + timedelta(minutes=rest_minutes)


def _validate_request(request: SchedulingRequest) -> None:
    employee_ids = [item.employee_id for item in request.employees]
    shift_type_ids = [item.shift_type_id for item in request.shift_types]
    work_shift_ids = [item.work_shift_id for item in request.work_shifts]

    for label, identifiers in (
        ("employee", employee_ids),
        ("shift type", shift_type_ids),
        ("work shift", work_shift_ids),
    ):
        if len(identifiers) != len(set(identifiers)):
            raise InvalidSchedulingInput(f"Duplicate {label} id")

    employee_id_set = set(employee_ids)
    shift_type_id_set = set(shift_type_ids)
    work_shift_id_set = set(work_shift_ids)
    shift_type_by_id = {item.shift_type_id: item for item in request.shift_types}

    for shift in request.work_shifts:
        if shift.shift_type_id not in shift_type_id_set:
            raise InvalidSchedulingInput(
                f"Unknown shiftTypeId {shift.shift_type_id} for workShiftId {shift.work_shift_id}"
            )
        shift_type = shift_type_by_id[shift.shift_type_id]
        if not shift_type.min_staff <= shift.required_staff <= shift_type.max_staff:
            raise InvalidSchedulingInput(
                f"requiredStaff for workShiftId {shift.work_shift_id} is outside shift type range"
            )
        if not request.period.start_date <= shift.work_date <= request.period.end_date:
            raise InvalidSchedulingInput(
                f"workShiftId {shift.work_shift_id} is outside the scheduling period"
            )
        if shift.starts_at.date() != shift.work_date:
            raise InvalidSchedulingInput(
                f"startsAt date does not match workDate for workShiftId {shift.work_shift_id}"
            )

    for leave in request.approved_leaves:
        if leave.employee_id not in employee_id_set:
            raise InvalidSchedulingInput(
                f"Unknown employeeId {leave.employee_id} in approved leave"
            )
    for preference in request.preferences:
        if preference.employee_id not in employee_id_set:
            raise InvalidSchedulingInput(
                f"Unknown employeeId {preference.employee_id} in preference"
            )
        if preference.shift_type_id is not None and preference.shift_type_id not in shift_type_id_set:
            raise InvalidSchedulingInput(
                f"Unknown shiftTypeId {preference.shift_type_id} in preference"
            )
    for locked in request.locked_assignments:
        if locked.employee_id not in employee_id_set:
            raise InvalidSchedulingInput(
                f"Unknown employeeId {locked.employee_id} in locked assignment"
            )
        if locked.work_shift_id not in work_shift_id_set:
            raise InvalidSchedulingInput(
                f"Unknown workShiftId {locked.work_shift_id} in locked assignment"
            )
    for boundary in request.boundary_assignments:
        if boundary.employee_id not in employee_id_set:
            raise InvalidSchedulingInput(
                f"Unknown employeeId {boundary.employee_id} in boundary assignment"
            )
        if boundary.shift_type_id not in shift_type_id_set:
            raise InvalidSchedulingInput(
                f"Unknown shiftTypeId {boundary.shift_type_id} in boundary assignment"
            )


def solve_schedule(request: SchedulingRequest) -> SchedulingResponse:
    _validate_request(request)
    model = cp_model.CpModel()
    employees = [item.employee_id for item in request.employees]
    shifts = request.work_shifts
    shift_type_by_id = {item.shift_type_id: item for item in request.shift_types}

    assigned = {
        (employee_id, shift.work_shift_id): model.new_bool_var(
            f"employee_{employee_id}_shift_{shift.work_shift_id}"
        )
        for employee_id in employees
        for shift in shifts
    }

    for shift in shifts:
        model.add(
            sum(assigned[employee_id, shift.work_shift_id] for employee_id in employees)
            == shift.required_staff
        )

    for locked in request.locked_assignments:
        model.add(assigned[locked.employee_id, locked.work_shift_id] == 1)

    for leave in request.approved_leaves:
        for shift in shifts:
            last_shift_date = (shift.ends_at - timedelta(microseconds=1)).date()
            if leave.start_date <= last_shift_date and shift.starts_at.date() <= leave.end_date:
                model.add(assigned[leave.employee_id, shift.work_shift_id] == 0)

    rest_minutes = request.rules.hard_rules.min_rest_hours_between_shifts * 60
    for employee_id in employees:
        for index, first in enumerate(shifts):
            for second in shifts[index + 1 :]:
                if _conflict(
                    first.starts_at, first.ends_at, second.starts_at, second.ends_at, rest_minutes
                ):
                    model.add(
                        assigned[employee_id, first.work_shift_id]
                        + assigned[employee_id, second.work_shift_id]
                        <= 1
                    )

        for boundary in request.boundary_assignments:
            if boundary.employee_id != employee_id:
                continue
            for shift in shifts:
                if _conflict(
                    boundary.starts_at,
                    boundary.ends_at,
                    shift.starts_at,
                    shift.ends_at,
                    rest_minutes,
                ):
                    model.add(assigned[employee_id, shift.work_shift_id] == 0)

    shifts_by_date = defaultdict(list)
    shifts_by_week = defaultdict(list)
    for shift in shifts:
        shifts_by_date[shift.work_date].append(shift)
        shifts_by_week[_week_key(shift.work_date)].append(shift)

    boundaries_by_employee_date = defaultdict(int)
    boundaries_by_employee_week = defaultdict(int)
    for boundary in request.boundary_assignments:
        duration_minutes = int((boundary.ends_at - boundary.starts_at).total_seconds() // 60)
        boundaries_by_employee_date[boundary.employee_id, boundary.starts_at.date()] += duration_minutes
        boundaries_by_employee_week[boundary.employee_id, _week_key(boundary.starts_at.date())] += 1

    max_daily_minutes = request.rules.hard_rules.max_work_hours_per_day * 60
    for employee_id in employees:
        for work_date, daily_shifts in shifts_by_date.items():
            fixed_minutes = boundaries_by_employee_date[employee_id, work_date]
            model.add(
                sum(
                    shift_type_by_id[shift.shift_type_id].duration_minutes
                    * assigned[employee_id, shift.work_shift_id]
                    for shift in daily_shifts
                )
                + fixed_minutes
                <= max_daily_minutes
            )
        for week, weekly_shifts in shifts_by_week.items():
            fixed_count = boundaries_by_employee_week[employee_id, week]
            model.add(
                sum(assigned[employee_id, shift.work_shift_id] for shift in weekly_shifts)
                + fixed_count
                <= request.rules.hard_rules.max_shifts_per_week
            )

    quota_limits = {
        "OVERTIME": request.rules.quotas.overtime_shifts_per_week,
        "BATHING": request.rules.quotas.bathing_shifts_per_week,
    }
    for category, limit in quota_limits.items():
        for employee_id in employees:
            for week, weekly_shifts in shifts_by_week.items():
                shifts_by_quota_date = defaultdict(list)
                for shift in weekly_shifts:
                    if shift_type_by_id[shift.shift_type_id].quota_category == category:
                        shifts_by_quota_date[shift.work_date].append(shift)
                boundary_dates = {
                    boundary.starts_at.date()
                    for boundary in request.boundary_assignments
                    if boundary.employee_id == employee_id
                    and _week_key(boundary.starts_at.date()) == week
                    and shift_type_by_id[boundary.shift_type_id].quota_category == category
                }
                assigned_dates = []
                for work_date, dated_shifts in shifts_by_quota_date.items():
                    if work_date in boundary_dates:
                        continue
                    date_assigned = model.new_bool_var(
                        f"{category.lower()}_{employee_id}_{work_date.isoformat()}_assigned"
                    )
                    model.add_max_equality(
                        date_assigned,
                        [assigned[employee_id, shift.work_shift_id] for shift in dated_shifts],
                    )
                    assigned_dates.append(date_assigned)
                model.add(sum(assigned_dates) + len(boundary_dates) <= limit)

    on_call_gap = request.rules.quotas.on_call_every_n_days
    on_call_shifts = [
        shift
        for shift in shifts
        if shift_type_by_id[shift.shift_type_id].quota_category == "ON_CALL"
    ]
    for employee_id in employees:
        for index, first in enumerate(on_call_shifts):
            for second in on_call_shifts[index + 1 :]:
                if abs((second.work_date - first.work_date).days) < on_call_gap:
                    model.add(
                        assigned[employee_id, first.work_shift_id]
                        + assigned[employee_id, second.work_shift_id]
                        <= 1
                    )
        for boundary in request.boundary_assignments:
            if (
                boundary.employee_id == employee_id
                and shift_type_by_id[boundary.shift_type_id].quota_category == "ON_CALL"
            ):
                for shift in on_call_shifts:
                    if abs((shift.work_date - boundary.starts_at.date()).days) < on_call_gap:
                        model.add(assigned[employee_id, shift.work_shift_id] == 0)

    penalty_terms: dict[str, list[tuple[int, cp_model.LinearExpr]]] = defaultdict(list)
    weights = request.rules.soft_weights

    for preference in request.preferences:
        matching = [
            assigned[preference.employee_id, shift.work_shift_id]
            for shift in shifts
            if shift.work_date == preference.preference_date
            and (
                preference.shift_type_id is None
                or shift.shift_type_id == preference.shift_type_id
            )
        ]
        if not matching:
            continue
        satisfied = model.new_bool_var(f"preference_{preference.preference_id}_satisfied")
        model.add_max_equality(satisfied, matching)
        raw_penalty = satisfied if preference.preference_type == "AVOID" else 1 - satisfied
        penalty_terms["preference"].append(
            (_weight(weights.preference) * preference.priority, raw_penalty)
        )

    total_shift_count = {}
    total_work_units = {}
    weekend_count = {}
    max_shift_count = len(shifts)
    max_work_units = sum(
        max(1, shift_type_by_id[shift.shift_type_id].duration_minutes // 6)
        for shift in shifts
    )
    for employee_id in employees:
        total_shift_count[employee_id] = model.new_int_var(
            0, max_shift_count, f"shift_count_{employee_id}"
        )
        model.add(
            total_shift_count[employee_id]
            == sum(assigned[employee_id, shift.work_shift_id] for shift in shifts)
        )
        total_work_units[employee_id] = model.new_int_var(
            0, max_work_units, f"work_units_{employee_id}"
        )
        model.add(
            total_work_units[employee_id]
            == sum(
                max(1, shift_type_by_id[shift.shift_type_id].duration_minutes // 6)
                * assigned[employee_id, shift.work_shift_id]
                for shift in shifts
            )
        )
        weekend_count[employee_id] = model.new_int_var(
            0, max_shift_count, f"weekend_count_{employee_id}"
        )
        model.add(
            weekend_count[employee_id]
            == sum(
                assigned[employee_id, shift.work_shift_id]
                for shift in shifts
                if shift.work_date.weekday() >= 5
            )
        )

    if employees:
        for name, values, upper_bound, weight_value in (
            ("fairness", total_shift_count, max_shift_count, weights.fairness),
            ("workHourBalance", total_work_units, max_work_units, weights.work_hour_balance),
            ("weekend", weekend_count, max_shift_count, weights.weekend),
        ):
            maximum = model.new_int_var(0, upper_bound, f"{name}_max")
            minimum = model.new_int_var(0, upper_bound, f"{name}_min")
            model.add_max_equality(maximum, list(values.values()))
            model.add_min_equality(minimum, list(values.values()))
            penalty_terms[name].append((_weight(weight_value), maximum - minimum))

    for employee_id in employees:
        heavy_shifts = [
            shift for shift in shifts if shift_type_by_id[shift.shift_type_id].heavy
        ]
        for index, first in enumerate(heavy_shifts):
            for second in heavy_shifts[index + 1 :]:
                if abs((second.work_date - first.work_date).days) != 1:
                    continue
                consecutive = model.new_bool_var(
                    f"heavy_pair_{employee_id}_{first.work_shift_id}_{second.work_shift_id}"
                )
                first_var = assigned[employee_id, first.work_shift_id]
                second_var = assigned[employee_id, second.work_shift_id]
                model.add(consecutive <= first_var)
                model.add(consecutive <= second_var)
                model.add(consecutive >= first_var + second_var - 1)
                penalty_terms["heavyShift"].append((_weight(weights.heavy_shift), consecutive))

    objective = [
        coefficient * expression
        for terms in penalty_terms.values()
        for coefficient, expression in terms
        if coefficient > 0
    ]
    if objective:
        model.minimize(sum(objective))

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = request.solver_options.time_limit_seconds
    solver.parameters.random_seed = request.solver_options.random_seed
    solver.parameters.num_search_workers = request.solver_options.num_workers
    result = solver.solve(model)
    solver_status = solver.status_name(result)
    statistics = SolverStatistics(
        wall_time_seconds=solver.wall_time,
        conflicts=solver.num_conflicts,
        branches=solver.num_branches,
    )

    if result in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        output_assignments = [
            AssignmentOutput(employee_id=employee_id, work_shift_id=shift.work_shift_id)
            for employee_id in employees
            for shift in shifts
            if solver.value(assigned[employee_id, shift.work_shift_id])
        ]
        details = {
            name: sum(
                coefficient * solver.value(expression)
                for coefficient, expression in penalty_terms.get(name, [])
            )
            / float(WEIGHT_SCALE)
            for name in (
                "fairness",
                "preference",
                "weekend",
                "heavyShift",
                "workHourBalance",
            )
        }
        total_penalty = sum(details.values())
        warnings = []
        if result == cp_model.FEASIBLE:
            warnings.append("A feasible schedule was found, but optimality was not proven")
        return SchedulingResponse(
            run_id=request.run_id,
            period_id=request.period.period_id,
            status="SUCCESS",
            solver_status=solver_status,
            assignments=output_assignments,
            total_penalty=total_penalty,
            penalty_details=details,
            warnings=warnings,
            statistics=statistics,
        )

    if result == cp_model.INFEASIBLE:
        diagnostics = [
            DiagnosticOutput(
                code="NO_FEASIBLE_SCHEDULE",
                message="No schedule satisfies all hard constraints",
            )
        ]
        for shift in shifts:
            unavailable = {
                leave.employee_id
                for leave in request.approved_leaves
                if leave.start_date <= shift.work_date <= leave.end_date
            }
            if len(employees) - len(unavailable) < shift.required_staff:
                diagnostics.append(
                    DiagnosticOutput(
                        code="INSUFFICIENT_AVAILABLE_STAFF",
                        message="Approved leave leaves too few employees for this shift",
                        work_shift_id=shift.work_shift_id,
                    )
                )
        return SchedulingResponse(
            run_id=request.run_id,
            period_id=request.period.period_id,
            status="INFEASIBLE",
            solver_status=solver_status,
            error_message="No feasible schedule found",
            diagnostics=diagnostics,
            statistics=statistics,
        )

    error_message = (
        "The CP-SAT model is invalid"
        if result == cp_model.MODEL_INVALID
        else "The solver stopped before finding a feasible schedule"
    )
    return SchedulingResponse(
        run_id=request.run_id,
        period_id=request.period.period_id,
        status="FAILED",
        solver_status=solver_status,
        error_message=error_message,
        statistics=statistics,
    )
