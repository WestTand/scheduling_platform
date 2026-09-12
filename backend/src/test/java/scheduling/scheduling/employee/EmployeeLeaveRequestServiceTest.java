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
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CreateLeaveRequestRequest;
import scheduling.scheduling.employee.dto.EmployeeLeaveRequestResponse;
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
    void listLeaveRequestsRejectsInvalidStatus() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));

        assertThatThrownBy(() -> service.listLeaveRequests(principal, "WAITING", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createLeaveRequestDefaultsPendingAndTrimsBlankReasonToNull() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        CreateLeaveRequestRequest request = new CreateLeaveRequestRequest(
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), "   ");
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));
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
        assertThat(response.reason()).isNull();
        assertThat(response.status()).isEqualTo("PENDING");
        verify(accountRepository).findByIdForUpdate(2L);
    }

    @Test
    void createLeaveRequestRejectsInvalidDateRange() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));

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
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account(principal, employee)));
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

    @Test
    void employeeResolutionRejectsAccountWithNoLinkedEmployee() {
        CurrentAccountPrincipal principal = employeePrincipal();
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, null)));

        assertThatThrownBy(() -> service.listLeaveRequests(principal, null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void employeeResolutionRejectsInactiveEmployee() {
        CurrentAccountPrincipal principal = employeePrincipal();
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee(7L, "INACTIVE"))));

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
