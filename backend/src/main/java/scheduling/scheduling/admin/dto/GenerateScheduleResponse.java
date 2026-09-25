package scheduling.scheduling.admin.dto;

import java.util.List;

public record GenerateScheduleResponse(
        Long periodId,
        String periodName,
        String status,
        String aiStatus,
        String solverStatus,
        Integer assignmentCount,
        List<String> warnings,
        String errorMessage) {
}
