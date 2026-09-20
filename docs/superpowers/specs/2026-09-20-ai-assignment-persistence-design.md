# Spec: Phase 6 Slice 6B — Persist AI Assignments

Date: 2026-09-20
Status: Draft (pending user review)
Branch: `phase6/ai-assignment-persistence`
Base: `phase6/backend-ai-payload-client` (Slice 6A, pushed)

## 1. Context

Slice 6A đã xong và push:

- `POST /api/admin/schedule-periods/{periodId}/optimize` cho period `DRAFT`.
- Backend build AI payload schema `1.0`, gọi AI client, lưu `OptimizationRun`.
- AI transport/protocol failure → `502` + run `FAILED` (persist bằng `REQUIRES_NEW`).
- AI response `INFEASIBLE`/`FAILED` hợp lệ → `200` + run tương ứng.
- **Không mutate `ShiftAssignment`** trong mọi path.

Slice 6B hoàn tất end-to-end backend: khi AI trả `SUCCESS` hợp lệ, backend persist assignments vào DB.

Contract AI hiện có (`docs/backend-ai-scheduling-contract.md`):

- `assignments` chỉ có khi `status = SUCCESS`, mỗi item `{employeeId, workShiftId}`.
- Backend map: `work_shift_id`, `employee_id`, `assigned_source = AUTO`, `assignment_status = ASSIGNED`, `is_locked = false` trừ khi backend quyết định khác.
- Locked assignments AI trả về có thể persist như locked records hiện có thay vì duplicate.
- `SUCCESS`: thay non-locked auto assignments, giữ locked, insert AUTO, lưu run + penalty.
- `INFEASIBLE`/`FAILED`/HTTP error: không tạo assignments, chỉ lưu run + diagnostics/error.

## 2. Goal

Mở rộng endpoint optimize hiện có: AI `SUCCESS` hợp lệ → ghi assignments AI vào `ShiftAssignment` cho period `DRAFT`, giữ nguyên assignments thủ công và locked. Mọi outcome khác giữ nguyên behavior 6A.

## 3. Scope (approved)

### 3.1 Endpoint (không đổi route)

- `POST /api/admin/schedule-periods/{periodId}/optimize`, `@PreAuthorize("hasRole('MANAGER')")`.
- Period missing → `404`; non-DRAFT → `400`; solver options validation → `400` như 6A.
- Chỉ khi AI response là `SUCCESS` hợp lệ mới persist assignments.

### 3.2 Persistence policy (Option 1, approved)

Xóa:

- Assignments thỏa đồng thời:
  - thuộc period (`workShift.schedulePeriod.id = periodId`)
  - `assignedSource = AUTO`
  - `locked = false`

Giữ:

- Mọi assignment `assignedSource = MANUAL` (locked hay không).
- Mọi assignment `locked = true` (kể cả `AUTO locked`).

Insert:

- Mỗi AI assignment `{employeeId, workShiftId}` chưa được preserved assignment nào cover (cùng `employeeId + workShiftId`) → insert row mới:
  - `workShift`, `employee`
  - `assignmentStatus = ASSIGNED`
  - `assignedSource = AUTO`
  - `locked = false`
- AI assignment đã có preserved row cùng cặp `employeeId + workShiftId` → skip, không duplicate.
- AI assignment trỏ tới employee/workShift không hợp lệ (ngoài period, ngoài snapshot schedulable) → fail toàn bộ apply, không persist một phần.

Solver-visibility rule:

- Vì backend giữ mọi `MANUAL` assignment, AI phải thấy các assignment đó như fixed/reserved inputs; nếu không, solver có thể trả thêm người cho cùng shift và backend sẽ overstaff.
- Trong slice 6B, payload builder đổi semantics của `lockedAssignments`: gửi tất cả assignments cần preserve/fix trước AI, gồm:
  - mọi assignment `locked = true`
  - mọi assignment `assignedSource = MANUAL`
- Giữ tên JSON `lockedAssignments` để tương thích schema `1.0`; trong backend code/test gọi đây là preserved/fixed assignments nếu cần để tránh nhầm nghĩa nghiệp vụ.
- Nếu một preserved assignment trỏ tới employee/work shift không còn schedulable/hợp lệ, pre-call validation phải fail `400` trước khi tạo run (giống locked validation hiện tại), thay vì âm thầm bỏ qua.

### 3.3 Validation trước insert

Dùng cùng snapshot đã build payload (không query lại toàn bộ nếu có thể):

