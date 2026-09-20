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
public class WorkloadExcelWriter {

    private static final String[] HEADERS = {
        "Employee code", "Employee name", "Total shifts", "Total hours", "Heavy shifts", "Overnight shifts",
        "Distinct working days"
    };

    public byte[] write(WorkloadReportData data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Workload");
            writeMetadata(sheet, data);
            writeHeader(sheet);
            writeRows(sheet, data);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write workload Excel report", exception);
        }
    }

    private static void writeMetadata(Sheet sheet, WorkloadReportData data) {
        sheet.createRow(0).createCell(0).setCellValue("Workload Report");
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

    private static void writeRows(Sheet sheet, WorkloadReportData data) {
        int rowIndex = 6;
        for (WorkloadReportData.Row reportRow : data.rows()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(blank(reportRow.employeeCode()));
            row.createCell(1).setCellValue(blank(reportRow.employeeName()));
            row.createCell(2).setCellValue(reportRow.totalShifts());
            row.createCell(3).setCellValue(reportRow.totalHours() == null ? 0.0 : reportRow.totalHours().doubleValue());
            row.createCell(4).setCellValue(reportRow.heavyShifts());
            row.createCell(5).setCellValue(reportRow.overnightShifts());
            row.createCell(6).setCellValue(reportRow.distinctWorkingDays());
        }
    }

    private static String blank(Object value) {
        return value == null ? "" : value.toString();
    }
}
