package scheduling.scheduling.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
                List.of(
                        new ScheduleExportData.Row(
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
                                true),
                        new ScheduleExportData.Row(
                                LocalDate.of(2026, 9, 22),
                                "TUESDAY",
                                101L,
                                "DAY",
                                "Day Shift",
                                LocalTime.of(8, 0),
                                LocalTime.of(16, 0),
                                "E002",
                                null,
                                "ASSIGNED",
                                "MANUAL",
                                false),
                        new ScheduleExportData.Row(
                                LocalDate.of(2026, 9, 23),
                                "WEDNESDAY",
                                102L,
                                "DAY",
                                "Day Shift",
                                LocalTime.of(8, 0),
                                LocalTime.of(16, 0),
                                null,
                                null,
                                "UNASSIGNED",
                                null,
                                false)));

        byte[] bytes = writer.write(data);

        assertThat(new String(bytes, 0, 4)).isEqualTo("%PDF");
        try (PdfReader reader = new PdfReader(bytes)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Schedule Export");
            assertThat(text).contains("Week 39");
            assertThat(text).contains("An Nguyen");
            assertThat(text).containsOnlyOnce("Unassigned");
            assertThat(text).contains("Shift code");
            assertThat(text).contains("Employee code");
            assertThat(text).contains("E001");
        }
    }

    @Test
    void writePrintsShiftCodeInItsOwnColumn() throws Exception {
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
                        "CA_DAY",
                        "Day Shift",
                        LocalTime.of(8, 0),
                        LocalTime.of(16, 0),
                        "E001",
                        "An Nguyen",
                        "ASSIGNED",
                        "MANUAL",
                        true)));

        byte[] bytes = writer.write(data);

        try (PdfReader reader = new PdfReader(bytes)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Shift code");
            assertThat(text).contains("CA_DAY");
            assertThat(text).contains("Employee code");
            assertThat(text).contains("E001");
        }
    }

    @Test
    void writeKeepsVietnameseDiacriticsInEmployeeNames() throws Exception {
        ScheduleExportData data = new ScheduleExportData(
                50L,
                "Week 39",
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 27),
                3,
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 20, 8, 30),
                List.of(
                        new ScheduleExportData.Row(
                                LocalDate.of(2026, 9, 21),
                                "MONDAY",
                                100L,
                                "DAY",
                                "Day Shift",
                                LocalTime.of(8, 0),
                                LocalTime.of(16, 0),
                                "E001",
                                "Nguyễn Văn An",
                                "ASSIGNED",
                                "MANUAL",
                                true),
                        new ScheduleExportData.Row(
                                LocalDate.of(2026, 9, 22),
                                "TUESDAY",
                                101L,
                                "NIGHT",
                                "Night Shift",
                                LocalTime.of(22, 0),
                                LocalTime.of(6, 0),
                                "E002",
                                "Trần Thị Bình",
                                "ASSIGNED",
                                "AI",
                                false)));

        byte[] bytes = writer.write(data);

        try (PdfReader reader = new PdfReader(bytes)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Nguyễn Văn An");
            assertThat(text).contains("Trần Thị Bình");
        }
    }

    @Test
    void boldFontKeepsVietnameseDiacritics() throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("Nguyễn Văn An", PdfExportFonts.bold(16)));
            document.add(new Paragraph("Trần Thị Bình", PdfExportFonts.bold(16)));
            document.close();

            try (PdfReader reader = new PdfReader(output.toByteArray())) {
                String text = new PdfTextExtractor(reader).getTextFromPage(1);
                assertThat(text).contains("Nguyễn Văn An");
                assertThat(text).contains("Trần Thị Bình");
            }
        }
    }

    @Test
    void writeStagesNoFontFileInTheTemporaryDirectory() throws Exception {
        baseFontCache().clear();

        List<Path> stagedBefore = stagedFontFiles();
        writer.write(singleRowExport());
        List<Path> stagedAfter = stagedFontFiles();

        assertThat(stagedAfter).containsExactlyInAnyOrderElementsOf(stagedBefore);
    }

    private static List<Path> stagedFontFiles() throws IOException {
        try (Stream<Path> entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return entries
                    .filter(entry -> entry.getFileName().toString().startsWith("scheduling-export-font-"))
                    .toList();
        }
    }

    private static ScheduleExportData singleRowExport() {
        return new ScheduleExportData(
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
                        "Nguyễn Văn An",
                        "ASSIGNED",
                        "MANUAL",
                        true)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, BaseFont> baseFontCache() throws ReflectiveOperationException {
        Field cache = PdfExportFonts.class.getDeclaredField("BASE_FONTS");
        cache.setAccessible(true);
        return (Map<String, BaseFont>) cache.get(null);
    }
}
