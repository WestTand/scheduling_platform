# Spec: Phase 6 Slice 6A — Backend AI Payload + Client (mocked)

Date: 2026-09-20
Status: Draft (pending user review)
Branch: `phase6/backend-ai-payload-client`

## 1. Context

Phase 6 (AI Scheduling) còn toàn bộ mục mở. AI service đã có:

- `POST /api/v1/scheduling/optimize` (`ai-service/app/api/scheduling.py`) nhận `SchedulingRequest` schema `1.0` và trả `SchedulingResponse` (`SUCCESS` / `INFEASIBLE` / `FAILED`).
- Solver OR-Tools với hard constraints, quotas, soft weights (`ai-service/app/solver/scheduler.py`).
- Backend có entity `OptimizationRun` (`RUNNING / SUCCESS / FAILED / INFEASIBLE`, `weightsJson`, `penaltyDetailsJson`, `totalPenalty`, `errorMessage`) nhưng chưa có repository, service, controller, hay AI client nào.
- Backend có sẵn `app.ai-service-url` config (`http://localhost:8001` default) nhưng chưa code nào dùng.
- Backend dùng `spring-boot-starter-webmvc` (không có WebFlux), Java 21.

Quyết định đã chốt với user: end-to-end chia 2 slice. Slice 6A (slice này): backend payload + client + `OptimizationRun` lifecycle, AI client mock trong test, **không** mutate `ShiftAssignment`. Slice 6B (sau): persist assignments từ AI result + pytest/contract còn thiếu.

## 2. Goal

Manager gọi một endpoint duy nhất để chạy AI scheduling cho một `SchedulePeriod` ở `DRAFT`; backend gom đúng payload schema `1.0`, gọi AI service, lưu `OptimizationRun` cho mọi outcome, và trả kết quả run về cho caller. Không thay đổi assignments trong slice này.

## 3. Scope (approved)

### 3.1 Endpoint

- `POST /api/admin/schedule-periods/{periodId}/optimize`, `@PreAuthorize("hasRole('MANAGER')")`.
- Period không tồn tại → `404 Schedule period not found`.
- Period không ở `DRAFT` → `400 Only DRAFT periods can be optimized`.
- Request body: object rỗng/optional `solverOptions` (`timeLimitSeconds`, `randomSeed`, `numWorkers`); nếu null dùng default của AI schema (`30`, `1`, `8`). Validation range theo AI schema (`timeLimitSeconds` 1–300, `numWorkers` 1–64, `randomSeed` >= 0), sai → `400`.

### 3.2 Payload builder

`AiSchedulingPayloadBuilder` gom dữ liệu và map sang DTO request schema `1.0` (camelCase JSON, `schemaVersion = "1.0"`):

- `period`: id, start/end date, versionNo, timezone `Asia/Ho_Chi_Minh` (hằng số, khớp default AI).
- `runId`: id của `OptimizationRun` vừa tạo (`RUNNING`). Vì vậy service tạo run trước, flush để có id, rồi build payload.
- `employees`: employee `ACTIVE` có account `EMPLOYEE` hợp lệ (dùng cùng tiêu chí employee mà các flow hiện có dùng). Chỉ gửi `employeeId`.
- `shiftTypes`: shift type `status = ACTIVE`. Map:
  - `durationMinutes` = `durationHours * 60` (làm tròn int; `durationHours` scale 1 nên luôn nguyên phút).
  - `overnight`, `heavy`, `minStaff`, `maxStaff` giữ nguyên.
  - `quotaCategory`: backend `REGULAR` → AI `null`; `OVERTIME`, `BATHING`, `ON_CALL` → giữ nguyên (contract đã chốt trong project plan).
  - Không gửi `penaltyWeightMultiplier` (AI schema 1.0 chưa dùng).
