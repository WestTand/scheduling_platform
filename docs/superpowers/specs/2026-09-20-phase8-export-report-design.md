# Phase 8 Export & Report Design

## Purpose

Phase 8 adds backend export/report capabilities required by the project brief: schedule export to Excel/PDF and operational workload reporting. This phase is backend-only. Frontend download buttons and UI integration remain in Phase 9 because the current frontend is mock-first and not yet aligned with backend contracts.

## Scope

In scope:

- Export a schedule period to `.xlsx`.
- Export a schedule period to `.pdf`.
- Export a workload report for a schedule period to `.xlsx`.
- Manager/admin-only download APIs.
- File generation tests that parse or validate the produced files.
- Content-type, filename, authorization, and missing-period behavior.

Out of scope:

- Frontend buttons or pages.
- PDF workload reports.
- Highly designed PDF layouts.
- Emailing exported files.
- Background export jobs or persisted export history.
- Cross-period analytics dashboards.

## API Design

Add manager/admin endpoints under the existing admin schedule period area:

- `GET /api/admin/schedule-periods/{periodId}/exports/schedule.xlsx`
- `GET /api/admin/schedule-periods/{periodId}/exports/schedule.pdf`
- `GET /api/admin/schedule-periods/{periodId}/reports/workload.xlsx`

All endpoints require manager/admin access. Phase 8 should explicitly allow both `MANAGER` and `ADMIN` roles for these export/report endpoints even though some existing schedule-period endpoints currently use manager-only guards.

Responses:

- Schedule Excel: `200 OK`, `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, attachment filename `schedule-period-{periodId}.xlsx`.
- Schedule PDF: `200 OK`, `Content-Type: application/pdf`, attachment filename `schedule-period-{periodId}.pdf`.
- Workload Excel: `200 OK`, `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, attachment filename `workload-period-{periodId}.xlsx`.
- Missing period: `404 Not Found` using existing error handling conventions.
- Unauthorized/forbidden: existing security behavior.

## Data Model

No schema changes are required.

Schedule export reads existing data:

- `SchedulePeriod` for period metadata and date range.
- `WorkShift` for daily concrete shifts.
- `ShiftType` for shift name, code, start/end time, duration, heavy flag, overnight flag, and quota category.
- `ShiftAssignment` for assigned employees and assignment status/lock state.
- `Employee` for employee code and display name.

Workload report derives values from active employees and assignments in one schedule period. Active employees with no assignments in the period must still appear with zero totals so the report can reveal staffing gaps or unfair distribution.

- Employee code.
- Employee full name.
- Total assigned shifts.
- Total scheduled hours.
- Heavy shift count when the assigned shift type is marked heavy.
- Overnight shift count when the assigned shift type crosses midnight or is marked overnight.
- Distinct working day count.

## File Layouts

### Schedule Excel

Use one workbook with a primary sheet named `Schedule`.

Top rows:

- Report title.
- Schedule period id, period name, status, and version number.
- Date range.
- Generated timestamp.

Table columns:

- Date.
- Day of week.
- Shift code.
- Shift name.
- Start time.
- End time.
- Employee code.
- Employee name.
- Assignment status.
- Assigned source and locked flag.

Each assignment gets one row. If a work shift has no assignment, include one row with blank employee fields so empty staffing is visible.

### Schedule PDF

Generate a simple printable PDF with:

- Title and period date range.
- A compact table containing date, shift, time range, and assigned employee names.
- Empty/unassigned shifts marked clearly.

The PDF layout prioritizes correctness, readability, and printability. It does not attempt a calendar-style design in Phase 8.

### Workload Excel

Use one workbook with a sheet named `Workload`.

Top rows:

- Report title.
- Schedule period id, period name, status, and version number.
- Date range.
- Generated timestamp.

Table columns:

- Employee code.
- Employee name.
- Total shifts.
- Total hours.
- Heavy shifts.
- Overnight shifts.
- Distinct working days.

Rows are sorted by `employeeCode ASC`, then `employeeName ASC` when codes are missing or equal.

## Architecture

Add focused classes under a new export/report package, keeping data loading separate from file formatting:

- `ScheduleExportController`: exposes schedule Excel/PDF and workload Excel endpoints.
- `ScheduleExportService`: loads and normalizes schedule-period export data.
- `ScheduleExportData`: immutable DTO/record for period metadata and rows.
- `ScheduleExcelWriter`: converts `ScheduleExportData` to `.xlsx` bytes.
- `SchedulePdfWriter`: converts `ScheduleExportData` to `.pdf` bytes.
- `WorkloadReportService`: computes workload rows from schedule assignments.
- `WorkloadReportData`: immutable DTO/record for period metadata and workload rows.
- `WorkloadExcelWriter`: converts `WorkloadReportData` to `.xlsx` bytes.

Repository usage should prefer existing repositories where possible. If an export-specific query is needed, add a narrowly named method to an existing repository rather than creating broad new persistence abstractions.

Recommended dependencies:

- Apache POI for `.xlsx` generation.
- OpenPDF for simple PDF generation.

The controller should return `ResponseEntity<byte[]>` with `Content-Disposition: attachment`. Writers should return byte arrays and close workbooks/documents reliably.

## Error Handling

- If the period does not exist, return `404`.
- If the period exists but has no work shifts, return a valid file containing metadata and an empty table.
- If a work shift has no assignment, include it in schedule exports as unassigned.
- If optional fields are null, render blanks or safe fallback labels instead of failing file generation.
- File generation failures should surface as server errors through existing exception handling and should not mutate database state.

## Security

- Endpoints are manager/admin-only and should explicitly authorize both roles.
- Employee users must not access manager export/report endpoints.
- Export data is scoped strictly to the requested `periodId`.
- No export file is persisted to disk by default.

## Testing Strategy

Unit tests:

- Workload calculation totals shifts, hours, heavy shifts, overnight shifts, and distinct days correctly.
- Schedule Excel writer creates a parseable workbook with expected headers and rows.
- Workload Excel writer creates a parseable workbook with expected totals.
- Schedule PDF writer creates parseable PDF bytes with a valid PDF header and expected text such as the period name/date range.

Controller/service tests:

- Manager/admin can download each export.
- Employee/anonymous access is rejected by existing security behavior.
- Missing period returns `404`.
- Schedule export includes only rows from the requested period.
- Empty period still returns a valid file.

Full verification:

- Run backend test suite after implementation.
- Run AI service tests if backend changes touch AI/report data dependencies.
- Run `git diff --check` before completion.

## Phase 9 Handoff

Phase 9 frontend integration should add download actions that call these endpoints directly. The frontend should not reimplement export formatting. It only needs authenticated file download behavior, loading/error states, and clear labels for Excel/PDF outputs.
