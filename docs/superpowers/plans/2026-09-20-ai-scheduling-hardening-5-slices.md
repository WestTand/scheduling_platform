# AI Scheduling Hardening 5 Slices Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining production-hardening gaps after Phase 6 by adding boundary assignments, period-level penalty sync, JSONB-safe scheduling rules, AI client retry/timeout controls, and richer infeasible diagnostics.

**Architecture:** Keep the current endpoint and schema `1.0`. Each slice is independently shippable: backend payload enrichment, backend persistence sync, backend JSONB fix, backend transport hardening, and AI diagnostics. Tests prove behavior at the smallest reliable level first, then run focused integration tests and full verification.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JPA, PostgreSQL JSONB, JUnit 6, Mockito, AssertJ, Python FastAPI, OR-Tools CP-SAT, pytest.

---

## Scope And Ordering

1. **Slice 6C - Boundary assignments:** Backend sends assignments near the period boundary through existing `boundaryAssignments` so AI can enforce rest/quota context.
2. **Slice 6D - Period penalty sync:** Backend copies successful run penalty to `SchedulePeriod.totalPenalty`.
3. **Slice 6E - SchedulingRule JSONB mapping:** Fix real Postgres writes for `SchedulingRule.ruleValueJson`.
4. **Slice 6F - AI client retry/timeout:** Make AI HTTP transport configurable and retry transient failures.
5. **Slice 6G - Rich infeasible diagnostics:** Add actionable AI diagnostics for common infeasibility causes.

Do the slices in this order. Slice 6E is intentionally after 6D because it is a technical fix that may affect rule service tests but should not block boundary/penalty behavior.

## File Structure

- Modify `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java`: add boundary-assignment query by date window and optional period exclusion.
- Modify `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java`: load boundary assignments, map them to `BoundaryAssignmentDto`, extend `PayloadSnapshot`.
- Modify `backend/src/test/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilderTest.java`: boundary payload tests.
- Modify `backend/src/main/java/scheduling/scheduling/ai/OptimizationRunRecorder.java`: update period penalty on success.
- Modify `backend/src/test/java/scheduling/scheduling/ai/OptimizationRunRecorderTransactionTest.java`: real DB proof for period penalty sync.
- Modify `backend/src/main/java/scheduling/scheduling/entity/SchedulingRule.java`: use Hibernate JSON type for `ruleValueJson`.
- Create `backend/src/test/java/scheduling/scheduling/entity/SchedulingRuleJsonbMappingTest.java`: real Postgres JSONB mapping regression test.
- Modify `backend/src/main/java/scheduling/scheduling/ai/RestClientAiSchedulingClient.java`: configurable connect/read timeout and retry loop.
- Create `backend/src/test/java/scheduling/scheduling/ai/RestClientAiSchedulingClientTest.java`: fake HTTP server tests for retry/timeout mapping.
- Modify `ai-service/app/solver/scheduler.py`: richer infeasible diagnostics.
- Modify `ai-service/tests/test_scheduling.py`: diagnostics tests.
- Modify `docs/project_plan.md` only if adding explicit follow-up checklist notes; do not untick completed Phase 6 items.

