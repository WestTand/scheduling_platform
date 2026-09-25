# Dashboard Backend Wiring Design

**Goal:** Replace mock data on the manager and employee dashboards with real backend data, remove dead links and hardcoded dates, and drop the manager "pending requests" card.

**Approach:** Dashboard-owned aggregation. Each dashboard page fetches from existing frontend services in parallel and computes its own view. No new backend endpoints. Mock rendering stays only when `VITE_USE_MOCK_API=true`.

**Scope:** `ManagerDashboardPage`, `EmployeeDashboardPage`, plus small pure helpers with unit tests. No AI solver, scheduling-rule, or auth changes.

## Decisions

- Approach 1 (aggregate existing APIs in frontend) over a new backend dashboard endpoint, because every needed data source already has a real API.
- The manager "Yêu cầu đang chờ xử lý" card (hardcoded REQ-101..103) is removed; the alerts card expands full width.
- Fake trend labels ("+8% so với tuần trước", "12 ca cần cân bằng") are replaced with neutral context labels because no backend trend source exists.
- Schedule periods have no `PENDING_APPROVAL` status in backend (`DRAFT`, `APPROVED`, `PUBLISHED`, `CANCELLED` only). Dashboard links needing "awaiting" schedules use `status=DRAFT`.

## Manager Dashboard

### Summary cards

- Total employees: `backendEmployeeService.list({ status: 'ACTIVE', page: 1, pageSize: 1 })`, display `total`. Label context: "đang hoạt động".
- Shifts this week: `backendScheduleService.listPeriods({ status: 'ALL', startDate, endDate })` for the current local week (Monday-Sunday), display sum of `shiftCount`. Label context: "tuần hiện tại".
- Pending requests: `navigationSummaryService.get()` in backend mode, display `pendingLeaveRequests + pendingSwapRequests + pendingPreferences`. Label context: "nghỉ phép, đổi ca, nguyện vọng".
- Conflict warnings: sum of `warnings.length` across fetched DRAFT period details in the current week, capped at the first 5 periods to avoid N+1 overload. Label context: "kỳ nháp tuần này".
- Period details fetched for the current week are loaded once per dashboard render and shared by the summary, upcoming-shifts, action-center, and alerts sections.
- Loading state shows skeleton text; API failure shows the backend error message and keeps the section title visible.

### Upcoming shifts

- From the same current-week period details, collect work shifts with assignment counts, sort by date then start time, take the first 3.
- Each row links to `/manager/schedules/{realPeriodId}`; understaffed shifts link to `?tab=warnings` and carry a red badge "Thiếu N".
- Empty state: "Không có ca trực nào trong tuần này."

### Action center

Five items mapped to real queues, each linking to its real queue or detail:

1. Draft schedules: first `DRAFT` period in range, link `/manager/schedules?status=DRAFT`.
2. Pending leave: first `PENDING` item from `leaveRequestService`, link `/manager/leave-requests?status=PENDING`.
3. Pending swap: first `PENDING_APPROVAL` item from `swapRequestService`, link `/manager/swap-requests?status=PENDING_APPROVAL`.
4. Pending preference: first `PENDING` item from `preferenceService`, link `/manager/preferences?status=PENDING`.
5. Critical schedule warning: first `HIGH` warning from fetched period details, link `/manager/schedules/{realPeriodId}?tab=warnings`.
- Missing items render "Không có ..." text instead of mock names.

### Schedule alerts

- Top `HIGH` warnings from fetched period details (max 3), each linking to its real period warnings tab.
- Empty state: "Không có cảnh báo lịch."
- The hardcoded alerts (Thiếu nhân sự, Trùng lịch, Nghỉ không hợp lệ) are deleted.

## Employee Dashboard

### Summary cards

- Data source: `employeeScheduleService.list()` for the current local week plus `navigationSummaryService.get()` (employee scope) for pending counts.
- Today's shift: shift name of the first assignment dated today, or "—".
- Next shift: shift name of the first assignment after today, or "—".
- Total hours: sum of shift work minutes in the week, rounded to hours.
- Pending requests: `pendingLeaveRequests + pendingPreferences + pendingSwapRequests`, detail "đổi ca, nghỉ phép, nguyện vọng".
- Card links use real assignment ids (`/employee/schedule?assignmentId={realId}`) so the detail dialog opens.

### Weekly schedule

- Group the week's backend assignments by date, take the next 3 days with assignments.
- Each row links with its real assignment id; badge shows assignment status.
- Empty state: "Bạn chưa có lịch làm việc trong tuần này."
- The hardcoded week badge becomes the computed current week range.

### Notifications

- `notificationService.list({ page: 1, pageSize: 3 })` in backend mode, newest first.
- Each item links with its real notification id; empty state: "Không có thông báo mới."
- The hardcoded notification strings are deleted.

## Stale links and dates

- `schedule-demo-01`, `assignment-04/05/06`, `NOTI-00x` links are replaced with real ids everywhere on both dashboards.
- `?status=PENDING_APPROVAL` schedule links become `?status=DRAFT`.
- `currentDateLabel` (`16/09/2026`) and the `Tuần 38` badge become the computed local today and current week range.
- Quick-action buttons (pure navigation) are unchanged.
- `SchedulesPage` query params are unchanged; no new `view`/`hasWarnings` support is added in this batch.

## Testing

- Pure helpers (`getManagerDashboardSummary`, shift picking, warning picking, week-window reuse where sensible) get Vitest unit tests with backend-shaped fixtures.
- Service-level tests assert dashboard code calls `from`/`to` current-week params and the navigation-summary endpoint.
- Existing suite must stay green: frontend `npm.cmd test -- --run`, `npm.cmd run build`, `npm.cmd run lint` with zero errors.
