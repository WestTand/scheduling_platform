# ShiftAssignment APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement manager-only manual shift assignment APIs (assign, period schedule view, delete, lock/unlock) per the approved spec.

**Architecture:** New `ShiftAssignmentRepository` with fetch-join queries plus a new `AdminShiftAssignmentService` following the `AdminWorkShiftService` manager-guard pattern; a new `AdminShiftAssignmentController` under `/api/admin`; overlap detection computed in the service from `workDate + ShiftType.startTime/endTime` with overnight rollover.

**Tech Stack:** Spring Boot, Spring Data JPA/JPQL, Spring MVC + Security (`@PreAuthorize("hasRole('MANAGER')")`), Jakarta validation, JUnit 5 + Mockito + MockMvc.

---

## File Structure

- Create: `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java` — fetch-join queries for detail, capacity count, employee date-window load, period schedule listing.
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftAssignmentRequest.java` — `{ employeeId }` with `@NotNull`.
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftAssignmentResponse.java` — assignment detail record with `from()` mapper.
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminPeriodScheduleResponse.java` — period schedule record with nested shift + assignment records.
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java` — assign, schedule view, delete, lock/unlock, overlap helper.
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java` — Mockito service tests for every spec rule.
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentController.java` — REST endpoints.
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentControllerTest.java` — `@WebMvcTest` security/delegation tests.
- Modify: `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java` — add `@MockitoBean ShiftAssignmentRepository`.
- Modify: `docs/project_plan.md` — mark the assignment slice status.

---

### Task 1: Repository plus request/response DTOs

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftAssignmentRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftAssignmentResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminPeriodScheduleResponse.java`

- [ ] **Step 1: Write the repository and DTO files**

```java
package scheduling.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.ShiftAssignment;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    @Query("""
            select sa
            from ShiftAssignment sa
            join fetch sa.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ws.shiftType st
            join fetch sa.employee e
            where sa.id = :id
            """)
    Optional<ShiftAssignment> findDetailById(@Param("id") Long id);

    long countByWorkShiftId(Long workShiftId);

    @Query("""
            select sa
            from ShiftAssignment sa
            join fetch sa.workShift ws
            join fetch ws.shiftType st
            where sa.employee.id = :employeeId
              and ws.workDate between :from and :to
            """)
    List<ShiftAssignment> findForEmployeeInWindow(
            @Param("employeeId") Long employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select sa
            from ShiftAssignment sa
            join fetch sa.workShift ws
            join fetch ws.shiftType st
            join fetch sa.employee e
            where ws.schedulePeriod.id = :periodId
            order by ws.workDate asc, st.startTime asc, sa.id asc
            """)
    List<ShiftAssignment> listForPeriod(@Param("periodId") Long periodId);
}
```

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.NotNull;

public record CreateShiftAssignmentRequest(@NotNull Long employeeId) {
}
```

```java
package scheduling.scheduling.admin.dto;

import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.WorkShift;

public record AdminShiftAssignmentResponse(
        Long id,
        Long workShiftId,
        Long periodId,
        Long employeeId,
        String employeeCode,
        String fullName,
        String assignmentStatus,
        String assignedSource,
        Boolean locked) {

    public static AdminShiftAssignmentResponse from(ShiftAssignment assignment) {
        WorkShift workShift = assignment.getWorkShift();
        SchedulePeriod period = workShift == null ? null : workShift.getSchedulePeriod();
        Employee employee = assignment.getEmployee();
        return new AdminShiftAssignmentResponse(
                assignment.getId(),
                workShift == null ? null : workShift.getId(),
                period == null ? null : period.getId(),
                employee == null ? null : employee.getId(),
                employee == null ? null : employee.getEmployeeCode(),
                employee == null ? null : employee.getFullName(),
                assignment.getAssignmentStatus(),
                assignment.getAssignedSource(),
                assignment.getLocked());
    }
}
```

```java
package scheduling.scheduling.admin.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AdminPeriodScheduleResponse(
        Long periodId,
        String periodName,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        List<ScheduledShift> workShifts) {

    public record ScheduledShift(
            Long id,
            LocalDate workDate,
            Long shiftTypeId,
            String shiftName,
            LocalTime shiftStartTime,
            LocalTime shiftEndTime,
            Boolean overnight,
            Integer requiredStaff,
            Integer maxStaff,
            List<AdminShiftAssignmentResponse> assignments) {
    }
}
```

- [ ] **Step 2: Verify compilation of the new types**

Run: `.\mvnw.cmd -q compile` from `backend`
Expected: exit 0 (service/controller do not exist yet, but repository and DTOs compile).

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/repository/ShiftAssignmentRepository.java backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftAssignmentRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftAssignmentResponse.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminPeriodScheduleResponse.java
git commit -m "feat: add shift assignment repository and dtos"
```

