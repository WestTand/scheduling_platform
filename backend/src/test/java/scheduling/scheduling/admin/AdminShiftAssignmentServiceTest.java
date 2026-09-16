package scheduling.scheduling.admin;

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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminPeriodScheduleResponse;
import scheduling.scheduling.admin.dto.AdminShiftAssignmentResponse;
import scheduling.scheduling.admin.dto.CreateShiftAssignmentRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
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

class AdminShiftAssignmentServiceTest {

    private final ShiftAssignmentRepository shiftAssignmentRepository = mock(ShiftAssignmentRepository.class);
    private final WorkShiftRepository workShiftRepository = mock(WorkShiftRepository.class);
    private final SchedulePeriodRepository schedulePeriodRepository = mock(SchedulePeriodRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final LeaveRequestRepository leaveRequestRepository = mock(LeaveRequestRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AdminShiftAssignmentService service = new AdminShiftAssignmentService(
            shiftAssignmentRepository, workShiftRepository, schedulePeriodRepository,
            employeeRepository, leaveRequestRepository, accountRepository);

    @Test
    void managerAssignsActiveEmployee() {
        CurrentAccountPrincipal principal = managerPrincipal();
        WorkShift shift = workShift();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(shift));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(200L, 7L)).thenReturn(false);
        when(shiftAssignmentRepository.countByWorkShiftId(200L)).thenReturn(1L);
        when(leaveRequestRepository.searchForEmployee(7L, "APPROVED",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of());
        when(shiftAssignmentRepository.findForEmployeeInWindow(7L,
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of());
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class)))
                .thenAnswer(invocation -> {
                    ShiftAssignment saved = invocation.getArgument(0);
                    saved.setId(500L);
                    return saved;
                });