- `workShifts`: mọi work shift thuộc period có shift type active. Map `workShiftId`, `shiftTypeId`, `workDate`, `startsAt`, `endsAt` (persisted values, `OffsetDateTime` → aware datetime), `requiredStaff`.
- `approvedLeaves`: leave request `status = APPROVED` overlap period (`startDate <= period.endDate && endDate >= period.startDate`).
- `preferences`: preference trong period (`preferenceDate` trong `[startDate, endDate]`) và `status in (PENDING, APPROVED)` (cùng semantics active với duplicate checks hiện có). Chỉ gửi preference của employee active và shift type active (nếu `shiftType` null vẫn gửi với `shiftTypeId = null`).
- `lockedAssignments`: assignment thuộc period có `locked = true`. Map `assignmentId`, `employeeId`, `workShiftId`.
- `boundaryAssignments`: **không gửi trong Slice 6A** (để trống list). Lý do: boundary assignments phục vụ rest/quota liên kỳ, cần design riêng ở slice sau; gửi sai còn nguy hiểm hơn không gửi.
- `rules`: đọc từ `SchedulingRule` active theo rule codes hiện có:
  - hard: `MIN_REST_HOURS_BETWEEN_SHIFTS`, `MAX_WORK_HOURS_PER_DAY`, `MAX_SHIFTS_PER_WEEK` (parse `ruleValueJson.value` int).
  - quotas: `OVERTIME_SHIFTS_PER_WEEK`, `BATHING_SHIFTS_PER_WEEK`, `ON_CALL_EVERY_N_DAYS` (parse int).
  - soft weights: `FAIRNESS_WEIGHT`, `PREFERENCE_WEIGHT`, `WEEKEND_WEIGHT`, `HEAVY_SHIFT_WEIGHT`, `WORK_HOUR_BALANCE_WEIGHT` (dùng `weight` BigDecimal).
  - Rule thiếu/inactive/unparseable → `400` với reason rõ ràng, ví dụ `Missing scheduling rule: MAX_WORK_HOURS_PER_DAY`. Không dùng default âm thầm (trừ khi rule vốn optional — trong slice này mọi rule trên đều required).
- `solverOptions`: từ request body hoặc default AI schema.

Pre-call validations (trước khi tạo `OptimizationRun` và trước khi gọi AI; xem §3.4):

- Không có active employee → `400 No active employees to schedule`.
- Không có work shift schedulable trong period → `400 No work shifts in period`.
- Work shift thiếu `startsAt`/`endsAt` → `400 Work shift <id> is missing time window` (AI schema bắt buộc aware datetime; không derive fallback trong slice này).
- Nếu có work shift thuộc period trỏ tới shift type không active → `400 Work shift <id> uses inactive shift type <id>` (không gửi payload thiếu master reference).
- Nếu có locked assignment trỏ tới employee không active hoặc work shift bị loại vì inactive shift type → `400 Locked assignment <id> references inactive scheduling data` (locked assignment không được âm thầm drop).
- Approved leave/preference của employee không active được bỏ qua vì employee đó không thể được AI xếp lịch trong run hiện tại.

### 3.3 AI client

- `AiSchedulingClient` là interface với một method `SchedulingResponse optimize(SchedulingRequestDto request)`.
- Implementation `RestClientAiSchedulingClient` dùng Spring `RestClient` (có sẵn qua `spring-boot-starter-webmvc` ở Spring Boot 3.2+), base URL từ `app.ai-service-url`, POST `/api/v1/scheduling/optimize`, timeout đọc 5 phút (solver `timeLimitSeconds` tối đa 300s).
- Backend tests mock `AiSchedulingClient`; không gọi AI thật trong test.
- AI trả HTTP 4xx (ví dụ 422 invalid input): coi như lỗi client → run `FAILED`, backend trả `502` (theo quyết định: mọi AI transport/protocol failure đều 502).
- AI trả response `status = FAILED`: backend cập nhật run `FAILED` và trả `200` với run status `FAILED` (AI đã xử lý xong, chỉ là solver báo lỗi nội bộ).

### 3.4 Run lifecycle và HTTP mapping

