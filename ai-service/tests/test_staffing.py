from datetime import date, datetime, time, timedelta, timezone

from app.schemas.scheduling import SchedulingRequest
from app.solver.staffing import _closed_form_bound, estimate_required_employees

TZ = timezone(timedelta(hours=7))

DEFAULT_RULES = {
    "hardRules": {
        "minRestHoursBetweenShifts": 12,
        "maxWorkHoursPerDay": 12,
        "maxShiftsPerWeek": 6,
    },
    "quotas": {
        "overtimeShiftsPerWeek": 1,
        "bathingShiftsPerWeek": 2,
        "onCallEveryNDays": 6,
    },
    "softWeights": {
        "fairness": 10,
        "preference": 5,
        "weekend": 5,
        "heavyShift": 8,
        "workHourBalance": 10,
    },
}

CANONICAL_WINDOWS = {
    "THONG_TAM": ("08:00", "17:00"),
    "NGOAI_GIO_1": ("17:00", "00:30"),
    "NGOAI_GIO_2": ("00:30", "08:00"),
    "TRUC_SANG": ("08:00", "16:00"),
    "TRUC_CHIEU": ("16:00", "00:00"),
    "TRUC_DEM": ("00:00", "08:00"),
}

CANONICAL_TYPES = {
    "THONG_TAM": {
        "durationMinutes": 480,
        "overnight": False,
        "heavy": True,
        "quotaCategory": "BATHING",
    },
    "NGOAI_GIO_1": {
        "durationMinutes": 450,
        "overnight": True,
        "heavy": False,
        "quotaCategory": "OVERTIME",
    },
    "NGOAI_GIO_2": {
        "durationMinutes": 450,
        "overnight": False,
        "heavy": True,
        "quotaCategory": "OVERTIME",
    },
    "TRUC_SANG": {
        "durationMinutes": 480,
        "overnight": False,
        "heavy": False,
        "quotaCategory": "ON_CALL",
    },
    "TRUC_CHIEU": {
        "durationMinutes": 480,
        "overnight": True,
        "heavy": False,
        "quotaCategory": "ON_CALL",
    },
    "TRUC_DEM": {
        "durationMinutes": 480,
        "overnight": False,
        "heavy": True,
        "quotaCategory": "ON_CALL",
    },
}

SHIFT_TYPE_IDS = {code: index for index, code in enumerate(CANONICAL_TYPES, start=1)}

WEEKDAY_TYPES = ("THONG_TAM", "NGOAI_GIO_1", "NGOAI_GIO_2")
WEEKEND_TYPES = ("TRUC_SANG", "TRUC_CHIEU", "TRUC_DEM")

MONDAY = date(2026, 9, 28)
SATURDAY = date(2026, 10, 3)


def _dt(day: date, hhmm: str) -> datetime:
    hour, minute = (int(part) for part in hhmm.split(":"))
    return datetime.combine(day, time(hour, minute), tzinfo=TZ)


def _shift(shift_id: int, day: date, code: str, **overrides) -> dict:
    start, end = CANONICAL_WINDOWS[code]
    return {
        "id": shift_id,
        "code": code,
        "shiftTypeId": SHIFT_TYPE_IDS[code],
        "date": day,
        "start": start,
        "end": end,
        **{**CANONICAL_TYPES[code], **overrides},
    }


def _derive_window(day: date, shift: dict) -> tuple[datetime, datetime]:
    starts_at = _dt(day, shift["start"])
    ends_at = _dt(day, shift["end"])
    if not ends_at > starts_at:
        ends_at = ends_at + timedelta(days=1)
    return starts_at, ends_at


