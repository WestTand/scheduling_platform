package scheduling.scheduling.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AiSchedulingResponseDto(
        String schemaVersion,
        Long runId,
        Long periodId,
        String status,
        String solverStatus,
        List<AssignmentDto> assignments,
        BigDecimal totalPenalty,
        Map<String, BigDecimal> penaltyDetails,
        List<String> warnings,
        List<DiagnosticDto> diagnostics,
        String errorMessage,
        StaffingAdviceDto staffingAdvice,
        StatisticsDto statistics) {

    public AiSchedulingResponseDto(
            String schemaVersion,
            Long runId,
            Long periodId,
            String status,
            String solverStatus,
            List<AssignmentDto> assignments,
            BigDecimal totalPenalty,
            Map<String, BigDecimal> penaltyDetails,
            List<String> warnings,
            List<DiagnosticDto> diagnostics,
            String errorMessage,
            StatisticsDto statistics) {
        this(schemaVersion, runId, periodId, status, solverStatus, assignments, totalPenalty, penaltyDetails,
                warnings, diagnostics, errorMessage, null, statistics);
    }

    public record AssignmentDto(Long employeeId, Long workShiftId) {}
    public record DiagnosticDto(String code, String message, Long employeeId, Long workShiftId) {}
    public record StaffingAdviceDto(
            Integer requiredEmployees,
            Integer availableEmployees,
            Integer totalShifts,
            Boolean exact,
            Boolean unreachable,
            List<StaffingReasonDto> reasons) {}

    /** The AI service sends each reason as an object; only {@code code} is surfaced to the UI for translation. */
    public record StaffingReasonDto(String code, Integer requiredEmployees, String detail) {}
    public record StatisticsDto(BigDecimal wallTimeSeconds, Integer conflicts, Integer branches) {}
}
