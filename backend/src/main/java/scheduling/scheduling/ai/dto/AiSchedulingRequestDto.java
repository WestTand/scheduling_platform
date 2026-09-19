package scheduling.scheduling.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.exc.MismatchedInputException;
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
    public record SolverOptionsDto(
            @Min(1) @Max(300) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer timeLimitSeconds,
            @Min(0) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer randomSeed,
            @Min(1) @Max(64) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer numWorkers) {}

    /**
     * Rejects fractional JSON numbers (e.g. 10.5) that Jackson would otherwise silently
     * truncate to Integer. Integral floats (e.g. 10.0) are accepted as-is; null is preserved
     * so callers can fall back to defaults. Failures surface as 400 via
     * HttpMessageNotReadableException.
     */
    public static class StrictIntegerDeserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            JsonNode node = p.readValueAsTree();
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isIntegralNumber() && node.canConvertToInt()) {
                return node.intValue();
            }
            if (node.isFloatingPointNumber()) {
                double value = node.doubleValue();
                if (Double.isFinite(value)
                        && value == Math.rint(value)
                        && value >= Integer.MIN_VALUE
                        && value <= Integer.MAX_VALUE) {
                    return (int) value;
                }
            }
            throw MismatchedInputException.from(p, Integer.class, "Expected an integer but got: " + node);
        }
    }
}
