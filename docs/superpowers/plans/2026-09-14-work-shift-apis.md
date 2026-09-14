# WorkShift APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build manager-only APIs to create and view concrete work shifts inside draft schedule periods.

**Architecture:** Follow the existing admin API pattern: controller delegates to a service, service enforces manager authorization and business rules, repository owns fetch/search queries, DTO records define request/response JSON. This slice is manual create/list/detail only and excludes bulk generation, update/delete, assignment, publish, and AI calls.

**Tech Stack:** Spring Boot 4, Spring MVC, Spring Security method authorization, Spring Data JPA, Jakarta Validation, JUnit 6, Mockito, AssertJ, MockMvc.

---

## File Structure

- Create `backend/src/main/java/scheduling/scheduling/repository/WorkShiftRepository.java`
  - Repository for listing work shifts in a period and fetching details with period/shift type.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java`
  - Manager authorization, draft-period validation, active-shift-type validation, staff/note validation, mapping.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftController.java`
  - REST endpoints under `/api/admin/schedule-periods/{periodId}/work-shifts` and `/api/admin/work-shifts/{workShiftId}`.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/CreateWorkShiftRequest.java`
  - Request DTO for manual work-shift creation.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/AdminWorkShiftResponse.java`
  - Response DTO containing work shift, period, and shift type summary.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftServiceTest.java`
  - Unit tests for service behavior.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftControllerTest.java`
  - Web MVC/security tests for API wiring.
- Modify `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java` only if the no-JPA context requires a `WorkShiftRepository` mock.
- Modify `docs/project_plan.md`
  - Mark work-shift API progress after implementation and verification.

---

### Task 1: WorkShift Service, Repository, And DTOs

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/repository/WorkShiftRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/CreateWorkShiftRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminWorkShiftResponse.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftServiceTest.java`

- [ ] **Step 1: Write the failing service test**

Create `backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftServiceTest.java`:

```java
package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminWorkShiftResponse;
import scheduling.scheduling.admin.dto.CreateWorkShiftRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

class AdminWorkShiftServiceTest {

    private final WorkShiftRepository workShiftRepository = org.mockito.Mockito.mock(WorkShiftRepository.class);
    private final SchedulePeriodRepository schedulePeriodRepository = org.mockito.Mockito.mock(SchedulePeriodRepository.class);
    private final ShiftTypeRepository shiftTypeRepository = org.mockito.Mockito.mock(ShiftTypeRepository.class);
    private final AccountRepository accountRepository = org.mockito.Mockito.mock(AccountRepository.class);
    private final AdminWorkShiftService service = new AdminWorkShiftService(
            workShiftRepository, schedulePeriodRepository, shiftTypeRepository, accountRepository);