## Slice 6C: Boundary Assignments

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java`
- Modify: `backend/src/test/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilderTest.java`

- [ ] **Step 1: Add failing builder test for previous-period boundary assignments**

In `AiSchedulingPayloadBuilderTest.java`, add:

```java
@Test
void includesAssignmentsBeforePeriodAsBoundaryAssignments() {
    Fixture fixture = happyPath();
    WorkShift previousShift = WorkShift.builder()
            .workDate(fixture.period().getStartDate().minusDays(1))
            .requiredStaff(1)
            .schedulePeriod(SchedulePeriod.builder()
                    .periodName("Previous Period")
                    .startDate(fixture.period().getStartDate().minusDays(7))
                    .endDate(fixture.period().getStartDate().minusDays(1))
                    .status("PUBLISHED")
                    .build())
            .shiftType(fixture.shiftType())
            .startsAt(OffsetDateTime.of(fixture.period().getStartDate().minusDays(1), LocalTime.of(20, 0), ZoneOffset.ofHours(7)))
            .endsAt(OffsetDateTime.of(fixture.period().getStartDate(), LocalTime.of(4, 0), ZoneOffset.ofHours(7)))
            .build();
    previousShift.setId(901L);
    ShiftAssignment previousAssignment = ShiftAssignment.builder()
            .employee(fixture.employee())
            .workShift(previousShift)
            .assignmentStatus("ASSIGNED")
            .assignedSource("AUTO")
            .locked(false)
            .build();
    previousAssignment.setId(701L);
    when(shiftAssignmentRepository.findBoundaryForAiPayload(
            eq(5L), eq(fixture.period().getStartDate().minusDays(1)), eq(fixture.period().getEndDate().plusDays(1))))
            .thenReturn(List.of(previousAssignment));

    AiSchedulingRequestDto payload = builder.build(fixture.period(), 900L, null);

    assertThat(payload.boundaryAssignments()).containsExactly(new AiSchedulingRequestDto.BoundaryAssignmentDto(
            fixture.employee().getId(), fixture.shiftType().getId(), previousShift.getStartsAt(), previousShift.getEndsAt()));
}
```

Add imports `java.time.LocalTime` and `java.time.ZoneOffset`. `OffsetDateTime` is already used by the fixture record; keep a single import.

- [ ] **Step 2: Run test and verify RED**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=AiSchedulingPayloadBuilderTest#includesAssignmentsBeforePeriodAsBoundaryAssignments test`

Expected: FAIL because `findBoundaryForAiPayload` does not exist or `payload.boundaryAssignments()` is empty.

- [ ] **Step 3: Add repository query**

In `ShiftAssignmentRepository.java`, add:

```java
@Query("""
        select sa
        from ShiftAssignment sa
        join fetch sa.workShift ws
        join fetch ws.schedulePeriod sp
        join fetch ws.shiftType st
        join fetch sa.employee e
        where sp.id <> :periodId
          and ws.workDate between :from and :to
        order by ws.workDate asc, st.startTime asc, sa.id asc
        """)
List<ShiftAssignment> findBoundaryForAiPayload(
        @Param("periodId") Long periodId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to);
```

This intentionally uses a 1-day buffer before and after the period. It is enough for current `minRestHoursBetweenShifts = 12`; if future rules exceed 24h, widen this window in a separate slice.

- [ ] **Step 4: Map boundary assignments in the builder**

In `AiSchedulingPayloadBuilder.java`:

1. In `loadAndValidate`, after fixed assignment validation, load:

```java
List<ShiftAssignment> boundaryAssignments = shiftAssignmentRepository.findBoundaryForAiPayload(
        period.getId(), period.getStartDate().minusDays(1), period.getEndDate().plusDays(1));
if (boundaryAssignments == null) {
    boundaryAssignments = List.of();
}
List<ShiftAssignment> schedulableBoundaryAssignments = boundaryAssignments.stream()
        .filter(assignment -> assignment.getEmployee() != null
                && activeEmployeeIds.contains(assignment.getEmployee().getId()))
        .filter(assignment -> assignment.getWorkShift() != null
                && assignment.getWorkShift().getShiftType() != null
                && activeShiftTypeIds.contains(assignment.getWorkShift().getShiftType().getId()))
        .filter(assignment -> assignment.getWorkShift().getStartsAt() != null
                && assignment.getWorkShift().getEndsAt() != null)
        .toList();
```

2. Add this list to `PayloadSnapshot` immediately after `fixedAssignments`:

```java
List<ShiftAssignment> boundaryAssignments,
```

3. Pass `schedulableBoundaryAssignments` when constructing `PayloadSnapshot`.

4. In `buildFrom`, replace `List.of()` at the `boundaryAssignments` position with:

```java
snapshot.boundaryAssignments().stream()
        .map(assignment -> new AiSchedulingRequestDto.BoundaryAssignmentDto(
                assignment.getEmployee().getId(),
                assignment.getWorkShift().getShiftType().getId(),
                assignment.getWorkShift().getStartsAt(),
                assignment.getWorkShift().getEndsAt()))
        .toList(),
```

- [ ] **Step 5: Update existing snapshot test helpers**

Every `new PayloadSnapshot(...)` construction in tests must insert `List.of()` for boundary assignments after fixed assignments. Use grep:

Run: `rg "new AiSchedulingPayloadBuilder\.PayloadSnapshot" backend/src/test/java backend/src/main/java`

Expected: every construction has the new parameter.

- [ ] **Step 6: Add repository execution test for boundary query**