def _request(shifts, employees, rules=DEFAULT_RULES) -> SchedulingRequest:
    shift_types = {}
    for shift in shifts:
        shift_types.setdefault(
            shift["shiftTypeId"],
            {
                "shiftTypeId": shift["shiftTypeId"],
                "durationMinutes": shift["durationMinutes"],
                "overnight": shift["overnight"],
                "heavy": shift["heavy"],
                "minStaff": 1,
                "maxStaff": 20,
                "quotaCategory": shift["quotaCategory"],
            },
        )
    work_shifts = []
    for shift in shifts:
        starts_at, ends_at = _derive_window(shift["date"], shift)
        work_shifts.append(
            {
                "workShiftId": shift["id"],
                "shiftTypeId": shift["shiftTypeId"],
                "workDate": shift["date"].isoformat(),
                "startsAt": starts_at.isoformat(),
                "endsAt": ends_at.isoformat(),
                "requiredStaff": shift.get("requiredStaff", 1),
            }
        )
    return SchedulingRequest.model_validate(
        {
            "schemaVersion": "1.0",
            "runId": 1,
            "period": {
                "periodId": 1,
                "startDate": min(shift["date"] for shift in shifts).isoformat(),
                "endDate": max(shift["date"] for shift in shifts).isoformat(),
                "versionNo": 1,
                "timezone": "Asia/Ho_Chi_Minh",
            },
            "employees": [{"employeeId": employee_id} for employee_id in employees],
            "shiftTypes": list(shift_types.values()),
            "workShifts": work_shifts,
            "approvedLeaves": [],
            "preferences": [],
            "lockedAssignments": [],
            "boundaryAssignments": [],
            "rules": rules,
            "solverOptions": {"timeLimitSeconds": 5, "randomSeed": 1, "numWorkers": 1},
        }
    )


def _weekday_only_week() -> list[dict]:
    return [
        _shift(offset * len(WEEKDAY_TYPES) + index + 1, MONDAY + timedelta(days=offset), code)
        for offset in range(5)
        for index, code in enumerate(WEEKDAY_TYPES)
    ]


def _full_week() -> list[dict]:
    shifts = []
    shift_id = 1
    for offset in range(7):
        day = MONDAY + timedelta(days=offset)
        codes = WEEKDAY_TYPES if day.weekday() < 5 else WEEKEND_TYPES
        for code in codes:
            shifts.append(_shift(shift_id, day, code))
            shift_id += 1
    return shifts


def _full_weeks(count: int) -> list[dict]:
    shifts = []
    shift_id = 1
    for week in range(count):
        for offset in range(7):
            day = MONDAY + timedelta(days=week * 7 + offset)
            codes = WEEKDAY_TYPES if day.weekday() < 5 else WEEKEND_TYPES
            for code in codes:
                shifts.append(_shift(shift_id, day, code))
                shift_id += 1
    return shifts


def _reasons_by_code(request: SchedulingRequest) -> tuple[int, dict[str, int]]:
    bound, reasons = _closed_form_bound(request)
    return bound, {reason.code: reason.required_employees for reason in reasons}


def _on_call_streak(count: int) -> list[dict]:
    return [
        _shift(index, SATURDAY + timedelta(days=index - 1), "TRUC_SANG")
        for index in range(1, count + 1)
    ]


def test_exact_minimum_for_three_shift_weekday_week():
    request = _request(_weekday_only_week(), list(range(1, 13)))

    advice = estimate_required_employees(request)

    assert advice.total_shifts == 15
    assert advice.available_employees == 12
    assert advice.required_employees == 10
    assert advice.exact is True
    assert "QUOTA_OVERTIME" in {reason.code for reason in advice.reasons}


def test_exact_minimum_for_full_week_with_every_shift_type():
    shifts = _full_week()
    request = _request(shifts, list(range(1, 13)))

    advice = estimate_required_employees(request)

    assert advice.total_shifts == 21
    assert advice.required_employees == 10
    assert advice.exact is True
    on_call_shifts = [
        shift
        for shift in shifts
        if CANONICAL_TYPES[shift["code"]]["quotaCategory"] == "ON_CALL"
    ]
    assert len(on_call_shifts) == 6


def test_exact_minimum_for_consecutive_on_call_days_respects_gap():
    request = _request(_on_call_streak(14), list(range(1, 13)))

    advice = estimate_required_employees(request)

    assert advice.total_shifts == 14
    assert advice.required_employees == 6
    assert advice.exact is True
    assert "ON_CALL_GAP" in {reason.code for reason in advice.reasons}


