package scheduling.scheduling.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OptimizeScheduleResponse(
        Long runId,
        Long periodId,
        String aiStatus,
        String solverStatus,
        BigDecimal totalPenalty,
        Integer assignmentCount,
        List<String> warnings,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {}
