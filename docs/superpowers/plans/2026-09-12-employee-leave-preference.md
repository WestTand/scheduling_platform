# Employee Leave and Preference APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Phase 4 employee self-service APIs for employees to submit, list, update, and cancel their own leave requests and preferences.

**Architecture:** Add a focused `employee` API package with separate leave and preference controllers/services/DTOs. Services resolve the current employee from `CurrentAccountPrincipal` and enforce ownership, pending-only mutations, overlap/duplicate validation, and active shift-type validation. Keep the existing string status style and add repositories for query operations.

**Tech Stack:** Java 21, Spring Boot 4, Spring MVC, Spring Security method annotations, Spring Data JPA, Bean Validation, JUnit 6, Mockito, AssertJ, MockMvc.

---

## Files

- Create `backend/src/main/java/scheduling/scheduling/employee/EmployeeAccess.java`: helper service for resolving the authenticated employee.
- Create `backend/src/main/java/scheduling/scheduling/employee/EmployeeLeaveRequestController.java`: employee leave endpoints.
- Create `backend/src/main/java/scheduling/scheduling/employee/EmployeeLeaveRequestService.java`: leave business rules.
- Create `backend/src/main/java/scheduling/scheduling/employee/EmployeePreferenceController.java`: employee preference endpoints.
- Create `backend/src/main/java/scheduling/scheduling/employee/EmployeePreferenceService.java`: preference business rules.
- Create `backend/src/main/java/scheduling/scheduling/employee/dto/CreateLeaveRequestRequest.java`: leave create body.
- Create `backend/src/main/java/scheduling/scheduling/employee/dto/EmployeeLeaveRequestResponse.java`: leave response.
- Create `backend/src/main/java/scheduling/scheduling/employee/dto/CreatePreferenceRequest.java`: preference create body.
- Create `backend/src/main/java/scheduling/scheduling/employee/dto/UpdatePreferenceRequest.java`: preference update body.
- Create `backend/src/main/java/scheduling/scheduling/employee/dto/EmployeePreferenceResponse.java`: preference response.
- Create `backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java`: leave queries and overlap checks.
- Create `backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java`: preference queries and duplicate checks.
- Modify `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java`: add generic `ResponseStatusException` JSON handling if tests require stable error bodies.
- Test `backend/src/test/java/scheduling/scheduling/employee/EmployeeLeaveRequestServiceTest.java`.
- Test `backend/src/test/java/scheduling/scheduling/employee/EmployeeLeaveRequestControllerTest.java`.
- Test `backend/src/test/java/scheduling/scheduling/employee/EmployeePreferenceServiceTest.java`.
- Test `backend/src/test/java/scheduling/scheduling/employee/EmployeePreferenceControllerTest.java`.
- Modify `docs/project_plan.md`: mark the employee leave/preference API items complete after verification.

## Task 1: Employee Leave Request APIs

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeeAccess.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeeLeaveRequestController.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeeLeaveRequestService.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/dto/CreateLeaveRequestRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/dto/EmployeeLeaveRequestResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java`
- Test: `backend/src/test/java/scheduling/scheduling/employee/EmployeeLeaveRequestServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/employee/EmployeeLeaveRequestControllerTest.java`

- [ ] **Step 1: Write failing service tests for leave behavior**

Create `backend/src/test/java/scheduling/scheduling/employee/EmployeeLeaveRequestServiceTest.java` with these tests:

```java
package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.employee.dto.CreateLeaveRequestRequest;
import scheduling.scheduling.employee.dto.EmployeeLeaveRequestResponse;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;

class EmployeeLeaveRequestServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final LeaveRequestRepository leaveRequestRepository = mock(LeaveRequestRepository.class);
    private final EmployeeAccess employeeAccess = new EmployeeAccess(accountRepository);
    private final EmployeeLeaveRequestService service = new EmployeeLeaveRequestService(employeeAccess, leaveRequestRepository);

    @Test
    void listLeaveRequestsFiltersByCurrentEmployee() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        LeaveRequest leaveRequest = leaveRequest(10L, employee, "PENDING");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(leaveRequestRepository.searchForEmployee(7L, "PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(leaveRequest));

        List<EmployeeLeaveRequestResponse> response = service.listLeaveRequests(
                principal, " PENDING ", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response).extracting(EmployeeLeaveRequestResponse::id).containsExactly(10L);
        verify(leaveRequestRepository).searchForEmployee(7L, "PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void createLeaveRequestDefaultsPending() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        CreateLeaveRequestRequest request = createRequest(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(leaveRequestRepository.existsOverlappingActiveRequest(7L, request.startDate(), request.endDate()))
                .thenReturn(false);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        EmployeeLeaveRequestResponse response = service.createLeaveRequest(principal, request);

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.startDate()).isEqualTo(request.startDate());
        assertThat(response.endDate()).isEqualTo(request.endDate());
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void createLeaveRequestRejectsInvalidDateRange() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));

        assertThatThrownBy(() -> service.createLeaveRequest(
                principal,
                createRequest(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 14))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequestRejectsOverlappingPendingOrApprovedRequest() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        CreateLeaveRequestRequest request = createRequest(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(leaveRequestRepository.existsOverlappingActiveRequest(7L, request.startDate(), request.endDate()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createLeaveRequest(principal, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelLeaveRequestOnlyCancelsOwnPendingRequest() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        LeaveRequest leaveRequest = leaveRequest(10L, employee, "PENDING");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(leaveRequestRepository.findByIdAndEmployeeId(10L, 7L)).thenReturn(Optional.of(leaveRequest));

        service.cancelLeaveRequest(principal, 10L);

        assertThat(leaveRequest.getStatus()).isEqualTo("CANCELLED");
        verify(leaveRequestRepository).save(leaveRequest);
    }

    @Test
    void cancelLeaveRequestRejectsNonPendingRequest() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        LeaveRequest leaveRequest = leaveRequest(10L, employee, "APPROVED");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(leaveRequestRepository.findByIdAndEmployeeId(10L, 7L)).thenReturn(Optional.of(leaveRequest));

        assertThatThrownBy(() -> service.cancelLeaveRequest(principal, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void employeeResolutionRejectsManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal, null)));

        assertThatThrownBy(() -> service.listLeaveRequests(principal, null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static CreateLeaveRequestRequest createRequest(LocalDate startDate, LocalDate endDate) {
        return new CreateLeaveRequestRequest(startDate, endDate, "Family appointment");
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

- [ ] **Step 2: Run service test to verify RED**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=EmployeeLeaveRequestServiceTest"
```

Expected: compilation fails because `EmployeeAccess`, `EmployeeLeaveRequestService`, leave DTOs, and `LeaveRequestRepository` do not exist.

- [ ] **Step 3: Add leave DTOs and repository**

Create `backend/src/main/java/scheduling/scheduling/employee/dto/CreateLeaveRequestRequest.java`:

```java
package scheduling.scheduling.employee.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateLeaveRequestRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 1000) String reason) {
}
```

Create `backend/src/main/java/scheduling/scheduling/employee/dto/EmployeeLeaveRequestResponse.java`:

```java
package scheduling.scheduling.employee.dto;

import java.time.LocalDate;
import scheduling.scheduling.entity.LeaveRequest;

public record EmployeeLeaveRequestResponse(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        String status) {

    public static EmployeeLeaveRequestResponse from(LeaveRequest leaveRequest) {
        return new EmployeeLeaveRequestResponse(
                leaveRequest.getId(),
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                leaveRequest.getReason(),
                leaveRequest.getStatus());
    }
}
```

Create `backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java`:

```java
package scheduling.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.LeaveRequest;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @Query("""
            select l from LeaveRequest l
            where l.employee.id = :employeeId
            and (:status is null or l.status = :status)
            and (:from is null or l.endDate >= :from)
            and (:to is null or l.startDate <= :to)
            order by l.id desc
            """)
    List<LeaveRequest> searchForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select count(l) > 0 from LeaveRequest l
            where l.employee.id = :employeeId
            and l.status in ('PENDING', 'APPROVED')
            and l.startDate <= :endDate
            and l.endDate >= :startDate
            """)
    boolean existsOverlappingActiveRequest(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    Optional<LeaveRequest> findByIdAndEmployeeId(Long id, Long employeeId);
}
```

- [ ] **Step 4: Add employee access helper and leave service**

Create `backend/src/main/java/scheduling/scheduling/employee/EmployeeAccess.java`:

```java
package scheduling.scheduling.employee;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;

@Service
public class EmployeeAccess {

    private final AccountRepository accountRepository;

    public EmployeeAccess(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Employee requireActiveEmployee(CurrentAccountPrincipal principal) {
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee access required"));
        if (account.getRole() != AccountRole.EMPLOYEE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee access required");
        }
        Employee employee = account.getEmployee();
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee profile not found");
        }
        if (!"ACTIVE".equals(employee.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee is inactive");
        }
        return employee;
    }
}
```

Create `backend/src/main/java/scheduling/scheduling/employee/EmployeeLeaveRequestService.java`:

```java
package scheduling.scheduling.employee;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.employee.dto.CreateLeaveRequestRequest;
import scheduling.scheduling.employee.dto.EmployeeLeaveRequestResponse;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.repository.LeaveRequestRepository;

@Service
public class EmployeeLeaveRequestService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";

    private final EmployeeAccess employeeAccess;
    private final LeaveRequestRepository leaveRequestRepository;

    public EmployeeLeaveRequestService(EmployeeAccess employeeAccess, LeaveRequestRepository leaveRequestRepository) {
        this.employeeAccess = employeeAccess;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @Transactional(readOnly = true)
    public List<EmployeeLeaveRequestResponse> listLeaveRequests(
            CurrentAccountPrincipal principal, String status, LocalDate from, LocalDate to) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        String normalizedStatus = normalizeStatus(status);
        return leaveRequestRepository.searchForEmployee(employee.getId(), normalizedStatus, from, to).stream()
                .map(EmployeeLeaveRequestResponse::from)
                .toList();
    }

    @Transactional
    public EmployeeLeaveRequestResponse createLeaveRequest(CurrentAccountPrincipal principal, CreateLeaveRequestRequest request) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        validateDateRange(request.startDate(), request.endDate());
        if (leaveRequestRepository.existsOverlappingActiveRequest(employee.getId(), request.startDate(), request.endDate())) {
            throw invalidRequest();
        }
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .employee(employee)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .reason(StringUtils.hasText(request.reason()) ? request.reason().trim() : null)
                .status(PENDING)
                .build();
        return EmployeeLeaveRequestResponse.from(leaveRequestRepository.save(leaveRequest));
    }

    @Transactional
    public void cancelLeaveRequest(CurrentAccountPrincipal principal, Long leaveRequestId) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        LeaveRequest leaveRequest = leaveRequestRepository.findByIdAndEmployeeId(leaveRequestId, employee.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (!PENDING.equals(leaveRequest.getStatus())) {
            throw invalidRequest();
        }
        leaveRequest.setStatus(CANCELLED);
        leaveRequestRepository.save(leaveRequest);
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
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
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leave request");
    }
}
```

- [ ] **Step 5: Run service tests to verify GREEN**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=EmployeeLeaveRequestServiceTest"
```

Expected: tests compile and pass.

- [ ] **Step 6: Write failing controller tests for leave endpoints**

Create `backend/src/test/java/scheduling/scheduling/employee/EmployeeLeaveRequestControllerTest.java` with these tests:

```java
package scheduling.scheduling.employee;

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
import scheduling.scheduling.employee.dto.CreateLeaveRequestRequest;
import scheduling.scheduling.employee.dto.EmployeeLeaveRequestResponse;
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

@WebMvcTest(EmployeeLeaveRequestController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class EmployeeLeaveRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeLeaveRequestService employeeLeaveRequestService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void employeeListsLeaveRequests() throws Exception {
        givenToken("employee-token", employee());
        when(employeeLeaveRequestService.listLeaveRequests(
                any(CurrentAccountPrincipal.class), any(), any(), any()))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/employee/leave-requests")
                        .param("status", "PENDING")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void employeeCreatesLeaveRequest() throws Exception {
        givenToken("employee-token", employee());
        when(employeeLeaveRequestService.createLeaveRequest(any(CurrentAccountPrincipal.class), any(CreateLeaveRequestRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/employee/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-09-14",
                                  "endDate": "2026-09-15",
                                  "reason": "Family appointment"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));

        ArgumentCaptor<CreateLeaveRequestRequest> requestCaptor = ArgumentCaptor.forClass(CreateLeaveRequestRequest.class);
        verify(employeeLeaveRequestService).createLeaveRequest(any(CurrentAccountPrincipal.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().startDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void createLeaveRequestRejectsInvalidBody() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "endDate": "2026-09-15"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeeLeaveRequestService);
    }

    @Test
    void employeeCancelsLeaveRequest() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/leave-requests/10/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isNoContent());

        verify(employeeLeaveRequestService).cancelLeaveRequest(any(CurrentAccountPrincipal.class), org.mockito.ArgumentMatchers.eq(10L));
    }

    @Test
    void managerCannotAccessEmployeeLeaveApis() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(get("/api/employee/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(employeeLeaveRequestService);
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

    private static EmployeeLeaveRequestResponse response() {
        return new EmployeeLeaveRequestResponse(
                10L,
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 15),
                "Family appointment",
                "PENDING");
    }
}
```

- [ ] **Step 7: Run controller test to verify RED**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=EmployeeLeaveRequestControllerTest"
```

Expected: compilation fails because `EmployeeLeaveRequestController` does not exist.

- [ ] **Step 8: Add leave controller**

Create `backend/src/main/java/scheduling/scheduling/employee/EmployeeLeaveRequestController.java`:

```java
package scheduling.scheduling.employee;

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
import scheduling.scheduling.employee.dto.CreateLeaveRequestRequest;
import scheduling.scheduling.employee.dto.EmployeeLeaveRequestResponse;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/employee/leave-requests")
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeLeaveRequestController {

    private final EmployeeLeaveRequestService employeeLeaveRequestService;

    public EmployeeLeaveRequestController(EmployeeLeaveRequestService employeeLeaveRequestService) {
        this.employeeLeaveRequestService = employeeLeaveRequestService;
    }

    @GetMapping
    ResponseEntity<List<EmployeeLeaveRequestResponse>> listLeaveRequests(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(employeeLeaveRequestService.listLeaveRequests(principal, status, from, to));
    }

    @PostMapping
    ResponseEntity<EmployeeLeaveRequestResponse> createLeaveRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @Valid @RequestBody CreateLeaveRequestRequest request) {
        return ResponseEntity.status(201).body(employeeLeaveRequestService.createLeaveRequest(principal, request));
    }

    @PostMapping("/{leaveRequestId}/cancel")
    ResponseEntity<Void> cancelLeaveRequest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long leaveRequestId) {
        employeeLeaveRequestService.cancelLeaveRequest(principal, leaveRequestId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 9: Run leave targeted tests to verify GREEN**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=EmployeeLeaveRequestServiceTest,EmployeeLeaveRequestControllerTest"
```

Expected: leave service and controller tests pass.

- [ ] **Step 10: Commit leave APIs**

Run from repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/employee/EmployeeAccess.java backend/src/main/java/scheduling/scheduling/employee/EmployeeLeaveRequestController.java backend/src/main/java/scheduling/scheduling/employee/EmployeeLeaveRequestService.java backend/src/main/java/scheduling/scheduling/employee/dto/CreateLeaveRequestRequest.java backend/src/main/java/scheduling/scheduling/employee/dto/EmployeeLeaveRequestResponse.java backend/src/main/java/scheduling/scheduling/repository/LeaveRequestRepository.java backend/src/test/java/scheduling/scheduling/employee/EmployeeLeaveRequestServiceTest.java backend/src/test/java/scheduling/scheduling/employee/EmployeeLeaveRequestControllerTest.java
git commit -m "feat: add employee leave request apis"
```

## Task 2: Employee Preference APIs

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeePreferenceController.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/EmployeePreferenceService.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/dto/CreatePreferenceRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/dto/UpdatePreferenceRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/employee/dto/EmployeePreferenceResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java`
- Test: `backend/src/test/java/scheduling/scheduling/employee/EmployeePreferenceServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/employee/EmployeePreferenceControllerTest.java`

- [ ] **Step 1: Write failing service tests for preference behavior**

Create `backend/src/test/java/scheduling/scheduling/employee/EmployeePreferenceServiceTest.java` with tests covering:

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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.employee.dto.CreatePreferenceRequest;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.employee.dto.UpdatePreferenceRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.Preference;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;

class EmployeePreferenceServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final PreferenceRepository preferenceRepository = mock(PreferenceRepository.class);
    private final ShiftTypeRepository shiftTypeRepository = mock(ShiftTypeRepository.class);
    private final EmployeeAccess employeeAccess = new EmployeeAccess(accountRepository);
    private final EmployeePreferenceService service = new EmployeePreferenceService(
            employeeAccess, preferenceRepository, shiftTypeRepository);

    @Test
    void listPreferencesFiltersByCurrentEmployee() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        Preference preference = preference(20L, employee, null, "WANT", "PENDING");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.searchForEmployee(7L, "PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(preference));

        List<EmployeePreferenceResponse> response = service.listPreferences(
                principal, " PENDING ", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response).extracting(EmployeePreferenceResponse::id).containsExactly(20L);
    }

    @ParameterizedTest
    @ValueSource(strings = { "WANT", "AVOID" })
    void createPreferenceAcceptsValidTypes(String preferenceType) {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        ShiftType shiftType = shiftType(10L, "ACTIVE");
        CreatePreferenceRequest request = createRequest(preferenceType, 10L);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));
        when(preferenceRepository.existsActiveDuplicate(7L, request.preferenceDate(), 10L, preferenceType, null)).thenReturn(false);
        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> {
            Preference saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        EmployeePreferenceResponse response = service.createPreference(principal, request);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.preferenceType()).isEqualTo(preferenceType);
        assertThat(response.shiftTypeId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void createPreferenceSupportsNullShiftType() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        CreatePreferenceRequest request = createRequest("WANT", null);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.existsActiveDuplicate(7L, request.preferenceDate(), null, "WANT", null)).thenReturn(false);
        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeePreferenceResponse response = service.createPreference(principal, request);

        assertThat(response.shiftTypeId()).isNull();
    }

    @Test
    void createPreferenceRejectsInvalidType() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));

        assertThatThrownBy(() -> service.createPreference(principal, createRequest("MAYBE", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPreferenceRejectsInactiveShiftType() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType(10L, "INACTIVE")));

        assertThatThrownBy(() -> service.createPreference(principal, createRequest("WANT", 10L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPreferenceRejectsDuplicateActivePreference() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        CreatePreferenceRequest request = createRequest("WANT", null);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.existsActiveDuplicate(7L, request.preferenceDate(), null, "WANT", null)).thenReturn(true);

        assertThatThrownBy(() -> service.createPreference(principal, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void updatePreferenceOnlyUpdatesOwnPendingPreference() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        Preference preference = preference(20L, employee, null, "WANT", "PENDING");
        UpdatePreferenceRequest request = new UpdatePreferenceRequest(LocalDate.of(2026, 9, 16), "AVOID", null, 2, "Avoid that day");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.findByIdAndEmployeeId(20L, 7L)).thenReturn(Optional.of(preference));
        when(preferenceRepository.existsActiveDuplicate(7L, request.preferenceDate(), null, "AVOID", 20L)).thenReturn(false);
        when(preferenceRepository.save(preference)).thenReturn(preference);

        EmployeePreferenceResponse response = service.updatePreference(principal, 20L, request);

        assertThat(response.preferenceDate()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(response.preferenceType()).isEqualTo("AVOID");
        assertThat(response.priority()).isEqualTo(2);
    }

    @Test
    void cancelPreferenceOnlyCancelsOwnPendingPreference() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        Preference preference = preference(20L, employee, null, "WANT", "PENDING");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(preferenceRepository.findByIdAndEmployeeId(20L, 7L)).thenReturn(Optional.of(preference));

        service.cancelPreference(principal, 20L);

        assertThat(preference.getStatus()).isEqualTo("CANCELLED");
        verify(preferenceRepository).save(preference);
    }

    private static CreatePreferenceRequest createRequest(String preferenceType, Long shiftTypeId) {
        return new CreatePreferenceRequest(LocalDate.of(2026, 9, 14), preferenceType, shiftTypeId, 1, "note");
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
        Employee employee = Employee.builder().employeeCode("E001").fullName("Employee One").status(status).build();
        employee.setId(id);
        return employee;
    }

    private static ShiftType shiftType(Long id, String status) {
        ShiftType shiftType = ShiftType.builder()
                .shiftName("Morning")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .durationHours(BigDecimal.valueOf(4.0))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(3)
                .quotaCategory("REGULAR")
                .status(status)
                .build();
        shiftType.setId(id);
        return shiftType;
    }

    private static Preference preference(Long id, Employee employee, ShiftType shiftType, String preferenceType, String status) {
        Preference preference = Preference.builder()
                .employee(employee)
                .shiftType(shiftType)
                .preferenceDate(LocalDate.of(2026, 9, 14))
                .preferenceType(preferenceType)
                .priority(1)
                .note("note")
                .status(status)
                .build();
        preference.setId(id);
        return preference;
    }
}
```

- [ ] **Step 2: Run service test to verify RED**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=EmployeePreferenceServiceTest"
```

Expected: compilation fails because preference DTOs, repository, and service do not exist.

- [ ] **Step 3: Add preference DTOs and repository**

Create `backend/src/main/java/scheduling/scheduling/employee/dto/CreatePreferenceRequest.java`:

```java
package scheduling.scheduling.employee.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreatePreferenceRequest(
        @NotNull LocalDate preferenceDate,
        @NotBlank @Size(max = 20) String preferenceType,
        Long shiftTypeId,
        @NotNull @Min(1) Integer priority,
        @Size(max = 1000) String note) {
}
```

Create `backend/src/main/java/scheduling/scheduling/employee/dto/UpdatePreferenceRequest.java`:

```java
package scheduling.scheduling.employee.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdatePreferenceRequest(
        @NotNull LocalDate preferenceDate,
        @NotBlank @Size(max = 20) String preferenceType,
        Long shiftTypeId,
        @NotNull @Min(1) Integer priority,
        @Size(max = 1000) String note) {
}
```

Create `backend/src/main/java/scheduling/scheduling/employee/dto/EmployeePreferenceResponse.java`:

```java
package scheduling.scheduling.employee.dto;

import java.time.LocalDate;
import scheduling.scheduling.entity.Preference;

public record EmployeePreferenceResponse(
        Long id,
        LocalDate preferenceDate,
        String preferenceType,
        Long shiftTypeId,
        String shiftName,
        Integer priority,
        String note,
        String status) {

    public static EmployeePreferenceResponse from(Preference preference) {
        Long shiftTypeId = preference.getShiftType() == null ? null : preference.getShiftType().getId();
        String shiftName = preference.getShiftType() == null ? null : preference.getShiftType().getShiftName();
        return new EmployeePreferenceResponse(
                preference.getId(),
                preference.getPreferenceDate(),
                preference.getPreferenceType(),
                shiftTypeId,
                shiftName,
                preference.getPriority(),
                preference.getNote(),
                preference.getStatus());
    }
}
```

Create `backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java`:

```java
package scheduling.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.Preference;

public interface PreferenceRepository extends JpaRepository<Preference, Long> {

    @Query("""
            select p from Preference p
            left join fetch p.shiftType
            where p.employee.id = :employeeId
            and (:status is null or p.status = :status)
            and (:from is null or p.preferenceDate >= :from)
            and (:to is null or p.preferenceDate <= :to)
            order by p.preferenceDate asc, p.id desc
            """)
    List<Preference> searchForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select count(p) > 0 from Preference p
            where p.employee.id = :employeeId
            and p.preferenceDate = :preferenceDate
            and ((:shiftTypeId is null and p.shiftType is null) or p.shiftType.id = :shiftTypeId)
            and p.preferenceType = :preferenceType
            and p.status in ('PENDING', 'APPROVED')
            and (:excludedPreferenceId is null or p.id <> :excludedPreferenceId)
            """)
    boolean existsActiveDuplicate(
            @Param("employeeId") Long employeeId,
            @Param("preferenceDate") LocalDate preferenceDate,
            @Param("shiftTypeId") Long shiftTypeId,
            @Param("preferenceType") String preferenceType,
            @Param("excludedPreferenceId") Long excludedPreferenceId);

    Optional<Preference> findByIdAndEmployeeId(Long id, Long employeeId);
}
```

- [ ] **Step 4: Add preference service**

Create `backend/src/main/java/scheduling/scheduling/employee/EmployeePreferenceService.java`:

```java
package scheduling.scheduling.employee;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.employee.dto.CreatePreferenceRequest;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.employee.dto.UpdatePreferenceRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.Preference;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;

@Service
public class EmployeePreferenceService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";
    private static final String WANT = "WANT";
    private static final String AVOID = "AVOID";
    private static final String ACTIVE = "ACTIVE";

    private final EmployeeAccess employeeAccess;
    private final PreferenceRepository preferenceRepository;
    private final ShiftTypeRepository shiftTypeRepository;

    public EmployeePreferenceService(
            EmployeeAccess employeeAccess,
            PreferenceRepository preferenceRepository,
            ShiftTypeRepository shiftTypeRepository) {
        this.employeeAccess = employeeAccess;
        this.preferenceRepository = preferenceRepository;
        this.shiftTypeRepository = shiftTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<EmployeePreferenceResponse> listPreferences(
            CurrentAccountPrincipal principal, String status, LocalDate from, LocalDate to) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        String normalizedStatus = normalizeStatus(status);
        return preferenceRepository.searchForEmployee(employee.getId(), normalizedStatus, from, to).stream()
                .map(EmployeePreferenceResponse::from)
                .toList();
    }

    @Transactional
    public EmployeePreferenceResponse createPreference(CurrentAccountPrincipal principal, CreatePreferenceRequest request) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        String preferenceType = normalizePreferenceType(request.preferenceType());
        ShiftType shiftType = resolveShiftType(request.shiftTypeId());
        Long shiftTypeId = shiftType == null ? null : shiftType.getId();
        if (preferenceRepository.existsActiveDuplicate(employee.getId(), request.preferenceDate(), shiftTypeId, preferenceType, null)) {
            throw invalidRequest();
        }
        Preference preference = Preference.builder()
                .employee(employee)
                .shiftType(shiftType)
                .preferenceDate(request.preferenceDate())
                .preferenceType(preferenceType)
                .priority(request.priority())
                .note(StringUtils.hasText(request.note()) ? request.note().trim() : null)
                .status(PENDING)
                .build();
        return EmployeePreferenceResponse.from(preferenceRepository.save(preference));
    }

    @Transactional
    public EmployeePreferenceResponse updatePreference(CurrentAccountPrincipal principal, Long preferenceId, UpdatePreferenceRequest request) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        Preference preference = preferenceRepository.findByIdAndEmployeeId(preferenceId, employee.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preference not found"));
        if (!PENDING.equals(preference.getStatus())) {
            throw invalidRequest();
        }
        String preferenceType = normalizePreferenceType(request.preferenceType());
        ShiftType shiftType = resolveShiftType(request.shiftTypeId());
        Long shiftTypeId = shiftType == null ? null : shiftType.getId();
        if (preferenceRepository.existsActiveDuplicate(employee.getId(), request.preferenceDate(), shiftTypeId, preferenceType, preferenceId)) {
            throw invalidRequest();
        }
        preference.setPreferenceDate(request.preferenceDate());
        preference.setPreferenceType(preferenceType);
        preference.setShiftType(shiftType);
        preference.setPriority(request.priority());
        preference.setNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
        return EmployeePreferenceResponse.from(preferenceRepository.save(preference));
    }

    @Transactional
    public void cancelPreference(CurrentAccountPrincipal principal, Long preferenceId) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        Preference preference = preferenceRepository.findByIdAndEmployeeId(preferenceId, employee.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preference not found"));
        if (!PENDING.equals(preference.getStatus())) {
            throw invalidRequest();
        }
        preference.setStatus(CANCELLED);
        preferenceRepository.save(preference);
    }

    private ShiftType resolveShiftType(Long shiftTypeId) {
        if (shiftTypeId == null) {
            return null;
        }
        ShiftType shiftType = shiftTypeRepository.findById(shiftTypeId)
                .orElseThrow(EmployeePreferenceService::invalidRequest);
        if (!ACTIVE.equals(shiftType.getStatus())) {
            throw invalidRequest();
        }
        return shiftType;
    }

    private static String normalizePreferenceType(String preferenceType) {
        String normalized = preferenceType.trim();
        if (!WANT.equals(normalized) && !AVOID.equals(normalized)) {
            throw invalidRequest();
        }
        return normalized;
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
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid preference request");
    }
}
```

- [ ] **Step 5: Run preference service tests to verify GREEN**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=EmployeePreferenceServiceTest,EmployeeLeaveRequestServiceTest"
```

