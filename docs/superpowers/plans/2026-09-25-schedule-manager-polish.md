# Schedule Manager Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make schedule creation/listing, employee schedule rendering, and auth refresh reliable and user-friendly for end-to-end testing.

**Architecture:** Keep backend API changes minimal and compatible with existing DTOs. Use backend tests to pin schedule-generation duplicate handling, date filtering, and CORS; use frontend helper/service tests for UX copy, pagination, date defaults, employee schedule backend mode, and auth refresh behavior. Prefer client-side pagination for current array endpoints.

**Tech Stack:** Spring Boot 4, Java 21/23, JUnit/Mockito/MockMvc, PostgreSQL, React/TypeScript, Vitest, Vite, Tailwind UI components.

---

## File Structure

- Modify `backend/src/main/java/scheduling/scheduling/admin/AdminScheduleGenerationService.java`: allow creation when matching periods are `CANCELLED`; throw readable duplicate errors.
- Modify `backend/src/main/java/scheduling/scheduling/repository/SchedulePeriodRepository.java`: add finder for matching date/version periods if absent.
- Modify `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodController.java`: verify date filter parameter names and validation path.
- Modify `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`: fix date filter validation/query handling if it is causing 500.
- Modify `backend/src/main/java/scheduling/scheduling/admin/dto/AdminSchedulePeriodResponse.java`: include `createdAt`.
- Modify `backend/src/main/java/scheduling/scheduling/auth/SecurityConfig.java`: keep `PATCH` in CORS allowed methods.
- Modify `backend/src/test/java/scheduling/scheduling/admin/AdminScheduleGenerationServiceTest.java`: add duplicate active/cancelled generation tests.
- Modify `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodControllerTest.java`: add valid/invalid date filter tests.
- Modify `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java`: keep PATCH CORS regression.
- Modify `frontend/src/services/apiClient.ts`: ensure refresh retry preserves storage scope and clears auth only on failed refresh.
- Modify `frontend/src/services/apiClient.test.ts`: add refresh storage-scope and retry tests.
- Modify `frontend/src/services/backendScheduleService.ts`: send correct date filter params and adapt `createdAt`.
- Modify `frontend/src/services/backendScheduleService.test.ts`: verify date filter query params and createdAt mapping.
- Modify `frontend/src/pages/manager/SchedulesPage.tsx`: add client-side pagination and created-time display source.
- Modify `frontend/src/pages/manager/SchedulesPage.test.tsx` or create it if missing: add pagination/filter tests.
- Modify `frontend/src/components/schedule/ScheduleGenerationForm.tsx`: default dates to local today/current week and paginate employee pool.
- Modify `frontend/src/components/schedule/ScheduleGenerationForm.test.tsx`: add default-date and employee pagination tests.
- Modify `frontend/src/pages/manager/ScheduleGenerationPage.helpers.ts`: simplify generation notice copy.
- Modify `frontend/src/pages/manager/ScheduleGenerationPage.test.tsx`: assert end-user notice copy.
- Modify `frontend/src/pages/employee/EmployeeSchedulePage.tsx`: fetch backend schedule in backend mode.
- Create or modify `frontend/src/services/employeeScheduleService.ts`: encapsulate employee schedule API calls if no existing service fits.
- Create or modify `frontend/src/pages/employee/EmployeeSchedulePage.test.tsx`: test backend mode and empty state.

---

