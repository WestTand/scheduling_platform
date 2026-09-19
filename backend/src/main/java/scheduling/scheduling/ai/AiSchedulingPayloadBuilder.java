package scheduling.scheduling.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.entity.Preference;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.SchedulingRule;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.SchedulingRuleRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@Service
public class AiSchedulingPayloadBuilder {

    private static final String SCHEMA_VERSION = "1.0";
    private static final String TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final String ACTIVE = "ACTIVE";
    private static final String APPROVED = "APPROVED";
    private static final String PENDING = "PENDING";

    private static final int DEFAULT_TIME_LIMIT_SECONDS = 30;
    private static final int DEFAULT_RANDOM_SEED = 1;
    private static final int DEFAULT_NUM_WORKERS = 8;

    private final EmployeeRepository employeeRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final WorkShiftRepository workShiftRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PreferenceRepository preferenceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final SchedulingRuleRepository schedulingRuleRepository;
    private final ObjectMapper objectMapper;

    public AiSchedulingPayloadBuilder(
            EmployeeRepository employeeRepository,
            ShiftTypeRepository shiftTypeRepository,
            WorkShiftRepository workShiftRepository,
            LeaveRequestRepository leaveRequestRepository,
            PreferenceRepository preferenceRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            SchedulingRuleRepository schedulingRuleRepository,
            ObjectMapper objectMapper) {
        this.employeeRepository = employeeRepository;
        this.shiftTypeRepository = shiftTypeRepository;
        this.workShiftRepository = workShiftRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.preferenceRepository = preferenceRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.schedulingRuleRepository = schedulingRuleRepository;
        this.objectMapper = objectMapper;
    }

    public void validateInputs(
            SchedulePeriod period, AiSchedulingRequestDto.SolverOptionsDto solverOptions) {
        loadAndValidate(period, solverOptions);
    }

    public Map<String, Object> weightsSnapshot(
            SchedulePeriod period, AiSchedulingRequestDto.SolverOptionsDto solverOptions) {
        PayloadSnapshot snapshot = loadAndValidate(period, solverOptions);
        return Map.of("rules", snapshot.rules(), "solverOptions", snapshot.solverOptions());
    }

    public AiSchedulingRequestDto build(
            SchedulePeriod period, Long runId, AiSchedulingRequestDto.SolverOptionsDto solverOptions) {
        PayloadSnapshot snapshot = loadAndValidate(period, solverOptions);
        return new AiSchedulingRequestDto(
                SCHEMA_VERSION,
                runId,
                new AiSchedulingRequestDto.PeriodDto(
                        period.getId(),
                        period.getStartDate(),
                        period.getEndDate(),
                        period.getVersionNo(),
                        TIMEZONE),
                snapshot.employees().stream()
                        .map(employee -> new AiSchedulingRequestDto.EmployeeDto(employee.getId()))
                        .toList(),
                snapshot.shiftTypes().stream().map(this::toShiftTypeDto).toList(),
                snapshot.workShifts().stream().map(this::toWorkShiftDto).toList(),
                snapshot.leaves().stream().map(this::toApprovedLeaveDto).toList(),
                snapshot.preferences().stream().map(this::toPreferenceDto).toList(),
                snapshot.lockedAssignments().stream()
                        .map(assignment -> new AiSchedulingRequestDto.LockedAssignmentDto(
                                assignment.getId(),
                                assignment.getEmployee().getId(),
                                assignment.getWorkShift().getId()))
                        .toList(),
                List.of(),
                snapshot.rules(),
                snapshot.solverOptions());
    }

