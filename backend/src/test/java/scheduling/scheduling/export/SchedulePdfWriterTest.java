package scheduling.scheduling.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulePdfWriterTest {

    private final SchedulePdfWriter writer = new SchedulePdfWriter();

    @Test
    void writeCreatesPrintablePdfWithScheduleText() throws Exception {
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

        assertThat(new String(bytes, 0, 4)).isEqualTo("%PDF");
        try (PdfReader reader = new PdfReader(bytes)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Schedule Export");
            assertThat(text).contains("Week 39");
            assertThat(text).contains("An Nguyen");
        }
    }
}
