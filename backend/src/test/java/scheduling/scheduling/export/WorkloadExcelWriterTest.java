package scheduling.scheduling.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class WorkloadExcelWriterTest {

    private final WorkloadExcelWriter writer = new WorkloadExcelWriter();

    @Test
    void writeCreatesWorkloadWorkbookWithMetadataHeaderAndRows() throws IOException {
        WorkloadReportData data = new WorkloadReportData(
                20L,
                "Week 39",
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 27),
                3,
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 20, 1, 2, 3),
                List.of(new WorkloadReportData.Row(
                        "NV001", "Nguyen Van A", 2, BigDecimal.valueOf(16), 1, 1, 2)));

        byte[] bytes = writer.write(data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet("Workload")).isNotNull();
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Workload");
            assertThat(workbook.getSheet("Workload").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Workload Report");
            assertThat(workbook.getSheet("Workload").getRow(5).getCell(0).getStringCellValue())
                    .isEqualTo("Employee code");
            assertThat(workbook.getSheet("Workload").getRow(6).getCell(0).getStringCellValue())
                    .isEqualTo("NV001");
            assertThat(workbook.getSheet("Workload").getRow(6).getCell(2).getNumericCellValue())
                    .isEqualTo(2.0);
            assertThat(workbook.getSheet("Workload").getRow(6).getCell(3).getNumericCellValue())
                    .isEqualTo(16.0);
        }
    }
}