1. Validate manager + period tồn tại + `DRAFT` (404/400).
2. Validate pre-call inputs (employees, work shifts, time windows, rules). **Fail ở bước này → 400 và KHÔNG tạo `OptimizationRun`** (run chỉ ghi lại lần chạy AI thực sự, tránh rác audit).
3. Tạo `OptimizationRun(status = RUNNING, startedAt = now(clock), startedBy = manager, schedulePeriod, schedulingRule = null, weightsJson = snapshot rules + solver options JSON)`. Flush để có id.
4. Build payload với `runId`.
5. Gọi AI client:
   - `SUCCESS` → run `SUCCESS`, `finishedAt`, `totalPenalty`, `penaltyDetailsJson`; trả `200` + response (run id, period id, aiStatus, totalPenalty, assignments count, warnings; **không** trả full assignments list nếu lớn — chỉ trả count; chi tiết xem §4).
   - `INFEASIBLE` → run `INFEASIBLE`, `finishedAt`, `errorMessage` (tổng hợp từ `diagnostics` + `errorMessage` của AI, cắt 2000 chars); trả `200`.
   - `FAILED` (AI response hợp lệ) → run `FAILED`, tương tự; trả `200`.
   - Exception/timeout/4xx/5xx/invalid body → run `FAILED` với `errorMessage`, `finishedAt`; persist failed run in a transaction that will not roll back, then throw `502 Bad Gateway` (`ResponseStatusException`). Implementation must use either a small `OptimizationRunRecorder` with `REQUIRES_NEW` transactions or an equivalent `@Transactional(noRollbackFor = ResponseStatusException.class)` design. The test must prove the failed run remains committed after the 502 path.
6. Không mutate `ShiftAssignment` trong mọi path.

`weightsJson`: snapshot JSON gồm rules đã resolve (hard + quotas + soft weights) và solver options. Không lưu full payload (có thể lớn); payload có thể reconstruct từ DB + run.

`Clock`: service inject `Clock`, `startedAt`/`finishedAt = LocalDateTime.now(clock)` (pattern `AdminLeaveRequestService`).

### 3.5 Response DTO

`OptimizeScheduleResponse` (backend → caller):

- `runId`, `periodId`, `aiStatus` (`SUCCESS` / `INFEASIBLE` / `FAILED`), `solverStatus` (từ AI, nullable khi transport failure — nhưng transport failure trả 502 nên field này luôn có khi 200), `totalPenalty` (nullable), `assignmentCount` (size của AI assignments, nullable), `warnings` (list, default empty), `errorMessage` (nullable), `startedAt`, `finishedAt`.

## 4. API Design

| Method | Path | Body | Success | Notes |
|--------|------|------|---------|-------|
| POST | `/api/admin/schedule-periods/{periodId}/optimize` | `{solverOptions?}` optional | `200` + `OptimizeScheduleResponse` | Chỉ `MANAGER`, chỉ period `DRAFT` |

Error mapping: `404` period missing; `400` non-DRAFT / bad input / solver options invalid; `502` AI transport/protocol failure. Mọi lỗi dùng `ResponseStatusException` theo convention hiện có.

Backend DTOs (package mới `scheduling.scheduling.admin.dto` hoặc `scheduling.scheduling.ai` — chốt ở plan: package riêng `ai` để chứa payload DTOs + client + builder, tránh phình `admin`):

- `AiSchedulingRequestDto` (+ nested records `Period`, `Employee`, `ShiftType`, `WorkShift`, `Leave`, `Preference`, `LockedAssignment`, `Rules`, `SolverOptions`) với `@JsonProperty` camelCase khớp AI schema, `schemaVersion = "1.0"`.
- `AiSchedulingResponseDto` (+ nested `Assignment`, `Diagnostic`, `Statistics`) parse response AI.
- `OptimizeScheduleRequest` (body optional: `solverOptions` nullable).
- `OptimizeScheduleResponse` (§3.5).

Jackson `ObjectMapper` của Spring Boot map snake/camel qua annotation; `OffsetDateTime` serialize thành ISO aware datetime mà Pydantic `AwareDatetime` parse được.

## 5. Data Model

- Không migration, không đổi entity hiện có.
- Mới: `OptimizationRunRepository extends JpaRepository<OptimizationRun, Long>`.
- Query helpers dùng repositories/methods hiện có; nếu thiếu (ví dụ approved leaves overlap period, preferences in range, locked assignments by period) thì thêm `@Query` methods vào repositories tương ứng trong slice này.

