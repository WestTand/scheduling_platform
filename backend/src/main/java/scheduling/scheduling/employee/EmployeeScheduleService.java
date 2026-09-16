package scheduling.scheduling.employee;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.repository.ShiftAssignmentRepository;

@Service
public class EmployeeScheduleService {

    private static final String PUBLISHED = "PUBLISHED";

    private final EmployeeAccess employeeAccess;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final Clock clock;

    public EmployeeScheduleService(
            EmployeeAccess employeeAccess,
            ShiftAssignmentRepository shiftAssignmentRepository,
            Clock clock) {
        this.employeeAccess = employeeAccess;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EmployeeScheduleItemResponse> getOwnSchedule(
            CurrentAccountPrincipal principal, LocalDate from, LocalDate to) {
        Employee employee = employeeAccess.requireActiveEmployee(principal);
        ScheduleWindows.Window window = ScheduleWindows.resolve(clock, from, to);
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findForEmployeeInWindow(
                employee.getId(), window.from(), window.to());
        return assignments.stream()
                .filter(assignment -> PUBLISHED.equals(
                        assignment.getWorkShift().getSchedulePeriod().getStatus()))
                .map(EmployeeScheduleItemResponse::from)
                .toList();
    }
}
