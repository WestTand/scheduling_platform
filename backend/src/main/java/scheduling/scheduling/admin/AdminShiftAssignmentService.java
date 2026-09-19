package scheduling.scheduling.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
import scheduling.scheduling.admin.dto.UpdateShiftAssignmentRequest;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.ScheduleWindows;
import scheduling.scheduling.employee.dto.EmployeeScheduleItemResponse;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.SchedulingRule;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.SchedulingRuleRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@Service
public class AdminShiftAssignmentService {

    private static final String DRAFT = "DRAFT";
    private static final String ACTIVE = "ACTIVE";
    private static final String APPROVED = "APPROVED";
    private static final String ASSIGNED = "ASSIGNED";
    private static final String MANUAL = "MANUAL";
    private static final String MAX_WORK_HOURS_PER_DAY = "MAX_WORK_HOURS_PER_DAY";
    private static final int DEFAULT_MAX_WORK_HOURS_PER_DAY = 12;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final WorkShiftRepository workShiftRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AccountRepository accountRepository;
    private final SchedulingRuleRepository schedulingRuleRepository;
    private final Clock clock;

    public AdminShiftAssignmentService(
            ShiftAssignmentRepository shiftAssignmentRepository,
            WorkShiftRepository workShiftRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            EmployeeRepository employeeRepository,
            LeaveRequestRepository leaveRequestRepository,
            AccountRepository accountRepository,
            SchedulingRuleRepository schedulingRuleRepository,
            Clock clock) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.workShiftRepository = workShiftRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.accountRepository = accountRepository;
        this.schedulingRuleRepository = schedulingRuleRepository;
        this.clock = clock;
    }

    @Transactional
    public AdminShiftAssignmentResponse createAssignment(
            CurrentAccountPrincipal principal, Long workShiftId, CreateShiftAssignmentRequest request) {
        requireManager(principal);
        if (request == null || request.employeeId() == null) {
            throw invalidAssignment("Employee id is required");
        }
        WorkShift shift = workShiftRepository.findDetailById(workShiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work shift not found"));
        requireDraft(shift.getSchedulePeriod());
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (!ACTIVE.equals(employee.getStatus())) {
            throw invalidAssignment("Employee is not active");
        }
        if (shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(workShiftId, employee.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee already assigned to this shift");
        }
        if (shiftAssignmentRepository.countByWorkShiftId(workShiftId) >= shift.getShiftType().getMaxStaff()) {
            throw invalidAssignment("Work shift is already at maximum staff");
        }
        validateEmployeeAvailability(shift, employee, null);
        ShiftAssignment assignment = ShiftAssignment.builder()
                .workShift(shift)
                .employee(employee)
                .assignmentStatus(ASSIGNED)
                .assignedSource(MANUAL)
                .locked(false)
                .build();
        return AdminShiftAssignmentResponse.from(shiftAssignmentRepository.save(assignment));
    }

    @Transactional
    public AdminShiftAssignmentResponse updateAssignment(
            CurrentAccountPrincipal principal, Long assignmentId, UpdateShiftAssignmentRequest request) {
        requireManager(principal);
        if (request == null || request.employeeId() == null) {
            throw invalidAssignment("Employee id is required");
        }
        ShiftAssignment assignment = shiftAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        WorkShift shift = assignment.getWorkShift();
        requireDraft(shift.getSchedulePeriod());
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (!ACTIVE.equals(employee.getStatus())) {
            throw invalidAssignment("Employee is not active");
        }
        Long currentEmployeeId = assignment.getEmployee() == null ? null : assignment.getEmployee().getId();
        if (!employee.getId().equals(currentEmployeeId)
                && shiftAssignmentRepository.existsByWorkShiftIdAndEmployeeId(shift.getId(), employee.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee already assigned to this shift");
        }
        validateEmployeeAvailability(shift, employee, assignmentId);
        assignment.setEmployee(employee);
        assignment.setAssignedSource(MANUAL);
        return AdminShiftAssignmentResponse.from(shiftAssignmentRepository.save(assignment));
    }

    private void validateEmployeeAvailability(WorkShift shift, Employee employee, Long ignoredAssignmentId) {
        List<LeaveRequest> leaves = leaveRequestRepository.searchForEmployee(
                employee.getId(), APPROVED, shift.getWorkDate(), shift.getWorkDate());
        if (!leaves.isEmpty()) {
            throw invalidAssignment("Employee has approved leave on the shift date");
        }
        LocalDateTime[] target = interval(shift);
        List<ShiftAssignment> nearby = shiftAssignmentRepository.findForEmployeeInWindow(
                employee.getId(), shift.getWorkDate().minusDays(1), shift.getWorkDate().plusDays(1));
        for (ShiftAssignment candidate : nearby) {
            if (candidate.getWorkShift() == null
                    || (ignoredAssignmentId != null && ignoredAssignmentId.equals(candidate.getId()))
                    || shift.getId().equals(candidate.getWorkShift().getId())) {
                continue;
            }
            LocalDateTime[] other = interval(candidate.getWorkShift());
            if (target[0].isBefore(other[1]) && other[0].isBefore(target[1])) {
                throw invalidAssignment("Employee has an overlapping assignment");
            }
        }
        enforceDailyWorkHours(shift, nearby, ignoredAssignmentId);
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
        WorkShiftWindowService windowService = new WorkShiftWindowService();
        List<AdminPeriodScheduleResponse.ScheduledShift> scheduled = shifts.stream()
                .map(shift -> {
                    WorkShiftWindowService.Window window = windowService.resolve(shift);
                    return new AdminPeriodScheduleResponse.ScheduledShift(
                            shift.getId(),
                            shift.getWorkDate(),
                            shift.getShiftType().getId(),
                            shift.getShiftType().getShiftName(),
                            shift.getShiftType().getStartTime(),
                            shift.getShiftType().getEndTime(),
                            window.startsAt(),
                            window.endsAt(),
                            window.workMinutes(),
                            window.transitionShift(),
                            shift.getShiftType().getOvernight(),
                            shift.getRequiredStaff(),
                            shift.getShiftType().getMaxStaff(),
                            byShift.getOrDefault(shift.getId(), List.of()));
                })
                .toList();
        return new AdminPeriodScheduleResponse(
                period.getId(),
                period.getPeriodName(),
                period.getStatus(),
                period.getStartDate(),
                period.getEndDate(),
                scheduled);
    }

    @Transactional(readOnly = true)
    public List<EmployeeScheduleItemResponse> getEmployeeSchedule(
            CurrentAccountPrincipal principal, Long employeeId, LocalDate from, LocalDate to) {
        requireManager(principal);
        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        ScheduleWindows.Window window = ScheduleWindows.resolve(clock, from, to);
        return shiftAssignmentRepository.findForEmployeeInWindow(employeeId, window.from(), window.to()).stream()
                .map(EmployeeScheduleItemResponse::from)
                .toList();
    }

    @Transactional
    public void deleteAssignment(CurrentAccountPrincipal principal, Long assignmentId) {
        requireManager(principal);
        // Intentional: locked assignments in DRAFT can still be deleted; locked only protects against future AI overwrite.
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

    private void enforceDailyWorkHours(
            WorkShift targetShift, List<ShiftAssignment> nearbyAssignments, Long ignoredAssignmentId) {
        int totalMinutes = workMinutes(targetShift);
        LocalDate targetDate = targetShift.getWorkDate();
        for (ShiftAssignment assignment : nearbyAssignments) {
            WorkShift shift = assignment.getWorkShift();
            if (shift != null
                    && (ignoredAssignmentId == null || !ignoredAssignmentId.equals(assignment.getId()))
                    && targetDate.equals(shift.getWorkDate())) {
                totalMinutes += workMinutes(shift);
            }
        }
        if (totalMinutes > maxWorkMinutesPerDay()) {
            throw invalidAssignment("Employee exceeds maximum work hours per day");
        }
    }

    private int maxWorkMinutesPerDay() {
        return schedulingRuleRepository.findByRuleCode(MAX_WORK_HOURS_PER_DAY)
                .map(SchedulingRule::getRuleValueJson)
                .map(AdminShiftAssignmentService::readRuleValue)
                .orElse(DEFAULT_MAX_WORK_HOURS_PER_DAY) * 60;
    }

    private static int readRuleValue(String ruleValueJson) {
        try {
            JsonNode value = OBJECT_MAPPER.readTree(ruleValueJson).get("value");
            return value == null ? DEFAULT_MAX_WORK_HOURS_PER_DAY : value.asInt(DEFAULT_MAX_WORK_HOURS_PER_DAY);
        } catch (JsonProcessingException exception) {
            return DEFAULT_MAX_WORK_HOURS_PER_DAY;
        }
    }

    private static int workMinutes(WorkShift shift) {
        if (shift.getWorkMinutes() != null) {
            return shift.getWorkMinutes();
        }
        BigDecimal durationHours = shift.getShiftType().getDurationHours();
        return durationHours.multiply(BigDecimal.valueOf(60)).intValue();
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
            throw invalidAssignment("Schedule period is not draft");
        }
    }

    private static ResponseStatusException invalidAssignment(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