- `workShiftId` phải thuộc period (`snapshot` work shifts).
- `employeeId` phải thuộc schedulable set (`snapshot` employees).
- Work shift phải có shift type active (đã validate ở 6A; nếu AI trả assignment cho shift bị loại thì fail apply).
- Preserved assignments load sau delete để check duplicate chính xác.
- AI response `SUCCESS` phải không conflict với preserved/fixed assignments ngoài exact duplicate:
  - exact duplicate `(employeeId, workShiftId)` → skip
  - same `workShiftId` nhưng staffing vượt `requiredStaff` khi cộng preserved + AI → fail apply
  - same employee overlap/max-hours/leave conflicts với preserved assignments → solver đáng ra đã thấy preserved as fixed; nếu vẫn xuất hiện thì fail apply thay vì commit lịch sai
- Backend không cần chạy lại toàn bộ objective/soft constraints, nhưng phải bảo vệ hard invariants cơ bản trước khi commit: referential integrity, duplicate pairs, shift capacity, employee overlap/date-leave/daily-hours nếu conflict phát sinh từ preserved + AI output.

### 3.4 AI response protocol tightening

Slice 6B cũng siết protocol validation trong `AdminOptimizationService` trước khi gọi persistence:

- `SUCCESS`:
  - `assignments` non-null
  - từng item non-null
  - từng `employeeId`/`workShiftId` non-null
  - không duplicate pair `(employeeId, workShiftId)` trong AI response
- non-`SUCCESS` (`INFEASIBLE`/`FAILED`):
  - `assignments` phải empty; nếu AI trả assignments cho non-SUCCESS thì coi là invalid protocol → run `FAILED`, `502`, không mutate assignments.
- Existing checks giữ nguyên: schemaVersion/runId/periodId/status/solverStatus/list fields/statistics/penaltyDetails.

### 3.5 Transaction và run lifecycle

1. AI response protocol passes + `status = SUCCESS` (logic 6A giữ nguyên).
2. Gọi `AiAssignmentPersistenceService.apply(period, snapshot, response)` trong transaction riêng:
   - delete unlocked AUTO của period
   - load preserved
   - validate + insert AI assignments
   - commit all-or-nothing
3. Nếu apply thành công:
   - `finishSuccess(runId, response)` như 6A
   - trả `200 SUCCESS` như 6A
4. Nếu apply throw (validation fail, DB error, unexpected):
   - assignment transaction rollback, DB assignments không đổi
   - `finishFailure(runId, "FAILED", "Failed to apply AI assignments: ...", response)` qua recorder (`REQUIRES_NEW`, null-safe path đã có)
   - throw `502 Bad Gateway` (`ResponseStatusException`), theo quyết định đã chốt

Không mutate paths (giữ nguyên 6A):

- `INFEASIBLE` → `200`, run `INFEASIBLE`, không động assignments.
- AI `FAILED` hợp lệ → `200`, run `FAILED`, không động assignments.
- Transport/protocol failure → `502`, run `FAILED`, không động assignments.
- Pre-call `400` → không tạo run, không động assignments.

### 3.6 Response DTO (giữ nguyên)

- `OptimizeScheduleResponse` không đổi trong slice này.
- `assignmentCount` = số assignments AI trả về, không phải số rows đã insert.
- Không trả full assignment list hay IDs; monitoring/inserted-count để slice frontend sau nếu cần.

## 4. API Design

| Method | Path | Body | Success | Notes |
|--------|------|------|---------|-------|
| POST | `/api/admin/schedule-periods/{periodId}/optimize` | `{solverOptions?}` optional | `200` + `OptimizeScheduleResponse` | `SUCCESS` giờ persist assignments; các outcome khác như 6A |

Error mapping bổ sung:

- AI `SUCCESS` nhưng assignment không hợp lệ (unknown employee/shift) → `502`, run `FAILED`, assignments rollback.
- AI `SUCCESS` nhưng DB apply fail → `502`, run `FAILED`, assignments rollback.
- Mọi lỗi dùng `ResponseStatusException` theo convention hiện có.

## 5. Data Model

- Không migration, không đổi entity hiện có.
- Dùng `ShiftAssignment` fields hiện có: `workShift`, `employee`, `assignmentStatus`, `assignedSource`, `locked`.
- Repository additions trong `ShiftAssignmentRepository`:
  - delete unlocked AUTO by period (bulk `@Modifying @Query` hoặc load + `deleteAll`, chốt ở plan; bulk delete cần cẩn thận với persistence context — plan phải nêu rõ)
  - query preserved assignments cho period với fetch joins (`employee`, `workShift`, `shiftType`) để check duplicate mà không N+1

## 6. Files to Create / Modify

Mới:

- `ai/AiAssignmentPersistenceService.java` — transactional apply (delete + validate + insert).
- `ai/ApplyResult.java` hoặc return record trong service (inserted count, skipped count) — dùng cho log/test, chưa expose qua API.
- Tests: `AiAssignmentPersistenceServiceTest` (unit tests for validation/mapping), real JPA transaction test for rollback behavior, extend `AdminOptimizationServiceTest` (`SUCCESS` gọi persistence trước `finishSuccess`; persistence failure → run `FAILED` + `502`; non-SUCCESS không gọi persistence).

