# Spec: Phase 4 Employee Swap Requests + Remaining Items

Date: 2026-09-19
Status: Draft (pending user review)
Branch: `phase4/employee-swap-requests`

## 1. Context

Phase 4 (Employee Core) trong `docs/project_plan.md` còn các mục mở:

- `API nhan vien xem profile ca nhan` (unchecked).
- `API gui yeu cau doi ca` (unchecked, chưa có code).
- `API xem yeu cau doi ca lien quan den minh` (unchecked, chưa có code).
- `Test leave request flow`, `Test preference flow`, `Test swap request flow` (unchecked trong plan, nhưng leave/preference tests đã tồn tại trong code — plan bị stale).

Hiện trạng code:

- Entity `SwapRequest` đã tồn tại (`backend/.../entity/SwapRequest.java`) với quan hệ `requesterAssignment`, `targetEmployee`, `targetAssignment` (nullable, nghĩa là "nhờ cover, không đổi ngược lại"), `reason`, `status`, `reviewedBy`, `reviewedAt`.
- Status flow của entity: `PENDING_TARGET → PENDING_APPROVAL → APPROVED / REJECTED`.
- Chưa có `SwapRequestRepository`, DTO, service, controller nào.
- `GET /api/auth/me` đã trả về `AccountResponse + EmployeeResponse` (id, employeeCode, fullName, email, phone, status) và đã có test.

## 2. Goal

Hoàn tất toàn bộ mục Phase 4 còn thiếu trong một slice duy nhất, theo TDD, không migration database.

## 3. Scope (approved)

### 3.1 Employee profile — reuse, no new endpoint

- Không thêm endpoint mới. `GET /api/auth/me` là profile API chính thức cho nhân viên.
- Bổ sung test xác nhận `/me` trả đủ `account + employee` cho role `EMPLOYEE`, rồi tick mục plan với ghi chú `(da xong: dung GET /api/auth/me)`.

### 3.2 Swap requests — full workflow, status-only

Status lifecycle trong slice này:

- `PENDING_TARGET`: vừa tạo, chờ target respond.
- `PENDING_APPROVAL`: target đã accept, chờ manager duyệt.
- `APPROVED`: manager duyệt (chỉ đổi status + lưu reviewer, không mutate assignment).
- `REJECTED`: target decline hoặc manager reject.
- `CANCELLED`: requester hủy khi còn `PENDING_TARGET`.

### 3.3 Leave / preference flow tests

- Không viết mới. Chạy lại `EmployeeLeaveRequest*Test`, `EmployeePreference*Test` trong full suite để xác nhận, rồi tick các mục plan tương ứng (ghi chú plan stale).

## 4. API Design

Tất cả response lỗi dùng `ResponseStatusException` theo convention hiện có. Request body validate bằng Bean Validation (`@Valid`).

### 4.1 Employee endpoints (`@PreAuthorize("hasRole('EMPLOYEE')")`)

| Method | Path | Body | Success | Notes |
|--------|------|------|---------|-------|
| POST | `/api/employee/swap-requests` | `CreateSwapRequestRequest` | `201` + `SwapRequestResponse` (`PENDING_TARGET`) | Requester là employee sở hữu `requesterAssignmentId` |
| GET | `/api/employee/swap-requests` | `?status=` optional | `200` + list | Các request mình gửi HOẶC mình là target |
| POST | `/api/employee/swap-requests/{id}/cancel` | — | `200` + response (`CANCELLED`) | Chỉ requester, chỉ khi `PENDING_TARGET` |
| POST | `/api/employee/swap-requests/{id}/respond` | `RespondSwapRequestRequest(accept)` | `200` + response | Chỉ target, chỉ khi `PENDING_TARGET`; `accept=true → PENDING_APPROVAL`, `false → REJECTED` |

`CreateSwapRequestRequest`:

- `requesterAssignmentId: Long` (`@NotNull`).
- `targetEmployeeId: Long` (`@NotNull`).
- `targetAssignmentId: Long` (nullable).
- `reason: String` (nullable, `@Size(max = 1000)`, trim).

### 4.2 Admin endpoints (`@PreAuthorize("hasRole('MANAGER')")`)

