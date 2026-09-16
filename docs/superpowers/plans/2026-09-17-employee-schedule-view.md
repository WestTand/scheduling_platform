# Employee Schedule View APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add employee self schedule view (PUBLISHED only) and manager schedule-by-employee view (all statuses) sharing one DTO and one date-range helper.

**Architecture:** New `EmployeeScheduleService`/`EmployeeScheduleController` in the `employee` package; new read-only `getEmployeeSchedule` in `AdminShiftAssignmentService` exposed via `AdminShiftAssignmentController`; shared `EmployeeScheduleItemResponse` DTO plus `ScheduleWindows` date helper in the `employee` package; extend `findForEmployeeInWindow` with schedule-period fetch and ordering.

**Tech Stack:** Java 17, Spring Boot (WebMVC, Data JPA), JUnit 5, AssertJ, Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-17-employee-schedule-view-design.md`

**Working directory:** All commands run from the repo root (`scheduling_platform/`). Maven commands use `.\backend\mvnw.cmd -f backend/pom.xml` because the wrapper and POM both live in `backend/`. In PowerShell, quote every `-D` argument (e.g. `"-Dtest=..." "-Dsurefire.failIfNoSpecifiedTests=true"`).

---

## File Map

- Create: `backend/src/main/java/scheduling/scheduling/employee/dto/EmployeeScheduleItemResponse.java` — shared item DTO with `from(ShiftAssignment)` mapper.
- Create: `backend/src/main/java/scheduling/scheduling/employee/ScheduleWindows.java` — date-range normalization helper with nested `Window` record.
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeeScheduleService.java` — employee self view, `PUBLISHED` filter.
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeeScheduleController.java` — `GET /api/employee/schedule`.
- Create: `backend/src/test/java/scheduling/scheduling/employee/ScheduleWindowsTest.java` — helper unit tests.
- Create: `backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleServiceTest.java` — service tests with fixed clock.
- Create: `backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleControllerTest.java` — MockMvc tests.
- Modify: `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java` — extend `findForEmployeeInWindow` with schedule-period fetch and ordering.
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java` — single public 7-arg constructor including `Clock` (Spring injects the `TimeConfig` bean), plus `getEmployeeSchedule`.
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentController.java` — `GET /employees/{employeeId}/schedule`.
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java` — manager view tests; existing setup updated to the 7-arg construction with `FIXED_CLOCK`.
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentControllerTest.java` — endpoint + security tests.
- Modify: `docs/project_plan.md` — mark the two schedule-view items done after green verification.

---

### Task 1: Shared DTO, date helper, repository query

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/employee/dto/EmployeeScheduleItemResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/ScheduleWindows.java`
- Create: `backend/src/test/java/scheduling/scheduling/employee/ScheduleWindowsTest.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java`

- [ ] **Step 1: Write failing helper tests**

Create `backend/src/test/java/scheduling/scheduling/employee/ScheduleWindowsTest.java`:

```java
package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ScheduleWindowsTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsMissingRangeToTodayThroughTodayPlusThirtyDays() {
        ScheduleWindows.Window window = ScheduleWindows.resolve(FIXED_CLOCK, null, null);

        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    void defaultsMissingToToFromPlusThirtyDays() {
        ScheduleWindows.Window window =
                ScheduleWindows.resolve(FIXED_CLOCK, LocalDate.of(2026, 9, 20), null);

        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    void defaultsMissingFromToToday() {
        ScheduleWindows.Window window =
                ScheduleWindows.resolve(FIXED_CLOCK, null, LocalDate.of(2026, 10, 1));

        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void passesExplicitRangeThrough() {
        ScheduleWindows.Window window = ScheduleWindows.resolve(
                FIXED_CLOCK, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void rejectsFromAfterTo() {
        assertThatThrownBy(() -> ScheduleWindows.resolve(
                        FIXED_CLOCK, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 2: Run the helper tests to verify they fail**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=ScheduleWindowsTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL — compilation error, `ScheduleWindows` does not exist.

- [ ] **Step 3: Implement the helper, DTO, and repository query**

Create `backend/src/main/java/scheduling/scheduling/employee/ScheduleWindows.java`:

```java
package scheduling.scheduling.employee;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ScheduleWindows {

    private ScheduleWindows() {
    }

    public record Window(LocalDate from, LocalDate to) {
    }

    public static Window resolve(Clock clock, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now(clock);
        LocalDate end = to != null ? to : start.plusDays(30);
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid schedule date range");
        }
        return new Window(start, end);
    }
}
```

Create `backend/src/main/java/scheduling/scheduling/employee/dto/EmployeeScheduleItemResponse.java`:

```java
package scheduling.scheduling.employee.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import scheduling.scheduling.entity.ShiftAssignment;

