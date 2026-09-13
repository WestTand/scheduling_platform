# SchedulePeriod APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build manager-only APIs for creating, listing, reading, and updating `DRAFT` schedule periods.

**Architecture:** Follow the existing admin API pattern: controller delegates to a service, service enforces manager authorization and business rules, repository owns JPQL filtering/fetching, DTO records define request/response JSON. This slice supports only `DRAFT` periods and intentionally excludes work shifts, assignments, approval, publishing, and AI calls.

**Tech Stack:** Spring Boot 4, Spring MVC, Spring Security method authorization, Spring Data JPA, Jakarta Validation, JUnit 6, Mockito, AssertJ, MockMvc.

---

## File Structure

- Create `backend/src/main/java/scheduling/scheduling/repository/SchedulePeriodRepository.java`
  - Spring Data repository for uniqueness checks, filtered list, and detail fetch with creator/approver.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`
  - Business rules, manager authorization, validation, and entity mutation.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodController.java`
  - Manager-only REST API under `/api/admin/schedule-periods`.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/SchedulePeriodRequest.java`
  - Shared create/update request DTO with validation annotations.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/AdminSchedulePeriodResponse.java`
  - Response mapper from `SchedulePeriod`.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`
  - Unit tests for service behavior and validation.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodControllerTest.java`
  - Web MVC/security tests for API wiring.
- Modify `docs/project_plan.md`
  - Mark SchedulePeriod API task/test progress after implementation is verified.
- Modify `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java` only if full context requires a repository mock.

---

### Task 1: SchedulePeriod Service, Repository, And DTOs

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/repository/SchedulePeriodRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/SchedulePeriodRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminSchedulePeriodResponse.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`

- [ ] **Step 1: Write the failing service test**

Create `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java`:

```java
package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminSchedulePeriodResponse;
import scheduling.scheduling.admin.dto.SchedulePeriodRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;

class AdminSchedulePeriodServiceTest {

    private final SchedulePeriodRepository schedulePeriodRepository = org.mockito.Mockito.mock(SchedulePeriodRepository.class);
    private final AccountRepository accountRepository = org.mockito.Mockito.mock(AccountRepository.class);
    private final AdminSchedulePeriodService service = new AdminSchedulePeriodService(schedulePeriodRepository, accountRepository);

    @Test
    void createSchedulePeriodCreatesDraftWithDefaultVersionAndCreator() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNo(
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)).thenReturn(false);
        when(schedulePeriodRepository.save(any(SchedulePeriod.class))).thenAnswer(invocation -> {
            SchedulePeriod period = invocation.getArgument(0);
            period.setId(20L);
            return period;
        });

        AdminSchedulePeriodResponse response = service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                " Week 38 ", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), null));

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.periodName()).isEqualTo("Week 38");
        assertThat(response.versionNo()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.createdByAccountId()).isEqualTo(1L);
        assertThat(response.createdByUsername()).isEqualTo("manager01");
    }

    @Test
    void createSchedulePeriodRejectsInvalidDateRange() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 14), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).save(any());
    }

    @Test
    void createSchedulePeriodRejectsDuplicateDateRangeAndVersion() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNo(
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)).thenReturn(true);

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSchedulePeriodRejectsInvalidVersion() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 0)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSchedulePeriodRejectsOverlongNameAfterTrim() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                " a".repeat(51), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listSchedulePeriodsNormalizesStatusAndDateFilters() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.searchForAdmin("DRAFT", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(period));

        List<AdminSchedulePeriodResponse> response = service.listSchedulePeriods(
                principal, " draft ", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response).extracting(AdminSchedulePeriodResponse::id).containsExactly(20L);
        verify(schedulePeriodRepository).searchForAdmin("DRAFT", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void listSchedulePeriodsRejectsUnsupportedStatus() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.listSchedulePeriods(principal, "PUBLISHED", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listSchedulePeriodsRejectsInvertedDateFilter() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.listSchedulePeriods(
                principal, null, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getSchedulePeriodReturnsMappedDetail() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        AdminSchedulePeriodResponse response = service.getSchedulePeriod(principal, 20L);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.createdByUsername()).isEqualTo("manager01");
    }

    @Test
    void getSchedulePeriodRejectsMissingPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSchedulePeriod(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateSchedulePeriodUpdatesDraftFields() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNoAndIdNot(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2, 20L)).thenReturn(false);
        when(schedulePeriodRepository.save(period)).thenReturn(period);

        AdminSchedulePeriodResponse response = service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                " Week 40 ", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2));

        assertThat(response.periodName()).isEqualTo("Week 40");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(response.versionNo()).isEqualTo(2);
        verify(schedulePeriodRepository).save(period);
    }

    @Test
    void updateSchedulePeriodRejectsNonDraftPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        period.setStatus("PUBLISHED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).save(any());
    }

    @Test
    void updateSchedulePeriodRejectsDuplicateDateRangeAndVersion() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SchedulePeriod period = draftPeriod(20L, account(principal));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(period));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNoAndIdNot(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2, 20L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateSchedulePeriod(principal, 20L, new SchedulePeriodRequest(
                "Week 40", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), 2)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(schedulePeriodRepository, never()).save(any());
    }

    @Test
    void createSchedulePeriodRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createSchedulePeriodRejectsMissingManagerAccount() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSchedulePeriod(principal, new SchedulePeriodRequest(
                "Week 38", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1)))
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

    private static SchedulePeriod draftPeriod(Long id, Account manager) {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Week 38")
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 20))
                .versionNo(1)
                .status("DRAFT")
                .createdBy(manager)
                .build();
        period.setId(id);
        return period;
    }
}
```

- [ ] **Step 2: Run service test to verify RED**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminSchedulePeriodServiceTest"
```

