# Admin Leave Review APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add manager APIs to list, approve, and reject employee leave requests with review metadata.

**Architecture:** Extend the existing leave request model with `reviewNote`, then add focused admin DTO/controller/service code for manager review transitions. Reuse `LeaveRequestRepository` for list, locked lookup, and approved-overlap checks, and keep employee self-service services separate from admin review behavior.

**Tech Stack:** Java 21, Spring Boot 4, Spring MVC, Spring Security method annotations, Spring Data JPA, Bean Validation, JUnit 6, Mockito, AssertJ, MockMvc.

---

## Files

- Modify `backend/src/main/java/scheduling/scheduling/entity/LeaveRequest.java`: add `reviewNote` mapped to `review_note TEXT`.
- Modify `backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java`: add admin list query, locked id lookup, and approved-overlap query excluding current id.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminLeaveRequestController.java`: manager list/approve/reject endpoints.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminLeaveRequestService.java`: manager review logic.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/AdminLeaveRequestResponse.java`: manager leave response with employee and review summary.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/ReviewLeaveRequestRequest.java`: optional review note request.
- Modify `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`: add `LeaveRequestRepository` mock if not already present after prior Phase 4 work.
- Test `backend/src/test/java/scheduling/scheduling/admin/AdminLeaveRequestServiceTest.java`.
- Test `backend/src/test/java/scheduling/scheduling/admin/AdminLeaveRequestControllerTest.java`.
- Modify `docs/project_plan.md`: mark manager leave review APIs complete if the roadmap has matching items.

## Task 1: Leave Review Domain and Service

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/entity/LeaveRequest.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminLeaveRequestService.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminLeaveRequestResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/ReviewLeaveRequestRequest.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminLeaveRequestServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `backend/src/test/java/scheduling/scheduling/admin/AdminLeaveRequestServiceTest.java`:

```java
package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminLeaveRequestResponse;
import scheduling.scheduling.admin.dto.ReviewLeaveRequestRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;

class AdminLeaveRequestServiceTest {

    private final LeaveRequestRepository leaveRequestRepository = mock(LeaveRequestRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AdminLeaveRequestService service = new AdminLeaveRequestService(leaveRequestRepository, accountRepository);

    @Test
    void listLeaveRequestsNormalizesFilters() {
        LeaveRequest leaveRequest = leaveRequest(10L, employee(7L), "PENDING");
        when(leaveRequestRepository.searchForAdmin(7L, "PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(leaveRequest));

        List<AdminLeaveRequestResponse> response = service.listLeaveRequests(
                7L, " PENDING ", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response).extracting(AdminLeaveRequestResponse::id).containsExactly(10L);
        assertThat(response.get(0).employeeCode()).isEqualTo("E001");
        verify(leaveRequestRepository).searchForAdmin(7L, "PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void listLeaveRequestsRejectsInvalidStatus() {
        assertThatThrownBy(() -> service.listLeaveRequests(null, "WAITING", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listLeaveRequestsRejectsInvertedDateFilter() {
        assertThatThrownBy(() -> service.listLeaveRequests(null, null, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void approveLeaveRequestSetsReviewMetadata() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        LeaveRequest leaveRequest = leaveRequest(10L, employee(7L), "PENDING");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.existsApprovedOverlapExcludingId(7L, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), 10L))
                .thenReturn(false);
        when(leaveRequestRepository.save(leaveRequest)).thenReturn(leaveRequest);

        AdminLeaveRequestResponse response = service.approveLeaveRequest(
                principal, 10L, new ReviewLeaveRequestRequest(" Approved "));

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewNote()).isEqualTo("Approved");
        assertThat(response.reviewedByAccountId()).isEqualTo(1L);
        assertThat(response.reviewedByUsername()).isEqualTo("manager01");
        assertThat(response.reviewedAt()).isNotNull();
        verify(leaveRequestRepository).save(leaveRequest);
    }

    @Test
    void approveLeaveRequestRejectsNonPendingRequest() {
        CurrentAccountPrincipal principal = managerPrincipal();
        LeaveRequest leaveRequest = leaveRequest(10L, employee(7L), "CANCELLED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(leaveRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(leaveRequest));

        assertThatThrownBy(() -> service.approveLeaveRequest(principal, 10L, new ReviewLeaveRequestRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void approveLeaveRequestRejectsApprovedOverlap() {
        CurrentAccountPrincipal principal = managerPrincipal();
        LeaveRequest leaveRequest = leaveRequest(10L, employee(7L), "PENDING");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(leaveRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.existsApprovedOverlapExcludingId(7L, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), 10L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.approveLeaveRequest(principal, 10L, new ReviewLeaveRequestRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectLeaveRequestSetsReviewMetadataAndBlankNoteToNull() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        LeaveRequest leaveRequest = leaveRequest(10L, employee(7L), "PENDING");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.save(leaveRequest)).thenReturn(leaveRequest);

        AdminLeaveRequestResponse response = service.rejectLeaveRequest(
                principal, 10L, new ReviewLeaveRequestRequest("   "));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.reviewNote()).isNull();
        assertThat(response.reviewedByAccountId()).isEqualTo(1L);
        assertThat(response.reviewedAt()).isNotNull();
    }

    @Test
    void rejectLeaveRequestRejectsNonPendingRequest() {
        CurrentAccountPrincipal principal = managerPrincipal();
        LeaveRequest leaveRequest = leaveRequest(10L, employee(7L), "APPROVED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(leaveRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(leaveRequest));

        assertThatThrownBy(() -> service.rejectLeaveRequest(principal, 10L, new ReviewLeaveRequestRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reviewRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.rejectLeaveRequest(principal, 10L, new ReviewLeaveRequestRequest(null)))
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

    private static Employee employee(Long id) {
        Employee employee = Employee.builder()
                .employeeCode("E001")
                .fullName("Nguyen Van A")
                .status("ACTIVE")
                .build();
        employee.setId(id);
        return employee;
    }

    private static LeaveRequest leaveRequest(Long id, Employee employee, String status) {
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employee(employee)
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 15))
                .reason("Family appointment")
                .status(status)
                .build();
        leaveRequest.setId(id);
        return leaveRequest;
    }
}
```

- [ ] **Step 2: Run service tests to verify RED**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminLeaveRequestServiceTest"
```

Expected: compilation fails because `AdminLeaveRequestService`, DTOs, repository methods, and `reviewNote` do not exist yet.

- [ ] **Step 3: Add domain field, DTOs, and repository methods**

Modify `backend/src/main/java/scheduling/scheduling/entity/LeaveRequest.java` to add the field after `reason`:

```java
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;
```

Create `backend/src/main/java/scheduling/scheduling/admin/dto/ReviewLeaveRequestRequest.java`:

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.Size;

public record ReviewLeaveRequestRequest(@Size(max = 1000) String reviewNote) {
}
```

Create `backend/src/main/java/scheduling/scheduling/admin/dto/AdminLeaveRequestResponse.java`:

```java
package scheduling.scheduling.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;

public record AdminLeaveRequestResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        String status,
        String reviewNote,
        Long reviewedByAccountId,
        String reviewedByUsername,
        LocalDateTime reviewedAt) {

    public static AdminLeaveRequestResponse from(LeaveRequest leaveRequest) {
        Employee employee = leaveRequest.getEmployee();
        Account reviewedBy = leaveRequest.getReviewedBy();
        return new AdminLeaveRequestResponse(
                leaveRequest.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                leaveRequest.getReason(),
                leaveRequest.getStatus(),
                leaveRequest.getReviewNote(),
                reviewedBy == null ? null : reviewedBy.getId(),
                reviewedBy == null ? null : reviewedBy.getUsername(),
                leaveRequest.getReviewedAt());
    }
}
```

Modify `backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java` to add methods below existing methods:

```java
    @Query("""
            select l from LeaveRequest l
            join fetch l.employee e
            left join fetch l.reviewedBy r
            where (:employeeId is null or e.id = :employeeId)
            and (:status is null or l.status = :status)
            and (:from is null or l.endDate >= :from)
            and (:to is null or l.startDate <= :to)
            order by l.id desc
            """)
    List<LeaveRequest> searchForAdmin(
            @Param("employeeId") Long employeeId,
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from LeaveRequest l
            where l.id = :id
            """)
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(l) > 0 from LeaveRequest l
            where l.employee.id = :employeeId
            and l.status = 'APPROVED'
            and l.startDate <= :endDate
            and l.endDate >= :startDate
            and l.id <> :excludedLeaveRequestId
            """)
    boolean existsApprovedOverlapExcludingId(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludedLeaveRequestId") Long excludedLeaveRequestId);
```

- [ ] **Step 4: Add admin leave request service**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminLeaveRequestService.java`:

```java
package scheduling.scheduling.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminLeaveRequestResponse;
import scheduling.scheduling.admin.dto.ReviewLeaveRequestRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;

@Service
public class AdminLeaveRequestService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";

    private final LeaveRequestRepository leaveRequestRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public AdminLeaveRequestService(LeaveRequestRepository leaveRequestRepository, AccountRepository accountRepository) {
        this(leaveRequestRepository, accountRepository, Clock.systemDefaultZone());
    }

    AdminLeaveRequestService(LeaveRequestRepository leaveRequestRepository, AccountRepository accountRepository, Clock clock) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminLeaveRequestResponse> listLeaveRequests(Long employeeId, String status, LocalDate from, LocalDate to) {
        String normalizedStatus = normalizeStatus(status);
        validateDateFilter(from, to);
        return leaveRequestRepository.searchForAdmin(employeeId, normalizedStatus, from, to).stream()
                .map(AdminLeaveRequestResponse::from)
                .toList();
    }

    @Transactional
    public AdminLeaveRequestResponse approveLeaveRequest(
            CurrentAccountPrincipal principal, Long leaveRequestId, ReviewLeaveRequestRequest request) {
        Account manager = requireManager(principal);
        LeaveRequest leaveRequest = findForUpdate(leaveRequestId);
        requirePending(leaveRequest);
        Long employeeId = leaveRequest.getEmployee().getId();
        if (leaveRequestRepository.existsApprovedOverlapExcludingId(
                employeeId, leaveRequest.getStartDate(), leaveRequest.getEndDate(), leaveRequest.getId())) {
            throw invalidRequest();
        }
        applyReview(leaveRequest, manager, APPROVED, request.reviewNote());
        return AdminLeaveRequestResponse.from(leaveRequestRepository.save(leaveRequest));
    }

    @Transactional
    public AdminLeaveRequestResponse rejectLeaveRequest(
            CurrentAccountPrincipal principal, Long leaveRequestId, ReviewLeaveRequestRequest request) {
        Account manager = requireManager(principal);
        LeaveRequest leaveRequest = findForUpdate(leaveRequestId);
        requirePending(leaveRequest);
        applyReview(leaveRequest, manager, REJECTED, request.reviewNote());
        return AdminLeaveRequestResponse.from(leaveRequestRepository.save(leaveRequest));
    }

    private Account requireManager(CurrentAccountPrincipal principal) {
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        return account;
    }

    private LeaveRequest findForUpdate(Long leaveRequestId) {
        return leaveRequestRepository.findByIdForUpdate(leaveRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));
    }

    private static void requirePending(LeaveRequest leaveRequest) {
        if (!PENDING.equals(leaveRequest.getStatus())) {
            throw invalidRequest();
        }
    }

    private void applyReview(LeaveRequest leaveRequest, Account manager, String status, String reviewNote) {
        leaveRequest.setStatus(status);
        leaveRequest.setReviewedBy(manager);
        leaveRequest.setReviewedAt(LocalDateTime.now(clock));
        leaveRequest.setReviewNote(StringUtils.hasText(reviewNote) ? reviewNote.trim() : null);
    }

    private static void validateDateFilter(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw invalidRequest();
        }
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim();
        if (!PENDING.equals(normalized) && !APPROVED.equals(normalized)
                && !REJECTED.equals(normalized) && !CANCELLED.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leave review request");
    }
}
```

- [ ] **Step 5: Run service tests to verify GREEN**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminLeaveRequestServiceTest,EmployeeLeaveRequestServiceTest"
```