In `ShiftAssignmentRepositoryFixedQueryTest.java`, add a second test `findBoundaryForAiPayloadReturnsOnlyAssignmentsOutsideTargetPeriodInWindow` using its existing `@Transactional` real DB setup style. Seed:
- target period in Feb 2031
- previous period with a shift on target start minus one day and assignment: included
- target-period shift with assignment: excluded
- far previous shift outside one-day window: excluded

Assert IDs exactly:

```java
assertThat(result).extracting(ShiftAssignment::getId).containsExactly(previousAssignment.getId());
```

- [ ] **Step 7: Run tests**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest='AiSchedulingPayloadBuilderTest,ShiftAssignmentRepositoryFixedQueryTest' test`

Expected: PASS.

- [ ] **Step 8: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java backend/src/main/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilder.java backend/src/test/java/scheduling/scheduling/ai/AiSchedulingPayloadBuilderTest.java backend/src/test/java/scheduling/scheduling/ai/ShiftAssignmentRepositoryFixedQueryTest.java && git commit -m "feat: send boundary assignments to AI"`

## Slice 6D: Period Penalty Sync

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/ai/OptimizationRunRecorder.java`
- Modify: `backend/src/test/java/scheduling/scheduling/ai/OptimizationRunRecorderTransactionTest.java`

- [ ] **Step 1: Write failing transaction test**

In `OptimizationRunRecorderTransactionTest.java`, add:

```java
@Test
void finishSuccessUpdatesSchedulePeriodTotalPenalty() {
    Account manager = accountRepository.save(Account.builder()
            .username("tx-penalty-manager")
            .passwordHash("hash")
            .role(AccountRole.MANAGER)
            .build());
    SchedulePeriod period = schedulePeriodRepository.save(SchedulePeriod.builder()
            .periodName("TX Penalty Period")
            .startDate(LocalDate.of(2033, 1, 1))
            .endDate(LocalDate.of(2033, 1, 7))
            .versionNo(1)
            .status("DRAFT")
            .createdBy(manager)
            .build());
    OptimizationRun run = recorder.start(period, manager, Map.of("timeLimitSeconds", 30));
    AiSchedulingResponseDto response = new AiSchedulingResponseDto(
            "1.0",
            run.getId(),
            period.getId(),
            "SUCCESS",
            "OPTIMAL",
            List.of(),
            new BigDecimal("12.34"),
            Map.of("fairness", new BigDecimal("12.34")),
            List.of(),
            List.of(),
            null,
            new AiSchedulingResponseDto.StatisticsDto(BigDecimal.ZERO, 0, 0));

    recorder.finishSuccess(run.getId(), response);

    assertThat(schedulePeriodRepository.findById(period.getId()))
            .get()
            .extracting(SchedulePeriod::getTotalPenalty)
            .isEqualTo(new BigDecimal("12.34"));
}
```

Add imports `BigDecimal` and `scheduling.scheduling.ai.dto.AiSchedulingResponseDto`. Use far-future dates shown in the snippet to avoid period unique constraint collisions.

- [ ] **Step 2: Run and verify RED**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=OptimizationRunRecorderTransactionTest#finishSuccessUpdatesSchedulePeriodTotalPenalty test`

Expected: FAIL because `SchedulePeriod.totalPenalty` remains null.

- [ ] **Step 3: Implement period penalty sync**

In `OptimizationRunRecorder.finishSuccess`, after `run.setTotalPenalty(response.totalPenalty());`, add:

```java
run.getSchedulePeriod().setTotalPenalty(response.totalPenalty());
```

This uses the existing managed association in the recorder transaction. Do not add a new repository dependency unless the test proves the association is detached.

- [ ] **Step 4: Run test**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=OptimizationRunRecorderTransactionTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/ai/OptimizationRunRecorder.java backend/src/test/java/scheduling/scheduling/ai/OptimizationRunRecorderTransactionTest.java && git commit -m "feat: sync period optimization penalty"`

## Slice 6E: SchedulingRule JSONB Mapping

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/entity/SchedulingRule.java`
- Create: `backend/src/test/java/scheduling/scheduling/entity/SchedulingRuleJsonbMappingTest.java`

- [ ] **Step 1: Write failing real DB mapping test**

Create `SchedulingRuleJsonbMappingTest.java`:

```java
package scheduling.scheduling.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import scheduling.scheduling.repository.SchedulingRuleRepository;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-with-at-least-32-characters",
        "app.auth.default-employee-password=configured-default",
})
class SchedulingRuleJsonbMappingTest {

    private static final String RULE_CODE = "JSONB_MAPPING_TEST";

    @Autowired
    private SchedulingRuleRepository schedulingRuleRepository;

    @AfterEach
    void tearDown() {
        schedulingRuleRepository.findByRuleCode(RULE_CODE).ifPresent(schedulingRuleRepository::delete);
    }

    @Test
    void persistsRuleValueJsonToPostgresJsonbColumn() {
        SchedulingRule saved = schedulingRuleRepository.save(SchedulingRule.builder()
                .ruleCode(RULE_CODE)
                .ruleName("JSONB Mapping Test")
                .ruleType("HARD")
                .ruleValueJson("{\"value\":12}")
                .weight(new BigDecimal("1.00"))
                .active(true)
                .build());

        assertThat(schedulingRuleRepository.findById(saved.getId()))
                .get()
                .extracting(SchedulingRule::getRuleValueJson)
                .isEqualTo("{\"value\":12}");
    }
}
```

- [ ] **Step 2: Run and verify RED**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=SchedulingRuleJsonbMappingTest test`

Expected: FAIL with Postgres error similar to `column "rule_value_json" is of type jsonb but expression is of type character varying`.

- [ ] **Step 3: Fix JSONB mapping**

In `SchedulingRule.java`, add imports:

```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
```

Change field mapping to:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "rule_value_json", nullable = false, columnDefinition = "JSONB")
private String ruleValueJson;
```

This matches `OptimizationRun.weightsJson` / `penaltyDetailsJson` pattern.

- [ ] **Step 4: Run mapping and rule tests**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest='SchedulingRuleJsonbMappingTest,AdminSchedulingRuleServiceTest,AiSchedulingPayloadBuilderTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/entity/SchedulingRule.java backend/src/test/java/scheduling/scheduling/entity/SchedulingRuleJsonbMappingTest.java && git commit -m "fix: map scheduling rule values as jsonb"`

## Slice 6F: AI Client Retry And Timeout Controls

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/ai/RestClientAiSchedulingClient.java`
- Create: `backend/src/test/java/scheduling/scheduling/ai/RestClientAiSchedulingClientTest.java`
- Modify: `backend/src/main/resources/application.properties` only if default properties are not already documented.

- [ ] **Step 1: Add constructor overload for tests**

Before writing behavior tests, add only this testability seam to `RestClientAiSchedulingClient`:

```java
RestClientAiSchedulingClient(String aiServiceUrl, Duration connectTimeout, Duration readTimeout, int maxAttempts) {
    this.restClient = buildRestClient(aiServiceUrl, connectTimeout, readTimeout);
    this.maxAttempts = maxAttempts;
}
```

Refactor existing constructor to call it with properties in later steps. If adding this before a failing behavior test feels too much, put it in the same commit as the first retry test and keep the production behavior unchanged.

- [ ] **Step 2: Write failing retry test using JDK HTTP server**

Create `RestClientAiSchedulingClientTest.java` with a local `com.sun.net.httpserver.HttpServer`. Test name:

```java
@Test void retriesTransientServerFailureThenReturnsResponse()
```

Server handler:
- request 1: respond `503` with `{"detail":"try again"}`
- request 2: respond `200` with valid AI response JSON:

```json
{
  "schemaVersion":"1.0",
  "runId":900,
  "periodId":5,
  "status":"SUCCESS",
  "solverStatus":"OPTIMAL",
  "assignments":[],
  "totalPenalty":0,
  "penaltyDetails":{},
  "warnings":[],
  "diagnostics":[],
  "statistics":{"wallTimeSeconds":0,"conflicts":0,"branches":0}
}
```

Instantiate client:

```java
RestClientAiSchedulingClient client = new RestClientAiSchedulingClient(
        "http://localhost:" + port, Duration.ofSeconds(1), Duration.ofSeconds(2), 2);
```

Assert response status is `SUCCESS` and request counter is `2`.

- [ ] **Step 3: Run and verify RED**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=RestClientAiSchedulingClientTest#retriesTransientServerFailureThenReturnsResponse test`

Expected: FAIL because current client does one attempt.

- [ ] **Step 4: Implement retry loop**

