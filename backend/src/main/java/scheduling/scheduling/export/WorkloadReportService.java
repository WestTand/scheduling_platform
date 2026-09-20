package scheduling.scheduling.export;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;

@Service
public class WorkloadReportService {

    private final SchedulePeriodRepository schedulePeriodRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final Clock clock;

    public WorkloadReportService(
            SchedulePeriodRepository schedulePeriodRepository,
            EmployeeRepository employeeRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            Clock clock) {
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.employeeRepository = employeeRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.clock = clock;
    }

    public WorkloadReportData buildWorkloadReport(Long periodId) {
        SchedulePeriod period = schedulePeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule period not found"));
        List<Employee> employees = employeeRepository.findActiveForWorkloadReport();
        Map<Long, Totals> totalsByEmployeeId = new HashMap<>();
        for (ShiftAssignment assignment : shiftAssignmentRepository.listForPeriod(periodId)) {
            Employee employee = assignment.getEmployee();
            if (employee != null && employee.getId() != null) {
                totalsByEmployeeId.computeIfAbsent(employee.getId(), ignored -> new Totals()).add(assignment);
            }
        }

        List<WorkloadReportData.Row> rows = employees.stream()
                .map(employee -> toRow(employee, totalsByEmployeeId.getOrDefault(employee.getId(), new Totals())))
                .toList();
        return new WorkloadReportData(
                period.getId(),
                period.getPeriodName(),
                period.getStartDate(),
                period.getEndDate(),
                period.getVersionNo(),
                period.getStatus(),
                LocalDateTime.now(clock),
                rows);
    }

    private static WorkloadReportData.Row toRow(Employee employee, Totals totals) {
        return new WorkloadReportData.Row(
                employee.getEmployeeCode(),
                employee.getFullName(),
                totals.totalShifts,
                totals.totalHours,
                totals.heavyShifts,
                totals.overnightShifts,
                totals.workingDays.size());
    }

    private static class Totals {
        private long totalShifts;
        private BigDecimal totalHours = BigDecimal.ZERO;
        private long heavyShifts;
        private long overnightShifts;
        private final Set<LocalDate> workingDays = new HashSet<>();

        private void add(ShiftAssignment assignment) {
            totalShifts++;
            WorkShift workShift = assignment.getWorkShift();
            if (workShift == null) {
                return;
            }
            if (workShift.getWorkDate() != null) {
                workingDays.add(workShift.getWorkDate());
            }
            ShiftType shiftType = workShift.getShiftType();
            if (shiftType == null) {
                return;
            }
            if (shiftType.getDurationHours() != null) {
                totalHours = totalHours.add(shiftType.getDurationHours());
            }
            if (Boolean.TRUE.equals(shiftType.getHeavy())) {
                heavyShifts++;
            }
            if (Boolean.TRUE.equals(shiftType.getOvernight())) {
                overnightShifts++;
            }
        }
    }
}