## 6. Files to Create / Modify

Mới (package `scheduling.scheduling.ai`):

- `ai/AiSchedulingClient.java` — interface `optimize`.
- `ai/RestClientAiSchedulingClient.java` — implementation bằng `RestClient`, đọc `app.ai-service-url`, map lỗi transport → `AiServiceException`.
- `ai/AiServiceException.java` — runtime exception mang `errorMessage` (service bắt → run FAILED + throw 502).
- `ai/AiSchedulingPayloadBuilder.java` — build `AiSchedulingRequestDto` từ period + runId.
- `ai/dto/AiSchedulingRequestDto.java`, `ai/dto/AiSchedulingResponseDto.java` (+ nested records).
- `admin/AdminOptimizationService.java` — orchestrate (§3.4), inject `Clock`.
- `admin/AdminOptimizationController.java` — endpoint + `@PreAuthorize("hasRole('MANAGER')")`.
- `admin/dto/OptimizeScheduleRequest.java`, `admin/dto/OptimizeScheduleResponse.java`.
- `repository/OptimizationRunRepository.java`.
- Tests: `AiSchedulingPayloadBuilderTest`, `AdminOptimizationServiceTest` (mock client + repos), `AdminOptimizationControllerTest` (`@WebMvcTest`).

Sửa:

- Repositories hiện có nếu cần thêm query methods (leave overlap, preference range, locked by period).
- `SchedulingApplicationTests`: thêm `@MockitoBean` cho beans mới nếu smoke test cần (kiểm tra khi implement).
- `docs/project_plan.md`: tick `Thiet ke request payload tu backend sang AI service` và `API backend goi AI service tao lich` sau khi slice pass (các mục AI-nhận-input tick ở Slice 6B hoặc khi payload cover — chốt ở plan).

## 7. Testing Plan

- TDD cho builder/service theo skill `test-driven-development`.
- Payload mapping tests: active-only filtering, inactive referenced shift type 400, inactive locked-assignment reference 400, cancelled/rejected preferences excluded, quota mapping `REGULAR → null`, persisted windows passthrough, approved-leave overlap, preference range, locked filter, rules resolve + missing-rule 400.
- Service tests với mocked client: SUCCESS/INFEASIBLE/FAILED/client-exception paths, run persistence assertions, 502 behavior with failed run still committed, no-assignment-mutation (verify `shiftAssignmentRepository` không có `save`, hoặc đơn giản là service không inject assignment repo).
- Controller tests: route, 200/400/404/502, role guard.
- Regression: full backend suite `.\backend\mvnw.cmd -f backend/pom.xml test`, `git diff --check`.

## 8. Risks

- `durationHours` BigDecimal scale 1 → minutes int; nếu DB có giá trị lẻ phút (không thể với scale 1 nhưng defensive) thì nhân 60 vẫn int — ghi rõ `intValueExact()` và 400 nếu overflow? Đơn giản: `(int) Math.round(hours * 60)`, không fail.
- `weightsJson` JSONB column — dùng String JSON serialize bằng ObjectMapper, pattern các JSON column hiện có.
- AI service chưa chạy khi demo: endpoint trả 502 + run FAILED — đúng thiết kế, demo cần bật ai-service hoặc mock.
- Timezone: backend gửi `OffsetDateTime` có offset; AI Pydantic parse thành aware datetime. Period timezone hằng số `Asia/Ho_Chi_Minh` chỉ là metadata cho AI, không convert thời gian ở backend.

## 9. Non-goals / Follow-ups (Slice 6B trở đi)

- Persist `ShiftAssignment` từ AI result; giữ locked assignments khi ghi; xóa assignments cũ của period.
- `boundaryAssignments` design + gửi.
- Retry/timeout tuning, background/async run.
- pytest AI service theo contract mới (nếu solver cần sửa theo payload thực tế).
- Frontend trigger/monitor optimization run.
- Tick các mục Phase 6 còn lại.