Expected: compilation fails because `AdminSchedulePeriodService`, `SchedulePeriodRepository`, and DTOs do not exist.

- [ ] **Step 3: Create request DTO**

Create `backend/src/main/java/scheduling/scheduling/admin/dto/SchedulePeriodRequest.java`:

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SchedulePeriodRequest(
        @NotBlank @Size(max = 100) String periodName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Min(1) Integer versionNo) {
}
```

- [ ] **Step 4: Create response DTO**

Create `backend/src/main/java/scheduling/scheduling/admin/dto/AdminSchedulePeriodResponse.java`:

```java
package scheduling.scheduling.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.SchedulePeriod;

public record AdminSchedulePeriodResponse(
        Long id,
        String periodName,
        LocalDate startDate,
        LocalDate endDate,
        Integer versionNo,
        String status,
        Long createdByAccountId,
        String createdByUsername,
        Long approvedByAccountId,
        String approvedByUsername,
        LocalDateTime publishedAt,
        BigDecimal totalPenalty) {

    public static AdminSchedulePeriodResponse from(SchedulePeriod period) {
        Account createdBy = period.getCreatedBy();
        Account approvedBy = period.getApprovedBy();
        return new AdminSchedulePeriodResponse(
                period.getId(),
                period.getPeriodName(),
                period.getStartDate(),
                period.getEndDate(),
                period.getVersionNo(),
                period.getStatus(),
                createdBy == null ? null : createdBy.getId(),
                createdBy == null ? null : createdBy.getUsername(),
                approvedBy == null ? null : approvedBy.getId(),
                approvedBy == null ? null : approvedBy.getUsername(),
                period.getPublishedAt(),
                period.getTotalPenalty());
    }
}
```

- [ ] **Step 5: Create repository**

Create `backend/src/main/java/scheduling/scheduling/repository/SchedulePeriodRepository.java`:

```java
package scheduling.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.SchedulePeriod;

public interface SchedulePeriodRepository extends JpaRepository<SchedulePeriod, Long> {

    boolean existsByStartDateAndEndDateAndVersionNo(LocalDate startDate, LocalDate endDate, Integer versionNo);

    boolean existsByStartDateAndEndDateAndVersionNoAndIdNot(
            LocalDate startDate, LocalDate endDate, Integer versionNo, Long id);

    @Query("""
            select sp
            from SchedulePeriod sp
            left join fetch sp.createdBy createdBy
            left join fetch sp.approvedBy approvedBy
            where sp.id = :id
            """)
    Optional<SchedulePeriod> findDetailById(@Param("id") Long id);