In `RestClientAiSchedulingClient.java`:

1. Add field:

```java
private final int maxAttempts;
```

2. Add property constructor:

```java
public RestClientAiSchedulingClient(
        @Value("${app.ai-service-url}") String aiServiceUrl,
        @Value("${app.ai.connect-timeout-seconds:30}") long connectTimeoutSeconds,
        @Value("${app.ai.read-timeout-seconds:300}") long readTimeoutSeconds,
        @Value("${app.ai.max-attempts:2}") int maxAttempts) {
    this(aiServiceUrl, Duration.ofSeconds(connectTimeoutSeconds), Duration.ofSeconds(readTimeoutSeconds), maxAttempts);
}
```

3. Extract `buildRestClient`:

```java
private static RestClient buildRestClient(String aiServiceUrl, Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder().baseUrl(aiServiceUrl).requestFactory(requestFactory).build();
}
```

4. Implement retry in `optimize`:

```java
RestClientException lastFailure = null;
int attempts = Math.max(1, maxAttempts);
for (int attempt = 1; attempt <= attempts; attempt++) {
    try {
        AiSchedulingResponseDto response = restClient.post()
                .uri("/api/v1/scheduling/optimize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiSchedulingResponseDto.class);
        if (response == null) {
            throw new AiServiceException("AI service returned empty response");
        }
        return response;
    } catch (RestClientException ex) {
        lastFailure = ex;
        if (attempt == attempts) {
            break;
        }
    }
}
throw new AiServiceException("AI service request failed after " + attempts + " attempt(s)", lastFailure);
```

Do not retry `AiServiceException("AI service returned empty response")` unless a test requires it; empty 200 response is a protocol/service bug, not transient transport.

- [ ] **Step 5: Add timeout/attempt validation tests**

Add tests:

```java
@Test void stopsAfterConfiguredAttemptCount()
@Test void mapsRepeatedServerFailuresToAiServiceException()
```

`stopsAfterConfiguredAttemptCount`: server always 503, client maxAttempts 3, assert `AiServiceException` and counter 3.

`mapsRepeatedServerFailuresToAiServiceException`: assert message contains `after 2 attempt`.

- [ ] **Step 6: Run tests**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -Dtest=RestClientAiSchedulingClientTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

Run: `git add backend/src/main/java/scheduling/scheduling/ai/RestClientAiSchedulingClient.java backend/src/test/java/scheduling/scheduling/ai/RestClientAiSchedulingClientTest.java backend/src/main/resources/application.properties && git commit -m "feat: retry transient AI service failures"`

If `application.properties` did not change, omit it from `git add`.

## Slice 6G: Rich Infeasible Diagnostics

**Files:**
- Modify: `ai-service/app/solver/scheduler.py`
- Modify: `ai-service/tests/test_scheduling.py`

- [ ] **Step 1: Add failing pytest for locked assignment conflicting with leave**

In `ai-service/tests/test_scheduling.py`, add:

```python
def test_infeasible_diagnostics_include_locked_leave_conflict() -> None:
    request = base_request()
    request["employees"] = [{"employeeId": 1}]
    request["lockedAssignments"] = [{"assignmentId": 50, "employeeId": 1, "workShiftId": 1000}]
    request["approvedLeaves"] = [
        {"leaveRequestId": 1, "employeeId": 1, "startDate": "2026-09-14", "endDate": "2026-09-14"}
    ]

    response = client.post("/api/v1/scheduling/optimize", json=request)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "INFEASIBLE"
    assert any(item["code"] == "LOCKED_ASSIGNMENT_CONFLICTS_WITH_LEAVE" for item in body["diagnostics"])
```

- [ ] **Step 2: Run and verify RED**

Run from `ai-service`: `.\.venv\Scripts\pytest.exe -q tests/test_scheduling.py::test_infeasible_diagnostics_include_locked_leave_conflict`

Expected: FAIL because diagnostics only include generic `NO_FEASIBLE_SCHEDULE` and possibly staff shortage.

- [ ] **Step 3: Add diagnostic helper functions**

In `scheduler.py`, before `solve_schedule`, add:

```python
def _leave_overlaps_shift(leave, shift) -> bool:
    last_shift_date = (shift.ends_at - timedelta(microseconds=1)).date()
    return leave.start_date <= last_shift_date and shift.starts_at.date() <= leave.end_date


def _infeasible_diagnostics(request: SchedulingRequest) -> list[DiagnosticOutput]:
    diagnostics = [
        DiagnosticOutput(
            code="NO_FEASIBLE_SCHEDULE",
            message="No schedule satisfies all hard constraints",
        )
    ]
    shift_by_id = {shift.work_shift_id: shift for shift in request.work_shifts}
    leaves_by_employee = defaultdict(list)
    for leave in request.approved_leaves:
        leaves_by_employee[leave.employee_id].append(leave)

    for locked in request.locked_assignments:
        shift = shift_by_id.get(locked.work_shift_id)
        if shift is None:
            continue
        for leave in leaves_by_employee[locked.employee_id]:
            if _leave_overlaps_shift(leave, shift):
                diagnostics.append(DiagnosticOutput(
                    code="LOCKED_ASSIGNMENT_CONFLICTS_WITH_LEAVE",
                    message="Locked assignment conflicts with approved leave",
                    employee_id=locked.employee_id,
                    work_shift_id=locked.work_shift_id,
                ))

    for shift in request.work_shifts:
        unavailable = {
            leave.employee_id
            for leave in request.approved_leaves
            if _leave_overlaps_shift(leave, shift)
        }
        if len(request.employees) - len(unavailable) < shift.required_staff:
            diagnostics.append(DiagnosticOutput(
                code="INSUFFICIENT_AVAILABLE_STAFF",
                message="Approved leave leaves too few employees for this shift",
                work_shift_id=shift.work_shift_id,
            ))
    return diagnostics
```

Check `DiagnosticOutput` schema first. If it lacks `employee_id`, add it as optional with alias `employeeId` in `ai-service/app/schemas/scheduling.py`, matching existing style.

- [ ] **Step 4: Use helper in INFEASIBLE response**

Replace the inline diagnostics block at `scheduler.py` lines around 406-427 with:

```python
diagnostics = _infeasible_diagnostics(request)
```

Keep the response shape unchanged.

- [ ] **Step 5: Add rest/conflict diagnostic tests**

Add tests:

```python
def test_infeasible_diagnostics_include_insufficient_staff() -> None:
    request = base_request()
    request["approvedLeaves"] = [
        {"leaveRequestId": employee_id, "employeeId": employee_id, "startDate": "2026-09-14", "endDate": "2026-09-14"}
        for employee_id in (1, 2)
    ]
    response = client.post("/api/v1/scheduling/optimize", json=request)
    assert response.status_code == 200
    assert any(item["code"] == "INSUFFICIENT_AVAILABLE_STAFF" for item in response.json()["diagnostics"])
```

The existing `test_approved_leave_can_make_schedule_infeasible` already asserts `INSUFFICIENT_AVAILABLE_STAFF`; update that test to also assert `workShiftId == 1000` on the matching diagnostic instead of adding this new test if both tests would be identical.

- [ ] **Step 6: Run AI tests**

Run from `ai-service`: `.\.venv\Scripts\pytest.exe -q`

Expected: PASS.

- [ ] **Step 7: Commit**

Run from repo root: `git add ai-service/app/solver/scheduler.py ai-service/app/schemas/scheduling.py ai-service/tests/test_scheduling.py && git commit -m "feat: enrich infeasible scheduling diagnostics"`

If `scheduling.py` schema did not change, omit it from `git add`.

## Final Verification

- [ ] **Step 1: Run backend full suite**

Run: `.\backend\mvnw.cmd -f backend/pom.xml test`

Expected: all backend tests pass.

- [ ] **Step 2: Run AI full suite**

Run from `ai-service`: `.\.venv\Scripts\pytest.exe -q`

Expected: all AI tests pass.

- [ ] **Step 3: Check whitespace and status**

Run from repo root:

```powershell
git diff --check
```

Expected: no tracked modifications after commits; existing unrelated untracked docs may remain.

## Self-Review Notes

- Spec coverage: all 5 requested follow-up slices are represented as independently testable tasks.
- Placeholder scan: no TBD/TODO placeholders remain; each slice has explicit files, tests, commands, and commit messages.
- Type consistency: `PayloadSnapshot.boundaryAssignments`, `findBoundaryForAiPayload`, `ApplyResult`, `SchedulingRule.ruleValueJson`, and AI diagnostic codes are named consistently.
- Scope: no frontend, notification, export, or deployment work included.
