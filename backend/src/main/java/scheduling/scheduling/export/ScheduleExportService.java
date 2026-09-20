package scheduling.scheduling.export;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@Service
public class ScheduleExportService {

    private final SchedulePeriodRepository schedulePeriodRepository;
    private final WorkShiftRepository workShiftRepository;
    private final Clock clock;

    public ScheduleExportService(
            SchedulePeriodRepository schedulePeriodRepository,
            WorkShiftRepository workShiftRepository,
            Clock clock) {
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.workShiftRepository = workShiftRepository;
        this.clock = clock;
    }

    public ScheduleExportData buildScheduleExport(Long periodId) {
        SchedulePeriod period = schedulePeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule period not found"));
        List<ScheduleExportData.Row> rows = new ArrayList<>();
        for (WorkShift workShift : workShiftRepository.findExportDetailForPeriod(periodId)) {
            Set<ShiftAssignment> assignments = workShift.getShiftAssignments();
            if (assignments == null || assignments.isEmpty()) {
                rows.add(toUnassignedRow(workShift));
            } else {
                assignments.stream()
                        .sorted(Comparator.comparing(
                                ScheduleExportService::employeeCode,
                                Comparator.nullsLast(String::compareTo)))
                        .map(assignment -> toAssignedRow(workShift, assignment))
                        .forEach(rows::add);
            }
        }
        return new ScheduleExportData(
                period.getId(),
                period.getPeriodName(),
                period.getStartDate(),
                period.getEndDate(),
                period.getVersionNo(),
                period.getStatus(),
                LocalDateTime.now(clock),
                rows);
    }

    private static ScheduleExportData.Row toAssignedRow(WorkShift workShift, ShiftAssignment assignment) {
        ShiftType shiftType = workShift.getShiftType();
        Employee employee = assignment.getEmployee();
        return new ScheduleExportData.Row(
                workShift.getWorkDate(),
                workShift.getWorkDate().getDayOfWeek().name(),
                workShift.getId(),
                shiftType.getShiftCode(),
                shiftType.getShiftName(),
                shiftType.getStartTime(),
                shiftType.getEndTime(),
                employee == null ? null : employee.getEmployeeCode(),
                employee == null ? null : employee.getFullName(),
                assignment.getAssignmentStatus(),
                assignment.getAssignedSource(),
                Boolean.TRUE.equals(assignment.getLocked()));
    }

    private static ScheduleExportData.Row toUnassignedRow(WorkShift workShift) {
        ShiftType shiftType = workShift.getShiftType();
        return new ScheduleExportData.Row(
                workShift.getWorkDate(),
                workShift.getWorkDate().getDayOfWeek().name(),
                workShift.getId(),
                shiftType.getShiftCode(),
                shiftType.getShiftName(),
                shiftType.getStartTime(),
                shiftType.getEndTime(),
                null,
                null,
                "UNASSIGNED",
                null,
                false);
    }

    private static String employeeCode(ShiftAssignment assignment) {
        Employee employee = assignment.getEmployee();
        return employee == null ? null : employee.getEmployeeCode();
    }
}
