# Backend AI Payload Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a manager-only backend optimize endpoint that builds AI scheduling payload schema `1.0`, calls the AI service through a mockable client, records `OptimizationRun` lifecycle, and does not mutate `ShiftAssignment`.

**Architecture:** Keep AI integration isolated in `scheduling.scheduling.ai`: DTOs, client, payload builder, and run recorder. Keep HTTP/admin orchestration in `scheduling.scheduling.admin`: controller/service/request/response. Use small repository query additions for the data sources and TDD at each boundary.

**Tech Stack:** Java 21, Spring Boot 4.1.1 WebMVC, Spring Security, Spring Data JPA, Jackson, JUnit 6, Mockito, AssertJ, MockMvc.

---

## File Structure

- Create `backend/src/main/java/scheduling/scheduling/repository/OptimizationRunRepository.java`: `JpaRepository<OptimizationRun, Long>`.
- Modify `backend/src/main/java/scheduling/scheduling/repository/WorkShiftRepository.java`: add `findForAiPayload(Long periodId)` period detail query for AI payload.
- Modify `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java`: add `findSchedulableForAi()` query for active employees linked to active employee accounts.
- Modify `backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java`: add `findByStatus(String status)` active shift types query.
- Modify `backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java`: add approved-overlap query.
- Modify `backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java`: add active-status in-range query.
- Modify `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java`: add locked-by-period query.
- Create `backend/src/main/java/scheduling/scheduling/ai/AiServiceException.java`: runtime exception for transport/protocol failures.
- Create `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingClient.java`: mockable interface.
- Create `backend/src/main/java/scheduling/scheduling/ai/RestClientAiSchedulingClient.java`: Spring `RestClient` implementation.
- Create `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java`: converts DB state into AI schema `1.0` DTO.
- Create `backend/src/main/java/scheduling/scheduling/ai/OptimizationRunRecorder.java`: persists run start/finish/failure with transaction semantics that survive 502 failures.
- Create `backend/src/main/java/scheduling/scheduling/ai/dto/AiSchedulingRequestDto.java`: backend-side request payload records.
- Create `backend/src/main/java/scheduling/scheduling/ai/dto/AiSchedulingResponseDto.java`: backend-side response payload records.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/OptimizeScheduleRequest.java`: optional solver options request.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/OptimizeScheduleResponse.java`: admin response DTO.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationService.java`: validate period, create run, build payload, call AI, update run, map response.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationController.java`: route `POST /api/admin/schedule-periods/{periodId}/optimize`.
- Create `backend/src/test/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilderTest.java`: mapping and validation tests.
- Create `backend/src/test/java/scheduling/scheduling/ai/OptimizationRunRecorderTransactionTest.java`: real transaction test proving a failed run remains committed after a throwing caller rolls back.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationServiceTest.java`: orchestration/run lifecycle tests.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationControllerTest.java`: route/security/error status tests.
- Modify `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`: add mocks only if context requires them.
- Modify `docs/project_plan.md`: tick Slice 6A items only after tests pass.