        AdminShiftAssignmentResponse response = service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(7L));

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.workShiftId()).isEqualTo(200L);
        assertThat(response.periodId()).isEqualTo(20L);
        assertThat(response.employeeId()).isEqualTo(7L);
        assertThat(response.assignmentStatus()).isEqualTo("ASSIGNED");
        assertThat(response.assignedSource()).isEqualTo("MANUAL");
        assertThat(response.locked()).isFalse();
    }

    @Test
    void assignRejectsDuplicateWithConflict() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(workShift()));
        when(employeeRepository.findById(7L))
                .thenReturn(Optional.of(employee(7L, "NV007", "Nguyen Van A")));
        when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(200L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Employee already assigned to this shift");

        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void assignAllowsOverstaffingAboveRequiredStaff() {
        CurrentAccountPrincipal principal = managerPrincipal();
        WorkShift shift = workShift();
        Employee employee = employee(8L, "NV008", "Tran Thi B");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(shift));
        when(employeeRepository.findById(8L)).thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(200L, 8L)).thenReturn(false);
        when(shiftAssignmentRepository.countByWorkShiftId(200L)).thenReturn(2L);
        when(leaveRequestRepository.searchForEmployee(8L, "APPROVED",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of());
        when(shiftAssignmentRepository.findForEmployeeInWindow(8L,
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of());
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class)))
                .thenAnswer(invocation -> {
                    ShiftAssignment saved = invocation.getArgument(0);
                    saved.setId(501L);
                    return saved;
                });

        AdminShiftAssignmentResponse response = service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(8L));

        assertThat(response.id()).isEqualTo(501L);
    }

    @Test
    void assignRejectsFullShift() {
        CurrentAccountPrincipal principal = managerPrincipal();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(workShift()));
        when(employeeRepository.findById(8L))
                .thenReturn(Optional.of(employee(8L, "NV008", "Tran Thi B")));
        when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(200L, 8L)).thenReturn(false);
        when(shiftAssignmentRepository.countByWorkShiftId(200L)).thenReturn(3L);

        assertThatThrownBy(() -> service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(8L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(8L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Work shift is already at maximum staff");

        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void assignRejectsApprovedLeave() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(workShift()));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(200L, 7L)).thenReturn(false);
        when(shiftAssignmentRepository.countByWorkShiftId(200L)).thenReturn(0L);
        when(leaveRequestRepository.searchForEmployee(7L, "APPROVED",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of(approvedLeave(employee)));

        assertThatThrownBy(() -> service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void assignRejectsOverlappingShift() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        ShiftAssignment clash = assignment(502L, eveningShift(), employee, false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(workShift()));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(200L, 7L)).thenReturn(false);
        when(shiftAssignmentRepository.countByWorkShiftId(200L)).thenReturn(0L);
        when(leaveRequestRepository.searchForEmployee(7L, "APPROVED",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of());
        when(shiftAssignmentRepository.findForEmployeeInWindow(7L,
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of(clash));

        assertThatThrownBy(() -> service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Employee has an overlapping assignment");

        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void assignAllowsBackToBackTouchingShifts() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        ShiftAssignment touching = assignment(503L, lateShift(), employee, false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(workShift()));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(200L, 7L)).thenReturn(false);
        when(shiftAssignmentRepository.countByWorkShiftId(200L)).thenReturn(0L);
        when(leaveRequestRepository.searchForEmployee(7L, "APPROVED",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of());
        when(shiftAssignmentRepository.findForEmployeeInWindow(7L,
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of(touching));
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class)))
                .thenAnswer(invocation -> {
                    ShiftAssignment saved = invocation.getArgument(0);
                    saved.setId(504L);
                    return saved;
                });

        AdminShiftAssignmentResponse response = service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(7L));

        assertThat(response.id()).isEqualTo(504L);
    }

    @Test
    void assignRejectsOvernightOverlap() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        WorkShift night = nightShift();
        ShiftAssignment earlyMorning = assignment(505L, morningAfterNight(), employee, false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(workShiftRepository.findDetailById(300L)).thenReturn(Optional.of(night));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(300L, 7L)).thenReturn(false);
        when(shiftAssignmentRepository.countByWorkShiftId(300L)).thenReturn(0L);
        when(leaveRequestRepository.searchForEmployee(7L, "APPROVED",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of());
        when(shiftAssignmentRepository.findForEmployeeInWindow(7L,
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of(earlyMorning));

        assertThatThrownBy(() -> service.createAssignment(
                principal, 300L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void assignRejectsOverlapFromAnotherPeriod() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        ShiftAssignment foreign = assignment(506L, foreignPeriodShift(), employee, false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(workShift()));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(200L, 7L)).thenReturn(false);
        when(shiftAssignmentRepository.countByWorkShiftId(200L)).thenReturn(0L);
        when(leaveRequestRepository.searchForEmployee(7L, "APPROVED",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of());
        when(shiftAssignmentRepository.findForEmployeeInWindow(7L,
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of(foreign));

        assertThatThrownBy(() -> service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void assignRejectsNonManager() {
        CurrentAccountPrincipal principal = employeePrincipal();
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.createAssignment(
                principal, 200L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(workShiftRepository, never()).findDetailById(any());
    }

    @Test
    void assignRejectsMissingAccount() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAssignment(
                managerPrincipal(), 200L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(workShiftRepository, never()).findDetailById(any());
    }

    @Test
    void assignRejectsMissingWorkShift() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(managerPrincipal())));
        when(workShiftRepository.findDetailById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAssignment(
                managerPrincipal(), 999L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignRejectsMissingEmployee() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(managerPrincipal())));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(workShift()));
        when(employeeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAssignment(
                managerPrincipal(), 200L, new CreateShiftAssignmentRequest(999L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignRejectsInactiveEmployee() {
        Employee inactive = employee(7L, "NV007", "Nguyen Van A");
        inactive.setStatus("INACTIVE");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(managerPrincipal())));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(workShift()));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.createAssignment(
                managerPrincipal(), 200L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void assignRejectsNonDraftPeriod() {
        WorkShift shift = workShift();
        shift.getSchedulePeriod().setStatus("PUBLISHED");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(managerPrincipal())));
        when(workShiftRepository.findDetailById(200L)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.createAssignment(
                managerPrincipal(), 200L, new CreateShiftAssignmentRequest(7L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void assignRejectsMissingEmployeeId() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(managerPrincipal())));

        assertThatThrownBy(() -> service.createAssignment(
                managerPrincipal(), 200L, new CreateShiftAssignmentRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(workShiftRepository, never()).findDetailById(any());
    }

    @Test
    void deleteRemovesLockedAssignmentInDraft() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        ShiftAssignment locked = assignment(500L, workShift(), employee, true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(shiftAssignmentRepository.findDetailById(500L)).thenReturn(Optional.of(locked));

        service.deleteAssignment(principal, 500L);

        verify(shiftAssignmentRepository).delete(locked);
    }

    @Test
    void deleteRejectsNonDraftPeriod() {
        WorkShift shift = workShift();
        shift.getSchedulePeriod().setStatus("APPROVED");
        ShiftAssignment locked = assignment(500L, shift, employee(7L, "NV007", "Nguyen Van A"), true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(managerPrincipal())));
        when(shiftAssignmentRepository.findDetailById(500L)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.deleteAssignment(managerPrincipal(), 500L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(shiftAssignmentRepository, never()).delete(any());
    }

    @Test
    void deleteRejectsMissingAssignment() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(managerPrincipal())));
        when(shiftAssignmentRepository.findDetailById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAssignment(managerPrincipal(), 999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void lockAndUnlockFlipFlag() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        ShiftAssignment assignment = assignment(500L, workShift(), employee, false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(shiftAssignmentRepository.findDetailById(500L)).thenReturn(Optional.of(assignment));
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.lockAssignment(principal, 500L).locked()).isTrue();
        assertThat(service.unlockAssignment(principal, 500L).locked()).isFalse();
    }

    @Test
    void lockIsIdempotent() {
        CurrentAccountPrincipal principal = managerPrincipal();
        Employee employee = employee(7L, "NV007", "Nguyen Van A");
        ShiftAssignment assignment = assignment(500L, workShift(), employee, true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(shiftAssignmentRepository.findDetailById(500L)).thenReturn(Optional.of(assignment));
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.lockAssignment(principal, 500L).locked()).isTrue();
    }

    @Test
    void lockRejectsNonDraftPeriod() {
        WorkShift shift = workShift();
        shift.getSchedulePeriod().setStatus("PUBLISHED");
        ShiftAssignment assignment = assignment(500L, shift, employee(7L, "NV007", "Nguyen Van A"), false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(managerPrincipal())));
        when(shiftAssignmentRepository.findDetailById(500L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.lockAssignment(managerPrincipal(), 500L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void scheduleViewReturnsOrderedShiftsWithAssignments() {
        CurrentAccountPrincipal principal = managerPrincipal();
        WorkShift morning = workShift();
        WorkShift evening = eveningShift();
        Employee first = employee(7L, "NV007", "Nguyen Van A");
        Employee second = employee(8L, "NV008", "Tran Thi B");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal)));
        when(schedulePeriodRepository.findDetailById(20L)).thenReturn(Optional.of(draftPeriod()));
        when(workShiftRepository.searchForPeriod(20L, null, null)).thenReturn(List.of(morning, evening));
        when(shiftAssignmentRepository.listForPeriod(20L)).thenReturn(List.of(
                assignment(500L, morning, first, false),
                assignment(501L, evening, second, true)));

        AdminPeriodScheduleResponse response = service.getSchedule(principal, 20L);

        assertThat(response.periodId()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.workShifts()).hasSize(2);
        assertThat(response.workShifts().get(0).id()).isEqualTo(200L);
        assertThat(response.workShifts().get(0).assignments()).hasSize(1);
        assertThat(response.workShifts().get(0).assignments().get(0).employeeCode()).isEqualTo("NV007");
        assertThat(response.workShifts().get(1).id()).isEqualTo(201L);
        assertThat(response.workShifts().get(1).shiftStartTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(response.workShifts().get(1).maxStaff()).isEqualTo(3);
        assertThat(response.workShifts().get(1).assignments().get(0).locked()).isTrue();
    }

    @Test
    void scheduleViewRejectsMissingPeriod() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(managerPrincipal())));
        when(schedulePeriodRepository.findDetailById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSchedule(managerPrincipal(), 999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void scheduleViewRejectsNonManager() {
        CurrentAccountPrincipal principal = employeePrincipal();
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal)));

        assertThatThrownBy(() -> service.getSchedule(principal, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(schedulePeriodRepository, never()).findDetailById(any());
    }

    private static CurrentAccountPrincipal managerPrincipal() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static CurrentAccountPrincipal employeePrincipal() {
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

    private static ShiftType shiftType(LocalTime startTime, LocalTime endTime, int maxStaff) {
        ShiftType type = ShiftType.builder()
                .shiftName("Day")
                .startTime(startTime)
                .endTime(endTime)
                .durationHours(new BigDecimal("8.0"))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(maxStaff)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .build();
        type.setId(100L);
        return type;
    }

    private static WorkShift workShift() {
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(draftPeriod())
                .shiftType(shiftType(LocalTime.of(8, 0), LocalTime.of(16, 0), 3))
                .workDate(LocalDate.of(2026, 9, 14))
                .requiredStaff(2)
                .build();
        shift.setId(200L);
        return shift;
    }

    private static WorkShift eveningShift() {
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(draftPeriod())
                .shiftType(shiftType(LocalTime.of(14, 0), LocalTime.of(22, 0), 3))
                .workDate(LocalDate.of(2026, 9, 14))
                .requiredStaff(1)
                .build();
        shift.setId(201L);
        shift.getShiftType().setId(101L);
        return shift;
    }

    private static WorkShift lateShift() {
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(draftPeriod())
                .shiftType(shiftType(LocalTime.of(16, 0), LocalTime.of(0, 0), 3))
                .workDate(LocalDate.of(2026, 9, 14))
                .requiredStaff(1)
                .build();
        shift.setId(202L);
        shift.getShiftType().setId(102L);
        return shift;
    }

    private static WorkShift nightShift() {
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(draftPeriod())
                .shiftType(shiftType(LocalTime.of(22, 0), LocalTime.of(6, 0), 2))
                .workDate(LocalDate.of(2026, 9, 14))
                .requiredStaff(1)
                .build();
        shift.setId(300L);
        shift.getShiftType().setId(103L);
        return shift;
    }

    private static WorkShift morningAfterNight() {
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(draftPeriod())
                .shiftType(shiftType(LocalTime.of(0, 0), LocalTime.of(8, 0), 2))
                .workDate(LocalDate.of(2026, 9, 15))
                .requiredStaff(1)
                .build();
        shift.setId(301L);
        shift.getShiftType().setId(104L);
        return shift;
    }

    private static WorkShift foreignPeriodShift() {
        SchedulePeriod other = draftPeriod();
        other.setId(21L);
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(other)
                .shiftType(shiftType(LocalTime.of(10, 0), LocalTime.of(18, 0), 2))
                .workDate(LocalDate.of(2026, 9, 14))
                .requiredStaff(1)
                .build();
        shift.setId(400L);
        shift.getShiftType().setId(105L);
        return shift;
    }

    private static Employee employee(Long id, String code, String name) {
        Employee employee = Employee.builder()
                .employeeCode(code)
                .fullName(name)
                .status("ACTIVE")
                .build();
        employee.setId(id);
        return employee;
    }

    private static ShiftAssignment assignment(Long id, WorkShift shift, Employee employee, boolean locked) {
        ShiftAssignment assignment = ShiftAssignment.builder()
                .workShift(shift)
                .employee(employee)
                .assignmentStatus("ASSIGNED")
                .assignedSource("MANUAL")
                .locked(locked)
                .build();
        assignment.setId(id);
        return assignment;
    }

    private static LeaveRequest approvedLeave(Employee employee) {
        return LeaveRequest.builder()
                .employee(employee)
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 14))
                .status("APPROVED")
                .build();
    }
}
