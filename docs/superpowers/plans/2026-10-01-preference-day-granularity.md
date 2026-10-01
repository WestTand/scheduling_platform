# Nguyện vọng làm việc theo ngày — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `/employee/preferences` collect and display a real day instead of a month, collapse the four preference-type options to the two that actually differ, and cap priority at 5 in all three tiers.

**Architecture:** The backend and solver are already day-granular (`Preference.preferenceDate` is a `LocalDate`; the solver matches `shift.work_date == preference.preference_date`). The `month` field is a frontend-only lossy truncation applied at `EmployeePreferencesPage.tsx:139` and reconstructed at `preferenceAdapter.ts:16`. This change removes that truncation layer, narrows the type vocabulary, and adds an upper bound on priority at UI, backend and AI service.

**Tech Stack:** React 19 + TypeScript + Vite, Vitest, Java 17 / Spring Boot / Jakarta Validation, Python 3 / Pydantic / pytest.

**Design doc:** `docs/superpowers/specs/2026-10-01-preference-day-granularity-design.md`

---

## Important constraints

- `WorkPreference.preferenceDate` is currently OPTIONAL (`types/management.ts`). It must become required, because after this change the table, filter and detail dialog all read it and there is no `month` fallback. Every construction site must set it: `preferenceAdapter`, `preferenceService` mock branches, and `mocks/managementData.ts`.
- `preferenceTypes` is exported from `preferenceService.ts:198` and `preferenceLabels` is a local map in `EmployeePreferencesPage.tsx:20`. Both list four labels and both must shrink to two.
- `preferenceService.filterPreferences` is shared by `list` (manager path) and `listMine` (employee path). The manager path currently passes `'ALL'` for month. The new signature must not break the manager page, which passes `month` to `preferenceService.list` from `WorkPreferencesPage.tsx`.
- Do NOT change `PreferenceType` for `AVOID_SHIFT`/`PREFER_SHIFT` handling anywhere else until the type is actually removed — do it in Task 2, and the compiler will find every usage.
- Existing rows store day 1 for former month-based entries. They must continue to render as day 1. Do not add any migration or backfill.

---

### Task 1: Frontend types and adapter

**Files:**
- Modify: `frontend/src/types/management.ts`
- Modify: `frontend/src/adapters/preferenceAdapter.ts`
- Test: `frontend/src/adapters/preferenceAdapter.test.ts`

- [ ] **Step 1: Write the failing test**

In `frontend/src/adapters/preferenceAdapter.test.ts`, replace the two `month` assertions and the four-value type test.

Change the first test's expectation block from `month: '2026-09',` to `preferenceDate: '2026-09-14',`.

Change line 58 in the second test from `expect(adapted.month).toBe('2026-09')` to remove it (it is already asserted on the next line as the full date, so the assertion is redundant).

Replace the type-mapping test with:

```ts
  test('maps frontend preference types to backend types', () => {
    expect(toBackendPreferenceType('PREFERRED')).toBe('WANT')
    expect(toBackendPreferenceType('AVOID')).toBe('AVOID')
  })
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend; npm.cmd test -- preferenceAdapter.test.ts`
Expected: FAIL — the adapter still sets `month`, so `toMatchObject` with `preferenceDate` alone may pass; the real failure will surface in Task 2. Confirm the suite is green or note the exact failure before continuing. The genuine red signal for this task is the type-level break in Step 3, verified in Task 2.

- [ ] **Step 3: Remove `month` from the work preference type**

In `frontend/src/types/management.ts`, delete this line from `interface WorkPreference`:

```ts
  month: string
```

and change:

```ts
  preferenceDate?: string
```

to:

```ts
  preferenceDate: string
```

- [ ] **Step 4: Stop truncating the date in the adapter**

In `frontend/src/adapters/preferenceAdapter.ts`, delete line 16:

```ts
  month: response.preferenceDate.slice(0, 7),
```

and change the type mapper to a direct comparison:

```ts
export const toBackendPreferenceType = (type: PreferenceType): BackendPreferenceType =>
  type === 'AVOID' ? 'AVOID' : 'WANT'
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd frontend; npm.cmd test -- preferenceAdapter.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types/management.ts frontend/src/adapters/preferenceAdapter.ts frontend/src/adapters/preferenceAdapter.test.ts
git commit -m "refactor(frontend): carry the preference date instead of a month"
```