## Task 1: DTOs and Repository Surface

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/ai/dto/AiSchedulingRequestDto.java`
- Create: `backend/src/main/java/scheduling/scheduling/ai/dto/AiSchedulingResponseDto.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/OptimizeScheduleRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/OptimizeScheduleResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/repository/OptimizationRunRepository.java`

- [ ] **Step 1: Create AI request DTO**

Create `AiSchedulingRequestDto` with records matching the AI schema. Use nullable `String quotaCategory` for `REGULAR -> null`. Use `OffsetDateTime` for aware datetimes and `BigDecimal` for weights/penalty-like numeric values.

```java
package scheduling.scheduling.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AiSchedulingRequestDto(
        String schemaVersion,
        Long runId,
        PeriodDto period,
        List<EmployeeDto> employees,
        List<ShiftTypeDto> shiftTypes,
        List<WorkShiftDto> workShifts,
        List<ApprovedLeaveDto> approvedLeaves,
        List<PreferenceDto> preferences,
        List<LockedAssignmentDto> lockedAssignments,
        List<BoundaryAssignmentDto> boundaryAssignments,
        RulesDto rules,
        SolverOptionsDto solverOptions) {

    public record PeriodDto(Long periodId, LocalDate startDate, LocalDate endDate, Integer versionNo, String timezone) {}
    public record EmployeeDto(Long employeeId) {}
    public record ShiftTypeDto(Long shiftTypeId, Integer durationMinutes, Boolean overnight,
                               Boolean heavy, Integer minStaff, Integer maxStaff, String quotaCategory) {}
    public record WorkShiftDto(Long workShiftId, Long shiftTypeId, LocalDate workDate, OffsetDateTime startsAt,
                               OffsetDateTime endsAt, Integer requiredStaff) {}
    public record ApprovedLeaveDto(Long leaveRequestId, Long employeeId, LocalDate startDate, LocalDate endDate) {}
    public record PreferenceDto(Long preferenceId, Long employeeId, Long shiftTypeId, LocalDate preferenceDate, String preferenceType,
                                Integer priority) {}
    public record LockedAssignmentDto(Long assignmentId, Long employeeId, Long workShiftId) {}
    public record BoundaryAssignmentDto(Long employeeId, Long shiftTypeId, OffsetDateTime startsAt, OffsetDateTime endsAt) {}
    public record RulesDto(HardRulesDto hardRules, QuotasDto quotas, SoftWeightsDto softWeights) {}
    public record HardRulesDto(Integer minRestHoursBetweenShifts, Integer maxWorkHoursPerDay, Integer maxShiftsPerWeek) {}
    public record QuotasDto(Integer overtimeShiftsPerWeek, Integer bathingShiftsPerWeek, Integer onCallEveryNDays) {}
    public record SoftWeightsDto(BigDecimal fairness, BigDecimal preference, BigDecimal weekend,
                                 BigDecimal heavyShift, BigDecimal workHourBalance) {}
    public record SolverOptionsDto(Integer timeLimitSeconds, Integer randomSeed, Integer numWorkers) {}
}
```

Backend intentionally accepts whole-second `timeLimitSeconds` only, even though the AI schema type is numeric/float. Keep this as `Integer` and reject fractional values at JSON binding/validation time.

- [ ] **Step 2: Create AI response DTO**

```java
package scheduling.scheduling.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AiSchedulingResponseDto(
        String schemaVersion,
        Long runId,
        Long periodId,
        String status,
        String solverStatus,
        List<AssignmentDto> assignments,
        BigDecimal totalPenalty,
        Map<String, BigDecimal> penaltyDetails,
        List<String> warnings,
        List<DiagnosticDto> diagnostics,
        String errorMessage,
        StatisticsDto statistics) {

    public record AssignmentDto(Long employeeId, Long workShiftId) {}
    public record DiagnosticDto(String code, String message, Long employeeId, Long workShiftId) {}
    public record StatisticsDto(BigDecimal wallTimeSeconds, Integer conflicts, Integer branches) {}
}
```

- [ ] **Step 3: Create admin request/response DTOs**

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.Valid;
import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;

public record OptimizeScheduleRequest(@Valid AiSchedulingRequestDto.SolverOptionsDto solverOptions) {}
```

```java
package scheduling.scheduling.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OptimizeScheduleResponse(
        Long runId,
        Long periodId,
        String aiStatus,
        String solverStatus,
        BigDecimal totalPenalty,
        Integer assignmentCount,
        List<String> warnings,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {}
```

- [ ] **Step 4: Create optimization run repository**

```java
package scheduling.scheduling.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.OptimizationRun;

public interface OptimizationRunRepository extends JpaRepository<OptimizationRun, Long> {
}
```

- [ ] **Step 5: Compile the minimal surface**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test -DskipTests`

Expected: compile succeeds. If `@Valid` on nested record does not compile because of package import issues, keep `OptimizeScheduleRequest` but move solver validation into service.

- [ ] **Step 6: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/ai/dto backend/src/main/java/scheduling/scheduling/admin/dto/OptimizeScheduleRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/OptimizeScheduleResponse.java backend/src/main/java/scheduling/scheduling/repository/OptimizationRunRepository.java && git commit -m "feat: add AI scheduling DTO surface"`

## Task 2: Repository Queries for Payload Inputs

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/WorkShiftRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java`

- [ ] **Step 1: Add query methods**

Add methods using established repository style. Exact signatures to use from builder:

```java
@Query("""
        select e from Employee e
        join fetch e.account a
        where e.status = 'ACTIVE'
          and a.role = scheduling.scheduling.entity.AccountRole.EMPLOYEE
          and a.status = scheduling.scheduling.entity.AccountStatus.ACTIVE
          and a.isActive = true
        order by e.employeeCode asc
        """)