public record EmployeeScheduleItemResponse(
        Long assignmentId,
        String assignmentStatus,
        String assignedSource,
        Boolean locked,
        LocalDate workDate,
        Long shiftTypeId,
        String shiftTypeName,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        Long periodId,
        String periodName,
        String periodStatus) {

    public static EmployeeScheduleItemResponse from(ShiftAssignment assignment) {
        return new EmployeeScheduleItemResponse(
                assignment.getId(),
                assignment.getAssignmentStatus(),
                assignment.getAssignedSource(),
                assignment.getLocked(),
                assignment.getWorkShift().getWorkDate(),
                assignment.getWorkShift().getShiftType().getId(),
                assignment.getWorkShift().getShiftType().getShiftName(),
                assignment.getWorkShift().getShiftType().getStartTime(),
                assignment.getWorkShift().getShiftType().getEndTime(),
                assignment.getWorkShift().getSchedulePeriod().getId(),
                assignment.getWorkShift().getSchedulePeriod().getPeriodName(),
                assignment.getWorkShift().getSchedulePeriod().getStatus());
    }
}
```

In `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java`, replace the `findForEmployeeInWindow` query with:

```java
    @Query("""
            select sa
            from ShiftAssignment sa
            join fetch sa.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ws.shiftType st
            join fetch sa.employee e
            where sa.employee.id = :employeeId
              and ws.workDate between :from and :to
            order by ws.workDate asc, st.startTime asc, sa.id asc
            """)
    List<ShiftAssignment> findForEmployeeInWindow(
            @Param("employeeId") Long employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
```

- [ ] **Step 4: Run the helper tests plus the existing assignment tests**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=ScheduleWindowsTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all helper tests PASS.

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftAssignmentServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all tests PASS — compile/regression guard after touching the shared repository file (Mockito tests do not execute JPQL, so this confirms compilation and unchanged mock behavior, not the new query semantics).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/employee/dto/EmployeeScheduleItemResponse.java backend/src/main/java/scheduling/scheduling/employee/ScheduleWindows.java backend/src/test/java/scheduling/scheduling/employee/ScheduleWindowsTest.java backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java
git commit -m "feat: add shared schedule view dto and date helper"
```

---

### Task 2: Employee self schedule service

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeeScheduleService.java`
- Create: `backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleServiceTest.java`:

```java
package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;

class EmployeeScheduleServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ShiftAssignmentRepository shiftAssignmentRepository = mock(ShiftAssignmentRepository.class);
    private final EmployeeAccess employeeAccess = new EmployeeAccess(accountRepository);
    private final EmployeeScheduleService service =
            new EmployeeScheduleService(employeeAccess, shiftAssignmentRepository, FIXED_CLOCK);

    @Test
    void returnsOnlyPublishedAssignments() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        ShiftAssignment published = assignment(300L, shift(200L, period(20L, "PUBLISHED")), employee);
        ShiftAssignment draft = assignment(301L, shift(201L, period(21L, "DRAFT")), employee);
        ShiftAssignment approved = assignment(302L, shift(202L, period(22L, "APPROVED")), employee);
        ShiftAssignment cancelled = assignment(303L, shift(203L, period(23L, "CANCELLED")), employee);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(shiftAssignmentRepository.findForEmployeeInWindow(
                        7L, LocalDate.of(2026, 9, 17), LocalDate.of(2026, 10, 17)))
                .thenReturn(List.of(published, draft, approved, cancelled));

        List<EmployeeScheduleItemResponse> response = service.getOwnSchedule(principal, null, null);

        assertThat(response).extracting(EmployeeScheduleItemResponse::assignmentId).containsExactly(300L);
        assertThat(response.get(0).periodStatus()).isEqualTo("PUBLISHED");
        assertThat(response.get(0).workDate()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(response.get(0).shiftTypeName()).isEqualTo("Day");
    }

    @Test
    void passesExplicitRangeThrough() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(shiftAssignmentRepository.findForEmployeeInWindow(
                        7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of());

        List<EmployeeScheduleItemResponse> response =
                service.getOwnSchedule(principal, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response).isEmpty();
        verify(shiftAssignmentRepository).findForEmployeeInWindow(
                7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void rejectsFromAfterTo() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));

        assertThatThrownBy(() -> service.getOwnSchedule(
                        principal, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(shiftAssignmentRepository, never()).findForEmployeeInWindow(any(), any(), any());
    }

    @Test
    void rejectsNonEmployeeAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal, null)));

        assertThatThrownBy(() -> service.getOwnSchedule(principal, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(shiftAssignmentRepository, never()).findForEmployeeInWindow(any(), any(), any());
    }

    private static CurrentAccountPrincipal employeePrincipal() {
        return new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
    }

    private static Account account(CurrentAccountPrincipal principal, Employee employee) {
        Account account = Account.builder()
                .username(principal.username())
                .passwordHash("hash")
                .role(principal.role())
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .employee(employee)
                .build();
        account.setId(principal.accountId());
        return account;
    }

    private static Employee employee(Long id, String status) {
        Employee employee = Employee.builder()
                .employeeCode("E001")
                .fullName("Employee One")
                .status(status)
                .build();
        employee.setId(id);
        return employee;
    }

    private static SchedulePeriod period(Long id, String status) {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Week 38")
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 20))
                .versionNo(1)
                .status(status)
                .build();
        period.setId(id);
        return period;
    }

    private static ShiftType shiftType() {
        ShiftType shiftType = ShiftType.builder()
                .shiftName("Day")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(BigDecimal.valueOf(4.0))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(3)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .build();
        shiftType.setId(100L);
        return shiftType;
    }

    private static WorkShift shift(Long id, SchedulePeriod period) {
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(period)
                .shiftType(shiftType())
                .workDate(LocalDate.of(2026, 9, 17))
                .requiredStaff(2)
                .build();
        shift.setId(id);
        return shift;
    }

    private static ShiftAssignment assignment(Long id, WorkShift shift, Employee employee) {
        ShiftAssignment assignment = ShiftAssignment.builder()
                .workShift(shift)
                .employee(employee)
                .assignmentStatus("ASSIGNED")
                .assignedSource("MANUAL")
                .locked(false)
                .build();
        assignment.setId(id);
        return assignment;
    }
}
```

- [ ] **Step 2: Run the service tests to verify they fail**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=EmployeeScheduleServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL — compilation error, `EmployeeScheduleService` does not exist.

- [ ] **Step 3: Implement the service**

Create `backend/src/main/java/scheduling/scheduling/employee/EmployeeScheduleService.java`:

```java
package scheduling.scheduling.employee;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.repository.ShiftAssignmentRepository;