---

### Task 2: Assignment service with validation, overlap check, schedule view, lock/unlock

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java`
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java`
- Modify: `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`

- [ ] **Step 1: Write the failing service test (assign happy path plus guards)**

```java
package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminShiftAssignmentResponse;
import scheduling.scheduling.admin.dto.CreateShiftAssignmentRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@ExtendWith(MockitoExtension.class)
class AdminShiftAssignmentServiceTest {

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private WorkShiftRepository workShiftRepository;
    @Mock
    private SchedulePeriodRepository schedulePeriodRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private AccountRepository accountRepository;

    private AdminShiftAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new AdminShiftAssignmentService(
                shiftAssignmentRepository,
                workShiftRepository,
                schedulePeriodRepository,
                employeeRepository,
                leaveRequestRepository,
                accountRepository);
    }

    @Test
    void managerAssignsActiveEmployee() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager");
        Account manager = Account.builder().role(AccountRole.MANAGER).build();
        manager.setId(1L);
        SchedulePeriod period = SchedulePeriod.builder().status("DRAFT").build();
        period.setId(20L);
        ShiftType shiftType = ShiftType.builder()
                .status("ACTIVE").startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(16, 0))
                .minStaff(1).maxStaff(3).build();
        shiftType.setId(100L);
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(period).shiftType(shiftType)
                .workDate(LocalDate.of(2026, 9, 14)).requiredStaff(2).build();
        shift.setId(200L);
        Employee employee = Employee.builder().status("ACTIVE").build();
        employee.setId(7L);
        ShiftAssignment saved = ShiftAssignment.builder()
                .workShift(shift).employee(employee)
                .assignmentStatus("ASSIGNED").assignedSource("MANUAL").locked(false).build();
        saved.setId(500L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(shift));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.countByWorkShiftId(200L)).thenReturn(1L);
        when(leaveRequestRepository.searchForEmployee(7L, "APPROVED",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of());
        when(shiftAssignmentRepository.findForEmployeeInWindow(7L,
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of());
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenReturn(saved);

        AdminShiftAssignmentResponse response = service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(7L));

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.assignedSource()).isEqualTo("MANUAL");
        assertThat(response.locked()).isFalse();
    }

    @Test
    void assignRejectsDuplicateWithConflict() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager");
        Account manager = Account.builder().role(AccountRole.MANAGER).build();
        manager.setId(1L);
        SchedulePeriod period = SchedulePeriod.builder().status("DRAFT").build();
        period.setId(20L);
        ShiftType shiftType = ShiftType.builder()
                .status("ACTIVE").startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(16, 0))
                .minStaff(1).maxStaff(3).build();
        shiftType.setId(100L);
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(period).shiftType(shiftType)
                .workDate(LocalDate.of(2026, 9, 14)).requiredStaff(2).build();
        shift.setId(200L);
        Employee employee = Employee.builder().status("ACTIVE").build();
        employee.setId(7L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(shift));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));

        ShiftAssignment existing = ShiftAssignment.builder().workShift(shift).employee(employee).build();
        existing.setId(501L);
        // Duplicate check runs before capacity/leave/overlap checks, so only this stub is needed.
        when(shiftAssignmentRepository.listForPeriod(20L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
```

Note: the duplicate test above is a placeholder-shape guard; the real implementation checks duplicates by scanning existing assignments for the same `(workShiftId, employeeId)` before the capacity check and throws `409 CONFLICT`. Keep the duplicate-first ordering in the service so this behavior holds.

- [ ] **Step 2: Run the new test to verify it fails (no service yet)**

Run: `.\mvnw.cmd test "-Dtest=AdminShiftAssignmentServiceTest"` from `backend`
Expected: FAIL — `AdminShiftAssignmentService` does not exist.

- [ ] **Step 3: Write the minimal service**

