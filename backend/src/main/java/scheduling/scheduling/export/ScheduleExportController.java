package scheduling.scheduling.export;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/schedule-periods/{periodId}")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ScheduleExportController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ScheduleExportService scheduleExportService;
    private final ScheduleExcelWriter scheduleExcelWriter;
    private final SchedulePdfWriter schedulePdfWriter;
    private final WorkloadReportService workloadReportService;
    private final WorkloadExcelWriter workloadExcelWriter;

    public ScheduleExportController(
            ScheduleExportService scheduleExportService,
            ScheduleExcelWriter scheduleExcelWriter,
            SchedulePdfWriter schedulePdfWriter,
            WorkloadReportService workloadReportService,
            WorkloadExcelWriter workloadExcelWriter) {
        this.scheduleExportService = scheduleExportService;
        this.scheduleExcelWriter = scheduleExcelWriter;
        this.schedulePdfWriter = schedulePdfWriter;
        this.workloadReportService = workloadReportService;
        this.workloadExcelWriter = workloadExcelWriter;
    }

    @GetMapping("/exports/schedule.xlsx")
    public ResponseEntity<byte[]> downloadScheduleXlsx(@PathVariable Long periodId) {
        byte[] bytes = scheduleExcelWriter.write(scheduleExportService.buildScheduleExport(periodId));
        return download(bytes, XLSX_MEDIA_TYPE, "schedule-period-" + periodId + ".xlsx");
    }

    @GetMapping("/exports/schedule.pdf")
    public ResponseEntity<byte[]> downloadSchedulePdf(@PathVariable Long periodId) {
        byte[] bytes = schedulePdfWriter.write(scheduleExportService.buildScheduleExport(periodId));
        return download(bytes, MediaType.APPLICATION_PDF, "schedule-period-" + periodId + ".pdf");
    }

    @GetMapping("/reports/workload.xlsx")
    public ResponseEntity<byte[]> downloadWorkloadXlsx(@PathVariable Long periodId) {
        byte[] bytes = workloadExcelWriter.write(workloadReportService.buildWorkloadReport(periodId));
        return download(bytes, XLSX_MEDIA_TYPE, "workload-period-" + periodId + ".xlsx");
    }

    private static ResponseEntity<byte[]> download(byte[] bytes, MediaType contentType, String filename) {
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes);
    }
}
