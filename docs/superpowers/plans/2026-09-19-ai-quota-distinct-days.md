# AI Quota Distinct Days Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change AI-service quota enforcement so `OVERTIME` and `BATHING` quotas count distinct work dates per employee per ISO week instead of work-shift rows.

**Architecture:** Keep the schema and field names unchanged (`schemaVersion = 1.0`, `overtimeShiftsPerWeek`, `bathingShiftsPerWeek`). Update only the solver quota block to create one Boolean per employee/category/week/date and enforce limits on those date Booleans plus deduplicated boundary dates. Update AI contract/project docs to describe day-count semantics without renaming backend API fields.

**Tech Stack:** Python 3.13, FastAPI TestClient, OR-Tools CP-SAT, pytest, Markdown docs.

**Spec:** `docs/superpowers/specs/2026-09-19-ai-quota-distinct-days-design.md`

**Working directory:** All commands run from repo root (`scheduling_platform/`) unless a step says `ai-service/`. Use `ai-service\.venv\Scripts\pytest.exe` for pytest on Windows.

---

## File Map

- Modify: `ai-service/app/solver/scheduler.py` — change quota enforcement from shift-row count to distinct-date count.
- Modify: `ai-service/tests/test_scheduling.py` — add quota semantics tests.
- Modify: `docs/backend-ai-scheduling-contract.md` — clarify legacy field names now mean distinct work dates.
- Modify: `docs/project_plan.md` — add a concise completion note for Phase 6 quota semantics after implementation.
- Modify: `docs/superpowers/specs/2026-09-11-admin-scheduling-rule-apis-design.md` — clarify scheduling-rule descriptions without renaming API fields.

---

### Task 1: AI Solver Distinct-Day Quota Semantics

**Files:**
- Modify: `ai-service/tests/test_scheduling.py`
- Modify: `ai-service/app/solver/scheduler.py`

- [ ] **Step 1: Add failing pytest coverage for same-date overtime quota**

In `ai-service/tests/test_scheduling.py`, add this test after `test_weekly_special_shift_quota_is_enforced`:

```python
def test_overtime_quota_counts_same_date_once() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["rules"]["hardRules"]["minRestHoursBetweenShifts"] = 0
    request["shiftTypes"][0]["durationMinutes"] = 60
    request["shiftTypes"][0]["quotaCategory"] = "OVERTIME"
    request["workShifts"] = [
        {
            "workShiftId": 1000,
            "shiftTypeId": 100,
            "workDate": "2026-09-14",
            "startsAt": "2026-09-14T08:00:00+07:00",
            "endsAt": "2026-09-14T09:00:00+07:00",
            "requiredStaff": 1,
        },
        {
            "workShiftId": 1001,
            "shiftTypeId": 100,
            "workDate": "2026-09-14",
            "startsAt": "2026-09-14T10:00:00+07:00",
            "endsAt": "2026-09-14T11:00:00+07:00",
            "requiredStaff": 1,
        },
    ]
    request["lockedAssignments"] = [
        {"assignmentId": 50, "employeeId": 1, "workShiftId": 1000},
        {"assignmentId": 51, "employeeId": 1, "workShiftId": 1001},
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    assert sorted(response.json()["assignments"], key=lambda item: item["workShiftId"]) == [
        {"employeeId": 1, "workShiftId": 1000},
        {"employeeId": 1, "workShiftId": 1001},
    ]
```

Run from `ai-service/`:

```powershell
.\.venv\Scripts\pytest.exe tests/test_scheduling.py::test_overtime_quota_counts_same_date_once -q
```

Expected: FAIL with `status` `INFEASIBLE` or assignment assertion failure because the current solver counts two work-shift rows against limit `1`.

- [ ] **Step 2: Add different-date overtime quota coverage**

Add this test after the same-date test:

```python
def test_overtime_quota_counts_different_dates_separately() -> None:
    request = base_request()
    request["period"]["endDate"] = "2026-09-15"
    request["shiftTypes"][0]["durationMinutes"] = 60
    request["shiftTypes"][0]["quotaCategory"] = "OVERTIME"
    request["workShifts"] = [
        {
            "workShiftId": 1000,
            "shiftTypeId": 100,
            "workDate": "2026-09-14",
            "startsAt": "2026-09-14T08:00:00+07:00",
            "endsAt": "2026-09-14T09:00:00+07:00",
            "requiredStaff": 1,
        },
        {
            "workShiftId": 1001,
            "shiftTypeId": 100,
            "workDate": "2026-09-15",
            "startsAt": "2026-09-15T08:00:00+07:00",
            "endsAt": "2026-09-15T09:00:00+07:00",
            "requiredStaff": 1,
        },
    ]
    request["lockedAssignments"] = [
        {"assignmentId": 50, "employeeId": 1, "workShiftId": 1000},
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    assert sorted(response.json()["assignments"], key=lambda item: item["workShiftId"]) == [
        {"employeeId": 1, "workShiftId": 1000},
        {"employeeId": 2, "workShiftId": 1001},
    ]
```

Run from `ai-service/`:

```powershell
.\.venv\Scripts\pytest.exe tests/test_scheduling.py::test_overtime_quota_counts_different_dates_separately -q
```

Expected: PASS before and after the solver change. This locks the unchanged behavior that different work dates count separately and force the second date to another employee when employee `1` has already consumed the weekly overtime date quota.

- [ ] **Step 3: Add bathing two-day limit and boundary consumption coverage**

Add these tests after the overtime tests:

```python
def test_bathing_quota_allows_two_distinct_dates_not_three() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["period"]["endDate"] = "2026-09-16"
    request["shiftTypes"][0]["durationMinutes"] = 60
    request["shiftTypes"][0]["quotaCategory"] = "BATHING"
    request["workShifts"] = [
        {
            "workShiftId": 1000,
            "shiftTypeId": 100,
            "workDate": "2026-09-14",
            "startsAt": "2026-09-14T08:00:00+07:00",
            "endsAt": "2026-09-14T09:00:00+07:00",
            "requiredStaff": 1,
        },
        {
            "workShiftId": 1001,
            "shiftTypeId": 100,
            "workDate": "2026-09-15",
            "startsAt": "2026-09-15T08:00:00+07:00",
            "endsAt": "2026-09-15T09:00:00+07:00",
            "requiredStaff": 1,
        },
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    assert response.json()["status"] == "SUCCESS"

    request["workShifts"].append(
        {
            "workShiftId": 1002,
            "shiftTypeId": 100,
            "workDate": "2026-09-16",
            "startsAt": "2026-09-16T08:00:00+07:00",
            "endsAt": "2026-09-16T09:00:00+07:00",
            "requiredStaff": 1,
        }
    )

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    assert response.json()["status"] == "INFEASIBLE"


def test_boundary_assignment_consumes_distinct_quota_date() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["period"]["endDate"] = "2026-09-15"
    request["shiftTypes"][0]["durationMinutes"] = 60
    request["shiftTypes"][0]["quotaCategory"] = "OVERTIME"
    request["workShifts"] = [
        {
            "workShiftId": 1000,
            "shiftTypeId": 100,
            "workDate": "2026-09-15",
            "startsAt": "2026-09-15T08:00:00+07:00",
            "endsAt": "2026-09-15T09:00:00+07:00",
            "requiredStaff": 1,
        }
    ]
    request["boundaryAssignments"] = [
        {
            "employeeId": 1,
            "shiftTypeId": 100,
            "startsAt": "2026-09-14T08:00:00+07:00",
            "endsAt": "2026-09-14T09:00:00+07:00",
        }
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    assert response.json()["status"] == "INFEASIBLE"
```

Run from `ai-service/`:

```powershell
.\.venv\Scripts\pytest.exe tests/test_scheduling.py::test_bathing_quota_allows_two_distinct_dates_not_three tests/test_scheduling.py::test_boundary_assignment_consumes_distinct_quota_date -q
```

Expected: PASS before and after the solver change. These tests lock existing different-date and boundary behavior while Step 1 drives the same-date distinct-day change.

- [ ] **Step 4: Implement distinct-date quota constraints**

In `ai-service/app/solver/scheduler.py`, replace the current `quota_limits` block that starts with `quota_limits = {` and ends at the `model.add(... <= limit)` quota constraint with this implementation:

```python
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

                assigned_dates = []
                for work_date, dated_shifts in shifts_by_quota_date.items():
                    date_assigned = model.new_bool_var(
                        f"{category.lower()}_{employee_id}_{work_date.isoformat()}_assigned"
                    )
                    model.add_max_equality(
                        date_assigned,
                        [assigned[employee_id, shift.work_shift_id] for shift in dated_shifts],
                    )
                    assigned_dates.append(date_assigned)

                boundary_dates = {
                    boundary.starts_at.date()
                    for boundary in request.boundary_assignments
                    if boundary.employee_id == employee_id
                    and _week_key(boundary.starts_at.date()) == week
                    and shift_type_by_id[boundary.shift_type_id].quota_category == category
                }
                model.add(sum(assigned_dates) + len(boundary_dates) <= limit)
```

Notes:
- Keep the existing `defaultdict` import.
- Do not change `ON_CALL` logic.
- Do not change schema models or API field names.

- [ ] **Step 5: Run task tests**

Run from `ai-service/`:

```powershell
.\.venv\Scripts\pytest.exe tests/test_scheduling.py -q
```

Expected: PASS. Existing `test_weekly_special_shift_quota_is_enforced` continues to pass because two different dates still count as two distinct quota dates.

- [ ] **Step 6: Commit**

```bash
git status --short
git diff -- ai-service/app/solver/scheduler.py ai-service/tests/test_scheduling.py
git add ai-service/app/solver/scheduler.py ai-service/tests/test_scheduling.py
git commit -m "fix: count AI quotas by distinct days"
```

---

### Task 2: Contract And Roadmap Documentation

**Files:**
- Modify: `docs/backend-ai-scheduling-contract.md`
- Modify: `docs/project_plan.md`
- Modify: `docs/superpowers/specs/2026-09-11-admin-scheduling-rule-apis-design.md`