    @Query("""
            select sp
            from SchedulePeriod sp
            left join fetch sp.createdBy createdBy
            left join fetch sp.approvedBy approvedBy
            where (:status is null or sp.status = :status)
              and (:from is null or sp.endDate >= :from)
              and (:to is null or sp.startDate <= :to)
            order by sp.startDate desc, sp.id desc
            """)
    List<SchedulePeriod> searchForAdmin(
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
```

- [ ] **Step 6: Create service**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java`:

```java
package scheduling.scheduling.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminSchedulePeriodResponse;
import scheduling.scheduling.admin.dto.SchedulePeriodRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;

@Service
public class AdminSchedulePeriodService {

    private static final String DRAFT = "DRAFT";

    private final SchedulePeriodRepository schedulePeriodRepository;
    private final AccountRepository accountRepository;

    public AdminSchedulePeriodService(SchedulePeriodRepository schedulePeriodRepository, AccountRepository accountRepository) {
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AdminSchedulePeriodResponse createSchedulePeriod(CurrentAccountPrincipal principal, SchedulePeriodRequest request) {
        Account manager = requireManager(principal);
        String periodName = normalizePeriodName(request.periodName());
        int versionNo = normalizeVersionNo(request.versionNo());
        validateRange(request.startDate(), request.endDate());
        if (schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNo(
                request.startDate(), request.endDate(), versionNo)) {
            throw invalidRequest();
        }

        SchedulePeriod period = SchedulePeriod.builder()
                .periodName(periodName)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .versionNo(versionNo)
                .status(DRAFT)
                .createdBy(manager)
                .build();
        return AdminSchedulePeriodResponse.from(schedulePeriodRepository.save(period));
    }

    @Transactional(readOnly = true)
    public List<AdminSchedulePeriodResponse> listSchedulePeriods(
            CurrentAccountPrincipal principal, String status, LocalDate from, LocalDate to) {
        requireManager(principal);
        String normalizedStatus = normalizeStatus(status);
        validateRange(from, to);
        return schedulePeriodRepository.searchForAdmin(normalizedStatus, from, to).stream()
                .map(AdminSchedulePeriodResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminSchedulePeriodResponse getSchedulePeriod(CurrentAccountPrincipal principal, Long periodId) {
        requireManager(principal);
        return AdminSchedulePeriodResponse.from(findDetail(periodId));
    }

    @Transactional
    public AdminSchedulePeriodResponse updateSchedulePeriod(
            CurrentAccountPrincipal principal, Long periodId, SchedulePeriodRequest request) {
        requireManager(principal);
        SchedulePeriod period = findDetail(periodId);
        if (!DRAFT.equals(period.getStatus())) {
            throw invalidRequest();
        }
        String periodName = normalizePeriodName(request.periodName());
        int versionNo = normalizeVersionNo(request.versionNo());
        validateRange(request.startDate(), request.endDate());
        if (schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNoAndIdNot(
                request.startDate(), request.endDate(), versionNo, periodId)) {
            throw invalidRequest();
        }

        period.setPeriodName(periodName);
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setVersionNo(versionNo);
        return AdminSchedulePeriodResponse.from(schedulePeriodRepository.save(period));
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

    private SchedulePeriod findDetail(Long periodId) {
        return schedulePeriodRepository.findDetailById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule period not found"));
    }

    private static String normalizePeriodName(String periodName) {
        if (!StringUtils.hasText(periodName)) {
            throw invalidRequest();
        }
        String normalized = periodName.trim();
        if (normalized.length() > 100) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static int normalizeVersionNo(Integer versionNo) {
        int normalized = versionNo == null ? 1 : versionNo;
        if (normalized < 1) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!DRAFT.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw invalidRequest();
        }
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid schedule period request");
    }
}
```

- [ ] **Step 7: Run service tests to verify GREEN**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminSchedulePeriodServiceTest"
```

Expected: all `AdminSchedulePeriodServiceTest` tests pass.

- [ ] **Step 8: Run adjacent regression tests**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminSchedulePeriodServiceTest,AdminLeaveRequestServiceTest"
```

Expected: both service test classes pass.

- [ ] **Step 9: Commit Task 1**

Run from repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/repository/SchedulePeriodRepository.java `
  backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodService.java `
  backend/src/main/java/scheduling/scheduling/admin/dto/SchedulePeriodRequest.java `
  backend/src/main/java/scheduling/scheduling/admin/dto/AdminSchedulePeriodResponse.java `
  backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodServiceTest.java
git commit -m "feat: add schedule period service"
```

---

### Task 2: SchedulePeriod Controller

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodController.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodControllerTest.java`
- Modify only if required: `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`

- [ ] **Step 1: Write the failing controller test**

Create `backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodControllerTest.java`:

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import scheduling.scheduling.admin.dto.AdminSchedulePeriodResponse;
import scheduling.scheduling.admin.dto.SchedulePeriodRequest;
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

@WebMvcTest(AdminSchedulePeriodController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminSchedulePeriodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminSchedulePeriodService adminSchedulePeriodService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerCreatesSchedulePeriod() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.createSchedulePeriod(any(CurrentAccountPrincipal.class), any(SchedulePeriodRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/admin/schedule-periods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodName": "Week 38",
                                  "startDate": "2026-09-14",
                                  "endDate": "2026-09-20",
                                  "versionNo": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        ArgumentCaptor<SchedulePeriodRequest> requestCaptor = ArgumentCaptor.forClass(SchedulePeriodRequest.class);
        verify(adminSchedulePeriodService).createSchedulePeriod(any(CurrentAccountPrincipal.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().periodName()).isEqualTo("Week 38");
        assertThat(requestCaptor.getValue().startDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void managerListsSchedulePeriodsWithFilters() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.listSchedulePeriods(
                any(CurrentAccountPrincipal.class), eq("DRAFT"), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/admin/schedule-periods")
                        .param("status", "DRAFT")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20));

        verify(adminSchedulePeriodService).listSchedulePeriods(
                any(CurrentAccountPrincipal.class), eq("DRAFT"), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)));
    }

    @Test
    void managerGetsSchedulePeriodDetail() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.getSchedulePeriod(any(CurrentAccountPrincipal.class), eq(20L)))
                .thenReturn(response());

        mockMvc.perform(get("/api/admin/schedule-periods/20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodName").value("Week 38"));

        verify(adminSchedulePeriodService).getSchedulePeriod(any(CurrentAccountPrincipal.class), eq(20L));
    }

