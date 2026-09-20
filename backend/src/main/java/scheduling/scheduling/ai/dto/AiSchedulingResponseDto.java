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
        StatisticsDto statistics) {

    public record AssignmentDto(Long employeeId, Long workShiftId) {}
    public record DiagnosticDto(String code, String message, Long employeeId, Long workShiftId) {}
    public record StatisticsDto(BigDecimal wallTimeSeconds, Integer conflicts, Integer branches) {}
}