Expected: preference service tests and previously added leave service tests pass.

- [ ] **Step 6: Write failing controller tests for preference endpoints**

Create `backend/src/test/java/scheduling/scheduling/employee/EmployeePreferenceControllerTest.java` with these controller tests:

```java
package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import scheduling.scheduling.employee.dto.CreatePreferenceRequest;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.employee.dto.UpdatePreferenceRequest;
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

@WebMvcTest(EmployeePreferenceController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class EmployeePreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeePreferenceService employeePreferenceService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void employeeListsPreferences() throws Exception {
        givenToken("employee-token", employee());
        when(employeePreferenceService.listPreferences(any(CurrentAccountPrincipal.class), any(), any(), any()))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/employee/preferences")
                        .param("status", "PENDING")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].preferenceType").value("WANT"));
    }

    @Test
    void employeeCreatesPreference() throws Exception {
        givenToken("employee-token", employee());
        when(employeePreferenceService.createPreference(any(CurrentAccountPrincipal.class), any(CreatePreferenceRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/employee/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferenceDate": "2026-09-14",
                                  "preferenceType": "WANT",
                                  "shiftTypeId": 10,
                                  "priority": 1,
                                  "note": "Prefer morning shift"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20));

        ArgumentCaptor<CreatePreferenceRequest> requestCaptor = ArgumentCaptor.forClass(CreatePreferenceRequest.class);
        verify(employeePreferenceService).createPreference(any(CurrentAccountPrincipal.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().shiftTypeId()).isEqualTo(10L);
    }

    @Test
    void employeeUpdatesPreference() throws Exception {
        givenToken("employee-token", employee());
        when(employeePreferenceService.updatePreference(any(CurrentAccountPrincipal.class), any(Long.class), any(UpdatePreferenceRequest.class)))
                .thenReturn(response());

        mockMvc.perform(put("/api/employee/preferences/20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferenceDate": "2026-09-14",
                                  "preferenceType": "WANT",
                                  "shiftTypeId": 10,
                                  "priority": 1,
                                  "note": "Prefer morning shift"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenceType").value("WANT"));
    }

    @Test
    void createPreferenceRejectsInvalidBody() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferenceType": " ",
                                  "priority": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeePreferenceService);
    }

    @Test
    void employeeCancelsPreference() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(post("/api/employee/preferences/20/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isNoContent());

        verify(employeePreferenceService).cancelPreference(any(CurrentAccountPrincipal.class), org.mockito.ArgumentMatchers.eq(20L));
    }

    @Test
    void managerCannotAccessEmployeePreferenceApis() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(get("/api/employee/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(employeePreferenceService);
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

    private static EmployeePreferenceResponse response() {
        return new EmployeePreferenceResponse(
                20L,
                LocalDate.of(2026, 9, 14),
                "WANT",
                10L,
                "Morning",
                1,
                "Prefer morning shift",
                "PENDING");
    }
}
```