    @Test
    void managerUpdatesSchedulePeriod() throws Exception {
        givenToken("manager-token", manager());
        when(adminSchedulePeriodService.updateSchedulePeriod(
                any(CurrentAccountPrincipal.class), eq(20L), any(SchedulePeriodRequest.class)))
                .thenReturn(response());

        mockMvc.perform(put("/api/admin/schedule-periods/20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodName": "Week 38",
                                  "startDate": "2026-09-14",
                                  "endDate": "2026-09-20",
                                  "versionNo": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20));

        verify(adminSchedulePeriodService).updateSchedulePeriod(
                any(CurrentAccountPrincipal.class), eq(20L), any(SchedulePeriodRequest.class));
    }

    @Test
    void createSchedulePeriodRejectsInvalidRequestBody() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(post("/api/admin/schedule-periods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodName": "",
                                  "startDate": "2026-09-14",
                                  "endDate": "2026-09-20",
                                  "versionNo": 1
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminSchedulePeriodService);
    }

    @Test
    void nonManagerCannotListSchedulePeriods() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/schedule-periods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminSchedulePeriodService);
    }

    @Test
    void anonymousCannotListSchedulePeriods() throws Exception {
        mockMvc.perform(get("/api/admin/schedule-periods"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminSchedulePeriodService);
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

    private static AdminSchedulePeriodResponse response() {
        return new AdminSchedulePeriodResponse(
                20L,
                "Week 38",
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 20),
                1,
                "DRAFT",
                1L,
                "manager01",
                null,
                null,
                null,
                null);
    }
}
```

- [ ] **Step 2: Run controller test to verify RED**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminSchedulePeriodControllerTest"
```

Expected: compilation fails because `AdminSchedulePeriodController` does not exist.

