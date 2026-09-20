package scheduling.scheduling.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ScheduleExcelWriterTest {

    private final ScheduleExcelWriter writer = new ScheduleExcelWriter();

    @Test
    void writeCreatesScheduleWorkbookWithMetadataHeaderAndRows() throws IOException {
        ScheduleExportData data = new ScheduleExportData(
                50L,
                "Week 39",
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 27),
                3,
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 20, 8, 30),
                List.of(new ScheduleExportData.Row(
                        LocalDate.of(2026, 9, 21),
                        "MONDAY",
                        100L,
                        "DAY",
                        "Day Shift",
                        LocalTime.of(8, 0),
                        LocalTime.of(16, 0),
                        "E001",
                        "An Nguyen",
                        "ASSIGNED",
                        "MANUAL",
                        true)));

        byte[] bytes = writer.write(data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet("Schedule")).isNotNull();
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Schedule");
            assertThat(workbook.getSheet("Schedule").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Schedule Export");
            assertThat(workbook.getSheet("Schedule").getRow(5).getCell(0).getStringCellValue())
                    .isEqualTo("Date");
            assertThat(workbook.getSheet("Schedule").getRow(6).getCell(0).getStringCellValue())
                    .isEqualTo("2026-09-21");
            assertThat(workbook.getSheet("Schedule").getRow(6).getCell(7).getStringCellValue())
                    .isEqualTo("An Nguyen");
        }
    }
}
