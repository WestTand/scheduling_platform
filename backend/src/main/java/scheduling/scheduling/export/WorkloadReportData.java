package scheduling.scheduling.export;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record WorkloadReportData(
        Long periodId,
        String periodName,
        LocalDate startDate,
        LocalDate endDate,
        Integer versionNo,
        String status,
        LocalDateTime generatedAt,
        List<Row> rows) {

    public record Row(
            String employeeCode,
            String employeeName,
            long totalShifts,
            BigDecimal totalHours,
            long heavyShifts,
            long overnightShifts,
            long distinctWorkingDays) {
    }
}