- [ ] **Step 7: Run controller test to verify RED**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=EmployeePreferenceControllerTest"
```

Expected: compilation fails because `EmployeePreferenceController` does not exist.

- [ ] **Step 8: Add preference controller**

Create `backend/src/main/java/scheduling/scheduling/employee/EmployeePreferenceController.java`:

```java
package scheduling.scheduling.employee;

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
import scheduling.scheduling.employee.dto.CreatePreferenceRequest;
import scheduling.scheduling.employee.dto.EmployeePreferenceResponse;
import scheduling.scheduling.employee.dto.UpdatePreferenceRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;

@RestController
@RequestMapping("/api/employee/preferences")
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeePreferenceController {

    private final EmployeePreferenceService employeePreferenceService;

    public EmployeePreferenceController(EmployeePreferenceService employeePreferenceService) {
        this.employeePreferenceService = employeePreferenceService;
    }

    @GetMapping
    ResponseEntity<List<EmployeePreferenceResponse>> listPreferences(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(employeePreferenceService.listPreferences(principal, status, from, to));
    }

    @PostMapping
    ResponseEntity<EmployeePreferenceResponse> createPreference(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @Valid @RequestBody CreatePreferenceRequest request) {
        return ResponseEntity.status(201).body(employeePreferenceService.createPreference(principal, request));
    }

    @PutMapping("/{preferenceId}")
    ResponseEntity<EmployeePreferenceResponse> updatePreference(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long preferenceId,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        return ResponseEntity.ok(employeePreferenceService.updatePreference(principal, preferenceId, request));
    }

    @PostMapping("/{preferenceId}/cancel")
    ResponseEntity<Void> cancelPreference(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable Long preferenceId) {
        employeePreferenceService.cancelPreference(principal, preferenceId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 9: Run preference targeted tests to verify GREEN**

Run from `backend`:

```powershell
.\mvnw.cmd test "-Dtest=EmployeePreferenceServiceTest,EmployeePreferenceControllerTest,EmployeeLeaveRequestServiceTest,EmployeeLeaveRequestControllerTest"
```

Expected: all employee leave/preference tests pass.

- [ ] **Step 10: Commit preference APIs**

Run from repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/employee/EmployeePreferenceController.java backend/src/main/java/scheduling/scheduling/employee/EmployeePreferenceService.java backend/src/main/java/scheduling/scheduling/employee/dto/CreatePreferenceRequest.java backend/src/main/java/scheduling/scheduling/employee/dto/UpdatePreferenceRequest.java backend/src/main/java/scheduling/scheduling/employee/dto/EmployeePreferenceResponse.java backend/src/main/java/scheduling/scheduling/repository/PreferenceRepository.java backend/src/test/java/scheduling/scheduling/employee/EmployeePreferenceServiceTest.java backend/src/test/java/scheduling/scheduling/employee/EmployeePreferenceControllerTest.java
git commit -m "feat: add employee preference apis"
```

## Task 3: Roadmap Update and Full Verification

**Files:**
- Modify: `docs/project_plan.md`

- [ ] **Step 1: Update roadmap checkboxes**

Modify `docs/project_plan.md` Phase 4 lines for this batch:

```markdown
- [x] API dang ky ngay nghi
- [x] API xem danh sach ngay nghi da gui
- [x] API huy yeu cau nghi neu con pending
- [x] API dang ky nguyen vong muon lam/khong muon lam
- [x] API xem danh sach nguyen vong
- [x] API cap nhat nguyen vong pending
```

Leave swap request and personal schedule lines unchecked.

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
git commit -m "docs: update employee core progress"
```

## Self-Review

- Spec coverage: Task 1 covers leave list/create/cancel, current employee resolution, overlap checks, status validation, controller auth, and tests. Task 2 covers preference list/create/update/cancel, duplicate checks, shift-type validation, pending-only mutations, controller auth, and tests. Task 3 covers roadmap and full regression.
- Placeholder scan: no incomplete markers remain; each code step includes concrete file content and commands.
- Type consistency: DTO, service, repository, and controller names are consistent across tasks.
