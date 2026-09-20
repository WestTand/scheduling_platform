package scheduling.scheduling.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ScheduleExcelWriter {

    private static final String[] HEADERS = {
        "Date", "Day", "Shift code", "Shift name", "Start", "End", "Employee code", "Employee name", "Status",
        "Source", "Locked"
    };
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public byte[] write(ScheduleExportData data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Schedule");
            writeMetadata(sheet, data);
            writeHeader(sheet);
            writeRows(sheet, data);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write schedule Excel export", exception);
        }
    }

    private static void writeMetadata(Sheet sheet, ScheduleExportData data) {
        sheet.createRow(0).createCell(0).setCellValue("Schedule Export");
        sheet.createRow(1).createCell(0).setCellValue("Period: "
                + blank(data.periodName())
                + " (id="
                + blank(data.periodId())
                + ", version="
                + blank(data.versionNo())
                + ", status="
                + blank(data.status())
                + ")");
        sheet.createRow(2).createCell(0).setCellValue("Date range: "
                + (data.startDate() == null ? "" : data.startDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                + " to "
                + (data.endDate() == null ? "" : data.endDate().format(DateTimeFormatter.ISO_LOCAL_DATE)));
        sheet.createRow(3).createCell(0).setCellValue("Generated: " + blank(data.generatedAt()));
    }

    private static void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(5);
        for (int index = 0; index < HEADERS.length; index++) {
            header.createCell(index).setCellValue(HEADERS[index]);
        }
    }

    private static void writeRows(Sheet sheet, ScheduleExportData data) {
        int rowIndex = 6;
        for (ScheduleExportData.Row exportRow : data.rows()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(exportRow.workDate() == null
                    ? ""
                    : exportRow.workDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            row.createCell(1).setCellValue(blank(exportRow.dayOfWeek()));
            row.createCell(2).setCellValue(blank(exportRow.shiftCode()));
            row.createCell(3).setCellValue(blank(exportRow.shiftName()));
            row.createCell(4)
                    .setCellValue(exportRow.startTime() == null ? "" : exportRow.startTime().format(TIME_FORMAT));
            row.createCell(5).setCellValue(exportRow.endTime() == null ? "" : exportRow.endTime().format(TIME_FORMAT));
            row.createCell(6).setCellValue(blank(exportRow.employeeCode()));
            row.createCell(7).setCellValue(blank(exportRow.employeeName()));
            row.createCell(8).setCellValue(blank(exportRow.assignmentStatus()));
            row.createCell(9).setCellValue(blank(exportRow.assignedSource()));
            row.createCell(10).setCellValue(Boolean.TRUE.equals(exportRow.locked()) ? "Yes" : "No");
        }
    }

    private static String blank(Object value) {
        return value == null ? "" : value.toString();
    }
}