---

### Task 2: Narrow the preference type vocabulary to two values

**Files:**
- Modify: `frontend/src/types/management.ts`
- Modify: `frontend/src/pages/employee/EmployeePreferencesPage.tsx`
- Modify: `frontend/src/services/preferenceService.ts`
- Modify: `frontend/src/mocks/managementData.ts`
- Test: `frontend/src/adapters/preferenceAdapter.test.ts`

- [ ] **Step 1: Write the failing test**

In `frontend/src/adapters/preferenceAdapter.test.ts`, add:

```ts
  test('no longer offers the duplicated shift preference types', async () => {
    const { PreferenceType } = await import('@/types/management')
    expect(Object.keys(PreferenceType)).toEqual(['PREFERRED', 'AVOID'])
  })
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend; npm.cmd test -- preferenceAdapter.test.ts`
Expected: FAIL — `Object.keys(PreferenceType)` still contains four entries.

- [ ] **Step 3: Remove the two duplicated type values**

In `frontend/src/types/management.ts`, replace:

```ts
export const PreferenceType = {
  PREFERRED: 'PREFERRED',
  AVOID: 'AVOID',
  PREFER_SHIFT: 'PREFER_SHIFT',
  AVOID_SHIFT: 'AVOID_SHIFT',
} as const
```

with:

```ts
export const PreferenceType = {
  PREFERRED: 'PREFERRED',
  AVOID: 'AVOID',
} as const
```

- [ ] **Step 4: Update the two label lists**

In `frontend/src/services/preferenceService.ts`, replace the exported `preferenceTypes` with:

```ts
export const preferenceTypes: Array<{ value: PreferenceType; label: string }> = [
  { value: 'PREFERRED', label: 'Muốn làm' },
  { value: 'AVOID', label: 'Không muốn làm' },
]
```

In `frontend/src/pages/employee/EmployeePreferencesPage.tsx`, replace `preferenceLabels` with:

```ts
const preferenceLabels: Record<string, string> = {
  PREFERRED: 'Muốn làm',
  AVOID: 'Không muốn làm',
}
```

- [ ] **Step 5: Update the dropdown**

In `frontend/src/pages/employee/EmployeePreferencesPage.tsx`, replace the four `<option>` elements in the "Loại nguyện vọng" select with:

```tsx
              <option value="PREFERRED">Muốn làm</option>
              <option value="AVOID">Không muốn làm</option>
```

- [ ] **Step 6: Update the mock seed**

In `frontend/src/mocks/managementData.ts`, in `preferenceSeed`: remove the `month` property from every entry, add a `preferenceDate` in `YYYY-MM-DD` form, and change the `type` of any entry using `PREFER_SHIFT` or `AVOID_SHIFT` to `PREFERRED` or `AVOID` respectively. Also change any `status: 'REVIEWED'` to `'APPROVED'` so the seed does not carry a status the backend vocabulary rejects. Read the file first and preserve the existing ids, notes and priorities.

- [ ] **Step 7: Run the build to find every remaining `month` usage**

Run: `cd frontend; npm.cmd run build`
Expected: FAIL with TypeScript errors naming each remaining use of `.month` or a missing `preferenceDate`. This is how the compiler enumerates the work left. Note each file and line.

- [ ] **Step 8: Fix the remaining usages found in Step 7**

In `frontend/src/services/preferenceService.ts`:
- `filterPreferences(items, search, month, type, status)` → change the `month` parameter to `from` and add `to`, and replace the month comparison with a date-range comparison:

```ts
const filterPreferences = (items: WorkPreference[], search: string, from: string, to: string, type: string, status: string) => {
  const normalizedSearch = search.trim().toLowerCase()
  return items.filter((preference) => {
    const matchesSearch =
      !normalizedSearch ||
      [preference.employeeName, preference.note ?? '', preference.targetLabel ?? '', preference.relatedShiftCode ?? ''].some((value) =>
        value.toLowerCase().includes(normalizedSearch),
      )
    const matchesFrom = !from || preference.preferenceDate >= from
    const matchesTo = !to || preference.preferenceDate <= to
    const matchesType = type === 'ALL' || preference.type === type
    const matchesStatus = status === 'ALL' || preference.status === status

    return matchesSearch && matchesFrom && matchesTo && matchesType && matchesStatus
  })
}
```