```java
package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminPeriodScheduleResponse;
import scheduling.scheduling.admin.dto.AdminShiftAssignmentResponse;
import scheduling.scheduling.admin.dto.CreateShiftAssignmentRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@Service
public class AdminShiftAssignmentService {

    private static final String DRAFT = "DRAFT";
    private static final String ACTIVE = "ACTIVE";
    private static final String APPROVED = "APPROVED";
    private static final String ASSIGNED = "ASSIGNED";
    private static final String MANUAL = "MANUAL";

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final WorkShiftRepository workShiftRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AccountRepository accountRepository;

    public AdminShiftAssignmentService(
            ShiftAssignmentRepository shiftAssignmentRepository,
            WorkShiftRepository workShiftRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            EmployeeRepository employeeRepository,
            LeaveRequestRepository leaveRequestRepository,
            AccountRepository accountRepository) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.workShiftRepository = workShiftRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AdminShiftAssignmentResponse createAssignment(
            CurrentAccountPrincipal principal, Long workShiftId, CreateShiftAssignmentRequest request) {
        requireManager(principal);
        if (request == null || request.employeeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
        }
        WorkShift shift = workShiftRepository.findDetailById(workShiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work shift not found"));
        requireDraft(shift.getSchedulePeriod());
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (!ACTIVE.equals(employee.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
        }
        List<ShiftAssignment> existingInShift = shiftAssignmentRepository.listForPeriod(
                shift.getSchedulePeriod().getId()).stream()
                .filter(candidate -> candidate.getWorkShift() != null
                        && workShiftId.equals(candidate.getWorkShift().getId()))
                .toList();
        if (existingInShift.stream().anyMatch(candidate -> candidate.getEmployee() != null
                && employee.getId().equals(candidate.getEmployee().getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee already assigned to this shift");
        }
        if (existingInShift.size() >= shift.getShiftType().getMaxStaff()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
        }
        List<LeaveRequest> leaves = leaveRequestRepository.searchForEmployee(
                employee.getId(), APPROVED, shift.getWorkDate(), shift.getWorkDate());
        if (!leaves.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
        }
        LocalDateTime[] target = interval(shift);
        List<ShiftAssignment> nearby = shiftAssignmentRepository.findForEmployeeInWindow(
                employee.getId(), shift.getWorkDate().minusDays(1), shift.getWorkDate().plusDays(1));
        for (ShiftAssignment candidate : nearby) {
            if (candidate.getWorkShift() == null
                    || workShiftId.equals(candidate.getWorkShift().getId())) {
                continue;
            }
            LocalDateTime[] other = interval(candidate.getWorkShift());
            if (target[0].isBefore(other[1]) && other[0].isBefore(target[1])) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
            }
        }
        ShiftAssignment assignment = ShiftAssignment.builder()
                .workShift(shift)
                .employee(employee)
                .assignmentStatus(ASSIGNED)
                .assignedSource(MANUAL)
                .locked(false)
                .build();
        return AdminShiftAssignmentResponse.from(shiftAssignmentRepository.save(assignment));
    }

    @Transactional(readOnly = true)
    public AdminPeriodScheduleResponse getSchedule(CurrentAccountPrincipal principal, Long periodId) {
        requireManager(principal);
        SchedulePeriod period = schedulePeriodRepository.findDetailById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule period not found"));
        List<WorkShift> shifts = workShiftRepository.searchForPeriod(periodId, null, null);
        List<ShiftAssignment> assignments = shiftAssignmentRepository.listForPeriod(periodId);
        Map<Long, List<AdminShiftAssignmentResponse>> byShift = new LinkedHashMap<>();
        for (ShiftAssignment assignment : assignments) {
            byShift.computeIfAbsent(assignment.getWorkShift().getId(), key -> new ArrayList<>())
                    .add(AdminShiftAssignmentResponse.from(assignment));
        }
        List<AdminPeriodScheduleResponse.ScheduledShift> scheduled = shifts.stream()
                .map(shift -> new AdminPeriodScheduleResponse.ScheduledShift(
                        shift.getId(),
                        shift.getWorkDate(),
                        shift.getShiftType().getId(),
                        shift.getShiftType().getShiftName(),
                        shift.getShiftType().getStartTime(),
                        shift.getShiftType().getEndTime(),
                        shift.getShiftType().getOvernight(),
                        shift.getRequiredStaff(),
                        shift.getShiftType().getMaxStaff(),
                        byShift.getOrDefault(shift.getId(), List.of())))
                .toList();
        return new AdminPeriodScheduleResponse(
                period.getId(),
                period.getPeriodName(),
                period.getStatus(),
                period.getStartDate(),
                period.getEndDate(),
                scheduled);
    }

    @Transactional
    public void deleteAssignment(CurrentAccountPrincipal principal, Long assignmentId) {
        requireManager(principal);
        ShiftAssignment assignment = shiftAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        requireDraft(assignment.getWorkShift().getSchedulePeriod());
        shiftAssignmentRepository.delete(assignment);
    }

    @Transactional
    public AdminShiftAssignmentResponse lockAssignment(CurrentAccountPrincipal principal, Long assignmentId) {
        return setLocked(principal, assignmentId, true);
    }

    @Transactional
    public AdminShiftAssignmentResponse unlockAssignment(CurrentAccountPrincipal principal, Long assignmentId) {
        return setLocked(principal, assignmentId, false);
    }

    private AdminShiftAssignmentResponse setLocked(
            CurrentAccountPrincipal principal, Long assignmentId, boolean locked) {
        requireManager(principal);
        ShiftAssignment assignment = shiftAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        requireDraft(assignment.getWorkShift().getSchedulePeriod());
        assignment.setLocked(locked);
        return AdminShiftAssignmentResponse.from(shiftAssignmentRepository.save(assignment));
    }

    static LocalDateTime[] interval(WorkShift shift) {
        ShiftType type = shift.getShiftType();
        LocalDateTime start = shift.getWorkDate().atTime(type.getStartTime());
        LocalDateTime end = shift.getWorkDate().atTime(type.getEndTime());
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
        }
        return new LocalDateTime[] {start, end};
    }

    private void requireManager(CurrentAccountPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
    }

    private static void requireDraft(SchedulePeriod period) {
        if (period == null || !DRAFT.equals(period.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
        }
    }
}
```