| Method | Path | Success | Notes |
|--------|------|---------|-------|
| GET | `/api/admin/swap-requests?status=` | `200` + list | Manager xem tất cả |
| POST | `/api/admin/swap-requests/{id}/approve` | `200` + response (`APPROVED`) | Chỉ khi `PENDING_APPROVAL`; lưu `reviewedBy` (manager account), `reviewedAt` |
| POST | `/api/admin/swap-requests/{id}/reject` | `200` + response (`REJECTED`) | Khi `PENDING_TARGET` hoặc `PENDING_APPROVAL`; lưu reviewer metadata |

### 4.3 Validation rules (shared)

- `requesterAssignment` không tồn tại hoặc không thuộc về caller → `404 Assignment not found`.
- `targetEmployee` không tồn tại → `404 Employee not found`; inactive → `400 Employee is not active`; trùng với requester → `400 Cannot target yourself`.
- `targetAssignment` (nếu có): không tồn tại → `404`; không thuộc về target → `400`; trùng `requesterAssignment` → `400`.
- Sai trạng thái cho cancel/respond/approve/reject → `400` với reason rõ ràng (ví dụ `Swap request is not pending target response`).
- Sai chủ thể: caller không phải requester (cancel), không phải target (respond) → `404 Swap request not found` (không leak sự tồn tại của resource); caller không phải manager ở admin endpoints → `403` bởi role guard.
- `employeeId/request body` null → `400 Employee id is required` / Bean Validation `400`.

## 5. Data Model

- Không thay đổi entity hiện có, không migration.
- Cột `status` là string tự do nên `CANCELLED` dùng được ngay không cần đổi schema.

## 6. Files to Create / Modify

Mới:

- `backend/.../repository/SwapRequestRepository.java` — `findDetailById`, `findRelatedForEmployee(employeeId, status?)` (fetch join assignment/employee/target).
- `backend/.../employee/dto/CreateSwapRequestRequest.java`, `RespondSwapRequestRequest.java`, `SwapRequestResponse.java` (có `from(SwapRequest)` mapper, map trong transaction).
- `backend/.../employee/EmployeeSwapRequestService.java` — dùng `EmployeeAccess` + repositories (không cần `Clock`; employee flow không set thời gian).
- `backend/.../employee/EmployeeSwapRequestController.java`.
- `backend/.../admin/AdminSwapRequestService.java` — dùng `AccountRepository` load manager account (pattern `requireManager` như `AdminShiftAssignmentService`) + inject `Clock` để set `reviewedAt` (test-friendly).
- `backend/.../admin/AdminSwapRequestController.java`.
- Tests: `EmployeeSwapRequestServiceTest`, `EmployeeSwapRequestControllerTest`, `AdminSwapRequestServiceTest`, `AdminSwapRequestControllerTest`.

Sửa:

- `docs/project_plan.md` — tick các mục Phase 4 (profile, gửi/xem swap, 3 test flow items) kèm ghi chú đã xong.

## 7. Testing Plan

- TDD: viết service test trước (create/list/cancel/respond/approve/reject + validation/error cases), watch RED, implement tối thiểu, watch GREEN.
- Controller test `@WebMvcTest`: route, status code, validation `400`, role guard (`EMPLOYEE` vs `MANAGER` vs anonymous).
- Regression: full backend suite `.\backend\mvnw.cmd -f backend/pom.xml test` phải `0 failures`; `git diff --check` sạch.
- Target baseline hiện tại: 421+ tests pass.

## 8. Risks

- Test hiện có (`AdminShiftAssignmentServiceTest`) dùng constructor `AdminShiftAssignmentService` — slice này không sửa service đó nên không ảnh hưởng.
- `EmployeeAccess.requireActiveEmployee` yêu cầu role `EMPLOYEE` — account `MANAGER` không gọi được employee endpoints, đúng theo thiết kế phân quyền hiện có.
- Lazy loading trong `SwapRequestResponse.from` — mapping thực hiện trong service `@Transactional`, theo pattern `AdminShiftAssignmentResponse.from` hiện có.

## 9. Non-goals / Follow-ups

- Auto-execute swap (hoán đổi `ShiftAssignment` giữa 2 nhân viên) khi manager approve — follow-up slice riêng, cần reuse validation manual assignment (overlap, leave, max hours/day).
- Không đổi enum/status frontend mock (`PENDING/APPROVED/REJECTED/CANCELLED`) trong slice này.
- Không làm frontend integration cho swap (thuộc Phase 9 plan).