Compare as ISO strings — that is correct for `YYYY-MM-DD` and avoids timezone bugs.
- `list(...)` signature: replace `month = 'ALL'` with `from = ''` and `to = ''`, pass both into `filterPreferences`, and replace the mock branch's `matchesMonth` with the same two range comparisons.
- `listMine(...)` signature: same replacement; pass `from`/`to` through to `filterPreferences` and to `this.list`.
- `createMine` mock branch: replace `month: input.preferenceDate.slice(0, 7),` with `preferenceDate: input.preferenceDate,`.
- `updateMine` mock branch: replace `preference.month = input.preferenceDate.slice(0, 7)` with `preference.preferenceDate = input.preferenceDate`.

In `frontend/src/pages/manager/WorkPreferencesPage.tsx`, replace any `month` passed to `preferenceService.list` with `from: '', to: ''` (read the file and match its actual call shape).

- [ ] **Step 9: Run build and tests to verify they pass**

Run: `cd frontend; npm.cmd run build`
Expected: exit 0.
Run: `cd frontend; npm.cmd test`
Expected: all pass.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/types/management.ts frontend/src/pages/employee/EmployeePreferencesPage.tsx frontend/src/pages/manager/WorkPreferencesPage.tsx frontend/src/services/preferenceService.ts frontend/src/mocks/managementData.ts frontend/src/adapters/preferenceAdapter.test.ts
git commit -m "refactor(frontend): drop the duplicated shift preference types"
```

---

### Task 3: Collect and display a real day

**Files:**
- Create: `frontend/src/pages/employee/EmployeePreferencesPage.helpers.ts`
- Create: `frontend/src/pages/employee/EmployeePreferencesPage.helpers.test.ts`
- Modify: `frontend/src/pages/employee/EmployeePreferencesPage.tsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/pages/employee/EmployeePreferencesPage.helpers.ts` later; first create `frontend/src/pages/employee/EmployeePreferencesPage.helpers.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import {
  getPreferenceSubmitError,
  isPreferenceDateValid,
  isPriorityValid,
  normalizePriority,
  todayIsoDate,
} from './EmployeePreferencesPage.helpers'

