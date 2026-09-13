package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.mockito.InOrder;
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

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-13T10:15:30Z"), ZoneOffset.UTC);
    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 9, 13, 10, 15, 30);

    private final LeaveRequestRepository leaveRequestRepository = mock(LeaveRequestRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AdminLeaveRequestService service = new AdminLeaveRequestService(
            leaveRequestRepository, accountRepository, FIXED_CLOCK);

    @Test
    void listLeaveRequestsNormalizesFiltersAndMapsEmployeeSummary() {
        CurrentAccountPrincipal principal = managerPrincipal();
        LeaveRequest leaveRequest = leaveRequest(10L, employee(7L), "PENDING");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(leaveRequestRepository.searchForAdmin(7L, "PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(leaveRequest));

        List<AdminLeaveRequestResponse> response = service.listLeaveRequests(
                principal, 7L, " pending ", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response).extracting(AdminLeaveRequestResponse::id).containsExactly(10L);
        assertThat(response.get(0).employeeId()).isEqualTo(7L);
        assertThat(response.get(0).employeeCode()).isEqualTo("E001");
        assertThat(response.get(0).employeeName()).isEqualTo("Nguyen Van A");
        verify(leaveRequestRepository).searchForAdmin(7L, "PENDING", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void listLeaveRequestsRejectsInvalidStatus() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.listLeaveRequests(principal, null, "WAITING", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listLeaveRequestsAcceptsAllReviewStatuses() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        for (String status : List.of("PENDING", "APPROVED", "REJECTED", "CANCELLED")) {
            service.listLeaveRequests(principal, null, status, null, null);

            verify(leaveRequestRepository).searchForAdmin(null, status, null, null);
        }
    }

    @Test
    void listLeaveRequestsRejectsInvertedDateFilter() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.listLeaveRequests(
                principal, null, null, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listLeaveRequestsRejectsNonManagerAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.listLeaveRequests(principal, null, null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(leaveRequestRepository, never()).searchForAdmin(any(), any(), any(), any());
    }

    @Test
    void approveLeaveRequestSetsReviewMetadata() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = account(principal);
        Account employeeAccount = account(new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE));
        LeaveRequest leaveRequest = leaveRequest(10L, employee(7L, employeeAccount), "PENDING");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(employeeAccount));
        when(leaveRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.existsApprovedOverlapExcludingId(
                7L, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), 10L))
                .thenReturn(false);
        when(leaveRequestRepository.save(leaveRequest)).thenReturn(leaveRequest);

        AdminLeaveRequestResponse response = service.approveLeaveRequest(
                principal, 10L, new ReviewLeaveRequestRequest(" Approved "));

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewNote()).isEqualTo("Approved");
        assertThat(response.reviewedByAccountId()).isEqualTo(1L);
        assertThat(response.reviewedByUsername()).isEqualTo("manager01");
        assertThat(response.reviewedAt()).isEqualTo(REVIEWED_AT);
        verify(leaveRequestRepository).save(leaveRequest);

        InOrder inOrder = inOrder(accountRepository, leaveRequestRepository);
        inOrder.verify(leaveRequestRepository).findByIdForUpdate(10L);
        inOrder.verify(accountRepository).findByIdForUpdate(2L);
        inOrder.verify(leaveRequestRepository).existsApprovedOverlapExcludingId(
                7L, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), 10L);
    }

    @Test
    void approveLeaveRequestFallsBackToEmployeeLeaveLockWhenEmployeeHasNoAccount() {
        CurrentAccountPrincipal principal = managerPrincipal();
        LeaveRequest leaveRequest = leaveRequest(10L, employee(7L), "PENDING");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(leaveRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.existsApprovedOverlapExcludingId(
                7L, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), 10L))
                .thenReturn(false);
        when(leaveRequestRepository.save(leaveRequest)).thenReturn(leaveRequest);

        service.approveLeaveRequest(principal, 10L, new ReviewLeaveRequestRequest(null));

        InOrder inOrder = inOrder(leaveRequestRepository);
        inOrder.verify(leaveRequestRepository).findByIdForUpdate(10L);
        inOrder.verify(leaveRequestRepository).lockEmployeeLeaveRequestsForUpdate(7L);
        inOrder.verify(leaveRequestRepository).existsApprovedOverlapExcludingId(
                7L, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), 10L);
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
        when(leaveRequestRepository.existsApprovedOverlapExcludingId(
                7L, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), 10L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.approveLeaveRequest(principal, 10L, new ReviewLeaveRequestRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(leaveRequestRepository, never()).save(any());
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
        assertThat(response.reviewedAt()).isEqualTo(REVIEWED_AT);
        verify(leaveRequestRepository).save(leaveRequest);
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
    void reviewRejectsMissingManagerAccount() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rejectLeaveRequest(principal, 10L, new ReviewLeaveRequestRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
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

    @Test
    void reviewRejectsMissingLeaveRequest() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(leaveRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rejectLeaveRequest(principal, 10L, new ReviewLeaveRequestRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
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

    private static Employee employee(Long id, Account account) {
        Employee employee = employee(id);
        employee.setAccount(account);
        account.setEmployee(employee);
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