- [ ] **Step 3: Create controller**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodController.java`:

```java
package scheduling.scheduling.admin;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminSchedulePeriodResponse;
import scheduling.scheduling.admin.dto.SchedulePeriodRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/admin/schedule-periods")
@PreAuthorize("hasRole('MANAGER')")
public class AdminSchedulePeriodController {

    private final AdminSchedulePeriodService adminSchedulePeriodService;

    public AdminSchedulePeriodController(AdminSchedulePeriodService adminSchedulePeriodService) {
        this.adminSchedulePeriodService = adminSchedulePeriodService;
    }

    @PostMapping
    ResponseEntity<AdminSchedulePeriodResponse> createSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @Valid @RequestBody SchedulePeriodRequest request) {
        return ResponseEntity.ok(adminSchedulePeriodService.createSchedulePeriod(principal, request));
    }

    @GetMapping
    ResponseEntity<List<AdminSchedulePeriodResponse>> listSchedulePeriods(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminSchedulePeriodService.listSchedulePeriods(principal, status, from, to));
    }

    @GetMapping("/{periodId}")
    ResponseEntity<AdminSchedulePeriodResponse> getSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId) {
        return ResponseEntity.ok(adminSchedulePeriodService.getSchedulePeriod(principal, periodId));
    }

    @PutMapping("/{periodId}")
    ResponseEntity<AdminSchedulePeriodResponse> updateSchedulePeriod(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long periodId,
            @Valid @RequestBody SchedulePeriodRequest request) {
        return ResponseEntity.ok(adminSchedulePeriodService.updateSchedulePeriod(principal, periodId, request));
    }
}
```

- [ ] **Step 4: Run controller tests to verify GREEN**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminSchedulePeriodControllerTest,AdminSchedulePeriodServiceTest"
```

Expected: controller and service tests pass.

- [ ] **Step 5: Run context test**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=SchedulingApplicationTests"
```

Expected: context test passes. If it fails because `SchedulePeriodRepository` is not mocked in `SchedulingApplicationTests`, add:

```java
@MockitoBean
private SchedulePeriodRepository schedulePeriodRepository;
```

and import:

```java
import scheduling.scheduling.repository.SchedulePeriodRepository;
```

- [ ] **Step 6: Commit Task 2**

Run from repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/admin/AdminSchedulePeriodController.java `
  backend/src/test/java/scheduling/scheduling/admin/AdminSchedulePeriodControllerTest.java `
  backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java
git commit -m "feat: add schedule period endpoints"
```

If `SchedulingApplicationTests.java` was not modified, omit it from `git add`.

---

### Task 3: Roadmap Update And Full Verification

**Files:**
- Modify: `docs/project_plan.md`

- [ ] **Step 1: Update Phase 5 roadmap status**

In `docs/project_plan.md`, change the Phase 5 SchedulePeriod task from unchecked to checked:

```markdown
- [x] API tao ky lap lich `SchedulePeriod`
```

Add a short note under the current status section that Phase 5 has started on branch `phase5/schedule-period-apis` with DRAFT-only SchedulePeriod APIs.

- [ ] **Step 2: Run full backend verification**

Run from `backend`:

```powershell
.\mvnw.cmd clean test
```

Expected: full backend suite passes with no failures or errors.

- [ ] **Step 3: Check working tree**

Run from repo root:

```powershell
git status --short
```

Expected: only `docs/project_plan.md` is modified before the docs commit.

- [ ] **Step 4: Commit Task 3**

Run from repo root:

```powershell
git add docs/project_plan.md
git commit -m "docs: update phase 5 schedule period status"
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

Expected branch: `phase5/schedule-period-apis`.

Expected status: no output from `git status --short`.

- [ ] Review the implementation against `docs/superpowers/specs/2026-09-13-schedule-period-apis-design.md`:
  - only `DRAFT` status is supported;
  - endpoints are manager-only;
  - service also enforces manager role;
  - create/update validate names, dates, version, duplicates;
  - update rejects non-`DRAFT` periods;
  - no WorkShift, assignment, approve, publish, or AI behavior was added.

- [ ] Prepare branch completion using `superpowers:finishing-a-development-branch`.
