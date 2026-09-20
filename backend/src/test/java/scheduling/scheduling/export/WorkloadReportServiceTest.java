package scheduling.scheduling.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;

class WorkloadReportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T01:02:03Z"), ZoneOffset.UTC);

    private final SchedulePeriodRepository schedulePeriodRepository = mock(SchedulePeriodRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final ShiftAssignmentRepository shiftAssignmentRepository = mock(ShiftAssignmentRepository.class);
    private final WorkloadReportService service = new WorkloadReportService(
            schedulePeriodRepository, employeeRepository, shiftAssignmentRepository, CLOCK);

    @Test
    void buildWorkloadReportIncludesEveryActiveEmployeeAndAggregatesAssignments() {
        Employee firstEmployee = employee(1L, "NV001", "Nguyen Van A");
        Employee secondEmployee = employee(2L, "NV002", "Tran Thi B");
        when(schedulePeriodRepository.findById(20L)).thenReturn(Optional.of(period()));
        when(employeeRepository.findActiveForWorkloadReport()).thenReturn(List.of(firstEmployee, secondEmployee));
        when(shiftAssignmentRepository.listForPeriod(20L)).thenReturn(List.of(
                assignment(firstEmployee, workShift(LocalDate.of(2026, 9, 21), shiftType(true, false))),
                assignment(firstEmployee, workShift(LocalDate.of(2026, 9, 22), shiftType(false, true)))));

        WorkloadReportData data = service.buildWorkloadReport(20L);

        assertThat(data.periodId()).isEqualTo(20L);
        assertThat(data.periodName()).isEqualTo("Week 39");
        assertThat(data.startDate()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(data.endDate()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(data.versionNo()).isEqualTo(3);
        assertThat(data.status()).isEqualTo("PUBLISHED");
        assertThat(data.generatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 20, 1, 2, 3));
        assertThat(data.rows()).extracting(WorkloadReportData.Row::employeeCode).containsExactly("NV001", "NV002");
        assertThat(data.rows()).containsExactly(
                new WorkloadReportData.Row("NV001", "Nguyen Van A", 2, BigDecimal.valueOf(16.0), 1, 1, 2),
                new WorkloadReportData.Row("NV002", "Tran Thi B", 0, BigDecimal.ZERO, 0, 0, 0));
        verify(employeeRepository).findActiveForWorkloadReport();
        verify(shiftAssignmentRepository).listForPeriod(20L);
    }

    @Test
    void buildWorkloadReportThrowsNotFoundForMissingPeriod() {
        when(schedulePeriodRepository.findById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildWorkloadReport(20L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Schedule period not found");
                });
    }

    private static SchedulePeriod period() {
        SchedulePeriod period = new SchedulePeriod();
        period.setId(20L);
        period.setPeriodName("Week 39");
        period.setStartDate(LocalDate.of(2026, 9, 21));
        period.setEndDate(LocalDate.of(2026, 9, 27));
        period.setVersionNo(3);
        period.setStatus("PUBLISHED");
        return period;
    }

    private static Employee employee(Long id, String code, String name) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeCode(code);
        employee.setFullName(name);
        return employee;
    }

    private static ShiftAssignment assignment(Employee employee, WorkShift workShift) {
        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setEmployee(employee);
        assignment.setWorkShift(workShift);
        return assignment;
    }

    private static WorkShift workShift(LocalDate workDate, ShiftType shiftType) {
        WorkShift workShift = new WorkShift();
        workShift.setWorkDate(workDate);
        workShift.setShiftType(shiftType);
        return workShift;
    }

    private static ShiftType shiftType(boolean heavy, boolean overnight) {
        ShiftType shiftType = new ShiftType();
        shiftType.setDurationHours(BigDecimal.valueOf(8.0));
        shiftType.setHeavy(heavy);
        shiftType.setOvernight(overnight);
        return shiftType;
    }
}
