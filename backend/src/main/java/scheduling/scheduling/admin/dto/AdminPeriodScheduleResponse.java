package scheduling.scheduling.admin.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AdminPeriodScheduleResponse(
        Long periodId,
        String periodName,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        List<ScheduledShift> workShifts) {

    public record ScheduledShift(
            Long id,
            LocalDate workDate,
            Long shiftTypeId,
            String shiftName,
            LocalTime shiftStartTime,
            LocalTime shiftEndTime,
            Boolean overnight,
            Integer requiredStaff,
            Integer maxStaff,
            List<AdminShiftAssignmentResponse> assignments) {
    }
}
