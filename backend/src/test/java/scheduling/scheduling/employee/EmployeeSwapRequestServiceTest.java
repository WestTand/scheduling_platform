package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.time.LocalDate;
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
import scheduling.scheduling.notification.NotificationService;
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
    private final NotificationService notificationService = mock(NotificationService.class);
    private final EmployeeSwapRequestService service = new EmployeeSwapRequestService(
            employeeAccess, swapRequestRepository, shiftAssignmentRepository, employeeRepository,
            notificationService);

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
        workShift.setWorkDate(LocalDate.of(2026, 9, 24));
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
        assertThat(response.requesterWorkDate()).isEqualTo(LocalDate.of(2026, 9, 24));
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
        Employee target = employee(9L, "ACTIVE");
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, requester)));
        when(shiftAssignmentRepository.findDetailById(100L))
                .thenReturn(
                        Optional.of(assignment(100L, requester, "PUBLISHED")),
                        Optional.of(assignment(100L, target, "PUBLISHED")));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.createSwapRequest(
                principal, new CreateSwapRequestRequest(100L, 9L, 100L, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo("Target assignment must differ from requester assignment");
                });

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

    @Test
    void listSwapRequestsReturnsRelatedRequestsNewestFirst() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(7L, "ACTIVE");
        SwapRequest mine = swapRequest(50L, assignment(100L, me, "PUBLISHED"), employee(9L, "ACTIVE"), "PENDING_TARGET");
        SwapRequest invited = swapRequest(51L, assignment(200L, employee(9L, "ACTIVE"), "PUBLISHED"), me, "PENDING_TARGET");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findRelatedForEmployee(7L, "PENDING_TARGET"))
                .thenReturn(List.of(mine, invited));

        List<SwapRequestResponse> response = service.listSwapRequests(principal, " PENDING_TARGET ");

        assertThat(response).extracting(SwapRequestResponse::id).containsExactly(50L, 51L);
        verify(swapRequestRepository).findRelatedForEmployee(7L, "PENDING_TARGET");
    }

    @Test
    void listSwapRequestsRejectsInvalidStatus() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(7L, "ACTIVE");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));

        assertThatThrownBy(() -> service.listSwapRequests(principal, "WAITING"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelSwapRequestMarksPendingTargetCancelled() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(7L, "ACTIVE");
        SwapRequest swapRequest = swapRequest(50L, assignment(100L, me, "PUBLISHED"), employee(9L, "ACTIVE"), "PENDING_TARGET");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SwapRequestResponse response = service.cancelSwapRequest(principal, 50L);

        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelSwapRequestRejectsNonRequester() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(7L, "ACTIVE");
        SwapRequest swapRequest = swapRequest(
                50L, assignment(100L, employee(8L, "ACTIVE"), "PUBLISHED"), employee(9L, "ACTIVE"), "PENDING_TARGET");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));

        assertThatThrownBy(() -> service.cancelSwapRequest(principal, 50L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void cancelSwapRequestRejectsNonPendingTarget() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(7L, "ACTIVE");
        SwapRequest swapRequest = swapRequest(50L, assignment(100L, me, "PUBLISHED"), employee(9L, "ACTIVE"), "PENDING_APPROVAL");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));

        assertThatThrownBy(() -> service.cancelSwapRequest(principal, 50L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void respondSwapRequestAcceptMovesToPendingApproval() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(9L, "ACTIVE");
        SwapRequest swapRequest = swapRequest(
                50L, assignment(100L, employee(7L, "ACTIVE"), "PUBLISHED"), me, "PENDING_TARGET");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SwapRequestResponse response = service.respondSwapRequest(
                principal, 50L, new RespondSwapRequestRequest(true));

        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void respondSwapRequestDeclineMarksRejected() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(9L, "ACTIVE");
        SwapRequest swapRequest = swapRequest(
                50L, assignment(100L, employee(7L, "ACTIVE"), "PUBLISHED"), me, "PENDING_TARGET");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SwapRequestResponse response = service.respondSwapRequest(
                principal, 50L, new RespondSwapRequestRequest(false));

        assertThat(response.status()).isEqualTo("REJECTED");
    }

    @Test
    void respondSwapRequestRejectsNonTarget() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(11L, "ACTIVE");
        SwapRequest swapRequest = swapRequest(
                50L, assignment(100L, employee(7L, "ACTIVE"), "PUBLISHED"), employee(9L, "ACTIVE"), "PENDING_TARGET");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));

        assertThatThrownBy(() -> service.respondSwapRequest(
                principal, 50L, new RespondSwapRequestRequest(true)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void respondSwapRequestRejectsNullBody() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(9L, "ACTIVE");
        SwapRequest swapRequest = swapRequest(
                50L, assignment(100L, employee(7L, "ACTIVE"), "PUBLISHED"), me, "PENDING_TARGET");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));

        assertThatThrownBy(() -> service.respondSwapRequest(principal, 50L, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void createSwapRequestNotifiesTargetAccount() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee requester = employee(7L, "ACTIVE");
        Employee target = employee(9L, "ACTIVE");
        Account targetAccount = Account.builder()
                .username("target01").passwordHash("hash").role(AccountRole.EMPLOYEE)
                .status(AccountStatus.ACTIVE).isActive(true).build();
        targetAccount.setId(3L);
        target.setAccount(targetAccount);
        ShiftAssignment requesterAssignment = assignment(100L, requester, "PUBLISHED");
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

        service.createSwapRequest(principal, new CreateSwapRequestRequest(100L, 9L, null, "Swap please"));

        verify(notificationService).notify(
                same(targetAccount),
                eq("SWAP_REQUESTED"),
                eq("Bạn có yêu cầu đổi ca mới"),
                contains("đổi ca"));
    }

    @Test
    void respondSwapRequestAcceptNotifiesRequesterAccount() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(9L, "ACTIVE");
        Employee requester = employee(7L, "ACTIVE");
        Account requesterAccount = Account.builder()
                .username("requester01").passwordHash("hash").role(AccountRole.EMPLOYEE)
                .status(AccountStatus.ACTIVE).isActive(true).build();
        requesterAccount.setId(4L);
        requester.setAccount(requesterAccount);
        SwapRequest swapRequest = swapRequest(
                50L, assignment(100L, requester, "PUBLISHED"), me, "PENDING_TARGET");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SwapRequestResponse response = service.respondSwapRequest(
                principal, 50L, new RespondSwapRequestRequest(true));

        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
        verify(notificationService).notify(
                same(requesterAccount),
                eq("SWAP_TARGET_ACCEPTED"),
                eq("Yêu cầu đổi ca đang chờ quản lý duyệt"),
                contains("chờ quản lý"));
    }

    @Test
    void respondSwapRequestRejectNotifiesRequesterAccount() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(9L, "ACTIVE");
        Employee requester = employee(7L, "ACTIVE");
        Account requesterAccount = Account.builder()
                .username("requester01").passwordHash("hash").role(AccountRole.EMPLOYEE)
                .status(AccountStatus.ACTIVE).isActive(true).build();
        requesterAccount.setId(4L);
        requester.setAccount(requesterAccount);
        SwapRequest swapRequest = swapRequest(
                50L, assignment(100L, requester, "PUBLISHED"), me, "PENDING_TARGET");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SwapRequestResponse response = service.respondSwapRequest(
                principal, 50L, new RespondSwapRequestRequest(false));

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(notificationService).notify(
                same(requesterAccount),
                eq("SWAP_TARGET_REJECTED"),
                eq("Yêu cầu đổi ca đã bị từ chối"),
                contains("từ chối"));
    }

    private static SwapRequest swapRequest(
            Long id, ShiftAssignment requesterAssignment, Employee target, String status) {
        SwapRequest swapRequest = SwapRequest.builder()
                .requesterAssignment(requesterAssignment)
                .targetEmployee(target)
                .status(status)
                .build();
        swapRequest.setId(id);
        return swapRequest;
    }

    @Test
    void listSwapRequestsWithBlankStatusReturnsAllRelated() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(7L, "ACTIVE");
        SwapRequest swapRequest = swapRequest(50L, assignment(100L, me, "PUBLISHED"), employee(9L, "ACTIVE"), "APPROVED");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findRelatedForEmployee(7L, null))
                .thenReturn(List.of(swapRequest));

        List<SwapRequestResponse> response = service.listSwapRequests(principal, "   ");

        assertThat(response).extracting(SwapRequestResponse::id).containsExactly(50L);
        verify(swapRequestRepository).findRelatedForEmployee(7L, null);
    }

    @Test
    void respondSwapRequestRejectsNonPendingTarget() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee me = employee(9L, "ACTIVE");
        SwapRequest swapRequest = swapRequest(
                50L, assignment(100L, employee(7L, "ACTIVE"), "PUBLISHED"), me, "PENDING_APPROVAL");
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(account(principal, me)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));

        assertThatThrownBy(() -> service.respondSwapRequest(
                principal, 50L, new RespondSwapRequestRequest(true)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }
}
