package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CreateSwapRequestRequest;
import scheduling.scheduling.employee.dto.RespondSwapRequestRequest;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.SwapRequest;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

class EmployeeSwapRequestServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final ShiftAssignmentRepository shiftAssignmentRepository = mock(ShiftAssignmentRepository.class);
    private final SwapRequestRepository swapRequestRepository = mock(SwapRequestRepository.class);
    private final EmployeeAccess employeeAccess = new EmployeeAccess(accountRepository);
    private final EmployeeSwapRequestService service = new EmployeeSwapRequestService(
            employeeAccess, swapRequestRepository, shiftAssignmentRepository, employeeRepository);

    private static final long ACCOUNT_ID = 2L;

    private static CurrentAccountPrincipal employeePrincipal() {
        return new CurrentAccountPrincipal(ACCOUNT_ID, "employee01", AccountRole.EMPLOYEE);
    }

    private static Employee employee(Long id, String status) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeCode("NV" + id);
        employee.setFullName("Nhan vien " + id);
        employee.setStatus(status);
        return employee;
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

    private static ShiftAssignment assignment(Long id, Employee employee, String periodStatus) {
        SchedulePeriod period = new SchedulePeriod();
        period.setStatus(periodStatus);
        WorkShift workShift = new WorkShift();
        workShift.setSchedulePeriod(period);
        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setId(id);
        assignment.setEmployee(employee);
        assignment.setWorkShift(workShift);
        return assignment;
    }

    @Test
    void createSwapRequestDefaultsPendingTargetAndTrimsReason() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        Employee target = employee(9L, "ACTIVE");
        ShiftAssignment requesterAssignment = assignment(100L, requester, "PUBLISHED");
        CreateSwapRequestRequest request = new CreateSwapRequestRequest(100L, 9L, null, "  Cover me  ");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(requesterAssignment));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(target));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> {
            SwapRequest saved = invocation.getArgument(0);
            saved.setId(50L);
            return saved;
        });

        SwapRequestResponse response = service.createSwapRequest(principal, request);

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.requesterAssignmentId()).isEqualTo(100L);
        assertThat(response.targetAssignmentId()).isNull();
        assertThat(response.requesterEmployee().employeeCode()).isEqualTo("NV7");
        assertThat(response.targetEmployee().employeeCode()).isEqualTo("NV9");
        assertThat(response.reason()).isEqualTo("Cover me");
        assertThat(response.status()).isEqualTo("PENDING_TARGET");
    }

    @Test
    void createSwapRequestRejectsAssignmentOwnedByAnotherEmployee() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        ShiftAssignment otherAssignment = assignment(100L, employee(8L, "ACTIVE"), "PUBLISHED");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(otherAssignment));

        assertThatThrownBy(() -> service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestRejectsMissingAssignment() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestRejectsAssignmentOutsidePublishedPeriod() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(assignment(100L, requester, "DRAFT")));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(employee(9L, "ACTIVE")));

        assertThatThrownBy(() -> service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestRejectsInactiveTargetEmployee() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(employee(9L, "INACTIVE")));

        assertThatThrownBy(() -> service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestRejectsTargetingSelf() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 7L, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestRejectsTargetAssignmentOwnedByAnotherEmployee() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        Employee target = employee(9L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(target));
        when(shiftAssignmentRepository.findDetailById(200L))
                .thenReturn(Optional.of(assignment(200L, employee(11L, "ACTIVE"), "PUBLISHED")));

        assertThatThrownBy(() -> service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, 200L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestRejectsNullRequest() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));

        assertThatThrownBy(() -> service.createSwapRequest(principal, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestRejectsMissingTargetEmployee() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
        when(employeeRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestRejectsMissingTargetAssignment() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(employee(9L, "ACTIVE")));
        when(shiftAssignmentRepository.findDetailById(200L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, 200L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestRejectsTargetAssignmentOutsidePublishedPeriod() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        Employee target = employee(9L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(target));
        when(shiftAssignmentRepository.findDetailById(200L))
                .thenReturn(Optional.of(assignment(200L, target, "APPROVED")));

        assertThatThrownBy(() -> service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, 200L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestRejectsTargetAssignmentEqualToRequesterAssignment() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(employee(9L, "ACTIVE")));

        assertThatThrownBy(() -> service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, 100L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestTrimsBlankReasonToNull() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        Employee target = employee(9L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(target));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> {
            SwapRequest saved = invocation.getArgument(0);
            saved.setId(51L);
            return saved;
        });

        SwapRequestResponse response = service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, null, "   "));

        assertThat(response.reason()).isNull();
        assertThat(response.status()).isEqualTo("PENDING_TARGET");
    }

    @Test
    void createSwapRequestAcceptsValidTargetAssignment() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        Employee target = employee(9L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(Optional.of(assignment(100L, requester, "PUBLISHED")));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(target));
        when(shiftAssignmentRepository.findDetailById(200L))
                .thenReturn(Optional.of(assignment(200L, target, "PUBLISHED")));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> {
            SwapRequest saved = invocation.getArgument(0);
            saved.setId(52L);
            return saved;
        });

        SwapRequestResponse response = service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, 200L, "Swap shifts"));

        assertThat(response.id()).isEqualTo(52L);
        assertThat(response.targetAssignmentId()).isEqualTo(200L);
        assertThat(response.status()).isEqualTo("PENDING_TARGET");
    }
}