    private PayloadSnapshot loadAndValidate(
            SchedulePeriod period, AiSchedulingRequestDto.SolverOptionsDto solverOptions) {
        AiSchedulingRequestDto.SolverOptionsDto effectiveOptions = resolveSolverOptions(solverOptions);
        AiSchedulingRequestDto.RulesDto rules = loadRules();

        List<Employee> employees = employeeRepository.findSchedulableForAi();
        if (employees == null || employees.isEmpty()) {
            throw badRequest("No active employees to schedule");
        }
        Set<Long> activeEmployeeIds = employees.stream()
                .map(Employee::getId)
                .collect(Collectors.toSet());

        List<ShiftType> shiftTypes = shiftTypeRepository.findByStatus(ACTIVE);
        if (shiftTypes == null) {
            shiftTypes = List.of();
        }
        Set<Long> activeShiftTypeIds = shiftTypes.stream()
                .map(ShiftType::getId)
                .collect(Collectors.toSet());

        List<WorkShift> workShifts = workShiftRepository.findForAiPayload(period.getId());
        if (workShifts == null || workShifts.isEmpty()) {
            throw badRequest("No work shifts in period");
        }
        for (WorkShift workShift : workShifts) {
            Long shiftTypeId =
                    workShift.getShiftType() == null ? null : workShift.getShiftType().getId();
            if (!activeShiftTypeIds.contains(shiftTypeId)) {
                throw badRequest(
                        "Work shift " + workShift.getId() + " uses inactive shift type " + shiftTypeId);
            }
            if (workShift.getStartsAt() == null || workShift.getEndsAt() == null) {
                throw badRequest("Work shift " + workShift.getId() + " is missing time window");
            }
        }
        Set<Long> periodWorkShiftIds = workShifts.stream()
                .map(WorkShift::getId)
                .collect(Collectors.toSet());

        List<ShiftAssignment> lockedAssignments =
                shiftAssignmentRepository.findLockedForPeriod(period.getId());
        if (lockedAssignments == null) {
            lockedAssignments = List.of();
        }
        for (ShiftAssignment assignment : lockedAssignments) {
            Long employeeId =
                    assignment.getEmployee() == null ? null : assignment.getEmployee().getId();
            Long workShiftId =
                    assignment.getWorkShift() == null ? null : assignment.getWorkShift().getId();
            if (!activeEmployeeIds.contains(employeeId) || !periodWorkShiftIds.contains(workShiftId)) {
                throw badRequest("Locked assignment "
                        + assignment.getId()
                        + " references inactive scheduling data");
            }
        }

        List<LeaveRequest> leaves =
                leaveRequestRepository.findApprovedOverlapping(period.getStartDate(), period.getEndDate());
        if (leaves == null) {
            leaves = List.of();
        }
        List<LeaveRequest> schedulableLeaves = leaves.stream()
                .filter(leave -> APPROVED.equals(leave.getStatus()))
                .filter(leave -> leave.getEmployee() != null
                        && activeEmployeeIds.contains(leave.getEmployee().getId()))
                .toList();

        List<Preference> preferences =
                preferenceRepository.findActiveInRange(period.getStartDate(), period.getEndDate());
        if (preferences == null) {
            preferences = List.of();
        }
        List<Preference> schedulablePreferences = preferences.stream()
                .filter(preference -> PENDING.equals(preference.getStatus())
                        || APPROVED.equals(preference.getStatus()))
                .filter(preference -> preference.getEmployee() != null
                        && activeEmployeeIds.contains(preference.getEmployee().getId()))
                .filter(preference -> preference.getShiftType() == null
                        || activeShiftTypeIds.contains(preference.getShiftType().getId()))
                .toList();

        return new PayloadSnapshot(
                employees,
                shiftTypes,
                workShifts,
                schedulableLeaves,
                schedulablePreferences,
                lockedAssignments,
                rules,
                effectiveOptions);
    }

    private AiSchedulingRequestDto.SolverOptionsDto resolveSolverOptions(
            AiSchedulingRequestDto.SolverOptionsDto solverOptions) {
        if (solverOptions == null) {
            return new AiSchedulingRequestDto.SolverOptionsDto(
                    DEFAULT_TIME_LIMIT_SECONDS, DEFAULT_RANDOM_SEED, DEFAULT_NUM_WORKERS);
        }
        if (solverOptions.timeLimitSeconds() == null
                || solverOptions.timeLimitSeconds() < 1
                || solverOptions.timeLimitSeconds() > 300
                || solverOptions.numWorkers() == null
                || solverOptions.numWorkers() < 1
                || solverOptions.numWorkers() > 64
                || solverOptions.randomSeed() == null
                || solverOptions.randomSeed() < 0) {
            throw badRequest("Invalid solver options");
        }
        return solverOptions;
    }