List<Employee> findSchedulableForAi();
List<ShiftType> findByStatus(String status);
@Query("""
        select ws from WorkShift ws
        join fetch ws.shiftType
        where ws.schedulePeriod.id = :periodId
        order by ws.workDate asc, ws.id asc
        """)
List<WorkShift> findForAiPayload(@Param("periodId") Long periodId);
@Query("""
        select lr from LeaveRequest lr
        join fetch lr.employee e
        where lr.status = 'APPROVED'
          and lr.startDate <= :periodEnd
          and lr.endDate >= :periodStart
        order by lr.startDate asc, lr.id asc
        """)
List<LeaveRequest> findApprovedOverlapping(@Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);
@Query("""
        select p from Preference p
        join fetch p.employee e
        left join fetch p.shiftType st
        where p.preferenceDate between :periodStart and :periodEnd
          and p.status in ('PENDING', 'APPROVED')
        order by p.preferenceDate asc, p.id asc
        """)
List<Preference> findActiveInRange(@Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);
@Query("""
        select sa from ShiftAssignment sa
        join fetch sa.employee e
        join fetch sa.workShift ws
        join fetch ws.shiftType st
        where ws.schedulePeriod.id = :periodId
          and sa.locked = true
        order by sa.id asc
        """)
List<ShiftAssignment> findLockedForPeriod(@Param("periodId") Long periodId);
```

Do not add duplicate `findByStatus(String status)` if it already exists in `ShiftTypeRepository`; use the existing method name in `AiSchedulingPayloadBuilder`. Always add `findSchedulableForAi()` to `EmployeeRepository` because employee scheduling requires account role/status checks, not only employee status.

- [ ] **Step 2: Compile repository methods**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test -DskipTests`

Expected: compile succeeds and Spring Data query parsing succeeds if tests start context later.

- [ ] **Step 3: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/repository && git commit -m "feat: add AI scheduling payload queries"`

## Task 3: Payload Builder With TDD

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java`
- Create: `backend/src/test/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilderTest.java`

- [ ] **Step 1: Write failing happy-path builder test**

Create a unit test with mocked repositories. Assert `schemaVersion`, `runId`, `timezone`, quota mapping `REGULAR -> null`, persisted `startsAt`/`endsAt`, `leaveRequestId`, `preferenceId`, empty `boundaryAssignments`, and resolved rules/options.

Use method name: `buildsPayloadFromDraftPeriodData()`.

Expected assertions:

```java
assertThat(payload.schemaVersion()).isEqualTo("1.0");
assertThat(payload.runId()).isEqualTo(900L);
assertThat(payload.period().timezone()).isEqualTo("Asia/Ho_Chi_Minh");
assertThat(payload.shiftTypes()).extracting(AiSchedulingRequestDto.ShiftTypeDto::quotaCategory).containsExactly((String) null);
assertThat(payload.workShifts()).extracting(AiSchedulingRequestDto.WorkShiftDto::startsAt).containsExactly(startsAt);
assertThat(payload.approvedLeaves()).extracting(AiSchedulingRequestDto.ApprovedLeaveDto::leaveRequestId).containsExactly(700L);
assertThat(payload.preferences()).extracting(AiSchedulingRequestDto.PreferenceDto::preferenceId).containsExactly(800L);
assertThat(payload.boundaryAssignments()).isEmpty();
assertThat(payload.rules().hardRules().maxWorkHoursPerDay()).isEqualTo(12);
assertThat(payload.solverOptions().timeLimitSeconds()).isEqualTo(30);
```

- [ ] **Step 2: Run the failing test**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AiSchedulingPayloadBuilderTest#buildsPayloadFromDraftPeriodData test`

Expected: FAIL because `AiSchedulingPayloadBuilder` does not exist.

- [ ] **Step 3: Implement minimal builder**

Constructor dependencies: `EmployeeRepository`, `ShiftTypeRepository`, `WorkShiftRepository`, `LeaveRequestRepository`, `PreferenceRepository`, `ShiftAssignmentRepository`, `SchedulingRuleRepository`, `ObjectMapper`.

Public method:

```java
public void validateInputs(SchedulePeriod period, AiSchedulingRequestDto.SolverOptionsDto solverOptions)
public Map<String, Object> weightsSnapshot(SchedulePeriod period, AiSchedulingRequestDto.SolverOptionsDto solverOptions)
public AiSchedulingRequestDto build(SchedulePeriod period, Long runId, AiSchedulingRequestDto.SolverOptionsDto solverOptions)
```

Implementation rules:

- Default solver options: `(30, 1, 8)` if request options is null.
- Validate integer `timeLimitSeconds` 1-300, `numWorkers` 1-64, `randomSeed >= 0`; throw `ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid solver options")`.
- Parse hard/quota rule value by reading `ruleValueJson` object field `value` using `ObjectMapper.readTree`.
- Throw `ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing scheduling rule: " + code)` for missing/inactive/unparseable rules.
- Build active employee id set from `employeeRepository.findSchedulableForAi()` and active shift type id set from `shiftTypeRepository.findByStatus("ACTIVE")`.
- Throw `400 No active employees to schedule` if no active employees.
- Throw `400 No work shifts in period` if no work shifts.
- Throw `400 Work shift <id> uses inactive shift type <id>` if any period work shift references inactive shift type.
- Throw `400 Work shift <id> is missing time window` if `startsAt` or `endsAt` null.
- Throw `400 Locked assignment <id> references inactive scheduling data` for locked assignment with inactive employee or invalid work shift.
- Drop approved leaves/preferences for inactive employees; drop preferences for inactive non-null shift types.
- Implement `validateInputs(...)`, `weightsSnapshot(...)`, and `build(...)` through one private `PayloadSnapshot loadAndValidate(...)` helper so validation, rules, and build cannot drift. `weightsSnapshot(...)` returns a map with keys `rules` and `solverOptions` and values matching the final DTO values.

- [ ] **Step 4: Run happy-path test**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AiSchedulingPayloadBuilderTest#buildsPayloadFromDraftPeriodData test`

Expected: PASS.

- [ ] **Step 5: Add failing validation/filter tests**

Add tests with these exact names:

```java
@Test void rejectsTimeLimitZeroBeforeCallingAiSchema()
@Test void rejectsInactiveShiftTypeReferencedByWorkShift()
@Test void rejectsLockedAssignmentReferencingInactiveEmployee()
@Test void excludesCancelledAndRejectedPreferences()
@Test void rejectsMissingRequiredRule()
```

Each thrown exception assertion should verify `ResponseStatusException` status is `HttpStatus.BAD_REQUEST`.

- [ ] **Step 6: Run validation/filter tests and fix builder**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AiSchedulingPayloadBuilderTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java backend/src/test/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilderTest.java && git commit -m "feat: build AI scheduling payload"`

## Task 4: AI Client and Run Recorder

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/ai/AiServiceException.java`
- Create: `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingClient.java`
- Create: `backend/src/main/java/scheduling/scheduling/ai/RestClientAiSchedulingClient.java`
- Create: `backend/src/main/java/scheduling/scheduling/ai/OptimizationRunRecorder.java`
- Create: `backend/src/test/java/scheduling/scheduling/ai/OptimizationRunRecorderTransactionTest.java`

- [ ] **Step 1: Implement client interface and exception**

```java
package scheduling.scheduling.ai;

public class AiServiceException extends RuntimeException {
    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiServiceException(String message) {
        super(message);
    }
}
```

```java
package scheduling.scheduling.ai;

import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;
import scheduling.scheduling.ai.dto.AiSchedulingResponseDto;

public interface AiSchedulingClient {
    AiSchedulingResponseDto optimize(AiSchedulingRequestDto request);
}
```

- [ ] **Step 2: Implement RestClient client**

Use `@Value("${app.ai-service-url}") String aiServiceUrl`. Build a `RestClient` with a `SimpleClientHttpRequestFactory` configured with 5-minute connect/read timeouts, then POST to `/api/v1/scheduling/optimize`. Catch `RestClientException` and null body as `AiServiceException`. If response body is null, throw `AiServiceException("AI service returned empty response")`. The client only handles transport/deserialization failures; semantic protocol validation happens in `AdminOptimizationService` after the run has been created, so a protocol-invalid body can be recorded as run `FAILED` before returning 502.

```java
SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
requestFactory.setConnectTimeout(Duration.ofSeconds(30));
requestFactory.setReadTimeout(Duration.ofMinutes(5));
this.restClient = RestClient.builder()
        .baseUrl(aiServiceUrl)
        .requestFactory(requestFactory)
        .build();
```

- [ ] **Step 3: Implement run recorder**

Use `@Service`. Inject `OptimizationRunRepository`, `Clock`, `ObjectMapper`. Methods:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public OptimizationRun start(SchedulePeriod period, Account manager, Object weightsSnapshot)

@Transactional(propagation = Propagation.REQUIRES_NEW)
public OptimizationRun finishSuccess(Long runId, AiSchedulingResponseDto response)

