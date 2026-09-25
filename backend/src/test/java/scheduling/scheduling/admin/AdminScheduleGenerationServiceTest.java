package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.admin.dto.GenerateScheduleRequest;
import scheduling.scheduling.admin.dto.GenerateScheduleResponse;
import scheduling.scheduling.admin.dto.OptimizeScheduleResponse;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

class AdminScheduleGenerationServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final SchedulePeriodRepository schedulePeriodRepository = mock(SchedulePeriodRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final ShiftTypeRepository shiftTypeRepository = mock(ShiftTypeRepository.class);
    private final WorkShiftRepository workShiftRepository = mock(WorkShiftRepository.class);
    private final DayClassificationService dayClassificationService = mock(DayClassificationService.class);
    private final AdminOptimizationService optimizationService = mock(AdminOptimizationService.class);
    private final AdminScheduleGenerationService service = new AdminScheduleGenerationService(
            accountRepository,
            schedulePeriodRepository,
            employeeRepository,
            shiftTypeRepository,
            workShiftRepository,
            dayClassificationService,
            optimizationService);

    @Test
    void generateCreatesPeriodWorkShiftsAndRunsOptimization() {
        Account manager = Account.builder().username("manager@example.com").role(AccountRole.MANAGER).build();
        manager.setId(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNo(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), 1)).thenReturn(false);
        when(employeeRepository.findAllById(List.of(31L, 32L))).thenReturn(List.of(employee(31L), employee(32L)));
        ShiftType shiftType = shiftType(11L);
        when(shiftTypeRepository.findAllById(List.of(11L))).thenReturn(List.of(shiftType));
        when(dayClassificationService.classify(any())).thenReturn(DayClassificationService.DayType.WEEKDAY);
        when(schedulePeriodRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SchedulePeriod period = invocation.getArgument(0);
            period.setId(7L);
            return period;
        });
        when(workShiftRepository.save(any())).thenAnswer(invocation -> {
            WorkShift workShift = invocation.getArgument(0);
            workShift.setId(workShift.getWorkDate().equals(LocalDate.of(2026, 10, 5)) ? 21L : 22L);
            return workShift;
        });
        when(optimizationService.optimize(any(), any(), any())).thenReturn(new OptimizeScheduleResponse(
                99L, 7L, "SUCCESS", "OPTIMAL", BigDecimal.ZERO, 4, List.of(), null, null, null));

        GenerateScheduleResponse response = service.generate(managerPrincipal(), new GenerateScheduleRequest(
                "Lịch tuần 1",
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 6),
                1,
                List.of(31L, 32L),
                List.of(11L)));

        assertThat(response.periodId()).isEqualTo(7L);
        assertThat(response.aiStatus()).isEqualTo("SUCCESS");
        assertThat(response.solverStatus()).isEqualTo("OPTIMAL");
        assertThat(response.assignmentCount()).isEqualTo(4);
        verify(workShiftRepository).save(argThat(workShift -> workShift.getWorkDate().equals(LocalDate.of(2026, 10, 5)) && workShift.getRequiredStaff().equals(2)));
        verify(workShiftRepository).save(argThat(workShift -> workShift.getWorkDate().equals(LocalDate.of(2026, 10, 6)) && workShift.getRequiredStaff().equals(2)));
        verify(schedulePeriodRepository).saveAndFlush(argThat(period -> period.getEmployeePool().stream().map(Employee::getId).toList().equals(List.of(31L, 32L))));
        verify(optimizationService).optimize(eq(managerPrincipal()), eq(7L), argThat(request -> request.employeeIds().equals(List.of(31L, 32L))));
    }

    @Test
    void generateReturnsOptimizationErrorMessageWhenInfeasible() {
        Account manager = Account.builder().username("manager@example.com").role(AccountRole.MANAGER).build();
        manager.setId(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.existsByStartDateAndEndDateAndVersionNo(
                LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 7), 1)).thenReturn(false);
        when(employeeRepository.findAllById(List.of(31L))).thenReturn(List.of(employee(31L)));
        ShiftType shiftType = shiftType(11L);
        when(shiftTypeRepository.findAllById(List.of(11L))).thenReturn(List.of(shiftType));
        when(dayClassificationService.classify(any())).thenReturn(DayClassificationService.DayType.WEEKDAY);
        when(schedulePeriodRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SchedulePeriod period = invocation.getArgument(0);
            period.setId(8L);
            return period;
        });
        when(workShiftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(optimizationService.optimize(any(), any(), any())).thenReturn(new OptimizeScheduleResponse(
                100L,
                8L,
                "INFEASIBLE",
                "INFEASIBLE",
                null,
                0,
                List.of(),
                "NO_FEASIBLE_SCHEDULE: No schedule satisfies all hard constraints",
                null,
                null));

        GenerateScheduleResponse response = service.generate(managerPrincipal(), new GenerateScheduleRequest(
                "Lịch thiếu người",
                LocalDate.of(2026, 10, 7),
                LocalDate.of(2026, 10, 7),
                1,
                List.of(31L),
                List.of(11L)));

        assertThat(response.aiStatus()).isEqualTo("INFEASIBLE");
        assertThat(response.errorMessage()).isEqualTo("NO_FEASIBLE_SCHEDULE: No schedule satisfies all hard constraints");
    }

    private static CurrentAccountPrincipal managerPrincipal() {
        return new CurrentAccountPrincipal(1L, "manager@example.com", AccountRole.MANAGER);
    }

    private static Employee employee(Long id) {
        Employee employee = Employee.builder().employeeCode("EMP" + id).fullName("Employee " + id).status("ACTIVE").build();
        employee.setId(id);
        return employee;
    }

    private static ShiftType shiftType(Long id) {
        ShiftType shiftType = ShiftType.builder()
                .shiftName("Thong tam")
                .shiftCode("THONG_TAM")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(17, 0))
                .durationHours(BigDecimal.valueOf(8))
                .minStaff(2)
                .maxStaff(10)
                .status("ACTIVE")
                .applicableWeekdays("MON,TUE,WED,THU,FRI,SAT,SUN")
                .build();
        shiftType.setId(id);
        return shiftType;
    }
}
