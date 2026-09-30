from __future__ import annotations

from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP

from ortools.sat.python import cp_model

from app.schemas.scheduling import (
    AssignmentOutput,
    DiagnosticOutput,
    SchedulingRequest,
    SchedulingResponse,
    SolverStatistics,
)
from app.solver.model_builder import build_model, conflict, leave_overlaps_shift, week_key
from app.solver.staffing import estimate_required_employees

WEIGHT_SCALE = Decimal("100")


class InvalidSchedulingInput(ValueError):
    pass


def _weight(value: Decimal) -> int:
    return int((value * WEIGHT_SCALE).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


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


def _infeasible_diagnostics(request: SchedulingRequest) -> list[DiagnosticOutput]:
    diagnostics = [
        DiagnosticOutput(
            code="NO_FEASIBLE_SCHEDULE",
            message="No schedule satisfies all hard constraints",
        )
    ]
    shift_by_id = {shift.work_shift_id: shift for shift in request.work_shifts}
    shift_type_by_id = {item.shift_type_id: item for item in request.shift_types}
    employees = [item.employee_id for item in request.employees]
    leaves_by_employee = defaultdict(list)
    for leave in request.approved_leaves:
        leaves_by_employee[leave.employee_id].append(leave)

    for locked in request.locked_assignments:
        shift = shift_by_id.get(locked.work_shift_id)
        if shift is None:
            continue
        for leave in leaves_by_employee[locked.employee_id]:
            if leave_overlaps_shift(leave, shift):
                diagnostics.append(DiagnosticOutput(
                    code="LOCKED_ASSIGNMENT_CONFLICTS_WITH_LEAVE",
                    message="Locked assignment conflicts with approved leave",
                    employee_id=locked.employee_id,
                    work_shift_id=locked.work_shift_id,
                ))

    for shift in request.work_shifts:
        unavailable = {
            leave.employee_id
            for leave in request.approved_leaves
            if leave_overlaps_shift(leave, shift)
        }
        if len(request.employees) - len(unavailable) < shift.required_staff:
            diagnostics.append(DiagnosticOutput(
                code="INSUFFICIENT_AVAILABLE_STAFF",
                message="Approved leave leaves too few employees for this shift",
                work_shift_id=shift.work_shift_id,
            ))

    rest_minutes = request.rules.hard_rules.min_rest_hours_between_shifts * 60
    for employee_id in employees:
        for index, first in enumerate(request.work_shifts):
            for second in request.work_shifts[index + 1:]:
                if conflict(first.starts_at, first.ends_at, second.starts_at, second.ends_at, rest_minutes):
                    diagnostics.append(DiagnosticOutput(
                        code="MIN_REST_CONFLICT",
                        message="Employee cannot cover both shifts because minimum rest would be violated",
                        employee_id=employee_id,
                        work_shift_id=second.work_shift_id,
                    ))
        for boundary in request.boundary_assignments:
            if boundary.employee_id != employee_id:
                continue
            for shift in request.work_shifts:
                if conflict(boundary.starts_at, boundary.ends_at, shift.starts_at, shift.ends_at, rest_minutes):
                    diagnostics.append(DiagnosticOutput(
                        code="MIN_REST_CONFLICT",
                        message="Existing assignment leaves insufficient rest before this shift",
                        employee_id=employee_id,
                        work_shift_id=shift.work_shift_id,
                    ))

    shifts_by_date = defaultdict(list)
    shifts_by_week = defaultdict(list)
    for shift in request.work_shifts:
        shifts_by_date[shift.work_date].append(shift)
        shifts_by_week[week_key(shift.work_date)].append(shift)

    boundaries_by_employee_date = defaultdict(int)
    boundaries_by_employee_week = defaultdict(int)
    for boundary in request.boundary_assignments:
        duration_minutes = int((boundary.ends_at - boundary.starts_at).total_seconds() // 60)
        boundaries_by_employee_date[boundary.employee_id, boundary.starts_at.date()] += duration_minutes
        boundaries_by_employee_week[boundary.employee_id, week_key(boundary.starts_at.date())] += 1

    max_daily_minutes = request.rules.hard_rules.max_work_hours_per_day * 60
    for employee_id in employees:
        for work_date, daily_shifts in shifts_by_date.items():
            total_minutes = boundaries_by_employee_date[employee_id, work_date]
            for shift in sorted(daily_shifts, key=lambda item: item.starts_at):
                total_minutes += shift_type_by_id[shift.shift_type_id].duration_minutes
                if total_minutes > max_daily_minutes:
                    diagnostics.append(DiagnosticOutput(
                        code="MAX_WORK_HOURS_EXCEEDED",
                        message="Employee would exceed maximum work hours for the day",
                        employee_id=employee_id,
                        work_shift_id=shift.work_shift_id,
                    ))
        for week, weekly_shifts in shifts_by_week.items():
            total_shifts = boundaries_by_employee_week[employee_id, week]
            for shift in sorted(weekly_shifts, key=lambda item: item.starts_at):
                total_shifts += 1
                if total_shifts > request.rules.hard_rules.max_shifts_per_week:
                    diagnostics.append(DiagnosticOutput(
                        code="MAX_SHIFTS_PER_WEEK_EXCEEDED",
                        message="Employee would exceed maximum shifts for the week",
                        employee_id=employee_id,
                        work_shift_id=shift.work_shift_id,
                    ))

    quota_limits = {
        "OVERTIME": request.rules.quotas.overtime_shifts_per_week,
        "BATHING": request.rules.quotas.bathing_shifts_per_week,
    }
    for category, limit in quota_limits.items():
        for employee_id in employees:
            for week, weekly_shifts in shifts_by_week.items():
                counted_dates = {
                    boundary.starts_at.date()
                    for boundary in request.boundary_assignments
                    if boundary.employee_id == employee_id
                    and week_key(boundary.starts_at.date()) == week
                    and shift_type_by_id[boundary.shift_type_id].quota_category == category
                }
                for shift in sorted(weekly_shifts, key=lambda item: item.starts_at):
                    if shift_type_by_id[shift.shift_type_id].quota_category != category:
                        continue
                    counted_dates.add(shift.work_date)
                    if len(counted_dates) > limit:
                        diagnostics.append(DiagnosticOutput(
                            code="QUOTA_EXCEEDED",
                            message=f"Employee would exceed {category} weekly quota",
                            employee_id=employee_id,
                            work_shift_id=shift.work_shift_id,
                        ))

    on_call_gap = request.rules.quotas.on_call_every_n_days
    on_call_shifts = [
        shift
        for shift in request.work_shifts
        if shift_type_by_id[shift.shift_type_id].quota_category == "ON_CALL"
    ]
    for employee_id in employees:
        for index, first in enumerate(on_call_shifts):
            for second in on_call_shifts[index + 1:]:
                if abs((second.work_date - first.work_date).days) < on_call_gap:
                    diagnostics.append(DiagnosticOutput(
                        code="ON_CALL_GAP_CONFLICT",
                        message="Employee would be assigned on-call shifts too close together",
                        employee_id=employee_id,
                        work_shift_id=second.work_shift_id,
                    ))
        for boundary in request.boundary_assignments:
            if (
                boundary.employee_id != employee_id
                or shift_type_by_id[boundary.shift_type_id].quota_category != "ON_CALL"
            ):
                continue
            for shift in on_call_shifts:
                if abs((shift.work_date - boundary.starts_at.date()).days) < on_call_gap:
                    diagnostics.append(DiagnosticOutput(
                        code="ON_CALL_GAP_CONFLICT",
                        message="Existing on-call assignment is too close to this shift",
                        employee_id=employee_id,
                        work_shift_id=shift.work_shift_id,
                    ))
    return diagnostics


def solve_schedule(request: SchedulingRequest) -> SchedulingResponse:
    _validate_request(request)
    staffing_advice = estimate_required_employees(request)
    employees = [item.employee_id for item in request.employees]
    model, assigned = build_model(request, employees)
    shifts = request.work_shifts
    shift_type_by_id = {item.shift_type_id: item for item in request.shift_types}

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
            staffing_advice=staffing_advice,
            statistics=statistics,
        )

    if result == cp_model.INFEASIBLE:
        diagnostics = _infeasible_diagnostics(request)
        return SchedulingResponse(
            run_id=request.run_id,
            period_id=request.period.period_id,
            status="INFEASIBLE",
            solver_status=solver_status,
            error_message="No feasible schedule found",
            diagnostics=diagnostics,
            staffing_advice=staffing_advice,
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
        staffing_advice=staffing_advice,
        statistics=statistics,
    )
