package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.SwapRequest;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.audit.ActivityLogService;
import scheduling.scheduling.notification.NotificationService;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

class AdminSwapRequestServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-19T08:00:00Z"), ZoneId.of("UTC"));

    private final SwapRequestRepository swapRequestRepository = mock(SwapRequestRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final ShiftAssignmentRepository shiftAssignmentRepository =
            mock(ShiftAssignmentRepository.class);
    private final AdminSwapRequestService service =
            new AdminSwapRequestService(swapRequestRepository, accountRepository, FIXED_CLOCK,
                    notificationService, activityLogService, shiftAssignmentRepository);

    private static CurrentAccountPrincipal managerPrincipal() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static Account managerAccount(CurrentAccountPrincipal principal) {
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

    private static Employee employee(Long id, String status) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeCode("NV" + id);
        employee.setFullName("Nhan vien " + id);
        employee.setStatus(status);
        return employee;
    }

    private static ShiftAssignment assignment(Long id, Employee employee, String periodStatus) {
        return assignmentOnShift(id, id, employee, periodStatus, "ASSIGNED");
    }

    private static ShiftAssignment assignmentOnShift(
            Long id, Long workShiftId, Employee employee, String periodStatus, String assignmentStatus) {
        SchedulePeriod period = new SchedulePeriod();
        period.setStatus(periodStatus);
        WorkShift workShift = new WorkShift();
        workShift.setId(workShiftId);
        workShift.setSchedulePeriod(period);
        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setId(id);
        assignment.setEmployee(employee);
        assignment.setWorkShift(workShift);
        assignment.setAssignmentStatus(assignmentStatus);
        return assignment;
    }

    private static ShiftAssignment publishedAssignment(Long id, Employee employee) {
        return assignment(id, employee, "PUBLISHED");
    }

    private static ShiftAssignment draftAssignment(Long id, Employee employee) {
        return assignment(id, employee, "DRAFT");
    }

    private static SwapRequest pendingApprovalSwapRequest(
            Long id, ShiftAssignment requesterAssignment, Employee target,
            ShiftAssignment targetAssignment) {
        SwapRequest swapRequest = SwapRequest.builder()
                .requesterAssignment(requesterAssignment)
                .targetEmployee(target)
                .targetAssignment(targetAssignment)
                .status("PENDING_APPROVAL")
                .build();
        swapRequest.setId(id);
        return swapRequest;
    }

    private static SwapRequest swapRequest(Long id, Employee requester, Employee target, String status) {
        SwapRequest swapRequest = SwapRequest.builder()
                .requesterAssignment(assignment(100L, requester, "PUBLISHED"))
                .targetEmployee(target)
                .targetAssignment(assignment(200L, target, "PUBLISHED"))
                .status(status)
                .build();
        swapRequest.setId(id);
        return swapRequest;
    }

    private void stubLockedAssignments(SwapRequest swapRequest) {
        when(shiftAssignmentRepository.findAllByIdInForUpdate(anyList()))
                .thenReturn(List.of(
                        swapRequest.getRequesterAssignment(),
                        swapRequest.getTargetAssignment()));
    }

    @Test
    void managerListsAllSwapRequests() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SwapRequest swapRequest = swapRequest(50L, employee(7L, "ACTIVE"), employee(9L, "ACTIVE"), "PENDING_APPROVAL");
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findAllDetail("PENDING_APPROVAL"))
                .thenReturn(List.of(swapRequest));

        List<SwapRequestResponse> response = service.listSwapRequests(principal, "PENDING_APPROVAL");

        assertThat(response).extracting(SwapRequestResponse::id).containsExactly(50L);
        verify(swapRequestRepository).findAllDetail("PENDING_APPROVAL");
    }

    @Test
    void managerListRejectsInvalidStatus() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));

        assertThatThrownBy(() -> service.listSwapRequests(principal, "WAITING"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonManagerCannotListSwapRequests() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
        Employee me = employee(7L, "ACTIVE");
        Account employeeAccount = Account.builder()
                .username("employee01").passwordHash("hash").role(AccountRole.EMPLOYEE)
                .status(AccountStatus.ACTIVE).isActive(true).employee(me).build();
        employeeAccount.setId(2L);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(employeeAccount));

        assertThatThrownBy(() -> service.listSwapRequests(principal, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void approvePendingApprovalMarksApprovedWithReviewMetadata() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SwapRequest swapRequest = swapRequest(50L, employee(7L, "ACTIVE"), employee(9L, "ACTIVE"), "PENDING_APPROVAL");
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLockedAssignments(swapRequest);

        SwapRequestResponse response = service.approveSwapRequest(principal, 50L);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewedByAccountId()).isEqualTo(1L);
        assertThat(response.reviewedByUsername()).isEqualTo("manager01");
        assertThat(response.reviewedAt()).isEqualTo(LocalDateTime.of(2026, 9, 19, 8, 0));
    }

    @Test
    void approveSwapRequestKeepsOriginalRequesterIdentityInResponse() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee requester = employee(10L, "ACTIVE");
        Employee target = employee(20L, "ACTIVE");
        SwapRequest swapRequest = swapRequest(50L, requester, target, "PENDING_APPROVAL");
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLockedAssignments(swapRequest);

        SwapRequestResponse response = service.approveSwapRequest(principal, 50L);

        assertThat(response.requesterEmployee().id()).isEqualTo(10L);
        assertThat(response.requesterEmployee().employeeCode()).isEqualTo("NV10");
        assertThat(response.targetEmployee().id()).isEqualTo(20L);
    }

    @Test
    void approveSwapRequestRejectsNonPendingApproval() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SwapRequest swapRequest = swapRequest(50L, employee(7L, "ACTIVE"), employee(9L, "ACTIVE"), "PENDING_TARGET");
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));

        assertThatThrownBy(() -> service.approveSwapRequest(principal, 50L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void rejectPendingTargetMarksRejectedWithReviewMetadata() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SwapRequest swapRequest = swapRequest(50L, employee(7L, "ACTIVE"), employee(9L, "ACTIVE"), "PENDING_TARGET");
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SwapRequestResponse response = service.rejectSwapRequest(principal, 50L);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.reviewedByAccountId()).isEqualTo(1L);
        assertThat(response.reviewedAt()).isEqualTo(LocalDateTime.of(2026, 9, 19, 8, 0));
    }

    @Test
    void rejectSwapRequestRejectsTerminalStatus() {
        CurrentAccountPrincipal principal = managerPrincipal();
        SwapRequest swapRequest = swapRequest(50L, employee(7L, "ACTIVE"), employee(9L, "ACTIVE"), "APPROVED");
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));

        assertThatThrownBy(() -> service.rejectSwapRequest(principal, 50L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void approveSwapRequestNotifiesBothPartiesAndRecordsLog() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = managerAccount(principal);
        Account requesterAccount = Account.builder()
                .username("requester01").passwordHash("hash").role(AccountRole.EMPLOYEE)
                .status(AccountStatus.ACTIVE).isActive(true).build();
        requesterAccount.setId(2L);
        Account targetAccount = Account.builder()
                .username("target01").passwordHash("hash").role(AccountRole.EMPLOYEE)
                .status(AccountStatus.ACTIVE).isActive(true).build();
        targetAccount.setId(3L);
        Employee requester = employee(7L, "ACTIVE");
        requester.setAccount(requesterAccount);
        Employee target = employee(9L, "ACTIVE");
        target.setAccount(targetAccount);
        SwapRequest swapRequest = swapRequest(50L, requester, target, "PENDING_APPROVAL");
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(manager));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLockedAssignments(swapRequest);

        SwapRequestResponse response = service.approveSwapRequest(principal, 50L);

        assertThat(response.status()).isEqualTo("APPROVED");
        verify(notificationService).notifyAll(
                argThat(recipients -> recipients != null
                        && recipients.stream().map(Account::getId).toList().containsAll(List.of(2L, 3L))),
                eq("SWAP_APPROVED"),
                eq("Yêu cầu đổi ca đã được duyệt"),
                contains("đổi ca"));
        verify(activityLogService).record(
                same(manager),
                eq("APPROVE"),
                eq("swap_requests"),
                eq(50L),
                eq(null),
                any());
    }

    @Test
    void rejectSwapRequestNotifiesBothPartiesAndRecordsLog() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = managerAccount(principal);
        Account requesterAccount = Account.builder()
                .username("requester01").passwordHash("hash").role(AccountRole.EMPLOYEE)
                .status(AccountStatus.ACTIVE).isActive(true).build();
        requesterAccount.setId(2L);
        Account targetAccount = Account.builder()
                .username("target01").passwordHash("hash").role(AccountRole.EMPLOYEE)
                .status(AccountStatus.ACTIVE).isActive(true).build();
        targetAccount.setId(3L);
        Employee requester = employee(7L, "ACTIVE");
        requester.setAccount(requesterAccount);
        Employee target = employee(9L, "ACTIVE");
        target.setAccount(targetAccount);
        SwapRequest swapRequest = swapRequest(50L, requester, target, "PENDING_APPROVAL");
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(manager));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SwapRequestResponse response = service.rejectSwapRequest(principal, 50L);

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(notificationService).notifyAll(
                argThat(recipients -> recipients != null
                        && recipients.stream().map(Account::getId).toList().containsAll(List.of(2L, 3L))),
                eq("SWAP_REJECTED"),
                eq("Yêu cầu đổi ca đã bị từ chối"),
                contains("từ chối"));
        verify(activityLogService).record(
                same(manager),
                eq("REJECT"),
                eq("swap_requests"),
                eq(50L),
                eq(null),
                any());
    }

    @Test
    void reviewSwapRequestReturnsNotFoundForMissingRequest() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveSwapRequest(principal, 404L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(swapRequestRepository, never()).save(any());
    }

    @Test
    void approveSwapRequestExchangesTheTwoAssignments() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee requester = employee(10L, "ACTIVE");
        Employee target = employee(20L, "ACTIVE");
        ShiftAssignment requesterAssignment = publishedAssignment(100L, requester);
        ShiftAssignment targetAssignment = publishedAssignment(200L, target);
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, target, targetAssignment);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubLockedAssignments(swapRequest);

        service.approveSwapRequest(principal, 5L);

        assertThat(requesterAssignment.getEmployee()).isSameAs(target);
        assertThat(targetAssignment.getEmployee()).isSameAs(requester);
        assertThat(requesterAssignment.getAssignmentStatus()).isEqualTo("SWAPPED");
        assertThat(targetAssignment.getAssignmentStatus()).isEqualTo("SWAPPED");
        verify(shiftAssignmentRepository).saveAllAndFlush(List.of(requesterAssignment, targetAssignment));
    }

    @Test
    void approveSwapRequestLocksBothAssignmentsOrderedById() {
        CurrentAccountPrincipal principal = managerPrincipal();
        ShiftAssignment requesterAssignment = publishedAssignment(100L, employee(10L, "ACTIVE"));
        ShiftAssignment targetAssignment = publishedAssignment(200L, employee(20L, "ACTIVE"));
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, employee(20L, "ACTIVE"), targetAssignment);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubLockedAssignments(swapRequest);

        service.approveSwapRequest(principal, 5L);

        verify(shiftAssignmentRepository).findAllByIdInForUpdate(List.of(100L, 200L));
    }

    @Test
    void approveSwapRequestRejectsWhenAssignmentRowNoLongerExists() {
        CurrentAccountPrincipal principal = managerPrincipal();
        ShiftAssignment requesterAssignment = publishedAssignment(100L, employee(10L, "ACTIVE"));
        ShiftAssignment targetAssignment = publishedAssignment(200L, employee(20L, "ACTIVE"));
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, employee(20L, "ACTIVE"), targetAssignment);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
        when(shiftAssignmentRepository.findAllByIdInForUpdate(anyList()))
                .thenReturn(List.of(requesterAssignment));

        assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("không còn tồn tại")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(shiftAssignmentRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void approveSwapRequestRejectsCollisionRaisedByTheUniqueConstraint() {
        CurrentAccountPrincipal principal = managerPrincipal();
        ShiftAssignment requesterAssignment = publishedAssignment(100L, employee(10L, "ACTIVE"));
        ShiftAssignment targetAssignment = publishedAssignment(200L, employee(20L, "ACTIVE"));
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, employee(20L, "ACTIVE"), targetAssignment);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
        stubLockedAssignments(swapRequest);
        when(shiftAssignmentRepository.saveAllAndFlush(anyList()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đã cùng có ca ở hai ca cần hoán đổi")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(activityLogService, never()).record(
                any(Account.class), eq("APPROVE"), eq("swap_requests"), any(), any(), any());
        verify(notificationService, never()).notifyAll(anyList(), any(), any(), any());
    }

    @Test
    void approveSwapRequestRejectsMissingTargetAssignment() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee requester = employee(10L, "ACTIVE");
        Employee target = employee(20L, "ACTIVE");
        ShiftAssignment requesterAssignment = publishedAssignment(100L, requester);
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, target, null);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));

        assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Yêu cầu đổi ca chưa có ca đối ứng")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(shiftAssignmentRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void approveSwapRequestRejectsAssignmentsNotInPublishedPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee requester = employee(10L, "ACTIVE");
        Employee target = employee(20L, "ACTIVE");
        ShiftAssignment requesterAssignment = publishedAssignment(100L, requester);
        ShiftAssignment draftTargetAssignment = draftAssignment(200L, target);
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, target, draftTargetAssignment);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
        stubLockedAssignments(swapRequest);

        assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("kỳ lịch đã công bố")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(shiftAssignmentRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void approveSwapRequestRejectsTargetAssignmentOwnedByOtherEmployee() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee requester = employee(10L, "ACTIVE");
        Employee target = employee(20L, "ACTIVE");
        ShiftAssignment requesterAssignment = publishedAssignment(100L, requester);
        ShiftAssignment targetAssignment = publishedAssignment(200L, employee(30L, "ACTIVE"));
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, target, targetAssignment);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
        stubLockedAssignments(swapRequest);

        assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ca đối ứng không thuộc nhân viên được yêu cầu đổi ca")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(shiftAssignmentRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void approveSwapRequestRejectsWhenRequesterAlreadyHoldsTargetShift() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee requester = employee(10L, "ACTIVE");
        Employee target = employee(20L, "ACTIVE");
        ShiftAssignment requesterAssignment = publishedAssignment(100L, requester);
        ShiftAssignment targetAssignment = publishedAssignment(200L, target);
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, target, targetAssignment);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
        stubLockedAssignments(swapRequest);
        when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(100L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đã cùng có ca ở hai ca cần hoán đổi")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(shiftAssignmentRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void approveSwapRequestLeavesAssignmentsUntouchedWhenTargetAlreadyHoldsRequesterShift() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee requester = employee(10L, "ACTIVE");
        Employee target = employee(20L, "ACTIVE");
        ShiftAssignment requesterAssignment = publishedAssignment(100L, requester);
        ShiftAssignment targetAssignment = publishedAssignment(200L, target);
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, target, targetAssignment);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
        stubLockedAssignments(swapRequest);
        when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(200L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đã cùng có ca ở hai ca cần hoán đổi")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(shiftAssignmentRepository, never()).saveAllAndFlush(anyList());
        assertThat(requesterAssignment.getEmployee()).isSameAs(requester);
        assertThat(targetAssignment.getEmployee()).isSameAs(target);
        assertThat(requesterAssignment.getAssignmentStatus()).isEqualTo("ASSIGNED");
        assertThat(targetAssignment.getAssignmentStatus()).isEqualTo("ASSIGNED");
    }

    @Test
    void approveSwapRequestRejectsWhenBothAssignmentsAreOnTheSameWorkShift() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee requester = employee(10L, "ACTIVE");
        Employee target = employee(20L, "ACTIVE");
        ShiftAssignment requesterAssignment =
                assignmentOnShift(100L, 300L, requester, "PUBLISHED", "ASSIGNED");
        ShiftAssignment targetAssignment =
                assignmentOnShift(200L, 300L, target, "PUBLISHED", "ASSIGNED");
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, target, targetAssignment);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
        stubLockedAssignments(swapRequest);

        assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Hai ca cần hoán đổi là cùng một ca")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(shiftAssignmentRepository, never()).saveAllAndFlush(anyList());
        assertThat(requesterAssignment.getEmployee()).isSameAs(requester);
        assertThat(targetAssignment.getEmployee()).isSameAs(target);
    }

    @Test
    void approveSwapRequestRejectsWhenRequesterAssignmentWasAlreadySwapped() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee requester = employee(10L, "ACTIVE");
        Employee target = employee(20L, "ACTIVE");
        ShiftAssignment requesterAssignment =
                assignmentOnShift(100L, 100L, requester, "PUBLISHED", "SWAPPED");
        ShiftAssignment targetAssignment = publishedAssignment(200L, target);
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, target, targetAssignment);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
        stubLockedAssignments(swapRequest);

        assertThatThrownBy(() -> service.approveSwapRequest(principal, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đã được đổi bởi một yêu cầu khác")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(shiftAssignmentRepository, never()).saveAllAndFlush(anyList());
        assertThat(requesterAssignment.getEmployee()).isSameAs(requester);
        assertThat(targetAssignment.getEmployee()).isSameAs(target);
    }

    @Test
    void approveSwapRequestLogsBothRewrittenAssignmentRows() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Account manager = managerAccount(principal);
        Employee requester = employee(10L, "ACTIVE");
        Employee target = employee(20L, "ACTIVE");
        ShiftAssignment requesterAssignment = publishedAssignment(100L, requester);
        ShiftAssignment targetAssignment = publishedAssignment(200L, target);
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                5L, requesterAssignment, target, targetAssignment);
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(manager));
        when(swapRequestRepository.findDetailByIdForUpdate(5L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubLockedAssignments(swapRequest);

        service.approveSwapRequest(principal, 5L);

        verify(activityLogService).record(same(manager), eq("SWAP"), eq("shift_assignments"),
                eq(100L), eq(10L), eq(20L));
        verify(activityLogService).record(same(manager), eq("SWAP"), eq("shift_assignments"),
                eq(200L), eq(20L), eq(10L));
    }

    @Test
    void rejectSwapRequestLogsNullTargetAssignmentId() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee requester = employee(7L, "ACTIVE");
        requester.setAccount(accountOf(2L, "requester01"));
        Employee target = employee(9L, "ACTIVE");
        target.setAccount(accountOf(3L, "target01"));
        SwapRequest swapRequest = pendingApprovalSwapRequest(
                50L, publishedAssignment(100L, requester), target, null);
        swapRequest.setStatus("PENDING_TARGET");
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(managerAccount(principal)));
        when(swapRequestRepository.findDetailByIdForUpdate(50L)).thenReturn(Optional.of(swapRequest));
        when(swapRequestRepository.save(any(SwapRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SwapRequestResponse response = service.rejectSwapRequest(principal, 50L);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.targetAssignmentId()).isNull();
        assertThat(response.requesterEmployee().id()).isEqualTo(7L);
    }

    private static Account accountOf(Long id, String username) {
        Account account = Account.builder()
                .username(username)
                .passwordHash("hash")
                .role(AccountRole.EMPLOYEE)
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(id);
        return account;
    }
}
