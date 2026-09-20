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

### 3.3 Validation trước insert

Dùng cùng snapshot đã build payload (không query lại toàn bộ nếu có thể):

- `workShiftId` phải thuộc period (`snapshot` work shifts).
- `employeeId` phải thuộc schedulable set (`snapshot` employees).
- Work shift phải có shift type active (đã validate ở 6A; nếu AI trả assignment cho shift bị loại thì fail apply).
- Preserved assignments load sau delete để check duplicate chính xác.
- Không cần re-validate leave/overlap/daily-hours ở backend: solver đã enforce; backend chỉ validate referential integrity + duplicate.

### 3.4 Transaction và run lifecycle

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

### 3.5 Response DTO (giữ nguyên)

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
- Tests: `AiAssignmentPersistenceServiceTest` (mock repos, transactional semantics kiểm tra qua interaction + rollback behavior), extend `AdminOptimizationServiceTest` (`SUCCESS` gọi persistence trước `finishSuccess`; persistence failure → run `FAILED` + `502`; non-SUCCESS không gọi persistence).

Sửa:

- `admin/AdminOptimizationService.java` — inject persistence service, gọi sau protocol pass khi `SUCCESS`, catch apply failure → `finishFailure` + `502`.
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
- Persistence unit tests: delete-unlocked-AUTO-only, preserve MANUAL + locked AUTO, insert AUTO/ASSIGNED/unlocked, skip preserved pairs, reject unknown employee/shift với rollback (verify `save` không gọi hoặc interaction đúng).
- Service tests với mocked client + mocked persistence: `SUCCESS` order (protocol → persist → `finishSuccess`); persist throw → `finishFailure("FAILED", contains "Failed to apply AI assignments")` + `502`; `INFEASIBLE`/`FAILED`/transport-failure không gọi persistence (`verifyNoInteractions` hoặc `never`).
- Controller tests: giữ nguyên (response shape không đổi); thêm case nếu behavior HTTP đổi (không dự kiến).
- Regression: full backend suite `.\backend\mvnw.cmd -f backend/pom.xml test` (plain command, UTC đã pin trong surefire), `git diff --check`.

## 8. Risks

- Bulk delete + insert trong một transaction dài: period lớn có thể chậm; slice này chấp nhận (optimize vốn đồng bộ), ghi chú cho async slice sau.
- AI trả assignment cho shift/employee ngoài snapshot (solver bug hoặc data race): fail apply + `502` có thể gây khó chịu; message phải nêu rõ cặp id lỗi để manager debug.
- Duplicate unique: nếu DB có unique constraint (employee+shift), insert phải skip preserved trước; validate logic phải khớp constraint để không 500 vì `DataIntegrityViolationException` — nếu DB throw, catch → `502` + run `FAILED` (không để 500 rò ra).
- Locked AUTO assignments: giữ nguyên, không update từ AI result (theo contract "persist as existing locked records instead of duplicated").
- `OptimizationRunRecorder.finishSuccess` non-null contract: persistence chạy trước `finishSuccess`, response đã validate nên an toàn.

## 9. Non-goals / Follow-ups

- `boundaryAssignments` design + gửi (slice riêng).
- Retry/timeout tuning, background/async run.
- pytest AI service theo payload thực tế (nếu cần, slice riêng với ai-service).
- Frontend trigger/monitor optimization run, hiển thị inserted/skipped counts.
- "AI overwrite all unlocked" mode (ghi đè cả MANUAL) — chỉ làm khi user yêu cầu rõ.
- Tick các mục Phase 6 còn lại ngoài danh sách §6.