Also add the context-test mock so the Spring context still loads:

```java
@MockitoBean
private ShiftAssignmentRepository shiftAssignmentRepository;
```

with import `scheduling.scheduling.repository.ShiftAssignmentRepository` in `SchedulingApplicationTests.java`.

- [ ] **Step 4: Expand the service test to the full spec matrix, then run it**

Extend `AdminShiftAssignmentServiceTest` with these cases (Mockito style, same fixture pattern as Step 1):
- rejects non-manager and missing account with `403`;
- rejects missing work shift, missing employee, inactive employee, non-draft period;
- allows assignment count above `requiredStaff` while below `maxStaff` (count `2`, required `2`, max `3` → `201`);
- rejects approved leave covering the date;
- rejects same-day time overlap (`08:00-16:00` vs `14:00-22:00`);
- allows back-to-back touching intervals (`08:00-16:00` vs `16:00-00:00`);
- rejects overnight overlap (`22:00-06:00` vs next-day `00:00-08:00`);
- rejects overlap with an assignment from another period inside the window;
- delete removes locked assignments in draft, rejects non-draft and missing id;
- lock/unlock flip the flag, are idempotent, and reject non-draft;
- schedule view requires manager, returns `404` for missing period, and nests ordered shifts with assignments.

Run: `.\mvnw.cmd test "-Dtest=AdminShiftAssignmentServiceTest"` from `backend`
Expected: PASS, all tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentService.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentServiceTest.java backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java
git commit -m "feat: add shift assignment service"
```

---

### Task 3: Controller plus security tests

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentController.java`
- Create: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentControllerTest.java`

- [ ] **Step 1: Write the failing controller test (assign + schedule view)**

```java
package scheduling.scheduling.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.admin.dto.AdminPeriodScheduleResponse;
import scheduling.scheduling.admin.dto.AdminShiftAssignmentResponse;
import scheduling.scheduling.admin.dto.CreateShiftAssignmentRequest;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;
import java.util.Optional;

