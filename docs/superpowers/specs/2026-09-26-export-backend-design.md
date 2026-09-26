# Export Backend Wiring Design

**Goal:** Wire the manager exports page to the three real backend file-download APIs and remove all unsupported export options.

**Approach:** Frontend-only changes. Rework `ExportsPage` around period selection plus one button per supported file, backed by a new blob-download client and a thin export service. No backend changes. Mock mode disables downloads with an explanatory hint.

**Scope:** `ExportsPage`, `apiClient` blob support, new `exportService`, unit tests. No AI solver, scheduling, or auth changes.

## Backend APIs (existing, unchanged)

All require `MANAGER` or `ADMIN` role and return `Content-Disposition: attachment`:

1. `GET /api/admin/schedule-periods/{periodId}/exports/schedule.xlsx` — one row per assignment: work date, weekday, work shift id, shift code, shift name, start/end time, employee code, full name, assignment status, assigned source, locked flag; unassigned shifts included as `UNASSIGNED` rows.
2. `GET /api/admin/schedule-periods/{periodId}/exports/schedule.pdf` — same schedule data as PDF.
3. `GET /api/admin/schedule-periods/{periodId}/reports/workload.xlsx` — one row per active employee: employee code, full name, total shifts, total hours, heavy shifts, overnight shifts, working days.

None of the three accepts date-range, status, or format parameters; all are keyed by `periodId`. There is no CSV support and no employees/leaves/preferences/swap-requests export on the backend.

## Frontend Behavior

### Exports page

- Replace the six mock data types with three supported options: schedule Excel, schedule PDF, workload Excel.
- Replace the date-range and status filters with a single period dropdown loaded from `backendScheduleService.listPeriods` (all statuses).
- Remove the CSV format entirely; format is implied by the chosen option.
- Each option triggers a download of the matching endpoint for the selected period.
- Show loading state while preparing/downloading, a success notice naming the downloaded file, and the backend error message on failure.
- In mock mode (`VITE_USE_MOCK_API=true`), disable the download action and show the hint that exports require backend mode.

### Download client

- Add `requestBlob(path)` to `apiClient` reusing the existing auth, 401-refresh-retry, and error-message behavior; it returns the raw `Blob` on success and throws `ApiError` otherwise.
- Add `exportService` with `downloadScheduleXlsx(periodId)`, `downloadSchedulePdf(periodId)`, and `downloadWorkloadXlsx(periodId)`, each resolving the filename from the `Content-Disposition` response header with a sensible fallback (`schedule-period-{id}.xlsx`, `schedule-period-{id}.pdf`, `workload-period-{id}.xlsx`).
- The page creates an object URL from the blob, clicks a temporary anchor with the `download` attribute, then revokes the URL.

## Testing

- `apiClient` blob test: successful binary response returns a `Blob`; error JSON response throws `ApiError` with the backend message.
- Export service tests: assert the three request URLs and the filename fallback behavior.
- Page helper tests: option-to-endpoint mapping and period-required validation message.
- Existing suite must stay green: frontend `npm.cmd test -- --run`, `npm.cmd run build`, `npm.cmd run lint` with zero errors.