describe('EmployeePreferencesPage helpers', () => {
  it('accepts a well formed ISO date', () => {
    expect(isPreferenceDateValid('2026-10-15')).toBe(true)
  })

  it('rejects a blank date and a seven character month', () => {
    expect(isPreferenceDateValid('')).toBe(false)
    expect(isPreferenceDateValid('2026-10')).toBe(false)
  })

  it('accepts priority between one and five inclusive', () => {
    expect(isPriorityValid(1)).toBe(true)
    expect(isPriorityValid(5)).toBe(true)
  })

  it('rejects priority outside one to five and non integers', () => {
    expect(isPriorityValid(0)).toBe(false)
    expect(isPriorityValid(6)).toBe(false)
    expect(isPriorityValid(1.5)).toBe(false)
    expect(isPriorityValid(Number.NaN)).toBe(false)
  })

  it('normalizes a priority string to a number or null', () => {
    expect(normalizePriority('3')).toBe(3)
    expect(normalizePriority('abc')).toBeNull()
    expect(normalizePriority('')).toBeNull()
  })

  it('returns today as an ISO date', () => {
    expect(todayIsoDate()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('reports a missing date before anything else', () => {
    expect(getPreferenceSubmitError({ preferenceDate: '', priority: 1, note: 'x' }))
      .toBe('Vui lòng chọn ngày áp dụng cho nguyện vọng.')
  })

  it('reports a missing note', () => {
    expect(getPreferenceSubmitError({ preferenceDate: '2026-10-15', priority: 1, note: '   ' }))
      .toBe('Nội dung ghi chú không được bỏ trống.')
  })

  it('reports an out of range priority', () => {
    expect(getPreferenceSubmitError({ preferenceDate: '2026-10-15', priority: 6, note: 'x' }))
      .toBe('Mức ưu tiên phải từ 1 đến 5.')
  })

  it('returns an empty string when the form is valid', () => {
    expect(getPreferenceSubmitError({ preferenceDate: '2026-10-15', priority: 3, note: 'x' })).toBe('')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend; npm.cmd test -- EmployeePreferencesPage.helpers.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Create the helpers**

Create `frontend/src/pages/employee/EmployeePreferencesPage.helpers.ts`:

```ts
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

export const todayIsoDate = () => new Date().toISOString().slice(0, 10)

export const isPreferenceDateValid = (value: string) => ISO_DATE_PATTERN.test(value)

export const isPriorityValid = (value: number) => Number.isInteger(value) && value >= 1 && value <= 5

export const normalizePriority = (value: string) => {
  const parsed = Number(value)
  return Number.isNaN(parsed) ? null : parsed
}

export const getPreferenceSubmitError = (form: { preferenceDate: string; priority: number; note: string }) => {
  if (!isPreferenceDateValid(form.preferenceDate)) {
    return 'Vui lòng chọn ngày áp dụng cho nguyện vọng.'
  }

  if (!form.note.trim()) {
    return 'Nội dung ghi chú không được bỏ trống.'
  }

  if (!isPriorityValid(form.priority)) {
    return 'Mức ưu tiên phải từ 1 đến 5.'
  }

  return ''
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd frontend; npm.cmd test -- EmployeePreferencesPage.helpers.test.ts`
Expected: PASS.

- [ ] **Step 5: Switch the form state to a date**

In `frontend/src/pages/employee/EmployeePreferencesPage.tsx`:
- change the form state initialiser to:

```tsx
  const [form, setForm] = useState({
    preferenceDate: todayIsoDate(),
    type: 'PREFERRED',
    relatedShiftCode: 'THONG_TAM',
    priority: '1',
    note: '',
  })
```

- in `handleEdit`, replace `month: item.month,` with `preferenceDate: item.preferenceDate,`
- in `handleSubmit`, replace the note check and the truncation with:

```tsx
    const priority = normalizePriority(form.priority) ?? Number.NaN
    const submitError = getPreferenceSubmitError({ preferenceDate: form.preferenceDate, priority, note: form.note })

    if (submitError) {
      showDialog({ variant: 'warning', description: submitError })
      return
    }

    try {
      const input = {
        preferenceDate: form.preferenceDate,
        type: form.type as PreferenceType,
        shiftTypeId: resolveShiftTypeId(form.relatedShiftCode),
        // Kept for mock-mode display (relatedShiftCode/targetLabel); backend branch forwards it as shiftCode.
        relatedShiftCode: form.relatedShiftCode,
        priority,
        note: form.note.trim(),
      }
```

- in the post-submit reset, replace the reset object with the same initialiser shape using `todayIsoDate()`.
- import `getPreferenceSubmitError`, `isPreferenceDateValid` (if used in the input's `required` hint), `normalizePriority`, `todayIsoDate` from `./EmployeePreferencesPage.helpers`. Only import what you actually use.

- [ ] **Step 6: Change the date input and its label**

Replace the "Khoảng thời gian áp dụng" field with:

```tsx
            <label className="text-sm text-slate-700">Ngày áp dụng</label>
            <input type="date" value={form.preferenceDate} onChange={(event) => setForm((current) => ({ ...current, preferenceDate: event.target.value }))} className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
```

- [ ] **Step 7: Replace the month filter with a date range filter**

Replace the `monthFilter` state with:

```tsx
  const [fromFilter, setFromFilter] = useState('')
  const [toFilter, setToFilter] = useState('')
```

Replace the filtering block with:

```tsx
  const filtered = useMemo(() => preferences.filter((item) => {
    const normalizedSearch = search.trim().toLowerCase()
    const matchesSearch =
      !normalizedSearch ||
      [item.note ?? '', item.relatedShiftCode ?? '', item.targetLabel ?? '', item.employeeName ?? ''].some((value) =>
        value.toLowerCase().includes(normalizedSearch),
      )
    const matchesFrom = !fromFilter || item.preferenceDate >= fromFilter
    const matchesTo = !toFilter || item.preferenceDate <= toFilter
    const matchesStatus = statusFilter === 'ALL' || item.status === statusFilter
    return matchesSearch && matchesFrom && matchesTo && matchesStatus
  }), [preferences, search, fromFilter, toFilter, statusFilter])
```

Replace the month `<select>` with two date inputs, adjusting the grid class to `md:grid-cols-[1fr_0.8fr_0.8fr_0.8fr]`:

```tsx
            <input type="date" value={fromFilter} onChange={(event) => setFromFilter(event.target.value)} aria-label="Từ ngày" className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm" />
            <input type="date" value={toFilter} onChange={(event) => setToFilter(event.target.value)} aria-label="Đến ngày" className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm" />
```

- [ ] **Step 8: Update the table column and the detail dialog**

Replace the month column with:

```tsx
                { key: 'preferenceDate', label: 'Ngày', render: (item: WorkPreference) => <span>{item.preferenceDate}</span> },
```

Replace the detail row with:

```tsx
            <div className="flex justify-between"><span>Ngày áp dụng</span><strong>{selected.preferenceDate}</strong></div>
```

Update the page description from `"Gửi và quản lý nguyện vọng làm việc theo tháng."` to `"Gửi và quản lý nguyện vọng làm việc theo ngày."`

- [ ] **Step 9: Run tests, build and lint**

Run: `cd frontend; npm.cmd test`
Expected: all pass.
Run: `cd frontend; npm.cmd run build`
Expected: exit 0.
Run: `cd frontend; npm.cmd run lint`
Expected: `0 errors`; the pre-existing `AuthContext.tsx` `react-refresh` warning is expected and must not be "fixed".

- [ ] **Step 10: Commit**

```bash
git add frontend/src/pages/employee/EmployeePreferencesPage.helpers.ts frontend/src/pages/employee/EmployeePreferencesPage.helpers.test.ts frontend/src/pages/employee/EmployeePreferencesPage.tsx
git commit -m "feat(frontend): collect work preferences by day instead of month"
```

---

### Task 4: Cap priority at 5 in the backend

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/employee/dto/CreatePreferenceRequest.java`
- Modify: `backend/src/main/java/scheduling/scheduling/employee/dto/UpdatePreferenceRequest.java`
- Test: `backend/src/test/java/scheduling/scheduling/employee/EmployeePreferenceControllerTest.java`

- [ ] **Step 1: Write the failing test**

In `backend/src/test/java/scheduling/scheduling/employee/EmployeePreferenceControllerTest.java`, read the existing `createPreferenceRejectsInvalidBody` test and follow its exact style. Add:

```java
    @Test
    void createPreferenceRejectsPriorityAboveFive() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferenceDate":"2026-09-16","preferenceType":"WANT","priority":6,"note":"note"}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeePreferenceService);
    }

    @Test
    void createPreferenceRejectsPriorityBelowOne() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferenceDate":"2026-09-16","preferenceType":"WANT","priority":0,"note":"note"}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeePreferenceService);
    }
```

Adapt the endpoint path, the token helper name, the service field name and the import list to whatever the existing tests in that file actually use. If the existing tests exercise `PUT` too, add the same two cases for the update endpoint.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend; ./mvnw.cmd -q test "-Dtest=EmployeePreferenceControllerTest"`
Expected: FAIL — priority 6 is currently accepted, so the mock service is called and the response is not 400.

- [ ] **Step 3: Add the upper bound**

In both `CreatePreferenceRequest.java` and `UpdatePreferenceRequest.java`, change:

```java
        @NotNull @Min(1) Integer priority,
```

to:

```java
        @NotNull @Min(1) @Max(5) Integer priority,
```

and add the import, keeping the existing import block alphabetical:

```java
import jakarta.validation.constraints.Max;
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend; ./mvnw.cmd -q test "-Dtest=EmployeePreferenceControllerTest"`
Expected: PASS.

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend; ./mvnw.cmd -q test`
Expected: exit 0. If any existing test uses a priority above 5, lower it to a value in range rather than weakening the new bound.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/employee/dto/CreatePreferenceRequest.java backend/src/main/java/scheduling/scheduling/employee/dto/UpdatePreferenceRequest.java backend/src/test/java/scheduling/scheduling/employee/EmployeePreferenceControllerTest.java
git commit -m "fix(backend): cap preference priority at five"
```

---

### Task 5: Cap priority at 5 in the AI service

**Files:**
- Modify: `ai-service/app/schemas/scheduling.py`
- Test: `ai-service/tests/test_scheduling.py`

- [ ] **Step 1: Write the failing test**

Read `ai-service/tests/test_scheduling.py` and find how the existing request-validation test for an unknown reference is written (around line 189). Follow its style. Add:

```python
def test_preference_priority_above_five_is_rejected() -> None:
    request = base_request()
    request["preferences"] = [
        {
            "preferenceId": 70,
            "employeeId": 1,
            "shiftTypeId": 100,
            "preferenceDate": "2026-09-14",
            "preferenceType": "WANT",
            "priority": 6,
        }
    ]

    with pytest.raises(ValidationError):
        SchedulingRequest.model_validate(request)


def test_preference_priority_at_five_is_accepted() -> None:
    request = base_request()
    request["preferences"] = [
        {
            "preferenceId": 70,
            "employeeId": 1,
            "shiftTypeId": 100,
            "preferenceDate": "2026-09-14",
            "preferenceType": "WANT",
            "priority": 5,
        }
    ]

    validated = SchedulingRequest.model_validate(request)

    assert validated.preferences[0].priority == 5
```

Use whatever validation-error type and import that file already uses; if it asserts via the API client rather than the model, match that instead.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd ai-service; .\.venv\Scripts\pytest.exe -q tests/test_scheduling.py -k priority`
Expected: FAIL — priority 6 currently validates.

- [ ] **Step 3: Add the upper bound**

In `ai-service/app/schemas/scheduling.py`, change line 89:

```python
    priority: int = Field(default=1, ge=1)
```

to:

```python
    priority: int = Field(default=1, ge=1, le=5)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd ai-service; .\.venv\Scripts\pytest.exe -q tests/test_scheduling.py`
Expected: PASS.

- [ ] **Step 5: Run the full AI suite**

Run: `cd ai-service; .\.venv\Scripts\pytest.exe -q`
Expected: unchanged pass count plus the two new tests.

- [ ] **Step 6: Commit**

```bash
git add ai-service/app/schemas/scheduling.py ai-service/tests/test_scheduling.py
git commit -m "fix(ai-service): cap preference priority at five"
```

---

### Task 6: Full verification

**Files:** none.

- [ ] **Step 1: Frontend**

Run: `cd frontend; npm.cmd test`
Expected: all pass.
Run: `cd frontend; npm.cmd run build`
Expected: exit 0.
Run: `cd frontend; npm.cmd run lint`
Expected: `0 errors`, at most the pre-existing `AuthContext.tsx` warning.

- [ ] **Step 2: Backend**

Run: `cd backend; ./mvnw.cmd -q test`
Expected: exit 0.

- [ ] **Step 3: AI service**

Run: `cd ai-service; .\.venv\Scripts\pytest.exe -q`
Expected: all pass.

- [ ] **Step 4: Manual walkthrough**

1. As an employee, open `/employee/preferences`.
2. Confirm the page description no longer says "theo tháng".
3. Open the form: the date field is a day picker, "Loại nguyện vọng" has exactly two options, and the priority box is limited to 1-5.
4. Confirm the submit is blocked with a Vietnamese message when the date is blank or the priority is 6.
5. Submit with a real day, e.g. `2026-10-15`. Confirm the list column and the detail dialog both show `2026-10-15`, not a month.
6. Filter by a date range and confirm the rows narrow.
7. Call the backend directly with `priority: 6` and confirm a 400.

- [ ] **Step 5: Report the known limitations**

After the walkthrough, restate for the user that existing rows created before this change still carry day 1 and are not backfilled, and that `PENDING` preferences still influence the solver. Both are documented in the design doc and were consciously left out of scope.

---

## Self-review notes

- Spec coverage: section 2.1 is Tasks 1-3, section 2.2 is Task 2, section 2.3 is Tasks 3-5, and the testing section maps to the test steps in each task. The known limitations section is Task 6 Step 5.
- No placeholders: every step names exact files, exact lines and exact commands with expected output.
- Type consistency: `preferenceDate` becomes required in Task 1 and every construction site is fixed in Task 2 Step 8; the helpers in Task 3 use `preferenceDate` (string) and `priority` (number), matching what Task 3 Step 5 passes in.
- Ordering: Task 1 must precede Task 2 because removing `month` from the type is what makes the compiler enumerate the remaining usages. Task 3 must follow Task 2 because the form must not reference the removed type values.
