package scheduling.scheduling.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminPeriodScheduleResponse(
        Long periodId,
        String periodName,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        List<EmployeePoolItem> employeePool,
        LatestOptimizationRun latestOptimizationRun,
        List<ScheduledShift> workShifts) {

    public record LatestOptimizationRun(
            Long runId,
            String status,
            BigDecimal totalPenalty,
            String penaltyDetailsJson,
            String errorMessage,
            List<OptimizationDiagnostic> diagnostics,
            StaffingAdvice staffingAdvice) {
    }

    public record StaffingAdvice(
            Integer requiredEmployees,
            Integer availableEmployees,
            Integer totalShifts,
            Boolean exact,
            Boolean unreachable,
            List<String> reasons) {
    }

    public record OptimizationDiagnostic(
            String code,
            String message,
            Long employeeId,
            Long workShiftId) {
    }

    public record EmployeePoolItem(
            Long id,
            String employeeCode,
            String fullName) {
    }

    public record ScheduledShift(
            Long id,
            LocalDate workDate,
            Long shiftTypeId,
            String shiftName,
            LocalTime shiftStartTime,
            LocalTime shiftEndTime,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer workMinutes,
            Boolean isTransitionShift,
            Boolean overnight,
            Integer requiredStaff,
            Integer maxStaff,
            List<AdminShiftAssignmentResponse> assignments) {
    }
}