    private AiSchedulingRequestDto.RulesDto loadRules() {
        int minRestHours = readRuleValue("MIN_REST_HOURS_BETWEEN_SHIFTS");
        int maxWorkHours = readRuleValue("MAX_WORK_HOURS_PER_DAY");
        int maxShifts = readRuleValue("MAX_SHIFTS_PER_WEEK");
        int overtime = readRuleValue("OVERTIME_SHIFTS_PER_WEEK");
        int bathing = readRuleValue("BATHING_SHIFTS_PER_WEEK");
        int onCall = readRuleValue("ON_CALL_EVERY_N_DAYS");
        BigDecimal fairness = readRuleWeight("FAIRNESS_WEIGHT");
        BigDecimal preference = readRuleWeight("PREFERENCE_WEIGHT");
        BigDecimal weekend = readRuleWeight("WEEKEND_WEIGHT");
        BigDecimal heavyShift = readRuleWeight("HEAVY_SHIFT_WEIGHT");
        BigDecimal workHourBalance = readRuleWeight("WORK_HOUR_BALANCE_WEIGHT");
        return new AiSchedulingRequestDto.RulesDto(
                new AiSchedulingRequestDto.HardRulesDto(minRestHours, maxWorkHours, maxShifts),
                new AiSchedulingRequestDto.QuotasDto(overtime, bathing, onCall),
                new AiSchedulingRequestDto.SoftWeightsDto(
                        fairness, preference, weekend, heavyShift, workHourBalance));
    }

    private int readRuleValue(String code) {
        SchedulingRule rule = schedulingRuleRepository.findByRuleCode(code).orElse(null);
        if (rule == null || !Boolean.TRUE.equals(rule.getActive())) {
            throw badRequest("Missing scheduling rule: " + code);
        }
        try {
            JsonNode value = objectMapper.readTree(rule.getRuleValueJson()).get("value");
            if (value == null || !value.isIntegralNumber()) {
                throw badRequest("Missing scheduling rule: " + code);
            }
            return value.asInt();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw badRequest("Missing scheduling rule: " + code);
        }
    }

    private BigDecimal readRuleWeight(String code) {
        SchedulingRule rule = schedulingRuleRepository.findByRuleCode(code).orElse(null);
        if (rule == null || !Boolean.TRUE.equals(rule.getActive()) || rule.getWeight() == null) {
            throw badRequest("Missing scheduling rule: " + code);
        }
        return rule.getWeight();
    }

    private AiSchedulingRequestDto.ShiftTypeDto toShiftTypeDto(ShiftType shiftType) {
        int durationMinutes = (int) Math.round(
                shiftType.getDurationHours().multiply(BigDecimal.valueOf(60)).doubleValue());
        String quotaCategory =
                "REGULAR".equals(shiftType.getQuotaCategory()) ? null : shiftType.getQuotaCategory();
        return new AiSchedulingRequestDto.ShiftTypeDto(
                shiftType.getId(),
                durationMinutes,
                shiftType.getOvernight(),
                shiftType.getHeavy(),
                shiftType.getMinStaff(),
                shiftType.getMaxStaff(),
                quotaCategory);
    }

    private AiSchedulingRequestDto.WorkShiftDto toWorkShiftDto(WorkShift workShift) {
        return new AiSchedulingRequestDto.WorkShiftDto(
                workShift.getId(),
                workShift.getShiftType().getId(),
                workShift.getWorkDate(),
                workShift.getStartsAt(),
                workShift.getEndsAt(),
                workShift.getRequiredStaff());
    }

    private AiSchedulingRequestDto.ApprovedLeaveDto toApprovedLeaveDto(LeaveRequest leave) {
        return new AiSchedulingRequestDto.ApprovedLeaveDto(
                leave.getId(), leave.getEmployee().getId(), leave.getStartDate(), leave.getEndDate());
    }

    private AiSchedulingRequestDto.PreferenceDto toPreferenceDto(Preference preference) {
        return new AiSchedulingRequestDto.PreferenceDto(
                preference.getId(),
                preference.getEmployee().getId(),
                preference.getShiftType() == null ? null : preference.getShiftType().getId(),
                preference.getPreferenceDate(),
                preference.getPreferenceType(),
                preference.getPriority());
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private record PayloadSnapshot(
            List<Employee> employees,
            List<ShiftType> shiftTypes,
            List<WorkShift> workShifts,
            List<LeaveRequest> leaves,
            List<Preference> preferences,
            List<ShiftAssignment> lockedAssignments,
            AiSchedulingRequestDto.RulesDto rules,
            AiSchedulingRequestDto.SolverOptionsDto solverOptions) {}
}