Sửa:

- `admin/AdminOptimizationService.java` — inject persistence service, gọi sau protocol pass khi `SUCCESS`, catch apply failure → `finishFailure` + `502`, siết protocol validation assignments như §3.4.
- `ai/AiSchedulingPayloadBuilder.java` — đổi `lockedAssignments` payload thành preserved/fixed assignments (`locked=true OR assignedSource=MANUAL`) để AI reserve dữ liệu backend sẽ giữ.
- `repository/ShiftAssignmentRepository.java` — thêm delete/query methods.
- `docs/project_plan.md` — tick sau khi slice pass:
  - `AI service nhan danh sach nhan vien active`
  - `AI service nhan shift types active`
  - `AI service nhan approved leave requests`
  - `AI service nhan preferences`
  - `AI service nhan locked assignments`
  - `Backend luu ShiftAssignment`
  - `Backend luu OptimizationRun`
- Không tick frontend/retry/pytest-AI nếu chưa làm trong slice này.

Quyết định: `boundaryAssignments` vẫn để trống trong slice này (design riêng sau như 6A đã nêu); xem §9.

## 7. Testing Plan

- TDD cho persistence service và service orchestration theo skill `test-driven-development`.
- Payload builder tests: `lockedAssignments` payload includes locked assignments and MANUAL assignments; rejects preserved/fixed assignments that reference inactive employee/invalid work shift before run creation.
- Persistence unit tests: delete-unlocked-AUTO-only, preserve MANUAL + locked AUTO, insert AUTO/ASSIGNED/unlocked, skip preserved pairs, reject unknown employee/shift, reject capacity/overlap/leave/max-hours conflicts from preserved + AI result.
- Persistence transaction test with real JPA: persisted unlocked AUTO row is deleted early, then a forced apply failure occurs; after rollback, original unlocked AUTO row is still present and no partial inserted AI rows remain. Service-level test separately proves run `FAILED` is committed via recorder.
- Service tests với mocked client + mocked persistence: `SUCCESS` order (protocol → persist → `finishSuccess`); persist throw → `finishFailure("FAILED", contains "Failed to apply AI assignments")` + `502`; `INFEASIBLE`/`FAILED`/transport-failure không gọi persistence (`verifyNoInteractions` hoặc `never`).
- Protocol tests: non-SUCCESS with non-empty assignments, null assignment item/id, duplicate AI pair → invalid protocol `502` + run `FAILED`, no persistence call.
- Controller tests: giữ nguyên (response shape không đổi); thêm case nếu behavior HTTP đổi (không dự kiến).
- Regression: full backend suite `.\backend\mvnw.cmd -f backend/pom.xml test` (plain command, UTC đã pin trong surefire), `git diff --check`.

## 8. Risks

- Bulk delete + insert trong một transaction dài: period lớn có thể chậm; slice này chấp nhận (optimize vốn đồng bộ), ghi chú cho async slice sau.
- AI trả assignment cho shift/employee ngoài snapshot (solver bug hoặc data race): fail apply + `502` có thể gây khó chịu; message phải nêu rõ cặp id lỗi để manager debug.
- Preserved MANUAL assignments phải được gửi sang AI như fixed inputs; nếu implementation quên bước này, AI có thể tạo overstaff/conflict. Tests phải khóa behavior này.
- Duplicate unique: nếu DB có unique constraint (employee+shift), insert phải skip preserved trước; validate logic phải khớp constraint để không 500 vì `DataIntegrityViolationException` — nếu DB throw, catch → `502` + run `FAILED` (không để 500 rò ra).
- Locked AUTO assignments: giữ nguyên, không update từ AI result (theo contract "persist as existing locked records instead of duplicated").
- `OptimizationRunRecorder.finishSuccess` non-null contract: persistence chạy trước `finishSuccess`, response đã validate nên an toàn.

## 9. Non-goals / Follow-ups

- `boundaryAssignments` design + gửi liên kỳ (slice riêng). Assignment trong cùng period mà backend preserve được gửi qua `lockedAssignments` như fixed inputs ở slice này.
- Retry/timeout tuning, background/async run.
- pytest AI service theo payload thực tế (nếu cần, slice riêng với ai-service).
- Frontend trigger/monitor optimization run, hiển thị inserted/skipped counts.
- "AI overwrite all unlocked" mode (ghi đè cả MANUAL) — chỉ làm khi user yêu cầu rõ.
- Tick các mục Phase 6 còn lại ngoài danh sách §6.
