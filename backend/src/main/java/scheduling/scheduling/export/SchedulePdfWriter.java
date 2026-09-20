package scheduling.scheduling.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class SchedulePdfWriter {

    private static final String[] HEADERS = {"Date", "Shift", "Time", "Code", "Employee", "Status"};
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public byte[] write(ScheduleExportData data) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(document, output);
            document.open();
            writeMetadata(document, data);
            writeTable(document, data);
            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException exception) {
            throw new IllegalStateException("Failed to write schedule PDF export", exception);
        }
    }

    private static void writeMetadata(Document document, ScheduleExportData data) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font metadataFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

        document.add(new Paragraph("Schedule Export", titleFont));
        document.add(new Paragraph("Period: "
                + blank(data.periodName())
                + " (id="
                + blank(data.periodId())
                + ", version="
                + blank(data.versionNo())
                + ", status="
                + blank(data.status())
                + ")", metadataFont));
        document.add(new Paragraph("Date range: "
                + (data.startDate() == null ? "" : data.startDate().format(DATE_FORMAT))
                + " to "
                + (data.endDate() == null ? "" : data.endDate().format(DATE_FORMAT)), metadataFont));
        document.add(new Paragraph("Generated: " + blank(data.generatedAt()), metadataFont));
        document.add(new Paragraph(" "));
    }

    private static void writeTable(Document document, ScheduleExportData data) throws DocumentException {
        PdfPTable table = new PdfPTable(HEADERS.length);
        table.setWidthPercentage(100);
        table.setWidths(new float[] {1.2F, 1.6F, 1.2F, 1.0F, 1.8F, 1.2F});

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        for (String header : HEADERS) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        for (ScheduleExportData.Row row : data.rows()) {
            table.addCell(new Phrase(row.workDate() == null ? "" : row.workDate().format(DATE_FORMAT), cellFont));
            table.addCell(new Phrase(blank(row.shiftName()), cellFont));
            table.addCell(new Phrase(formatTimeRange(row), cellFont));
            table.addCell(new Phrase(blank(row.shiftCode()), cellFont));
            table.addCell(new Phrase(employeeName(row), cellFont));
            table.addCell(new Phrase(blank(row.assignmentStatus()), cellFont));
        }

        document.add(table);
    }

    private static String formatTimeRange(ScheduleExportData.Row row) {
        String start = row.startTime() == null ? "" : row.startTime().format(TIME_FORMAT);
        String end = row.endTime() == null ? "" : row.endTime().format(TIME_FORMAT);
        return start + "-" + end;
    }

    private static String employeeName(ScheduleExportData.Row row) {
        String employeeName = blank(row.employeeName());
        return employeeName.isBlank() ? "Unassigned" : employeeName;
    }

    private static String blank(Object value) {
        return value == null ? "" : value.toString();
    }
}