@WebMvcTest(AdminShiftAssignmentController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminShiftAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminShiftAssignmentService adminShiftAssignmentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    private void givenToken(String token, Account account) throws Exception {
        when(jwtService.validateAccessToken(token)).thenReturn(
                new CurrentAccountPrincipal(account.getId(), account.getUsername()));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
    }

    private Account manager() {
        Account account = Account.builder()
                .username("manager").role(AccountRole.MANAGER).status(AccountStatus.ACTIVE).build();
        account.setId(1L);
        return account;
    }

    @Test
    void managerAssignsEmployee() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftAssignmentService.createAssignment(
                any(CurrentAccountPrincipal.class), eq(200L), any(CreateShiftAssignmentRequest.class)))
                .thenReturn(new AdminShiftAssignmentResponse(
                        500L, 200L, 20L, 7L, "NV007", "Nguyen Van A", "ASSIGNED", "MANUAL", false));

        mockMvc.perform(post("/api/admin/work-shifts/200/assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId": 7}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.assignedSource").value("MANUAL"));
    }

    @Test
    void managerViewsPeriodSchedule() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftAssignmentService.getSchedule(any(CurrentAccountPrincipal.class), eq(20L)))
                .thenReturn(new AdminPeriodScheduleResponse(
                        20L, "Week 38", "DRAFT",
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), List.of()));

        mockMvc.perform(get("/api/admin/schedule-periods/20/schedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(20));
    }
}
```

- [ ] **Step 2: Run the controller test to verify it fails (no controller yet)**

Run: `.\mvnw.cmd test "-Dtest=AdminShiftAssignmentControllerTest"` from `backend`
Expected: FAIL — `AdminShiftAssignmentController` does not exist.

- [ ] **Step 3: Write the minimal controller**

```java
package scheduling.scheduling.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminPeriodScheduleResponse;
import scheduling.scheduling.admin.dto.AdminShiftAssignmentResponse;
import scheduling.scheduling.admin.dto.CreateShiftAssignmentRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('MANAGER')")
public class AdminShiftAssignmentController {

    private final AdminShiftAssignmentService adminShiftAssignmentService;

    public AdminShiftAssignmentController(AdminShiftAssignmentService adminShiftAssignmentService) {
        this.adminShiftAssignmentService = adminShiftAssignmentService;
    }

    @PostMapping("/work-shifts/{workShiftId}/assignments")
    ResponseEntity<AdminShiftAssignmentResponse> createAssignment(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long workShiftId,
            @Valid @RequestBody CreateShiftAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminShiftAssignmentService.createAssignment(principal, workShiftId, request));
    }

    @GetMapping("/schedule-periods/{periodId}/schedule")
    ResponseEntity<AdminPeriodScheduleResponse> getSchedule(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId) {
        return ResponseEntity.ok(adminShiftAssignmentService.getSchedule(principal, periodId));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    ResponseEntity<Void> deleteAssignment(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long assignmentId) {
        adminShiftAssignmentService.deleteAssignment(principal, assignmentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/assignments/{assignmentId}/lock")
    ResponseEntity<AdminShiftAssignmentResponse> lockAssignment(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long assignmentId) {
        return ResponseEntity.ok(adminShiftAssignmentService.lockAssignment(principal, assignmentId));
    }

    @PatchMapping("/assignments/{assignmentId}/unlock")
    ResponseEntity<AdminShiftAssignmentResponse> unlockAssignment(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long assignmentId) {
        return ResponseEntity.ok(adminShiftAssignmentService.unlockAssignment(principal, assignmentId));
    }
}
```

- [ ] **Step 4: Extend the controller test, then run it**

Add delete (`204`), lock, unlock, body-validation `400`, non-manager `403`, and anonymous `401` cases following the `AdminWorkShiftControllerTest` pattern, then run:

Run: `.\mvnw.cmd test "-Dtest=AdminShiftAssignmentControllerTest,AdminShiftAssignmentServiceTest"` from `backend`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/scheduling/scheduling/admin/AdminShiftAssignmentController.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftAssignmentControllerTest.java
git commit -m "feat: add shift assignment endpoints"
```

---

### Task 4: Roadmap docs plus full verification

**Files:**
- Modify: `docs/project_plan.md`

- [ ] **Step 1: Update the roadmap status**

In `docs/project_plan.md`, update the Phase 5 section: manual assignment, period schedule view, and lock/unlock are implemented for `DRAFT` periods; remaining items are update-in-place, view-by-employee, approval/publish, and AI orchestration.

- [ ] **Step 2: Run the full backend suite**

Run: `.\mvnw.cmd clean test` from `backend`
Expected: all tests pass, `0` failures, `0` errors.

- [ ] **Step 3: Check the branch diff**

Run: `git diff --check main...HEAD`
Expected: no output.

- [ ] **Step 4: Commit**

```bash
git add docs/project_plan.md
git commit -m "docs: update phase 5 shift assignment status"
```