    @Test
    void createWorkShiftCreatesShiftInDraftPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod();
        ShiftType shiftType = activeShiftType();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(shiftType));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> {
            WorkShift workShift = invocation.getArgument(0);
            workShift.setId(200L);
            return workShift;
        });

        AdminWorkShiftResponse response = service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 2, " Morning coverage "));

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.periodId()).isEqualTo(20L);
        assertThat(response.shiftTypeId()).isEqualTo(100L);
        assertThat(response.workDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(response.requiredStaff()).isEqualTo(2);
        assertThat(response.note()).isEqualTo("Morning coverage");
    }

    @Test
    void createWorkShiftDefaultsRequiredStaffToShiftTypeMinimum() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod();
        ShiftType shiftType = activeShiftType();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(shiftType));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminWorkShiftResponse response = service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), null, "   "));

        assertThat(response.requiredStaff()).isEqualTo(1);
        assertThat(response.note()).isNull();
    }

    @Test
    void createWorkShiftRejectsNonDraftPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod();
        period.setStatus("PUBLISHED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(workShiftRepository, never()).save(any());
    }

    @Test
    void createWorkShiftRejectsInactiveShiftType() {
        CurrentAccountPrincipal principal = managerPrincipal();
        ShiftType shiftType = activeShiftType();
        shiftType.setStatus("INACTIVE");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(shiftType));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWorkShiftRejectsDateOutsidePeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(activeShiftType()));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 30), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWorkShiftRejectsRequiredStaffOutsideShiftTypeRange() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(activeShiftType()));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 3, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWorkShiftRejectsOverlongNoteAfterTrim() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.of(activeShiftType()));

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, "a".repeat(1001))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWorkShiftRejectsMissingPeriodOrShiftType() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(shiftTypeRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createWorkShift(principal, 20L, new CreateWorkShiftRequest(
                100L, LocalDate.of(2026, 9, 14), 1, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listWorkShiftsValidatesManagerAndDelegatesFilters() {
        CurrentAccountPrincipal principal = managerPrincipal();
        WorkShift workShift = workShift();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(workShiftRepository.searchForPeriod(20L, LocalDate.of(2026, 9, 14), 100L)).thenReturn(List.of(workShift));

        List<AdminWorkShiftResponse> response = service.listWorkShifts(
                principal, 20L, LocalDate.of(2026, 9, 14), 100L);

        assertThat(response).extracting(AdminWorkShiftResponse::id).containsExactly(200L);
        verify(workShiftRepository).searchForPeriod(20L, LocalDate.of(2026, 9, 14), 100L);
    }

    @Test
    void listWorkShiftsRejectsFilterDateOutsidePeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));

        assertThatThrownBy(() -> service.listWorkShifts(principal, 20L, LocalDate.of(2026, 9, 30), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getWorkShiftReturnsDetailOrNotFound() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(workShift()));

        AdminWorkShiftResponse response = service.getWorkShift(principal, 200L);

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.periodName()).isEqualTo("Week 38");
        assertThat(response.shiftName()).isEqualTo("Morning");

        when(workShiftRepository.findDetailById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getWorkShift(principal, 404L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void serviceMethodsRejectNonManager() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.getWorkShift(principal, 200L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static CurrentAccountPrincipal managerPrincipal() {
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

    private static SchedulePeriod draftPeriod() {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Week 38")
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 20))
                .versionNo(1)
                .status("DRAFT")
                .createdBy(account(managerPrincipal()))
                .build();
        period.setId(20L);
        return period;
    }

    private static ShiftType activeShiftType() {
        ShiftType shiftType = ShiftType.builder()
                .shiftName("Morning")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(new BigDecimal("8.0"))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(2)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .build();
        shiftType.setId(100L);
        return shiftType;
    }

    private static WorkShift workShift() {
        WorkShift workShift = WorkShift.builder()
                .schedulePeriod(draftPeriod())
                .shiftType(activeShiftType())
                .workDate(LocalDate.of(2026, 9, 14))
                .requiredStaff(1)
                .note("Morning coverage")
                .build();
        workShift.setId(200L);
        return workShift;
    }
}
```

- [ ] **Step 2: Run service test to verify RED**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminWorkShiftServiceTest"
```

Expected: compilation fails because `AdminWorkShiftService`, `WorkShiftRepository`, and DTOs do not exist.

- [ ] **Step 3: Create request DTO**

Create `backend/src/main/java/scheduling/scheduling/admin/dto/CreateWorkShiftRequest.java`:

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateWorkShiftRequest(
        @NotNull Long shiftTypeId,
        @NotNull LocalDate workDate,
        @Min(1) Integer requiredStaff,
        String note) {
}
```

- [ ] **Step 4: Create response DTO**

Create `backend/src/main/java/scheduling/scheduling/admin/dto/AdminWorkShiftResponse.java`:

```java
package scheduling.scheduling.admin.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;

public record AdminWorkShiftResponse(
        Long id,
        Long periodId,
        String periodName,
        Long shiftTypeId,
        String shiftName,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        LocalDate workDate,
        Integer requiredStaff,
        String note) {

    public static AdminWorkShiftResponse from(WorkShift workShift) {
        SchedulePeriod period = workShift.getSchedulePeriod();
        ShiftType shiftType = workShift.getShiftType();
        return new AdminWorkShiftResponse(
                workShift.getId(),
                period == null ? null : period.getId(),
                period == null ? null : period.getPeriodName(),
                shiftType == null ? null : shiftType.getId(),
                shiftType == null ? null : shiftType.getShiftName(),
                shiftType == null ? null : shiftType.getStartTime(),
                shiftType == null ? null : shiftType.getEndTime(),
                workShift.getWorkDate(),
                workShift.getRequiredStaff(),
                workShift.getNote());
    }
}
```

- [ ] **Step 5: Create repository**

Create `backend/src/main/java/scheduling/scheduling/repository/WorkShiftRepository.java`:

```java
package scheduling.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.WorkShift;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    @Query("""
            select ws
            from WorkShift ws
            join fetch ws.schedulePeriod sp
            join fetch ws.shiftType st
            where sp.id = :periodId
              and (:date is null or ws.workDate = :date)
              and (:shiftTypeId is null or st.id = :shiftTypeId)
            order by ws.workDate asc, st.startTime asc, ws.id asc
            """)
    List<WorkShift> searchForPeriod(
            @Param("periodId") Long periodId,
            @Param("date") LocalDate date,
            @Param("shiftTypeId") Long shiftTypeId);

    @Query("""
            select ws
            from WorkShift ws
            join fetch ws.schedulePeriod sp
            join fetch ws.shiftType st
            where ws.id = :id
            """)
    Optional<WorkShift> findDetailById(@Param("id") Long id);
}
```

- [ ] **Step 6: Create service**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java`:

```java
package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminWorkShiftResponse;
import scheduling.scheduling.admin.dto.CreateWorkShiftRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@Service
public class AdminWorkShiftService {

    private static final String DRAFT = "DRAFT";
    private static final String ACTIVE = "ACTIVE";
    private static final int MAX_NOTE_LENGTH = 1000;

    private final WorkShiftRepository workShiftRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final AccountRepository accountRepository;

    public AdminWorkShiftService(
            WorkShiftRepository workShiftRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            ShiftTypeRepository shiftTypeRepository,
            AccountRepository accountRepository) {
        this.workShiftRepository = workShiftRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.shiftTypeRepository = shiftTypeRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AdminWorkShiftResponse createWorkShift(
            CurrentAccountPrincipal principal, Long periodId, CreateWorkShiftRequest request) {
        requireManager(principal);
        SchedulePeriod period = findPeriod(periodId);
        requireDraft(period);
        ShiftType shiftType = findShiftType(request.shiftTypeId());
        requireActiveShiftType(shiftType);
        validateDateInPeriod(request.workDate(), period);
        int requiredStaff = normalizeRequiredStaff(request.requiredStaff(), shiftType);
        String note = normalizeNote(request.note());

        WorkShift workShift = WorkShift.builder()
                .schedulePeriod(period)
                .shiftType(shiftType)
                .workDate(request.workDate())
                .requiredStaff(requiredStaff)
                .note(note)
                .build();
        return AdminWorkShiftResponse.from(workShiftRepository.save(workShift));
    }

    @Transactional(readOnly = true)
    public List<AdminWorkShiftResponse> listWorkShifts(
            CurrentAccountPrincipal principal, Long periodId, LocalDate date, Long shiftTypeId) {
        requireManager(principal);
        SchedulePeriod period = findPeriod(periodId);
        if (date != null) {
            validateDateInPeriod(date, period);
        }
        return workShiftRepository.searchForPeriod(periodId, date, shiftTypeId).stream()
                .map(AdminWorkShiftResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminWorkShiftResponse getWorkShift(CurrentAccountPrincipal principal, Long workShiftId) {
        requireManager(principal);
        WorkShift workShift = workShiftRepository.findDetailById(workShiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work shift not found"));
        return AdminWorkShiftResponse.from(workShift);
    }

    private Account requireManager(CurrentAccountPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        return account;
    }

    private SchedulePeriod findPeriod(Long periodId) {
        return schedulePeriodRepository.findDetailById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule period not found"));
    }

    private ShiftType findShiftType(Long shiftTypeId) {
        return shiftTypeRepository.findById(shiftTypeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift type not found"));
    }

    private static void requireDraft(SchedulePeriod period) {
        if (!DRAFT.equals(period.getStatus())) {
            throw invalidRequest();
        }
    }

    private static void requireActiveShiftType(ShiftType shiftType) {
        if (!ACTIVE.equals(shiftType.getStatus())) {
            throw invalidRequest();
        }
    }

    private static void validateDateInPeriod(LocalDate workDate, SchedulePeriod period) {
        if (workDate == null || workDate.isBefore(period.getStartDate()) || workDate.isAfter(period.getEndDate())) {
            throw invalidRequest();
        }
    }

    private static int normalizeRequiredStaff(Integer requiredStaff, ShiftType shiftType) {
        int normalized = requiredStaff == null ? shiftType.getMinStaff() : requiredStaff;
        if (normalized < shiftType.getMinStaff() || normalized > shiftType.getMaxStaff()) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static String normalizeNote(String note) {
        if (!StringUtils.hasText(note)) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.length() > MAX_NOTE_LENGTH) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid work shift request");
    }
}
```

- [ ] **Step 7: Run service tests to verify GREEN**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminWorkShiftServiceTest,AdminSchedulePeriodServiceTest"
```

Expected: service tests pass.

- [ ] **Step 8: Run context test and fix mock if needed**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=SchedulingApplicationTests"
```

If it fails because the no-JPA context cannot create `AdminWorkShiftService`, modify `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`:

```java
import scheduling.scheduling.repository.WorkShiftRepository;
```

and add:

```java
@MockitoBean
private WorkShiftRepository workShiftRepository;
```

Expected after fix: context test passes.

- [ ] **Step 9: Commit Task 1**

Run from repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/repository/WorkShiftRepository.java `
  backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftService.java `
  backend/src/main/java/scheduling/scheduling/admin/dto/CreateWorkShiftRequest.java `
  backend/src/main/java/scheduling/scheduling/admin/dto/AdminWorkShiftResponse.java `
  backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftServiceTest.java `
  backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java
git commit -m "feat: add work shift service"
```

If `SchedulingApplicationTests.java` was not modified, omit it from `git add`.

---

### Task 2: WorkShift Controller

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftController.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftControllerTest.java`

- [ ] **Step 1: Write the failing controller test**

Create `backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftControllerTest.java`:

```java
package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.admin.dto.AdminWorkShiftResponse;
import scheduling.scheduling.admin.dto.CreateWorkShiftRequest;
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

@WebMvcTest(AdminWorkShiftController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminWorkShiftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminWorkShiftService adminWorkShiftService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerCreatesWorkShift() throws Exception {
        givenToken("manager-token", manager());
        when(adminWorkShiftService.createWorkShift(any(CurrentAccountPrincipal.class), eq(20L), any(CreateWorkShiftRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/admin/schedule-periods/20/work-shifts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shiftTypeId": 100,
                                  "workDate": "2026-09-14",
                                  "requiredStaff": 2,
                                  "note": "Morning coverage"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.periodId").value(20))
                .andExpect(jsonPath("$.shiftTypeId").value(100));

        ArgumentCaptor<CreateWorkShiftRequest> requestCaptor = ArgumentCaptor.forClass(CreateWorkShiftRequest.class);
        verify(adminWorkShiftService).createWorkShift(any(CurrentAccountPrincipal.class), eq(20L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().shiftTypeId()).isEqualTo(100L);
        assertThat(requestCaptor.getValue().workDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void managerListsWorkShiftsWithFilters() throws Exception {
        givenToken("manager-token", manager());
        when(adminWorkShiftService.listWorkShifts(
                any(CurrentAccountPrincipal.class), eq(20L), eq(LocalDate.of(2026, 9, 14)), eq(100L)))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/admin/schedule-periods/20/work-shifts")
                        .param("date", "2026-09-14")
                        .param("shiftTypeId", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(200));

        verify(adminWorkShiftService).listWorkShifts(
                any(CurrentAccountPrincipal.class), eq(20L), eq(LocalDate.of(2026, 9, 14)), eq(100L));
    }

    @Test
    void managerGetsWorkShiftDetail() throws Exception {
        givenToken("manager-token", manager());
        when(adminWorkShiftService.getWorkShift(any(CurrentAccountPrincipal.class), eq(200L))).thenReturn(response());

        mockMvc.perform(get("/api/admin/work-shifts/200")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftName").value("Morning"));

        verify(adminWorkShiftService).getWorkShift(any(CurrentAccountPrincipal.class), eq(200L));
    }

    @Test
    void createWorkShiftRejectsInvalidBody() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(post("/api/admin/schedule-periods/20/work-shifts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workDate": "2026-09-14",
                                  "requiredStaff": 1
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminWorkShiftService);
    }

    @Test
    void nonManagerCannotListWorkShifts() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/schedule-periods/20/work-shifts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminWorkShiftService);
    }

    @Test
    void anonymousCannotListWorkShifts() throws Exception {
        mockMvc.perform(get("/api/admin/schedule-periods/20/work-shifts"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminWorkShiftService);
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static CurrentAccountPrincipal manager() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static CurrentAccountPrincipal employee() {
        return new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
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

    private static AdminWorkShiftResponse response() {
        return new AdminWorkShiftResponse(
                200L,
                20L,
                "Week 38",
                100L,
                "Morning",
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                LocalDate.of(2026, 9, 14),
                2,
                "Morning coverage");
    }
}
```

- [ ] **Step 2: Run controller test to verify RED**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminWorkShiftControllerTest"
```

Expected: compilation fails because `AdminWorkShiftController` does not exist.

- [ ] **Step 3: Create controller**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftController.java`:

```java
package scheduling.scheduling.admin;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminWorkShiftResponse;
import scheduling.scheduling.admin.dto.CreateWorkShiftRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('MANAGER')")
public class AdminWorkShiftController {

    private final AdminWorkShiftService adminWorkShiftService;

    public AdminWorkShiftController(AdminWorkShiftService adminWorkShiftService) {
        this.adminWorkShiftService = adminWorkShiftService;
    }

    @PostMapping("/schedule-periods/{periodId}/work-shifts")
    ResponseEntity<AdminWorkShiftResponse> createWorkShift(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId,
            @Valid @RequestBody CreateWorkShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminWorkShiftService.createWorkShift(principal, periodId, request));
    }

    @GetMapping("/schedule-periods/{periodId}/work-shifts")
    ResponseEntity<List<AdminWorkShiftResponse>> listWorkShifts(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long shiftTypeId) {
        return ResponseEntity.ok(adminWorkShiftService.listWorkShifts(principal, periodId, date, shiftTypeId));
    }

    @GetMapping("/work-shifts/{workShiftId}")
    ResponseEntity<AdminWorkShiftResponse> getWorkShift(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long workShiftId) {
        return ResponseEntity.ok(adminWorkShiftService.getWorkShift(principal, workShiftId));
    }
}
```

- [ ] **Step 4: Run controller tests to verify GREEN**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminWorkShiftControllerTest,AdminWorkShiftServiceTest"
```

Expected: controller and service tests pass.

- [ ] **Step 5: Commit Task 2**

Run from repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/admin/AdminWorkShiftController.java `
  backend/src/test/java/scheduling/scheduling/admin/AdminWorkShiftControllerTest.java
git commit -m "feat: add work shift endpoints"
```

---

### Task 3: Roadmap Update And Full Verification

**Files:**
- Modify: `docs/project_plan.md`

- [ ] **Step 1: Update Phase 5 roadmap status**

In `docs/project_plan.md`, change:

```markdown
- [ ] API tao work shifts theo ky
```

to:

```markdown
- [x] API tao work shifts theo ky
```

Add a brief current-status note that Phase 5 now has DRAFT SchedulePeriod APIs and manual create/list/detail WorkShift APIs.

- [ ] **Step 2: Run full backend verification**

Run from `backend`:

```powershell
.\mvnw.cmd clean test
```

Expected: full backend suite passes with no failures or errors.

- [ ] **Step 3: Commit Task 3**

Run from repo root:

```powershell
git add docs/project_plan.md
git commit -m "docs: update phase 5 work shift status"
```

---

## Final Review

- [ ] Run final full backend verification from `backend`:

```powershell
.\mvnw.cmd clean test
```

- [ ] Confirm branch and clean status from repo root:

```powershell
git branch --show-current
git status --short
```

Expected branch: `phase5/work-shift-apis`.

Expected status: no output from `git status --short`.

- [ ] Review implementation against `docs/superpowers/specs/2026-09-14-work-shift-apis-design.md`:
  - manager-only endpoints;
  - service-level manager authorization;
  - create only in `DRAFT` period;
  - create only with `ACTIVE` shift type;
  - `workDate` inside period;
  - `requiredStaff` defaults to min staff and stays in `[minStaff, maxStaff]`;
  - list/detail fetch period and shift type summaries;
  - no bulk, update/delete, assignment, publish, or AI behavior added.

- [ ] Prepare branch completion using `superpowers:finishing-a-development-branch`.