@Service
public class EmployeeScheduleService {

    private static final String PUBLISHED = "PUBLISHED";

    private final EmployeeAccess employeeAccess;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final Clock clock;

    public EmployeeScheduleService(
            EmployeeAccess employeeAccess,
            ShiftAssignmentRepository shiftAssignmentRepository,
            Clock clock) {
        this.employeeAccess = employeeAccess;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EmployeeScheduleItemResponse> getOwnSchedule(
            CurrentAccountPrincipal principal, LocalDate from, LocalDate to) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        ScheduleWindows.Window window = ScheduleWindows.resolve(clock, from, to);
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findForEmployeeInWindow(
                employee.getId(), window.from(), window.to());
        return assignments.stream()
                .filter(assignment -> PUBLISHED.equals(
                        assignment.getWorkShift().getSchedulePeriod().getStatus()))
                .map(EmployeeScheduleItemResponse::from)
                .toList();
    }
}
```

Spring injects the existing `TimeConfig` `Clock` bean into the new service automatically (single constructor, no `@Autowired` needed).

- [ ] **Step 4: Run the service tests to verify they pass**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=EmployeeScheduleServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/employee/EmployeeScheduleService.java backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleServiceTest.java
git commit -m "feat: add employee own schedule service"
```

---

### Task 3: Employee schedule controller

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeeScheduleController.java`
- Create: `backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleControllerTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleControllerTest.java`

- [ ] **Step 1: Write failing MockMvc tests**

Create `backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleControllerTest.java`, mirroring `EmployeePreferenceControllerTest` setup (same `@WebMvcTest` annotations, `@Import`, `@TestPropertySource`, `@MockitoBean` fields, `givenToken` helper):

```java
package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(EmployeeScheduleController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class EmployeeScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeScheduleService employeeScheduleService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void employeeViewsOwnSchedule() throws Exception {
        givenToken("employee-token", employee());
        when(employeeScheduleService.getOwnSchedule(
                        any(CurrentAccountPrincipal.class),
                        eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/employee/schedule")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignmentId").value(300))
                .andExpect(jsonPath("$[0].periodStatus").value("PUBLISHED"));

        ArgumentCaptor<CurrentAccountPrincipal> principalCaptor =
                ArgumentCaptor.forClass(CurrentAccountPrincipal.class);
        verify(employeeScheduleService).getOwnSchedule(
                principalCaptor.capture(), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)));
        assertThat(principalCaptor.getValue().accountId()).isEqualTo(2L);
    }

    @Test
    void employeeViewsOwnScheduleWithoutDates() throws Exception {
        givenToken("employee-token", employee());
        when(employeeScheduleService.getOwnSchedule(
                        any(CurrentAccountPrincipal.class), eq(null), eq(null)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/employee/schedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(employeeScheduleService).getOwnSchedule(
                any(CurrentAccountPrincipal.class), eq(null), eq(null));
    }

    @Test
    void managerCannotAccessEmployeeSchedule() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(get("/api/employee/schedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(employeeScheduleService);
    }

    @Test
    void anonymousCannotAccessEmployeeSchedule() throws Exception {
        mockMvc.perform(get("/api/employee/schedule"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(employeeScheduleService);
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static CurrentAccountPrincipal employee() {
        return new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
    }

    private static CurrentAccountPrincipal manager() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static Account account(CurrentAccountPrincipal principal) {
        Account account = Account.builder()
                .username(principal.username())
                .passwordHash("hash")
                .role(principal.role())
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(principal.accountId());
        return account;
    }

    private static EmployeeScheduleItemResponse response() {
        return new EmployeeScheduleItemResponse(
                300L,
                "ASSIGNED",
                "MANUAL",
                false,
                LocalDate.of(2026, 9, 17),
                100L,
                "Day",
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                20L,
                "Week 38",
                "PUBLISHED");
    }
}
```

- [ ] **Step 2: Run the controller tests to verify they fail**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=EmployeeScheduleControllerTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL — compilation error (controller/service delegation) or 404 (no handler).

- [ ] **Step 3: Implement the controller**

Create `backend/src/main/java/scheduling/scheduling/employee/EmployeeScheduleController.java`:

```java
package scheduling.scheduling.employee;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse;

@RestController
@RequestMapping("/api/employee/schedule")
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeScheduleController {

    private final EmployeeScheduleService employeeScheduleService;

    public EmployeeScheduleController(EmployeeScheduleService employeeScheduleService) {
        this.employeeScheduleService = employeeScheduleService;
    }

    @GetMapping
    ResponseEntity<List<EmployeeScheduleItemResponse>> getOwnSchedule(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(employeeScheduleService.getOwnSchedule(principal, from, to));
    }
}
```

- [ ] **Step 4: Run the controller tests to verify they pass**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=EmployeeScheduleControllerTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/employee/EmployeeScheduleController.java backend/src/test/java/scheduling/scheduling/employee/EmployeeScheduleControllerTest.java
git commit -m "feat: add employee own schedule endpoint"
```

---

### Task 4: Manager schedule-by-employee service method

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java`

- [ ] **Step 1: Write failing manager view tests**

The service uses a single public 7-arg constructor including `Clock`; Spring injects the existing `TimeConfig` clock bean (`Clock.systemUTC()`), matching the spec. Update the existing test setup to pass the fixed clock (only this test class instantiates the service directly; the controller uses DI and is unaffected):

```java
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);
    private final AdminShiftAssignmentService service = new AdminShiftAssignmentService(
            shiftAssignmentRepository, workShiftRepository, schedulePeriodRepository,
            employeeRepository, leaveRequestRepository, accountRepository, FIXED_CLOCK);
```

This replaces the existing 6-arg `service` construction at the top of the class. All new tests below call `service` (not a second instance).

Add imports: `java.time.Clock`, `java.time.Instant`, `java.time.ZoneOffset`, `scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse`.

Add tests:

```java
    @Test
    void managerViewsEmployeeScheduleInAllStatuses() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        ShiftAssignment published = assignment(300L, publishedShift(200L), employee, false);
        ShiftAssignment draft = assignment(301L, draftShift(201L), employee, false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.findForEmployeeInWindow(
                        7L, LocalDate.of(2026, 9, 17), LocalDate.of(2026, 10, 17)))
                .thenReturn(List.of(published, draft));

        List<EmployeeScheduleItemResponse> response =
                service.getEmployeeSchedule(principal, 7L, null, null);

        assertThat(response).extracting(EmployeeScheduleItemResponse::assignmentId)
                .containsExactly(300L, 301L);
        assertThat(response).extracting(EmployeeScheduleItemResponse::periodStatus)
                .containsExactly("PUBLISHED", "DRAFT");
    }

    @Test
    void managerViewPassesExplicitRangeThrough() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.findForEmployeeInWindow(
                        7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of());

        List<EmployeeScheduleItemResponse> response = service.getEmployeeSchedule(
                principal, 7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response).isEmpty();
        verify(shiftAssignmentRepository).findForEmployeeInWindow(
                7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void managerViewRejectsFromAfterTo() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.getEmployeeSchedule(
                        principal, 7L, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(shiftAssignmentRepository, never()).findForEmployeeInWindow(any(), any(), any());
    }

    @Test
    void managerViewRejectsUnknownEmployee() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(employeeRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEmployeeSchedule(principal, 7L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(shiftAssignmentRepository, never()).findForEmployeeInWindow(any(), any(), any());
    }

    @Test
    void managerViewRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.getEmployeeSchedule(principal, 7L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(employeeRepository, never()).findById(any());
    }
```

Helpers use the existing test file's `managerPrincipal()`, `account(principal)`, `employee(7L, "NV007", "Nguyen Van A")` methods — reuse them, do not redefine. Add only these new helpers (existing `workShift()` builds a DRAFT shift; add status-parameterized variants):

```java
    private static WorkShift publishedShift(Long id) {
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(publishedPeriod())
                .shiftType(shiftType(LocalTime.of(8, 0), LocalTime.of(16, 0), 3))
                .workDate(LocalDate.of(2026, 9, 17))
                .requiredStaff(2)
                .build();
        shift.setId(id);
        return shift;
    }

    private static WorkShift draftShift(Long id) {
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(draftPeriod())
                .shiftType(shiftType(LocalTime.of(8, 0), LocalTime.of(16, 0), 3))
                .workDate(LocalDate.of(2026, 9, 17))
                .requiredStaff(2)
                .build();
        shift.setId(id);
        return shift;
    }

    private static SchedulePeriod publishedPeriod() {
        SchedulePeriod period = draftPeriod();
        period.setStatus("PUBLISHED");
        return period;
    }
```

Before writing, READ the existing helper methods `draftPeriod()`, `shiftType(...)`, `assignment(...)`, `account(...)`, `managerPrincipal()` in `AdminShiftAssignmentServiceTest.java` and match their exact signatures:
- `draftPeriod()` takes no args and returns a DRAFT period (id 20).
- `shiftType(LocalTime start, LocalTime end, int maxStaff)` — verify exact parameter list from the file.
- `assignment(Long id, WorkShift shift, Employee employee, boolean locked)` — verify exact parameter list from the file; the new tests above already include the `locked` argument (`..., false`). If the signature differs, adapt the calls to match.
- `account(CurrentAccountPrincipal principal)` and `managerPrincipal()` exist — reuse as-is.

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftAssignmentServiceTest#managerViewsEmployeeScheduleInAllStatuses+managerViewPassesExplicitRangeThrough+managerViewRejectsFromAfterTo+managerViewRejectsUnknownEmployee+managerViewRejectsNonManagerAccount" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL — compilation error, `getEmployeeSchedule` does not exist (and possibly the 7-arg constructor).

- [ ] **Step 3: Implement the Clock overload and manager view method**

In `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java`, add a `Clock` field and change the constructor to a single public 7-arg constructor. Spring injects the existing `TimeConfig` clock bean automatically (single constructor, no `@Autowired` needed). Do NOT default to `Clock.systemDefaultZone()` — the spec requires the `TimeConfig` bean (`Clock.systemUTC()`):

```java
import java.time.Clock;
import java.time.LocalDate;
```

```java
    private final Clock clock;

    public AdminShiftAssignmentService(
            ShiftAssignmentRepository shiftAssignmentRepository,
            WorkShiftRepository workShiftRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            EmployeeRepository employeeRepository,
            LeaveRequestRepository leaveRequestRepository,
            AccountRepository accountRepository,
            Clock clock) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.workShiftRepository = workShiftRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }
```

Add the manager view method after `getSchedule`:

```java
    @Transactional(readOnly = true)
    public List<EmployeeScheduleItemResponse> getEmployeeSchedule(
            CurrentAccountPrincipal principal, Long employeeId, LocalDate from, LocalDate to) {
        requireManager(principal);
        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        ScheduleWindows.Window window = ScheduleWindows.resolve(clock, from, to);
        return shiftAssignmentRepository.findForEmployeeInWindow(employeeId, window.from(), window.to()).stream()
                .map(EmployeeScheduleItemResponse::from)
                .toList();
    }
```

Add imports (alphabetical):

```java
import scheduling.scheduling.employee.ScheduleWindows;
import scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse;
```

- [ ] **Step 4: Run the admin assignment service tests**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftAssignmentServiceTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all tests PASS (the whole class now uses the updated 7-arg construction with `FIXED_CLOCK`; the clock is only consumed by the new method).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java
git commit -m "feat: add manager schedule by employee service"
```

---

### Task 5: Manager schedule-by-employee controller endpoint

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentController.java`
- Modify: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentControllerTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentControllerTest.java`

- [ ] **Step 1: Write failing MockMvc tests**

First READ the tail of `AdminShiftAssignmentControllerTest.java` (helper methods `givenToken`, `manager()`, `employee()`, existing security tests) and mirror its patterns exactly.

Add tests:

```java
    @Test
    void managerViewsEmployeeSchedule() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftAssignmentService.getEmployeeSchedule(
                        any(CurrentAccountPrincipal.class), eq(7L),
                        eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(List.of(scheduleItem()));

        mockMvc.perform(get("/api/admin/employees/7/schedule")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignmentId").value(300))
                .andExpect(jsonPath("$[0].periodStatus").value("PUBLISHED"));

        verify(adminShiftAssignmentService).getEmployeeSchedule(
                any(CurrentAccountPrincipal.class), eq(7L),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)));
    }

    @Test
    void employeeCannotViewScheduleByEmployee() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/employees/7/schedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminShiftAssignmentService);
    }

    @Test
    void anonymousCannotViewScheduleByEmployee() throws Exception {
        mockMvc.perform(get("/api/admin/employees/7/schedule"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminShiftAssignmentService);
    }
```

Add a `scheduleItem()` helper returning:

```java
    private static EmployeeScheduleItemResponse scheduleItem() {
        return new EmployeeScheduleItemResponse(
                300L,
                "ASSIGNED",
                "MANUAL",
                false,
                LocalDate.of(2026, 9, 17),
                100L,
                "Day",
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                20L,
                "Week 38",
                "PUBLISHED");
    }
```

Add imports: `java.time.LocalTime`, `scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse` (alphabetical; `LocalDate` and `List` already imported).

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftAssignmentControllerTest#managerViewsEmployeeSchedule+employeeCannotViewScheduleByEmployee+anonymousCannotViewScheduleByEmployee" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: FAIL — 404 for the new path (and compilation error if the helper import is added before the DTO exists — DTO exists since Task 1, so expect 404s).

- [ ] **Step 3: Implement the endpoint**

In `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentController.java`, add imports:

```java
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
```

(Check existing imports first; add only missing ones in alphabetical order. `List` may already be absent — the current file imports neither `List` nor `LocalDate`.)

Add method after `getSchedule`:

```java
    @GetMapping("/employees/{employeeId}/schedule")
    ResponseEntity<List<EmployeeScheduleItemResponse>> getEmployeeSchedule(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(
                adminShiftAssignmentService.getEmployeeSchedule(principal, employeeId, from, to));
    }
```

Add imports:

```java
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse;
```

(Check existing imports: `RequestParam` is NOT currently imported in this controller; `DateTimeFormat` neither. Insert alphabetically: `DateTimeFormat` after `format`... exact order: `org.springframework.format.annotation.DateTimeFormat` sorts before `org.springframework.http.*`; `RequestParam` sorts after `RequestMapping` and before `RestController`.)

- [ ] **Step 4: Run the controller tests**

Run: `.\backend\mvnw.cmd -f backend/pom.xml -q test "-Dtest=AdminShiftAssignmentControllerTest" "-Dsurefire.failIfNoSpecifiedTests=true"`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentController.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentControllerTest.java
git commit -m "feat: add manager schedule by employee endpoint"
```

---

### Task 6: Full verification and status docs

**Files:**
- Modify: `docs/project_plan.md`
- Test: full backend suite

- [ ] **Step 1: Run the full backend suite**

Run: `.\backend\mvnw.cmd -f backend/pom.xml clean test`
Expected: all tests pass, 0 failures/errors. Report the final summary line.

- [ ] **Step 2: Check the working tree is diff-clean**

Run: `git diff --check`
Expected: no output. If either Step 1 or Step 2 fails: STOP and report BLOCKED.

- [ ] **Step 3: Update project plan status**

READ `docs/project_plan.md` Phase 4/Phase 5 sections first. Mark done, following the exact existing `(da xong: ...)` style:
- Phase 4 `API nhan vien xem lich cua minh` (employee sees own PUBLISHED schedule).
- Phase 5 `API xem lich theo nhan vien` (manager views any employee schedule).
Leave all other items untouched. Minimal diff.

- [ ] **Step 4: Commit**

```bash
git add docs/project_plan.md
git commit -m "docs: update schedule view status"
```

---

## Spec Coverage

- `GET /api/employee/schedule`, role `EMPLOYEE`, own schedule via `EmployeeAccess`: Tasks 2, 3.
- `GET /api/admin/employees/{employeeId}/schedule`, role `MANAGER`, any existing employee regardless of status: Tasks 4, 5.
- Employee sees `PUBLISHED` only; manager sees all statuses: Tasks 2, 4.
- `from`/`to` optional, default today through today plus 30 days via injected `Clock` (`TimeConfig` bean in production, `Clock.fixed` in tests); `from` after `to` is `400`: Tasks 1, 2, 4.
- Ordering by work date then shift start time via extended repository query: Task 1.
- Shared DTO + shared date helper: Task 1.
- `404` unknown employee / missing profile, `403` wrong role / inactive, `401` anonymous: Tasks 2, 3, 4, 5.
- Empty list is `200 []`: Tasks 2, 3, 4 (asserted in passthrough tests).
- Full verification: Task 6.

## Self-Review

- Spec coverage: every spec section maps to a task above.
- Placeholder scan: no TBD/TODO; every code step shows exact code, exact commands, exact expected output. Task 4/5 instruct reading existing helpers first where exact signatures must be matched, with explicit adaptation notes.
- Type consistency: `getOwnSchedule(principal, from, to)`, `getEmployeeSchedule(principal, employeeId, from, to)`, `ScheduleWindows.resolve(clock, from, to)` returning `Window(from, to)`, `EmployeeScheduleItemResponse.from(assignment)` — same names/signatures in all tasks; fixed clock `2026-09-17T00:00:00Z` UTC gives default window `2026-09-17` through `2026-10-17` everywhere.
