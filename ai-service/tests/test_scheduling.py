from copy import deepcopy

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def base_request() -> dict:
    return {
        "schemaVersion": "1.0",
        "runId": 10,
        "period": {
            "periodId": 20,
            "startDate": "2026-09-14",
            "endDate": "2026-09-15",
            "versionNo": 1,
            "timezone": "Asia/Ho_Chi_Minh",
        },
        "employees": [{"employeeId": 1}, {"employeeId": 2}],
        "shiftTypes": [
            {
                "shiftTypeId": 100,
                "durationMinutes": 480,
                "overnight": False,
                "heavy": False,
                "minStaff": 1,
                "maxStaff": 2,
                "quotaCategory": None,
            }
        ],
        "workShifts": [
            {
                "workShiftId": 1000,
                "shiftTypeId": 100,
                "workDate": "2026-09-14",
                "startsAt": "2026-09-14T08:00:00+07:00",
                "endsAt": "2026-09-14T16:00:00+07:00",
                "requiredStaff": 1,
            }
        ],
        "approvedLeaves": [],
        "preferences": [],
        "lockedAssignments": [],
        "boundaryAssignments": [],
        "rules": {
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
        },
        "solverOptions": {
            "timeLimitSeconds": 5,
            "randomSeed": 1,
            "numWorkers": 1,
        },
    }


def test_optimize_returns_feasible_assignment() -> None:
    response = client.post("/api/v1/scheduling/optimize", json=base_request())

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCESS"
    assert body["solverStatus"] in ("OPTIMAL", "FEASIBLE")
    assert len(body["assignments"]) == 1
    assert body["assignments"][0]["workShiftId"] == 1000


