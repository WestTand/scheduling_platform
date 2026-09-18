package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;

class EmployeeScheduleServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ShiftAssignmentRepository shiftAssignmentRepository = mock(ShiftAssignmentRepository.class);
    private final EmployeeAccess employeeAccess = new EmployeeAccess(accountRepository);
    private final EmployeeScheduleService service =
            new EmployeeScheduleService(employeeAccess, shiftAssignmentRepository, FIXED_CLOCK);

    @Test
    void returnsOnlyPublishedAssignments() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        ShiftAssignment published = assignment(300L, shift(200L, period(20L, "PUBLISHED")), employee);
        ShiftAssignment draft = assignment(301L, shift(201L, period(21L, "DRAFT")), employee);
        ShiftAssignment approved = assignment(302L, shift(202L, period(22L, "APPROVED")), employee);
        ShiftAssignment cancelled = assignment(303L, shift(203L, period(23L, "CANCELLED")), employee);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(shiftAssignmentRepository.findForEmployeeInWindow(
                        7L, LocalDate.of(2026, 9, 17), LocalDate.of(2026, 10, 17)))
                .thenReturn(List.of(published, draft, approved, cancelled));

        List<EmployeeScheduleItemResponse> response = service.getOwnSchedule(principal, null, null);

        assertThat(response).extracting(EmployeeScheduleItemResponse::assignmentId).containsExactly(300L);
        assertThat(response.get(0).periodStatus()).isEqualTo("PUBLISHED");
        assertThat(response.get(0).workDate()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(response.get(0).shiftTypeName()).isEqualTo("Day");
        assertThat(response.get(0).startsAt()).isEqualTo(OffsetDateTime.parse("2026-09-17T08:00:00+07:00"));
        assertThat(response.get(0).endsAt()).isEqualTo(OffsetDateTime.parse("2026-09-17T16:00:00+07:00"));
        assertThat(response.get(0).workMinutes()).isEqualTo(240);
        assertThat(response.get(0).isTransitionShift()).isFalse();
    }

    @Test
    void passesExplicitRangeThrough() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));
        when(shiftAssignmentRepository.findForEmployeeInWindow(
                        7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of());

        List<EmployeeScheduleItemResponse> response =
                service.getOwnSchedule(principal, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response).isEmpty();
        verify(shiftAssignmentRepository).findForEmployeeInWindow(
                7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void rejectsFromAfterTo() {
        CurrentAccountPrincipal principal = employeePrincipal();
        Employee employee = employee(7L, "ACTIVE");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(principal, employee)));

        assertThatThrownBy(() -> service.getOwnSchedule(
                        principal, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(shiftAssignmentRepository, never()).findForEmployeeInWindow(any(), any(), any());
    }

    @Test
    void rejectsNonEmployeeAccount() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(principal, null)));

        assertThatThrownBy(() -> service.getOwnSchedule(principal, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(shiftAssignmentRepository, never()).findForEmployeeInWindow(any(), any(), any());
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

    private static SchedulePeriod period(Long id, String status) {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Week 38")
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 20))
                .versionNo(1)
                .status(status)
                .build();
        period.setId(id);
        return period;
    }

    private static ShiftType shiftType() {
        ShiftType shiftType = ShiftType.builder()
                .shiftName("Day")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(BigDecimal.valueOf(4.0))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(3)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .build();
        shiftType.setId(100L);
        return shiftType;
    }

    private static WorkShift shift(Long id, SchedulePeriod period) {
        WorkShift shift = WorkShift.builder()
                .schedulePeriod(period)
                .shiftType(shiftType())
                .workDate(LocalDate.of(2026, 9, 17))
                .requiredStaff(2)
                .build();
        shift.setId(id);
        return shift;
    }

    private static ShiftAssignment assignment(Long id, WorkShift shift, Employee employee) {
        ShiftAssignment assignment = ShiftAssignment.builder()
                .workShift(shift)
                .employee(employee)
                .assignmentStatus("ASSIGNED")
                .assignedSource("MANUAL")
                .locked(false)
                .build();
        assignment.setId(id);
        return assignment;
    }
}