Expected: admin leave service and existing employee leave service tests pass.

- [ ] **Step 6: Commit Task 1**

Run from repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/entity/LeaveRequest.java backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java backend/src/main/java/scheduling/scheduling/admin/AdminLeaveRequestService.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminLeaveRequestResponse.java backend/src/main/java/scheduling/scheduling/admin/dto/ReviewLeaveRequestRequest.java backend/src/test/java/scheduling/scheduling/admin/AdminLeaveRequestServiceTest.java
git commit -m "feat: add admin leave review service"
```

## Task 2: Admin Leave Review Controller

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminLeaveRequestController.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminLeaveRequestControllerTest.java`
- Modify: `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`

- [ ] **Step 1: Write failing controller tests**

Create `backend/src/test/java/scheduling/scheduling/admin/AdminLeaveRequestControllerTest.java`:

```java
package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import scheduling.scheduling.admin.dto.AdminLeaveRequestResponse;
import scheduling.scheduling.admin.dto.ReviewLeaveRequestRequest;
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

@WebMvcTest(AdminLeaveRequestController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminLeaveRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminLeaveRequestService adminLeaveRequestService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerListsLeaveRequests() throws Exception {
        givenToken("manager-token", manager());
        when(adminLeaveRequestService.listLeaveRequests(7L, "PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(response("PENDING")));

        mockMvc.perform(get("/api/admin/leave-requests")
                        .param("employeeId", "7")
                        .param("status", "PENDING")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].employeeCode").value("E001"));
    }

    @Test
    void managerApprovesLeaveRequest() throws Exception {
        givenToken("manager-token", manager());
        when(adminLeaveRequestService.approveLeaveRequest(any(CurrentAccountPrincipal.class), any(Long.class), any(ReviewLeaveRequestRequest.class)))
                .thenReturn(response("APPROVED"));

        mockMvc.perform(post("/api/admin/leave-requests/10/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewNote": "Approved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        ArgumentCaptor<ReviewLeaveRequestRequest> requestCaptor = ArgumentCaptor.forClass(ReviewLeaveRequestRequest.class);
        verify(adminLeaveRequestService).approveLeaveRequest(any(CurrentAccountPrincipal.class), org.mockito.ArgumentMatchers.eq(10L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().reviewNote()).isEqualTo("Approved");
    }

    @Test
    void managerRejectsLeaveRequest() throws Exception {
        givenToken("manager-token", manager());
        when(adminLeaveRequestService.rejectLeaveRequest(any(CurrentAccountPrincipal.class), any(Long.class), any(ReviewLeaveRequestRequest.class)))
                .thenReturn(response("REJECTED"));

        mockMvc.perform(post("/api/admin/leave-requests/10/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewNote": "Coverage is not enough"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void reviewLeaveRequestRejectsInvalidBody() throws Exception {
        givenToken("manager-token", manager());
        String longNote = "a".repeat(1001);

        mockMvc.perform(post("/api/admin/leave-requests/10/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewNote": "%s"
                                }
                                """.formatted(longNote)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminLeaveRequestService);
    }

    @Test
    void employeeCannotAccessAdminLeaveRequestApis() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminLeaveRequestService);
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

    private static AdminLeaveRequestResponse response(String status) {
        return new AdminLeaveRequestResponse(
                10L,
                7L,
                "E001",
                "Nguyen Van A",
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 15),
                "Family appointment",
                status,
                "Approved",
                1L,
                "manager01",
                LocalDateTime.of(2026, 9, 13, 10, 0));
    }
}
```

