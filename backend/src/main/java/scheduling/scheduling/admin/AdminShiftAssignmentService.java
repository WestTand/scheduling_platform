package scheduling.scheduling.admin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminPeriodScheduleResponse;
import scheduling.scheduling.admin.dto.AdminShiftAssignmentResponse;
import scheduling.scheduling.admin.dto.CreateShiftAssignmentRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
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

@Service
public class AdminShiftAssignmentService {

    private static final String DRAFT = "DRAFT";
    private static final String ACTIVE = "ACTIVE";
    private static final String APPROVED = "APPROVED";
    private static final String ASSIGNED = "ASSIGNED";
    private static final String MANUAL = "MANUAL";

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final WorkShiftRepository workShiftRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AccountRepository accountRepository;

    public AdminShiftAssignmentService(
            ShiftAssignmentRepository shiftAssignmentRepository,
            WorkShiftRepository workShiftRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            EmployeeRepository employeeRepository,
            LeaveRequestRepository leaveRequestRepository,
            AccountRepository accountRepository) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.workShiftRepository = workShiftRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AdminShiftAssignmentResponse createAssignment(
            CurrentAccountPrincipal principal, Long workShiftId, CreateShiftAssignmentRequest request) {
        requireManager(principal);
        if (request == null || request.employeeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
        }
        WorkShift shift = workShiftRepository.findDetailById(workShiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work shift not found"));
        requireDraft(shift.getSchedulePeriod());
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (!ACTIVE.equals(employee.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
        }
        if (shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(workShiftId, employee.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee already assigned to this shift");
        }
        if (shiftAssignmentRepository.countByWorkShiftId(workShiftId) >= shift.getShiftType().getMaxStaff()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
        }
        List<LeaveRequest> leaves = leaveRequestRepository.searchForEmployee(
                employee.getId(), APPROVED, shift.getWorkDate(), shift.getWorkDate());
        if (!leaves.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
        }
        LocalDateTime[] target = interval(shift);
        List<ShiftAssignment> nearby = shiftAssignmentRepository.findForEmployeeInWindow(
                employee.getId(), shift.getWorkDate().minusDays(1), shift.getWorkDate().plusDays(1));
        for (ShiftAssignment candidate : nearby) {
            if (candidate.getWorkShift() == null
                    || workShiftId.equals(candidate.getWorkShift().getId())) {
                continue;
            }
            LocalDateTime[] other = interval(candidate.getWorkShift());
            if (target[0].isBefore(other[1]) && other[0].isBefore(target[1])) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
            }
        }
        ShiftAssignment assignment = ShiftAssignment.builder()
                .workShift(shift)
                .employee(employee)
                .assignmentStatus(ASSIGNED)
                .assignedSource(MANUAL)
                .locked(false)
                .build();
        return AdminShiftAssignmentResponse.from(shiftAssignmentRepository.save(assignment));
    }

    @Transactional(readOnly = true)
    public AdminPeriodScheduleResponse getSchedule(CurrentAccountPrincipal principal, Long periodId) {
        requireManager(principal);
        SchedulePeriod period = schedulePeriodRepository.findDetailById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule period not found"));
        List<WorkShift> shifts = workShiftRepository.searchForPeriod(periodId, null, null);
        List<ShiftAssignment> assignments = shiftAssignmentRepository.listForPeriod(periodId);
        Map<Long, List<AdminShiftAssignmentResponse>> byShift = new LinkedHashMap<>();
        for (ShiftAssignment assignment : assignments) {
            byShift.computeIfAbsent(assignment.getWorkShift().getId(), key -> new ArrayList<>())
                    .add(AdminShiftAssignmentResponse.from(assignment));
        }
        List<AdminPeriodScheduleResponse.ScheduledShift> scheduled = shifts.stream()
                .map(shift -> new AdminPeriodScheduleResponse.ScheduledShift(
                        shift.getId(),
                        shift.getWorkDate(),
                        shift.getShiftType().getId(),
                        shift.getShiftType().getShiftName(),
                        shift.getShiftType().getStartTime(),
                        shift.getShiftType().getEndTime(),
                        shift.getShiftType().getOvernight(),
                        shift.getRequiredStaff(),
                        shift.getShiftType().getMaxStaff(),
                        byShift.getOrDefault(shift.getId(), List.of())))
                .toList();
        return new AdminPeriodScheduleResponse(
                period.getId(),
                period.getPeriodName(),
                period.getStatus(),
                period.getStartDate(),
                period.getEndDate(),
                scheduled);
    }

    @Transactional
    public void deleteAssignment(CurrentAccountPrincipal principal, Long assignmentId) {
        requireManager(principal);
        ShiftAssignment assignment = shiftAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        requireDraft(assignment.getWorkShift().getSchedulePeriod());
        shiftAssignmentRepository.delete(assignment);
    }

    @Transactional
    public AdminShiftAssignmentResponse lockAssignment(CurrentAccountPrincipal principal, Long assignmentId) {
        return setLocked(principal, assignmentId, true);
    }

    @Transactional
    public AdminShiftAssignmentResponse unlockAssignment(CurrentAccountPrincipal principal, Long assignmentId) {
        return setLocked(principal, assignmentId, false);
    }

    private AdminShiftAssignmentResponse setLocked(
            CurrentAccountPrincipal principal, Long assignmentId, boolean locked) {
        requireManager(principal);
        ShiftAssignment assignment = shiftAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        requireDraft(assignment.getWorkShift().getSchedulePeriod());
        assignment.setLocked(locked);
        return AdminShiftAssignmentResponse.from(shiftAssignmentRepository.save(assignment));
    }

    static LocalDateTime[] interval(WorkShift shift) {
        ShiftType type = shift.getShiftType();
        LocalDateTime start = shift.getWorkDate().atTime(type.getStartTime());
        LocalDateTime end = shift.getWorkDate().atTime(type.getEndTime());
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
        }
        return new LocalDateTime[] {start, end};
    }

    private void requireManager(CurrentAccountPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
    }

    private static void requireDraft(SchedulePeriod period) {
        if (period == null || !DRAFT.equals(period.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift assignment request");
        }
    }
}