### Task 1: Backend Schedule Generation Duplicate Rules

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminScheduleGenerationService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/SchedulePeriodRepository.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminScheduleGenerationServiceTest.java`

- [ ] **Step 1: Write failing test for cancelled duplicate period creation**

Add a test to `AdminScheduleGenerationServiceTest` showing that a cancelled period with the same dates/version does not block generation. Use existing test helpers in that file for principal, manager account, employees, shift types, and optimize response. The core assertion should be:

```java
@Test
void generateAllowsSameDateVersionWhenExistingPeriodIsCancelled() {
    CurrentAccountPrincipal principal = managerPrincipal();
    Account manager = account(principal);
    GenerateScheduleRequest request = new GenerateScheduleRequest(
            "Test new period",
            LocalDate.of(2026, 9, 21),
            LocalDate.of(2026, 9, 23),
            1,
            List.of(1L, 2L),
            List.of(1L));
    SchedulePeriod cancelled = SchedulePeriod.builder()
            .periodName("Old cancelled")
            .startDate(request.startDate())
            .endDate(request.endDate())
            .versionNo(1)
            .status("CANCELLED")
            .createdBy(manager)
            .build();

    when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
    when(schedulePeriodRepository.findByStartDateAndEndDateAndVersionNo(
            request.startDate(), request.endDate(), 1)).thenReturn(List.of(cancelled));
    when(employeeRepository.findAllById(request.employeeIds())).thenReturn(List.of(activeEmployee(1L), activeEmployee(2L)));
    when(shiftTypeRepository.findAllById(request.shiftTypeIds())).thenReturn(List.of(activeShiftType(1L)));
    when(schedulePeriodRepository.saveAndFlush(any(SchedulePeriod.class))).thenAnswer(invocation -> {
        SchedulePeriod period = invocation.getArgument(0);
        period.setId(48L);
        return period;
    });
    when(optimizationService.optimize(any(), eq(48L), any())).thenReturn(new OptimizeScheduleResponse(
            90L, 48L, "SUCCESS", "OPTIMAL", BigDecimal.ZERO, 1, List.of(), null, null, null));

    GenerateScheduleResponse response = service.generate(principal, request);

    assertThat(response.periodId()).isEqualTo(48L);
    verify(schedulePeriodRepository).saveAndFlush(any(SchedulePeriod.class));
}
```

If helper names differ, adapt to existing helper names in the same test file without changing the test meaning.

- [ ] **Step 2: Write failing test for active duplicate readable error**

Add a second test:

```java
@Test
void generateRejectsSameDateVersionWhenExistingPeriodIsNotCancelled() {
    CurrentAccountPrincipal principal = managerPrincipal();
    Account manager = account(principal);
    GenerateScheduleRequest request = new GenerateScheduleRequest(
            "Duplicate period",
            LocalDate.of(2026, 9, 21),
            LocalDate.of(2026, 9, 23),
            1,
            List.of(1L),
            List.of(1L));
    SchedulePeriod draft = SchedulePeriod.builder()
            .periodName("Existing draft")
            .startDate(request.startDate())
            .endDate(request.endDate())
            .versionNo(1)
            .status("DRAFT")
            .createdBy(manager)
            .build();

    when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
    when(schedulePeriodRepository.findByStartDateAndEndDateAndVersionNo(
            request.startDate(), request.endDate(), 1)).thenReturn(List.of(draft));

    assertThatThrownBy(() -> service.generate(principal, request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Đã tồn tại lịch trong khoảng ngày này");
    verify(schedulePeriodRepository, never()).saveAndFlush(any());
}
```

- [ ] **Step 3: Run tests to verify red**

Run:

```powershell
./mvnw.cmd "-Dtest=AdminScheduleGenerationServiceTest#generateAllowsSameDateVersionWhenExistingPeriodIsCancelled+generateRejectsSameDateVersionWhenExistingPeriodIsNotCancelled" test
```

Expected: compile/test failure because `findByStartDateAndEndDateAndVersionNo` or new behavior does not exist yet.

- [ ] **Step 4: Implement repository finder**

In `SchedulePeriodRepository.java`, add:

```java
List<SchedulePeriod> findByStartDateAndEndDateAndVersionNo(LocalDate startDate, LocalDate endDate, Integer versionNo);
```

Ensure imports include `java.time.LocalDate` and `java.util.List` if not already present.

- [ ] **Step 5: Implement duplicate validation**

In `AdminScheduleGenerationService.generate`, replace the current duplicate check:

```java
if (versionNo < 1 || schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNo(request.startDate(), request.endDate(), versionNo)) {
    throw invalidRequest();
}
```

with:

```java
if (versionNo < 1) {
    throw invalidRequest();
}
boolean hasBlockingDuplicate = schedulePeriodRepository
        .findByStartDateAndEndDateAndVersionNo(request.startDate(), request.endDate(), versionNo)
        .stream()
        .anyMatch(period -> !"CANCELLED".equals(period.getStatus()));
if (hasBlockingDuplicate) {
    throw duplicatePeriodRequest();
}
```

Add helper:

```java
private static ResponseStatusException duplicatePeriodRequest() {
    return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Đã tồn tại lịch trong khoảng ngày này. Vui lòng chọn lịch đã có hoặc đổi ngày.");
}
```

- [ ] **Step 6: Run focused tests to verify green**

Run the same Maven command from Step 3.

Expected: both tests pass.

---

### Task 2: Backend Schedule List Filtering and Created Time

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodController.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`
- Modify: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminSchedulePeriodResponse.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodControllerTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`

- [ ] **Step 1: Write controller test for valid date filter params**

In `AdminSchedulePeriodControllerTest`, add or update a test that performs:

```java
mockMvc.perform(get("/api/admin/schedule-periods")
        .param("status", "ALL")
        .param("from", "2026-09-21")
        .param("to", "2026-09-26")
        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
    .andExpect(status().isOk());
```

Verify service receives `LocalDate.of(2026, 9, 21)` and `LocalDate.of(2026, 9, 26)`.

- [ ] **Step 2: Write controller test for invalid date range**

Add:

```java
mockMvc.perform(get("/api/admin/schedule-periods")
        .param("from", "2026-09-26")
        .param("to", "2026-09-21")
        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
    .andExpect(status().isBadRequest());
```

Expected behavior is 400, not 500.

- [ ] **Step 3: Run controller tests to verify red or current failure**

Run:

```powershell
./mvnw.cmd "-Dtest=AdminSchedulePeriodControllerTest" test
```

Expected: failure if current controller does not validate or parameter names mismatch.

- [ ] **Step 4: Add or confirm date validation in service**

In `AdminSchedulePeriodService`, ensure list method checks:

```java
if (from != null && to != null && from.isAfter(to)) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu lọc phải trước hoặc bằng ngày kết thúc.");
}
```

Use existing method/parameter names if they differ.

- [ ] **Step 5: Add createdAt to period response**

In `AdminSchedulePeriodResponse`, add a `LocalDateTime createdAt` field to the record. In its factory method, set it from the period base entity timestamp, for example:

```java
period.getCreatedAt()
```

If the base entity field is named differently, use the actual getter in `BaseEntity`.

- [ ] **Step 6: Run backend focused tests**

Run:

```powershell
./mvnw.cmd "-Dtest=AdminSchedulePeriodControllerTest,AdminSchedulePeriodServiceTest" test
```

Expected: pass.

---

### Task 3: Frontend API Error and Schedule Service Adaptation

**Files:**
- Modify: `frontend/src/services/apiClient.ts`
- Modify: `frontend/src/services/backendScheduleService.ts`
- Modify: `frontend/src/types/backendSchedule.ts`
- Test: `frontend/src/services/backendScheduleService.test.ts`
- Test: `frontend/src/services/apiClient.test.ts`

- [ ] **Step 1: Write backend schedule service test for date params**

In `backendScheduleService.test.ts`, add:

```ts
test('sends schedule filters as backend date range params', async () => {
  vi.mocked(fetch).mockResolvedValueOnce(jsonResponse([]))

  await backendScheduleService.listPeriods({
    status: 'ALL',
    startDate: '2026-09-21',
    endDate: '2026-09-26',
  })

  expect(fetch).toHaveBeenCalledWith(
    expect.stringContaining('/api/admin/schedule-periods?'),
    expect.anything(),
  )
  const url = String(vi.mocked(fetch).mock.calls[0]?.[0])
  expect(url).toContain('from=2026-09-21')
  expect(url).toContain('to=2026-09-26')
  expect(url).not.toContain('startDate=')
  expect(url).not.toContain('endDate=')
})
```

- [ ] **Step 2: Write service test for createdAt mapping**

Add a period fixture with `createdAt: '2026-09-25T05:00:00'` and assert adapted `createdAt` equals that value.

- [ ] **Step 3: Run tests to verify red**

Run:

```powershell
npm.cmd test -- backendScheduleService.test.ts
```

Expected: fail if current query params or types do not match.

- [ ] **Step 4: Update backend schedule types**

In `BackendSchedulePeriodResponse`, add:

```ts
createdAt?: string | null
```

- [ ] **Step 5: Update period adapter**

In `adaptBackendSchedulePeriod`, change `createdAt` to:

```ts
createdAt: period.createdAt ?? period.publishedAt ?? `${period.startDate}T00:00:00`,
```

- [ ] **Step 6: Update filter param names**

In `backendScheduleService.ts`, ensure `listPeriodsForStatus` sends:

```ts
if (startDate) params.set('from', startDate)
if (endDate) params.set('to', endDate)
```

Do not send `startDate`/`endDate` to backend list endpoint.

- [ ] **Step 7: Run service tests**

Run:

```powershell
npm.cmd test -- backendScheduleService.test.ts
```

Expected: pass.

---

### Task 4: Manager Schedule List Pagination

**Files:**
- Modify: `frontend/src/pages/manager/SchedulesPage.tsx`
- Create or modify: `frontend/src/pages/manager/SchedulesPage.test.tsx`

- [ ] **Step 1: Write helper or component test for pagination behavior**

If `SchedulesPage.test.tsx` does not exist, create it with helper-level tests by exporting pure helpers from `SchedulesPage.tsx` or a new `SchedulesPage.helpers.ts`. Prefer a helper file if the component is hard to test.

Create `frontend/src/pages/manager/SchedulesPage.helpers.ts` with tests expecting:

```ts
import { describe, expect, test } from 'vitest'
import { getPaginatedSchedules, getTotalSchedulePages } from './SchedulesPage.helpers'

describe('SchedulesPage helpers', () => {
  test('paginates schedules with a fixed page size', () => {
    const schedules = Array.from({ length: 12 }, (_, index) => ({ id: String(index + 1) }))
    expect(getTotalSchedulePages(schedules, 10)).toBe(2)
    expect(getPaginatedSchedules(schedules, 1, 10).map((item) => item.id)).toEqual(['1','2','3','4','5','6','7','8','9','10'])
    expect(getPaginatedSchedules(schedules, 2, 10).map((item) => item.id)).toEqual(['11','12'])
  })
})
```

Use `Partial<WorkSchedule>[]` or real `WorkSchedule` fixtures if TypeScript requires full objects.

- [ ] **Step 2: Run test to verify red**

Run:

```powershell
npm.cmd test -- SchedulesPage.test.tsx
```

Expected: fail because helpers do not exist.

- [ ] **Step 3: Implement helpers**

Create `SchedulesPage.helpers.ts`:

```ts
export const SCHEDULES_PAGE_SIZE = 10

export const getTotalSchedulePages = <T>(items: T[], pageSize = SCHEDULES_PAGE_SIZE) => Math.max(1, Math.ceil(items.length / pageSize))

export const getPaginatedSchedules = <T>(items: T[], page: number, pageSize = SCHEDULES_PAGE_SIZE) => {
  const safePage = Math.min(Math.max(page, 1), getTotalSchedulePages(items, pageSize))
  const start = (safePage - 1) * pageSize
  return items.slice(start, start + pageSize)
}
```

- [ ] **Step 4: Wire page state**

In `SchedulesPage.tsx`:

```tsx
const [page, setPage] = useState(1)
useEffect(() => setPage(1), [search, status, startDate, endDate])
const totalPages = getTotalSchedulePages(schedules)
const visibleSchedules = getPaginatedSchedules(schedules, page)
```

Render `visibleSchedules` instead of `schedules` in `ScheduleList`.

Add pagination controls below list:

```tsx
{schedules.length > SCHEDULES_PAGE_SIZE && (
  <div className="flex items-center justify-end gap-2">
    <Button variant="outline" disabled={page <= 1} onClick={() => setPage((current) => Math.max(1, current - 1))}>Trước</Button>
    <span className="text-sm text-slate-500">Trang {page}/{totalPages}</span>
    <Button variant="outline" disabled={page >= totalPages} onClick={() => setPage((current) => Math.min(totalPages, current + 1))}>Sau</Button>
  </div>
)}
```

- [ ] **Step 5: Run focused frontend tests**

Run:

```powershell
npm.cmd test -- SchedulesPage.test.tsx
```

Expected: pass.

---

### Task 5: Schedule Generation Form Defaults, Employee Pagination, and Notices

**Files:**
- Modify: `frontend/src/components/schedule/ScheduleGenerationForm.tsx`
- Modify: `frontend/src/components/schedule/ScheduleGenerationForm.test.tsx`
- Modify: `frontend/src/pages/manager/ScheduleGenerationPage.helpers.ts`
- Modify: `frontend/src/pages/manager/ScheduleGenerationPage.test.tsx`

- [ ] **Step 1: Write tests for local today defaults**

In `ScheduleGenerationForm.test.tsx`, freeze time:

```ts
vi.setSystemTime(new Date('2026-09-25T10:00:00+07:00'))
```

Render the form in backend mode and assert start date input value is `2026-09-25` and end date is `2026-10-01` for a 7-day default.

- [ ] **Step 2: Write tests for employee pagination**

Create 10 active employee fixtures, render form, assert only first 9 names are visible and a pagination label/button exists. Then click `Sau` and assert employee 10 appears.

- [ ] **Step 3: Write notice copy tests**

In `ScheduleGenerationPage.test.tsx`, update `buildGenerationNotice` expectations:

```ts
expect(buildGenerationNotice('Lịch A', { aiStatus: 'SUCCESS', solverStatus: 'OPTIMAL', assignmentCount: 3, warnings: [], errorMessage: null }).message)
  .toBe('Tạo lịch thành công.')
expect(buildGenerationNotice('Lịch A', { aiStatus: 'INFEASIBLE', solverStatus: 'INFEASIBLE', assignmentCount: 0, warnings: [], errorMessage: 'x' }).message)
  .toBe('Đã tạo lịch nhưng chưa phân công đủ. Vui lòng xem cảnh báo hoặc chỉnh sửa lịch.')
```

Use the full object shape required by current tests.

- [ ] **Step 4: Run tests to verify red**

Run:

```powershell
npm.cmd test -- ScheduleGenerationForm.test.tsx ScheduleGenerationPage.test.tsx
```

Expected: fail on defaults/pagination/copy.

- [ ] **Step 5: Implement local date helpers**

Inside `ScheduleGenerationForm.tsx` or a small helper file:

```ts
const toDateInputValue = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const addDaysInputValue = (dateValue: string, days: number) => {
  const [year, month, day] = dateValue.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  date.setDate(date.getDate() + days)
  return toDateInputValue(date)
}
```

Initialize:

```ts
const defaultStartDate = toDateInputValue(new Date())
const [startDate, setStartDate] = useState(defaultStartDate)
const [endDate, setEndDate] = useState(addDaysInputValue(defaultStartDate, 6))
```

When period resets, use the same helpers.

- [ ] **Step 6: Implement employee pagination**

Add state:

```ts
const EMPLOYEE_PAGE_SIZE = 9
const [employeePage, setEmployeePage] = useState(1)
const totalEmployeePages = Math.max(1, Math.ceil(visibleEmployees.length / EMPLOYEE_PAGE_SIZE))
const employeePageItems = visibleEmployees.slice((employeePage - 1) * EMPLOYEE_PAGE_SIZE, employeePage * EMPLOYEE_PAGE_SIZE)
```

Render `employeePageItems` instead of `visibleEmployees`. Reset page to 1 when `employeeSearch` changes.

- [ ] **Step 7: Simplify generation notices**

In `ScheduleGenerationPage.helpers.ts`, make `buildGenerationNotice` return the exact copy from Step 3. Keep `summarizeOptimizeResult` for any secondary UI/tests that still need it, but do not use it in the main notice.

- [ ] **Step 8: Run focused tests**

Run:

```powershell
npm.cmd test -- ScheduleGenerationForm.test.tsx ScheduleGenerationPage.test.tsx
```

Expected: pass.

---

### Task 6: Employee Schedule Backend Mode

**Files:**
- Create or modify: `frontend/src/services/employeeScheduleService.ts`
- Modify: `frontend/src/pages/employee/EmployeeSchedulePage.tsx`
- Create or modify: `frontend/src/pages/employee/EmployeeSchedulePage.test.tsx`

- [ ] **Step 1: Inspect backend employee schedule endpoint**

Confirm endpoint in backend controller. Expected likely path: `GET /api/employee/schedule?from=YYYY-MM-DD&to=YYYY-MM-DD`. Use actual path found in `EmployeeScheduleController`.

- [ ] **Step 2: Write frontend service test**

Create or update service test:

```ts
test('loads employee schedule from backend date window', async () => {
  vi.mocked(fetch).mockResolvedValueOnce(jsonResponse([
    { id: 1, workDate: '2026-09-25', shiftName: 'Thong tam', shiftStartTime: '08:00:00', shiftEndTime: '17:00:00', warningCount: 0 }
  ]))

  const result = await employeeScheduleService.list({ from: '2026-09-22', to: '2026-09-28' })

  expect(String(vi.mocked(fetch).mock.calls[0]?.[0])).toContain('/api/employee/schedule')
  expect(result).toHaveLength(1)
})
```

Adapt response fields to actual backend DTO.

- [ ] **Step 3: Write page test for backend mode empty state**

Stub `VITE_USE_MOCK_API=false`, mock service returning `[]`, render `EmployeeSchedulePage`, assert text `Bạn chưa có lịch làm việc trong khoảng thời gian này.`.

- [ ] **Step 4: Run tests to verify red**

Run:

```powershell
npm.cmd test -- EmployeeSchedulePage.test.tsx employeeScheduleService.test.ts
```

Expected: fail because page still uses mock data.

- [ ] **Step 5: Implement employee schedule service**

Use `requestJson` and actual backend endpoint:

```ts
export const employeeScheduleService = {
  async list({ from, to }: { from: string; to: string }) {
    const params = new URLSearchParams({ from, to })
    return requestJson<BackendEmployeeScheduleItem[]>(`/api/employee/schedule?${params.toString()}`)
  },
}
```

Define `BackendEmployeeScheduleItem` in a local service type or `frontend/src/types/backendEmployeeSchedule.ts`.

- [ ] **Step 6: Update page backend branch**

In `EmployeeSchedulePage.tsx`, when `apiConfig.useMockApi` is false:

```tsx
const [backendItems, setBackendItems] = useState<EmployeeScheduleItem[]>([])
const [loading, setLoading] = useState(false)
const [error, setError] = useState('')

useEffect(() => {
  if (apiConfig.useMockApi) return
  setLoading(true)
  employeeScheduleService.list(currentWeekWindow())
    .then((items) => { setBackendItems(adaptItems(items)); setError('') })
    .catch((loadError) => setError(loadError instanceof Error ? loadError.message : 'Không thể tải lịch làm việc.'))
    .finally(() => setLoading(false))
}, [])
```

Use existing UI structure; only replace data source.

- [ ] **Step 7: Run focused tests**

Run:

```powershell
npm.cmd test -- EmployeeSchedulePage.test.tsx employeeScheduleService.test.ts
```

Expected: pass.

---

### Task 7: Auth Refresh Token Reliability

**Files:**
- Modify: `frontend/src/services/apiClient.ts`
- Modify: `frontend/src/services/authStorage.ts`
- Modify: `frontend/src/services/authService.ts` if needed
- Test: `frontend/src/services/apiClient.test.ts`
- Test: `frontend/src/services/authService.test.ts` if existing

- [ ] **Step 1: Read current storage behavior**

Inspect `authStorage.ts`, `authService.ts`, and `apiClient.ts`. Confirm how current storage chooses local vs session.

- [ ] **Step 2: Add test for preserving localStorage refresh scope**

In `apiClient.test.ts`, add:

```ts
test('stores refreshed token in local storage when current token is local scoped', async () => {
  localStorage.setItem('auth_token', 'expired-local-token')
  sessionStorage.clear()
  vi.mocked(fetch)
    .mockResolvedValueOnce(jsonResponse({ message: 'Unauthorized' }, { status: 401 }))
    .mockResolvedValueOnce(jsonResponse({ accessToken: 'fresh-local-token', account: {} }))
    .mockResolvedValueOnce(jsonResponse({ value: 'ok' }))

  await requestJson('/api/admin/schedule-periods')

  expect(localStorage.getItem('auth_token')).toBe('fresh-local-token')
  expect(sessionStorage.getItem('auth_token')).toBeNull()
})
```

Use actual storage keys from `AUTH_STORAGE_KEYS`.

- [ ] **Step 3: Add test for no auth clear on 400/403/500**

Add:

```ts
test('does not clear stored auth for non-401 API errors', async () => {
  localStorage.setItem('auth_token', 'valid-token')
  vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ message: 'Bad request' }, { status: 400 }))

  await expect(requestJson('/api/admin/schedule-periods/generate', { method: 'POST', body: {} })).rejects.toThrow('Bad request')

  expect(localStorage.getItem('auth_token')).toBe('valid-token')
})
```

- [ ] **Step 4: Run tests to verify red if behavior is broken**

Run:

```powershell
npm.cmd test -- apiClient.test.ts
```

Expected: fail only if current storage/clear behavior is wrong. If it passes, keep tests as coverage.

- [ ] **Step 5: Fix token storage helpers only if tests fail**

If refreshed token always writes to session storage, change `updateStoredToken` to detect existing token scope:

```ts
const getActiveTokenStorage = () => localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN) ? localStorage : sessionStorage
export const updateStoredToken = (accessToken: string) => {
  getActiveTokenStorage().setItem(AUTH_STORAGE_KEYS.TOKEN, accessToken)
}
```

If keys differ, use actual key constants.

- [ ] **Step 6: Run auth tests**

Run:

```powershell
npm.cmd test -- apiClient.test.ts
```

Expected: pass.

---

### Task 8: Final Verification

**Files:**
- No new source changes unless verification exposes failures.

- [ ] **Step 1: Run AI tests**

Run:

```powershell
& ".venv\Scripts\pytest.exe" -q
```

Workdir: `ai-service`

Expected: all tests pass.

- [ ] **Step 2: Run backend tests**

Run:

```powershell
./mvnw.cmd test
```

Workdir: `backend`

Expected: all tests pass.

- [ ] **Step 3: Run frontend tests**

Run:

```powershell
npm.cmd test -- --run
```

Workdir: `frontend`

Expected: all tests pass.

- [ ] **Step 4: Run frontend build**

Run:

```powershell
npm.cmd run build
```

Workdir: `frontend`

Expected: build succeeds. Existing Vite chunk-size warning is acceptable.

- [ ] **Step 5: Run frontend lint**

Run:

```powershell
npm.cmd run lint
```

Workdir: `frontend`

Expected: zero errors. Existing Fast Refresh warning in `AuthContext.tsx` is acceptable if still present.

- [ ] **Step 6: Inspect diff**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only intended files changed. Do not stage or commit unless the user explicitly asks.

---

## Self-Review Notes

- Spec coverage: duplicate generation, readable errors, list filtering, created time, pagination, generation defaults, employee pool pagination, employee schedule backend rendering, token refresh, and final verification are covered.
- Placeholder scan: no TBD/TODO placeholders are present.
- Type consistency: frontend names use existing `BackendSchedulePeriodResponse`, `WorkSchedule`, `ScheduleGenerationForm`, and `backendScheduleService` conventions; backend names use existing service/repository/controller naming.
- Scope: one large batch as requested by user, broken into independently testable tasks.