def test_weekly_capacity_is_used_when_no_quota_binds():
    shifts = [
        _shift(
            index,
            MONDAY + timedelta(days=index - 1),
            "TRUC_SANG",
            quotaCategory=None,
        )
        for index in range(1, 8)
    ]
    request = _request(shifts, [1, 2, 3])

    advice = estimate_required_employees(request)

    assert advice.total_shifts == 7
    assert advice.required_employees == 2
    assert advice.exact is True
    assert "WEEKLY_CAPACITY" in {reason.code for reason in advice.reasons}


def test_exact_minimum_accounts_for_required_staff_per_shift():
    shifts = [
        _shift(
            index,
            MONDAY + timedelta(days=index - 1),
            "TRUC_SANG",
            quotaCategory=None,
            requiredStaff=4,
        )
        for index in range(1, 8)
    ]
    request = _request(shifts, list(range(1, 13)))

    advice = estimate_required_employees(request)

    assert advice.total_shifts == 7
    assert advice.required_employees == 5
    assert advice.exact is True


def test_on_call_gap_reuses_employees_far_apart_in_time():
    shifts = [
        _shift(1, SATURDAY, "TRUC_SANG"),
        _shift(2, SATURDAY + timedelta(days=7), "TRUC_SANG"),
    ]
    request = _request(shifts, [1, 2])

    advice = estimate_required_employees(request)

    assert advice.required_employees == 1
    assert advice.exact is True


def test_single_week_bound_is_unchanged_by_multi_week_handling():
    request = _request(_full_week(), list(range(1, 13)))

    bound, reasons = _reasons_by_code(request)

    assert bound == 5
    assert reasons["WEEKLY_CAPACITY"] == 4
    assert reasons["QUOTA_OVERTIME"] == 5
    assert reasons["QUOTA_BATHING"] == 3
    assert reasons["ON_CALL_GAP"] == 2
    assert reasons["SAME_DAY_REST"] == 3


def test_multi_week_capacity_bound_uses_busiest_week_not_period_total():
    shifts = [
        _shift(
            index,
            MONDAY + timedelta(days=index - 1),
            "TRUC_SANG",
            quotaCategory=None,
        )
        for index in range(1, 15)
    ]
    request = _request(shifts, list(range(1, 13)))

    bound, reasons = _reasons_by_code(request)

    # ceil(7 / 6) for the busiest week, not ceil(14 / 6) over the whole period.
    assert reasons["WEEKLY_CAPACITY"] == 2
    assert bound == 2

    advice = estimate_required_employees(request)

    assert advice.required_employees == 2
    assert advice.exact is True


def test_multi_week_quota_bound_uses_busiest_week_not_period_total():
    days = [
        MONDAY + timedelta(days=week * 7 + offset)
        for week in range(2)
        for offset in range(4)
    ]
    shifts = [
        _shift(index, day, "TRUC_SANG", quotaCategory="OVERTIME")
        for index, day in enumerate(days, start=1)
    ]
    request = _request(shifts, list(range(1, 13)))

    bound, reasons = _reasons_by_code(request)

    # ceil(4 / 1) for the busiest week, not ceil(8 / 1) over the whole period.
    assert reasons["QUOTA_OVERTIME"] == 4
    assert bound == 4

    advice = estimate_required_employees(request)

    assert advice.required_employees == 4
    assert advice.exact is True


def test_two_full_weeks_bound_never_overstates_the_true_minimum():
    request = _request(_full_weeks(2), list(range(1, 13)))

    bound, reasons = _reasons_by_code(request)

    # Period-wide division would give ceil(42 / 6) = 7 and ceil(10 / 1) = 10.
    assert reasons["WEEKLY_CAPACITY"] == 4
    assert reasons["QUOTA_OVERTIME"] == 5
    assert bound == 5
    assert bound <= 10


def test_exact_minimum_for_two_full_weeks():
    request = _request(_full_weeks(2), list(range(1, 13)))

    advice = estimate_required_employees(request)

    assert advice.total_shifts == 42
    assert advice.required_employees == 10
    assert advice.exact is True