- [ ] **Step 1: Update AI contract quota semantics**

In `docs/backend-ai-scheduling-contract.md`, update the `Important` bullet near the `shiftTypes` section that currently says current solver counts work shifts. Replace it with:

```markdown
- AI quota semantics: `OVERTIME` and `BATHING` quotas are counted by distinct `workDate` per employee per ISO week. Multiple same-category work shifts on the same `workDate` count once. Boundary assignments count by `startsAt.date()`. The field names `overtimeShiftsPerWeek` and `bathingShiftsPerWeek` are kept for `schemaVersion = "1.0"` compatibility even though their semantics are day-based.
```

In the `Quotas:` section, replace the two quota bullets with:

```markdown
- `overtimeShiftsPerWeek`: max distinct `OVERTIME` work dates per employee per ISO week. Legacy field name kept for schema `1.0` compatibility.
- `bathingShiftsPerWeek`: max distinct `BATHING` work dates per employee per ISO week. Legacy field name kept for schema `1.0` compatibility.
```

Replace the exact open follow-up line at `docs/backend-ai-scheduling-contract.md:661`:

```markdown
- Decide whether quota should count work shifts or distinct days. Project wording says day/week for overtime and bathing. Current CP-SAT PR counts work shifts/week.
```

with:

```markdown
- Quota count ambiguity resolved: `OVERTIME` and `BATHING` use distinct work-date counts per employee per ISO week while retaining legacy schema `1.0` field names.
```

- [ ] **Step 2: Update scheduling-rule design docs without renaming fields**

In `docs/superpowers/specs/2026-09-11-admin-scheduling-rule-apis-design.md`, replace these table purposes:

```markdown
| `OVERTIME_SHIFTS_PER_WEEK` | `HARD` | `quotas.overtimeShiftsPerWeek` | Maximum overtime/special shifts per employee per week. |
| `BATHING_SHIFTS_PER_WEEK` | `HARD` | `quotas.bathingShiftsPerWeek` | Maximum bathing/special shifts per employee per week. |
```

with:

```markdown
| `OVERTIME_SHIFTS_PER_WEEK` | `HARD` | `quotas.overtimeShiftsPerWeek` | Maximum distinct overtime work dates per employee per ISO week. Field name is kept for compatibility. |
| `BATHING_SHIFTS_PER_WEEK` | `HARD` | `quotas.bathingShiftsPerWeek` | Maximum distinct bathing/thong-tam work dates per employee per ISO week. Field name is kept for compatibility. |
```

- [ ] **Step 3: Update project plan Phase 6 note**

In `docs/project_plan.md` under the Phase 6 quota bullets, keep the existing tasks unchecked but add this note directly after the `thong tam 2 ngay/tuan` explanation:

```markdown
  - Quota semantics slice: AI counts `OVERTIME` and `BATHING` by distinct work dates per employee per ISO week while keeping legacy schema field names.
```

Do not mark unrelated backend AI integration tasks complete.

- [ ] **Step 4: Verify docs and AI tests**

Run from `ai-service/`:

```powershell
.\.venv\Scripts\pytest.exe -q
```

Expected: PASS.

Run from repo root:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` has no output. `git status --short` shows only intentional docs changes plus the known unrelated untracked external docs if still present:

```text
?? docs/quy_dinh_ca_lam_viec.md
?? "docs/Đặc tả_Phát.txt"
```

- [ ] **Step 5: Commit docs**

```bash
git status --short
git diff -- docs/backend-ai-scheduling-contract.md docs/project_plan.md docs/superpowers/specs/2026-09-11-admin-scheduling-rule-apis-design.md
git add docs/backend-ai-scheduling-contract.md docs/project_plan.md docs/superpowers/specs/2026-09-11-admin-scheduling-rule-apis-design.md
git commit -m "docs: clarify AI quota day semantics"
```

---

## Spec Coverage

- Keep field names and `schemaVersion = 1.0`: Task 1 keeps schema models unchanged; Task 2 documents compatibility.
- Count `OVERTIME` and `BATHING` by distinct `workDate` per employee per ISO week: Task 1 implementation and tests.
- Same-date category shifts count once, different dates count separately: Task 1 tests.
- Boundary assignments count by `startsAt.date()` and deduplicate dates: Task 1 implementation and boundary test.
- `ON_CALL` unchanged: Task 1 explicitly leaves existing block and reruns existing tests.
- Docs update without frontend/backend integration: Task 2.
- Full AI pytest and `git diff --check`: Task 2 verification.

## Self-Review

- Spec coverage: every in-scope item maps to Task 1 or Task 2.
- Placeholder scan: no TBD/TODO placeholders or vague implementation steps remain.
- Type consistency: Python names use existing Pydantic snake_case fields (`overtime_shifts_per_week`, `bathing_shifts_per_week`) while docs preserve JSON camelCase names (`overtimeShiftsPerWeek`, `bathingShiftsPerWeek`).
- Scope check: this remains one AI-service + docs slice and intentionally defers backend AI integration, result persistence, `penaltyWeightMultiplier`, frontend, migrations, and API field renames.
