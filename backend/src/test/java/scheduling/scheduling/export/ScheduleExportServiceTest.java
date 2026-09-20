package scheduling.scheduling.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
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
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

class ScheduleExportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T08:30:00Z"), ZoneOffset.UTC);

    private final SchedulePeriodRepository schedulePeriodRepository = mock(SchedulePeriodRepository.class);
    private final WorkShiftRepository workShiftRepository = mock(WorkShiftRepository.class);
    private final ScheduleExportService service = new ScheduleExportService(
            schedulePeriodRepository, workShiftRepository, CLOCK);

    @Test
    void buildScheduleExportReturnsAssignedAndUnassignedRows() {
        SchedulePeriod period = period();
        WorkShift assignedShift = workShift(100L, LocalDate.of(2026, 9, 21), shiftType(10L, "DAY", "Day Shift"));
        ShiftAssignment secondAssignment = assignment(2L, employee(2L, "E002", "Bao Tran"), "CONFIRMED", "AUTO", false);
        ShiftAssignment firstAssignment = assignment(1L, employee(1L, "E001", "An Nguyen"), "ASSIGNED", "MANUAL", true);
        assignedShift.setShiftAssignments(new LinkedHashSet<>(List.of(secondAssignment, firstAssignment)));
        WorkShift unassignedShift = workShift(101L, LocalDate.of(2026, 9, 22), shiftType(11L, "NIGHT", "Night Shift"));
        unassignedShift.setShiftAssignments(new LinkedHashSet<>());
        when(schedulePeriodRepository.findById(50L)).thenReturn(Optional.of(period));
        when(workShiftRepository.findExportDetailForPeriod(50L)).thenReturn(List.of(assignedShift, unassignedShift));

        ScheduleExportData data = service.buildScheduleExport(50L);

        assertThat(data.periodId()).isEqualTo(50L);
        assertThat(data.periodName()).isEqualTo("Week 39");
        assertThat(data.startDate()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(data.endDate()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(data.versionNo()).isEqualTo(3);
        assertThat(data.status()).isEqualTo("PUBLISHED");
        assertThat(data.generatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 20, 8, 30));
        assertThat(data.rows()).containsExactly(
                new ScheduleExportData.Row(
                        LocalDate.of(2026, 9, 21),
                        "MONDAY",
                        100L,
                        "DAY",
                        "Day Shift",
                        LocalTime.of(8, 0),
                        LocalTime.of(16, 0),
                        "E001",
                        "An Nguyen",
                        "ASSIGNED",
                        "MANUAL",
                        true),
                new ScheduleExportData.Row(
                        LocalDate.of(2026, 9, 21),
                        "MONDAY",
                        100L,
                        "DAY",
                        "Day Shift",
                        LocalTime.of(8, 0),
                        LocalTime.of(16, 0),
                        "E002",
                        "Bao Tran",
                        "CONFIRMED",
                        "AUTO",
                        false),
                new ScheduleExportData.Row(
                        LocalDate.of(2026, 9, 22),
                        "TUESDAY",
                        101L,
                        "NIGHT",
                        "Night Shift",
                        LocalTime.of(22, 0),
                        LocalTime.of(6, 0),
                        null,
                        null,
                        "UNASSIGNED",
                        null,
                        false));
        verify(workShiftRepository).findExportDetailForPeriod(50L);
    }

    @Test
    void buildScheduleExportThrowsNotFoundForMissingPeriod() {
        when(schedulePeriodRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildScheduleExport(404L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Schedule period not found");
                });
    }

    private static SchedulePeriod period() {
        SchedulePeriod period = new SchedulePeriod();
        period.setId(50L);
        period.setPeriodName("Week 39");
        period.setStartDate(LocalDate.of(2026, 9, 21));
        period.setEndDate(LocalDate.of(2026, 9, 27));
        period.setVersionNo(3);
        period.setStatus("PUBLISHED");
        return period;
    }

    private static WorkShift workShift(Long id, LocalDate workDate, ShiftType shiftType) {
        WorkShift workShift = new WorkShift();
        workShift.setId(id);
        workShift.setWorkDate(workDate);
        workShift.setShiftType(shiftType);
        return workShift;
    }

    private static ShiftType shiftType(Long id, String code, String name) {
        ShiftType shiftType = new ShiftType();
        shiftType.setId(id);
        shiftType.setShiftCode(code);
        shiftType.setShiftName(name);
        if ("NIGHT".equals(code)) {
            shiftType.setStartTime(LocalTime.of(22, 0));
            shiftType.setEndTime(LocalTime.of(6, 0));
        } else {
            shiftType.setStartTime(LocalTime.of(8, 0));
            shiftType.setEndTime(LocalTime.of(16, 0));
        }
        return shiftType;
    }

    private static ShiftAssignment assignment(
            Long id, Employee employee, String assignmentStatus, String assignedSource, Boolean locked) {
        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setId(id);
        assignment.setEmployee(employee);
        assignment.setAssignmentStatus(assignmentStatus);
        assignment.setAssignedSource(assignedSource);
        assignment.setLocked(locked);
        return assignment;
    }

    private static Employee employee(Long id, String code, String name) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeCode(code);
        employee.setFullName(name);
        return employee;
    }
}