@Transactional(propagation = Propagation.REQUIRES_NEW)
public OptimizationRun finishFailure(Long runId, String status, String errorMessage, AiSchedulingResponseDto response)
```

Set `startedAt` on start and `finishedAt` on finish with `LocalDateTime.now(clock)`. Serialize weights snapshot and `response.penaltyDetails()` with `ObjectMapper.writeValueAsString` only when `response != null`; for transport/protocol failures where `response == null`, set `penaltyDetailsJson = null`. Truncate `errorMessage` to 2000 chars.

- [ ] **Step 4: Add transaction persistence test**

Create `OptimizationRunRecorderTransactionTest` as a Spring Boot test using real repositories. Add an inner `@TestConfiguration` service that starts a run, calls `finishFailure(...)`, then throws `ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable")` from an outer `@Transactional` method. Assert after catching the exception that `optimizationRunRepository.findAll()` contains one run with `status = "FAILED"` and non-null `finishedAt`. This proves `REQUIRES_NEW` survives caller rollback.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=OptimizationRunRecorderTransactionTest test`

Expected: PASS.

- [ ] **Step 5: Compile**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test -DskipTests`

Expected: PASS compile.

- [ ] **Step 6: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/ai backend/src/test/java/scheduling/scheduling/ai/OptimizationRunRecorderTransactionTest.java && git commit -m "feat: add AI scheduling client and run recorder"`

## Task 5: Admin Optimization Service With TDD

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationService.java`
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationServiceTest.java`

- [ ] **Step 1: Write failing SUCCESS service test**

Test name: `optimizeCreatesRunCallsAiAndReturnsSuccessWithoutMutatingAssignments()`.

Mock dependencies: `AccountRepository`, `SchedulePeriodRepository`, `AiSchedulingPayloadBuilder`, `OptimizationRunRecorder`, `AiSchedulingClient`. Do not inject `ShiftAssignmentRepository` into service.

Expected: manager required, period `DRAFT`, recorder `start`, builder `build`, client `optimize`, recorder `finishSuccess`, response `aiStatus=SUCCESS`, `assignmentCount=2`.

- [ ] **Step 2: Run failing test**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AdminOptimizationServiceTest#optimizeCreatesRunCallsAiAndReturnsSuccessWithoutMutatingAssignments test`

Expected: FAIL because service does not exist.

- [ ] **Step 3: Implement service**

Constructor dependencies: `AccountRepository`, `SchedulePeriodRepository`, `AiSchedulingPayloadBuilder`, `OptimizationRunRecorder`, `AiSchedulingClient`.

Public method:

```java
public OptimizeScheduleResponse optimize(CurrentAccountPrincipal principal, Long periodId, OptimizeScheduleRequest request)
```

Rules:

- `requireManager` copied from admin service pattern.
- `findDetailById(periodId)` and 404 `Schedule period not found`.
- If status != `DRAFT`, throw 400 `Only DRAFT periods can be optimized`.
- Call `payloadBuilder.validateInputs(period, options)` before creating a run. If this throws 400, verify no `OptimizationRun` is created.
- Call `optimizationRunRecorder.start(period, manager, payloadBuilder.weightsSnapshot(period, options))` to create and flush the run.
- Call `payloadBuilder.build(period, run.getId(), options)` after run creation so the AI payload includes the persisted `runId`.
- Call client and update run by AI status.
- Validate response protocol before treating it as a normal AI outcome. Required checks: `schemaVersion == "1.0"`, `runId.equals(run.getId())`, `periodId.equals(period.getId())`, `status in ["SUCCESS", "INFEASIBLE", "FAILED"]`, `solverStatus in ["OPTIMAL", "FEASIBLE", "INFEASIBLE", "UNKNOWN", "MODEL_INVALID"]`, `assignments != null`, `penaltyDetails != null`, `warnings != null`, `diagnostics != null`, `statistics != null`, and for `SUCCESS`, `totalPenalty != null`. If validation fails, call `optimizationRunRecorder.finishFailure(run.getId(), "FAILED", "Invalid AI response: " + reason, null)` and throw `ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid AI response")`.
- For `AiServiceException`, call recorder `finishFailure(run.getId(), "FAILED", ex.getMessage(), null)` then throw `ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable")`.

- [ ] **Step 4: Add failing outcome tests**

Add tests:

```java
@Test void optimizeReturnsInfeasibleAndStoresDiagnostics()
@Test void optimizeReturnsFailedWhenAiReturnsFailedStatus()
@Test void optimizeThrows502AndRecordsFailedRunWhenClientFails()
@Test void optimizeThrows502AndRecordsFailedRunWhenAiResponseIsInvalid()
@Test void optimizeRejectsNonDraftPeriodBeforeCreatingRun()
@Test void optimizeRejectsMissingPeriod()
```

Assertions: 502 path verifies `optimizationRunRecorder.finishFailure(...)` is called before exception; committed persistence is covered by `OptimizationRunRecorderTransactionTest` in Task 4. Non-DRAFT/missing period verifies recorder/client have no interactions.

- [ ] **Step 5: Run service tests**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AdminOptimizationServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationService.java backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationServiceTest.java && git commit -m "feat: orchestrate AI optimization runs"`

## Task 6: Admin Controller With TDD

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationController.java`
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Use `@WebMvcTest(AdminOptimizationController.class)` with same imports as `AdminSchedulePeriodControllerTest`:

```java
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
```

Add tests:

```java
@Test void managerOptimizesSchedulePeriod()
@Test void employeeCannotOptimizeSchedulePeriod()
@Test void propagatesBadGatewayWhenAiTransportFails()
@Test void validatesSolverOptionsJson()
```

- [ ] **Step 2: Run failing controller test**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AdminOptimizationControllerTest test`

Expected: FAIL because controller does not exist.

- [ ] **Step 3: Implement controller**

```java
@RestController
@RequestMapping("/api/admin/schedule-periods")
@PreAuthorize("hasRole('MANAGER')")
public class AdminOptimizationController {
    private final AdminOptimizationService adminOptimizationService;

    public AdminOptimizationController(AdminOptimizationService adminOptimizationService) {
        this.adminOptimizationService = adminOptimizationService;
    }

    @PostMapping("/{periodId}/optimize")
    ResponseEntity<OptimizeScheduleResponse> optimize(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId,
            @Valid @RequestBody(required = false) OptimizeScheduleRequest request) {
        return ResponseEntity.ok(adminOptimizationService.optimize(principal, periodId, request));
    }
}
```

- [ ] **Step 4: Run controller tests**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AdminOptimizationControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/admin/AdminOptimizationController.java backend/src/test/java/scheduling/scheduling/admin/AdminOptimizationControllerTest.java && git commit -m "feat: expose AI optimization endpoint"`

## Task 7: Context Smoke Test and Project Plan Update

**Files:**
- Modify: `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`
- Modify: `docs/project_plan.md`

- [ ] **Step 1: Run context test**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=SchedulingApplicationTests test`

Expected: PASS. If it fails because new beans need mocks/config, add the minimal `@MockitoBean` entries or test property for `app.ai-service-url=http://localhost:8001`.

- [ ] **Step 2: Update project plan**

Tick only Slice 6A backend items:

- `Thiet ke request payload tu backend sang AI service`
- `API backend goi AI service tao lich`

Do not tick assignment persistence/frontend/deployment items.

- [ ] **Step 3: Commit**

Run: `git add backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java docs/project_plan.md && git commit -m "docs: mark backend AI payload slice progress"`

If `SchedulingApplicationTests.java` did not change, run: `git add docs/project_plan.md && git commit -m "docs: mark backend AI payload slice progress"`.

## Task 8: Full Verification

**Files:**
- No source edits expected unless verification reveals failures.

- [ ] **Step 1: Run backend suite**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test`

Expected: all tests pass.

- [ ] **Step 2: Run diff check**

Run: `git diff --check`

Expected: no whitespace errors.

- [ ] **Step 3: Inspect worktree**

Run: `git status --short --branch`

Expected: branch `phase6/backend-ai-payload-client`; no tracked modifications. Existing unrelated untracked docs may remain:

```text
?? docs/quy_dinh_ca_lam_viec.md
?? docs/Đặc tả_Phát.txt
```

- [ ] **Step 4: Final commit only if verification fixes were needed**

If verification required code fixes, commit them with a focused message. Otherwise do not create an empty commit.

## Self-Review Notes

- Spec coverage: endpoint, payload mapping, rules, solver options, 502 persistence, no assignment mutation, tests, and project-plan update are each covered by tasks.
- Placeholder scan: no `TBD`, `TODO`, or vague “handle edge cases” steps remain; edge cases are named in Task 3 and Task 5.
- Type consistency: DTO and service names match across tasks; `OptimizeScheduleRequest` uses `AiSchedulingRequestDto.SolverOptionsDto` consistently.