- [ ] **Step 2: Run controller tests to verify RED**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminLeaveRequestControllerTest"
```

Expected: compilation fails because `AdminLeaveRequestController` does not exist.

- [ ] **Step 3: Add admin leave request controller**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminLeaveRequestController.java`:

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminLeaveRequestResponse;
import scheduling.scheduling.admin.dto.ReviewLeaveRequestRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/admin/leave-requests")
@PreAuthorize("hasRole('MANAGER')")
public class AdminLeaveRequestController {

    private final AdminLeaveRequestService adminLeaveRequestService;

    public AdminLeaveRequestController(AdminLeaveRequestService adminLeaveRequestService) {
        this.adminLeaveRequestService = adminLeaveRequestService;
    }

    @GetMapping
    ResponseEntity<List<AdminLeaveRequestResponse>> listLeaveRequests(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminLeaveRequestService.listLeaveRequests(employeeId, status, from, to));
    }

    @PostMapping("/{leaveRequestId}/approve")
    ResponseEntity<AdminLeaveRequestResponse> approveLeaveRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long leaveRequestId,
            @Valid @RequestBody ReviewLeaveRequestRequest request) {
        return ResponseEntity.ok(adminLeaveRequestService.approveLeaveRequest(principal, leaveRequestId, request));
    }

    @PostMapping("/{leaveRequestId}/reject")
    ResponseEntity<AdminLeaveRequestResponse> rejectLeaveRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long leaveRequestId,
            @Valid @RequestBody ReviewLeaveRequestRequest request) {
        return ResponseEntity.ok(adminLeaveRequestService.rejectLeaveRequest(principal, leaveRequestId, request));
    }
}
```

- [ ] **Step 4: Update context test mocks if needed**

Inspect `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java`. If it uses `@MockitoBean` for repositories because JPA auto-config is excluded, ensure `LeaveRequestRepository` is mocked. The file should include:

```java
@MockitoBean
private LeaveRequestRepository leaveRequestRepository;
```

If the mock already exists from Phase 4 employee APIs, do not add a duplicate.

- [ ] **Step 5: Run targeted admin leave review tests to verify GREEN**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminLeaveRequestServiceTest,AdminLeaveRequestControllerTest,EmployeeLeaveRequestServiceTest,EmployeeLeaveRequestControllerTest"
```

Expected: admin leave review and existing employee leave tests pass.

- [ ] **Step 6: Commit Task 2**

Run from repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/admin/AdminLeaveRequestController.java backend/src/test/java/scheduling/scheduling/admin/AdminLeaveRequestControllerTest.java backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java
git commit -m "feat: expose admin leave review apis"
```

## Task 3: Roadmap Update and Full Verification

**Files:**
- Modify: `docs/project_plan.md`

- [ ] **Step 1: Update roadmap status**

In `docs/project_plan.md`, locate the manager leave review lines around the manager/admin roadmap section. Mark the completed manager leave list/review item as checked. If the roadmap item is a single combined line, use this exact text:

```markdown
- [x] API manager xem/duyet leave requests
```

Do not mark notification, audit log, preference review, or AI payload work complete.

- [ ] **Step 2: Run full backend verification**

Run from `backend`:

```powershell
.\mvnw.cmd clean test
```

Expected: all backend tests pass with zero failures and zero errors.

- [ ] **Step 3: Commit roadmap update**

Run from repo root:

```powershell
git add docs/project_plan.md
git commit -m "docs: update admin leave review progress"
```

## Self-Review

- Spec coverage: Task 1 covers review note field, manager service behavior, review metadata, locked lookup, approved-overlap validation, status/date validation, and response mapping. Task 2 covers endpoints, method security, request binding, and controller authorization. Task 3 covers roadmap and full regression.
- Placeholder scan: no incomplete markers remain; each code step includes concrete file content and commands.
- Type consistency: `reviewNote`, `reviewedByAccountId`, `reviewedByUsername`, `reviewedAt`, service method names, and repository method names are consistent across tests, DTOs, service, and controller.
