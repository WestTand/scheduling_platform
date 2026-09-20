package scheduling.scheduling.export;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record ScheduleExportData(
        Long periodId,
        String periodName,
        LocalDate startDate,
        LocalDate endDate,
        Integer versionNo,
        String status,
        LocalDateTime generatedAt,
        List<Row> rows) {

    public record Row(
            LocalDate workDate,
            String dayOfWeek,
            Long workShiftId,
            String shiftCode,
            String shiftName,
            LocalTime startTime,
            LocalTime endTime,
            String employeeCode,
            String employeeName,
            String assignmentStatus,
            String assignedSource,
            Boolean locked) {
    }
}
