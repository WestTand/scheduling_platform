package scheduling.scheduling.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AiSchedulingRequestDto(
        String schemaVersion,
        Long runId,
        PeriodDto period,
        List<EmployeeDto> employees,
        List<ShiftTypeDto> shiftTypes,
        List<WorkShiftDto> workShifts,
        List<ApprovedLeaveDto> approvedLeaves,
        List<PreferenceDto> preferences,
        List<LockedAssignmentDto> lockedAssignments,
        List<BoundaryAssignmentDto> boundaryAssignments,
        RulesDto rules,
        SolverOptionsDto solverOptions) {

    public record PeriodDto(Long periodId, LocalDate startDate, LocalDate endDate, Integer versionNo, String timezone) {}
    public record EmployeeDto(Long employeeId) {}
    public record ShiftTypeDto(Long shiftTypeId, Integer durationMinutes, Boolean overnight,
                               Boolean heavy, Integer minStaff, Integer maxStaff, String quotaCategory) {}
    public record WorkShiftDto(Long workShiftId, Long shiftTypeId, LocalDate workDate, OffsetDateTime startsAt,
                               OffsetDateTime endsAt, Integer requiredStaff) {}
    public record ApprovedLeaveDto(Long leaveRequestId, Long employeeId, LocalDate startDate, LocalDate endDate) {}
    public record PreferenceDto(Long preferenceId, Long employeeId, Long shiftTypeId, LocalDate preferenceDate, String preferenceType,
                                Integer priority) {}
    public record LockedAssignmentDto(Long assignmentId, Long employeeId, Long workShiftId) {}
    public record BoundaryAssignmentDto(Long employeeId, Long shiftTypeId, OffsetDateTime startsAt, OffsetDateTime endsAt) {}
    public record RulesDto(HardRulesDto hardRules, QuotasDto quotas, SoftWeightsDto softWeights) {}
    public record HardRulesDto(Integer minRestHoursBetweenShifts, Integer maxWorkHoursPerDay, Integer maxShiftsPerWeek) {}
    public record QuotasDto(Integer overtimeShiftsPerWeek, Integer bathingShiftsPerWeek, Integer onCallEveryNDays) {}
    public record SoftWeightsDto(BigDecimal fairness, BigDecimal preference, BigDecimal weekend,
                                 BigDecimal heavyShift, BigDecimal workHourBalance) {}
    public record SolverOptionsDto(Integer timeLimitSeconds, Integer randomSeed, Integer numWorkers) {}
}