def test_approved_leave_can_make_schedule_infeasible() -> None:
    request = base_request()
    request["approvedLeaves"] = [
        {
            "leaveRequestId": employee_id,
            "employeeId": employee_id,
            "startDate": "2026-09-14",
            "endDate": "2026-09-14",
        }
        for employee_id in (1, 2)
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "INFEASIBLE"
    assert body["solverStatus"] == "INFEASIBLE"
    assert any(
        item["code"] == "INSUFFICIENT_AVAILABLE_STAFF" and item["workShiftId"] == 1000
        for item in body["diagnostics"]
    )


def test_locked_assignment_is_preserved() -> None:
    request = base_request()
    request["lockedAssignments"] = [
        {"assignmentId": 50, "employeeId": 2, "workShiftId": 1000}
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    assert response.json()["assignments"] == [
        {"employeeId": 2, "workShiftId": 1000}
    ]


def test_minimum_rest_blocks_close_shifts() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["workShifts"].append(
        {
            "workShiftId": 1001,
            "shiftTypeId": 100,
            "workDate": "2026-09-15",
            "startsAt": "2026-09-15T02:00:00+07:00",
            "endsAt": "2026-09-15T10:00:00+07:00",
            "requiredStaff": 1,
        }
    )

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    assert response.json()["status"] == "INFEASIBLE"


def test_preference_changes_selected_employee() -> None:
    request = base_request()
    request["preferences"] = [
        {
            "preferenceId": 70,
            "employeeId": 2,
            "shiftTypeId": 100,
            "preferenceDate": "2026-09-14",
            "preferenceType": "WANT",
            "priority": 3,
        }
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    assert response.json()["assignments"] == [
        {"employeeId": 2, "workShiftId": 1000}
    ]


def test_unknown_reference_returns_validation_error() -> None:
    request = deepcopy(base_request())
    request["lockedAssignments"] = [
        {"assignmentId": 50, "employeeId": 999, "workShiftId": 1000}
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 422
    assert "Unknown employeeId 999" in response.json()["detail"]


def test_boundary_assignment_blocks_shift_with_insufficient_rest() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["boundaryAssignments"] = [
        {
            "employeeId": 1,
            "shiftTypeId": 100,
            "startsAt": "2026-09-13T20:00:00+07:00",
            "endsAt": "2026-09-14T04:00:00+07:00",
        }
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    assert response.json()["status"] == "INFEASIBLE"


def test_weekly_special_shift_quota_is_enforced() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["shiftTypes"][0]["quotaCategory"] = "OVERTIME"
    request["workShifts"].append(
        {
            "workShiftId": 1001,
            "shiftTypeId": 100,
            "workDate": "2026-09-15",
            "startsAt": "2026-09-15T08:00:00+07:00",
            "endsAt": "2026-09-15T16:00:00+07:00",
            "requiredStaff": 1,
        }
    )

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    assert response.json()["status"] == "INFEASIBLE"


def test_overtime_quota_counts_same_date_once() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["rules"]["hardRules"]["minRestHoursBetweenShifts"] = 0
    request["shiftTypes"][0]["durationMinutes"] = 60
    request["shiftTypes"][0]["quotaCategory"] = "OVERTIME"
    request["workShifts"] = [
        {"workShiftId":1000,"shiftTypeId":100,"workDate":"2026-09-14","startsAt":"2026-09-14T08:00:00+07:00","endsAt":"2026-09-14T09:00:00+07:00","requiredStaff":1},
        {"workShiftId":1001,"shiftTypeId":100,"workDate":"2026-09-14","startsAt":"2026-09-14T10:00:00+07:00","endsAt":"2026-09-14T11:00:00+07:00","requiredStaff":1},
    ]
    request["lockedAssignments"] = [{"assignmentId":50,"employeeId":1,"workShiftId":1000},{"assignmentId":51,"employeeId":1,"workShiftId":1001}]
    response = client.post("/api/v1/scheduling/optimize", json=request)
    assert response.status_code == 200
    assert sorted(response.json()["assignments"], key=lambda item: item["workShiftId"]) == [{"employeeId":1,"workShiftId":1000},{"employeeId":1,"workShiftId":1001}]


def test_overtime_quota_counts_different_dates_separately() -> None:
    request = base_request()
    request["period"]["endDate"] = "2026-09-15"
    request["rules"]["hardRules"]["minRestHoursBetweenShifts"] = 0
    request["shiftTypes"][0]["durationMinutes"] = 60
    request["shiftTypes"][0]["quotaCategory"] = "OVERTIME"
    request["workShifts"] = [
        {"workShiftId":1000,"shiftTypeId":100,"workDate":"2026-09-14","startsAt":"2026-09-14T08:00:00+07:00","endsAt":"2026-09-14T09:00:00+07:00","requiredStaff":1},
        {"workShiftId":1001,"shiftTypeId":100,"workDate":"2026-09-15","startsAt":"2026-09-15T08:00:00+07:00","endsAt":"2026-09-15T09:00:00+07:00","requiredStaff":1},
    ]
    request["lockedAssignments"] = [{"assignmentId":50,"employeeId":1,"workShiftId":1000}]
    response = client.post("/api/v1/scheduling/optimize", json=request)
    assert response.status_code == 200
    assert sorted(response.json()["assignments"], key=lambda item: item["workShiftId"]) == [{"employeeId":1,"workShiftId":1000},{"employeeId":2,"workShiftId":1001}]


def test_bathing_quota_allows_two_distinct_dates_not_three() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["period"]["endDate"] = "2026-09-16"
    request["rules"]["hardRules"]["minRestHoursBetweenShifts"] = 0
    request["shiftTypes"][0]["durationMinutes"] = 60
    request["shiftTypes"][0]["quotaCategory"] = "BATHING"
    request["workShifts"] = [
        {"workShiftId":1000,"shiftTypeId":100,"workDate":"2026-09-14","startsAt":"2026-09-14T08:00:00+07:00","endsAt":"2026-09-14T09:00:00+07:00","requiredStaff":1},
        {"workShiftId":1001,"shiftTypeId":100,"workDate":"2026-09-15","startsAt":"2026-09-15T08:00:00+07:00","endsAt":"2026-09-15T09:00:00+07:00","requiredStaff":1},
    ]
    response = client.post("/api/v1/scheduling/optimize", json=request)
    assert response.status_code == 200
    assert response.json()["status"] == "SUCCESS"
    request["workShifts"].append(
        {"workShiftId":1002,"shiftTypeId":100,"workDate":"2026-09-16","startsAt":"2026-09-16T08:00:00+07:00","endsAt":"2026-09-16T09:00:00+07:00","requiredStaff":1},
    )
    response = client.post("/api/v1/scheduling/optimize", json=request)
    assert response.status_code == 200
    assert response.json()["status"] == "INFEASIBLE"


def test_boundary_assignment_consumes_distinct_quota_date() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["period"]["endDate"] = "2026-09-15"
    request["rules"]["hardRules"]["minRestHoursBetweenShifts"] = 0
    request["shiftTypes"][0]["durationMinutes"] = 60
    request["shiftTypes"][0]["quotaCategory"] = "OVERTIME"
    request["workShifts"] = [
        {"workShiftId":1000,"shiftTypeId":100,"workDate":"2026-09-15","startsAt":"2026-09-15T08:00:00+07:00","endsAt":"2026-09-15T09:00:00+07:00","requiredStaff":1},
    ]
    request["boundaryAssignments"] = [
        {"employeeId":1,"shiftTypeId":100,"startsAt":"2026-09-14T08:00:00+07:00","endsAt":"2026-09-14T09:00:00+07:00"},
    ]
    response = client.post("/api/v1/scheduling/optimize", json=request)
    assert response.status_code == 200
    assert response.json()["status"] == "INFEASIBLE"


def test_boundary_same_date_counts_once() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["rules"]["hardRules"]["minRestHoursBetweenShifts"] = 0
    request["shiftTypes"][0]["durationMinutes"] = 60
    request["shiftTypes"][0]["quotaCategory"] = "OVERTIME"
    request["workShifts"] = [
        {"workShiftId":1000,"shiftTypeId":100,"workDate":"2026-09-14","startsAt":"2026-09-14T08:00:00+07:00","endsAt":"2026-09-14T09:00:00+07:00","requiredStaff":1},
    ]
    request["boundaryAssignments"] = [
        {"employeeId":1,"shiftTypeId":100,"startsAt":"2026-09-14T18:00:00+07:00","endsAt":"2026-09-14T19:00:00+07:00"},
    ]
    response = client.post("/api/v1/scheduling/optimize", json=request)
    assert response.status_code == 200
    assert response.json()["status"] == "SUCCESS"


def test_naive_shift_datetime_is_rejected() -> None:
    request = base_request()
    request["workShifts"][0]["startsAt"] = "2026-09-14T08:00:00"

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 422


def test_infeasible_diagnostics_include_locked_leave_conflict() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["lockedAssignments"] = [{"assignmentId": 50, "employeeId": 1, "workShiftId": 1000}]
    request["approvedLeaves"] = [
        {"leaveRequestId": 1, "employeeId": 1, "startDate": "2026-09-14", "endDate": "2026-09-14"}
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "INFEASIBLE"
    assert any(
        item["code"] == "LOCKED_ASSIGNMENT_CONFLICTS_WITH_LEAVE"
        and item["employeeId"] == 1
        and item["workShiftId"] == 1000
        for item in body["diagnostics"]
    )


def test_infeasible_diagnostics_include_rest_conflict() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["period"]["endDate"] = "2026-09-15"
    request["workShifts"].append(
        {
            "workShiftId": 1001,
            "shiftTypeId": 100,
            "workDate": "2026-09-15",
            "startsAt": "2026-09-15T02:00:00+07:00",
            "endsAt": "2026-09-15T10:00:00+07:00",
            "requiredStaff": 1,
        }
    )

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "INFEASIBLE"
    assert any(
        item["code"] == "MIN_REST_CONFLICT"
        and item["employeeId"] == 1
        and item["workShiftId"] == 1001
        for item in body["diagnostics"]
    )


def test_infeasible_diagnostics_include_daily_hour_conflict() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["rules"]["hardRules"]["minRestHoursBetweenShifts"] = 0
    request["rules"]["hardRules"]["maxWorkHoursPerDay"] = 8
    request["workShifts"].append(
        {
            "workShiftId": 1001,
            "shiftTypeId": 100,
            "workDate": "2026-09-14",
            "startsAt": "2026-09-14T17:00:00+07:00",
            "endsAt": "2026-09-14T21:00:00+07:00",
            "requiredStaff": 1,
        }
    )

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "INFEASIBLE"
    assert any(
        item["code"] == "MAX_WORK_HOURS_EXCEEDED"
        and item["employeeId"] == 1
        and item["workShiftId"] == 1001
        for item in body["diagnostics"]
    )


def test_infeasible_diagnostics_include_weekly_shift_conflict() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["period"]["endDate"] = "2026-09-15"
    request["rules"]["hardRules"]["minRestHoursBetweenShifts"] = 0
    request["rules"]["hardRules"]["maxShiftsPerWeek"] = 1
    request["workShifts"].append(
        {
            "workShiftId": 1001,
            "shiftTypeId": 100,
            "workDate": "2026-09-15",
            "startsAt": "2026-09-15T08:00:00+07:00",
            "endsAt": "2026-09-15T16:00:00+07:00",
            "requiredStaff": 1,
        }
    )

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "INFEASIBLE"
    assert any(
        item["code"] == "MAX_SHIFTS_PER_WEEK_EXCEEDED"
        and item["employeeId"] == 1
        and item["workShiftId"] == 1001
        for item in body["diagnostics"]
    )


def test_infeasible_diagnostics_include_quota_conflict() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["period"]["endDate"] = "2026-09-15"
    request["rules"]["hardRules"]["minRestHoursBetweenShifts"] = 0
    request["shiftTypes"][0]["durationMinutes"] = 60
    request["shiftTypes"][0]["quotaCategory"] = "OVERTIME"
    request["workShifts"] = [
        {"workShiftId": 1000, "shiftTypeId": 100, "workDate": "2026-09-14", "startsAt": "2026-09-14T08:00:00+07:00", "endsAt": "2026-09-14T09:00:00+07:00", "requiredStaff": 1},
        {"workShiftId": 1001, "shiftTypeId": 100, "workDate": "2026-09-15", "startsAt": "2026-09-15T08:00:00+07:00", "endsAt": "2026-09-15T09:00:00+07:00", "requiredStaff": 1},
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "INFEASIBLE"
    assert any(
        item["code"] == "QUOTA_EXCEEDED"
        and item["employeeId"] == 1
        and item["workShiftId"] == 1001
        for item in body["diagnostics"]
    )
