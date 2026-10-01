from __future__ import annotations

from collections import defaultdict
from datetime import timedelta

from ortools.sat.python import cp_model

from app.schemas.scheduling import SchedulingRequest


def week_key(moment) -> tuple[int, int]:
    iso = moment.isocalendar()
    return iso.year, iso.week


def conflict(first_start, first_end, second_start, second_end, rest_minutes: int) -> bool:
    if second_start < first_start:
        first_start, second_start = second_start, first_start
        first_end, second_end = second_end, first_end
    return second_start < first_end + timedelta(minutes=rest_minutes)


def leave_overlaps_shift(leave, shift) -> bool:
    last_shift_date = (shift.ends_at - timedelta(microseconds=1)).date()
    return leave.start_date <= last_shift_date and shift.starts_at.date() <= leave.end_date


def build_model(request: SchedulingRequest, employees: list[int]) -> tuple[cp_model.CpModel, dict]:
    model = cp_model.CpModel()
    shifts = request.work_shifts
    shift_type_by_id = {item.shift_type_id: item for item in request.shift_types}
    employee_ids = set(employees)
    shift_ids = {shift.work_shift_id for shift in shifts}

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
        if locked.employee_id in employee_ids and locked.work_shift_id in shift_ids:
            model.add(assigned[locked.employee_id, locked.work_shift_id] == 1)

    for leave in request.approved_leaves:
        if leave.employee_id not in employee_ids:
            continue
        for shift in shifts:
            if leave_overlaps_shift(leave, shift):
                model.add(assigned[leave.employee_id, shift.work_shift_id] == 0)

    rest_minutes = request.rules.hard_rules.min_rest_hours_between_shifts * 60
    for employee_id in employees:
        for index, first in enumerate(shifts):
            for second in shifts[index + 1 :]:
                if conflict(
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
                if conflict(
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
                    and week_key(boundary.starts_at.date()) == week
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

    return model, assigned
