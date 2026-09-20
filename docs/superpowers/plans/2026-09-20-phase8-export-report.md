# Phase 8 Export & Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend-only schedule Excel/PDF exports and workload Excel reporting for schedule periods.

**Architecture:** Add a focused `scheduling.scheduling.export` package. Services load normalized export/report data from existing entities and repositories; writer classes format bytes only; the controller streams files with attachment headers and manager/admin authorization.

**Tech Stack:** Spring Boot 4.1.1, Spring MVC, Spring Security method authorization, Spring Data JPA, Apache POI for `.xlsx`, OpenPDF for `.pdf`, JUnit 6, Mockito, AssertJ, MockMvc.

---

## File Map

Create:

- `backend/src/main/java/scheduling/scheduling/export/ScheduleExportController.java` - file download endpoints.
- `backend/src/main/java/scheduling/scheduling/export/ScheduleExportService.java` - loads schedule rows for one period.
- `backend/src/main/java/scheduling/scheduling/export/ScheduleExportData.java` - immutable schedule export DTO.
- `backend/src/main/java/scheduling/scheduling/export/ScheduleExcelWriter.java` - schedule workbook formatter.
- `backend/src/main/java/scheduling/scheduling/export/SchedulePdfWriter.java` - schedule PDF formatter.
- `backend/src/main/java/scheduling/scheduling/export/WorkloadReportService.java` - computes employee workload totals.
- `backend/src/main/java/scheduling/scheduling/export/WorkloadReportData.java` - immutable workload report DTO.
- `backend/src/main/java/scheduling/scheduling/export/WorkloadExcelWriter.java` - workload workbook formatter.
- `backend/src/test/java/scheduling/scheduling/export/ScheduleExportServiceTest.java`.
- `backend/src/test/java/scheduling/scheduling/export/ScheduleExcelWriterTest.java`.
- `backend/src/test/java/scheduling/scheduling/export/SchedulePdfWriterTest.java`.
- `backend/src/test/java/scheduling/scheduling/export/WorkloadReportServiceTest.java`.
- `backend/src/test/java/scheduling/scheduling/export/WorkloadExcelWriterTest.java`.
- `backend/src/test/java/scheduling/scheduling/export/ScheduleExportControllerTest.java`.

Modify:

- `backend/pom.xml` - add Apache POI and OpenPDF dependencies.
- `backend/src/main/java/scheduling/scheduling/repository/WorkShiftRepository.java` - add export detail query with assignments.
- `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java` - add active employee report query.
- `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java` - add export repository/service mocks only if the context test fails due to new constructor dependencies.
- `docs/project_plan.md` - mark Phase 8 backend export/report tasks when implementation is complete.

Do not modify frontend files in this phase.

---

### Task 1: Add Export Dependencies

**Files:**

- Modify: `backend/pom.xml`

- [ ] **Step 1: Add Apache POI and OpenPDF dependencies**

Insert the dependencies after `springdoc-openapi-starter-webmvc-ui`:

```xml
<dependency>
	<groupId>org.apache.poi</groupId>
	<artifactId>poi-ooxml</artifactId>
	<version>5.4.1</version>
</dependency>
<dependency>
	<groupId>com.github.librepdf</groupId>
	<artifactId>openpdf</artifactId>
	<version>2.2.4</version>
</dependency>
```

- [ ] **Step 2: Verify dependency resolution**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```powershell
git add backend/pom.xml
git commit -m "build: add export file dependencies"
```

---

### Task 2: Define Export DTOs And Repository Queries

**Files:**

- Create: `backend/src/main/java/scheduling/scheduling/export/ScheduleExportData.java`
- Create: `backend/src/main/java/scheduling/scheduling/export/WorkloadReportData.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/WorkShiftRepository.java`
- Modify: `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java`

- [ ] **Step 1: Create schedule export DTO**

Create `ScheduleExportData.java`:

```java
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
```

- [ ] **Step 2: Create workload report DTO**

Create `WorkloadReportData.java`:

```java
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
```

- [ ] **Step 3: Add work shift export query**

Add to `WorkShiftRepository.java`:

```java
@Query("""
        select distinct ws
        from WorkShift ws
        join fetch ws.schedulePeriod sp
        join fetch ws.shiftType st
        left join fetch ws.shiftAssignments sa
        left join fetch sa.employee e
        where sp.id = :periodId
        order by ws.workDate asc, st.startTime asc, ws.id asc
        """)
List<WorkShift> findExportDetailForPeriod(@Param("periodId") Long periodId);
```

- [ ] **Step 4: Add active employee report query**

Add to `EmployeeRepository.java`:

```java
@Query("""
        select e from Employee e
        where e.status = 'ACTIVE'
        order by e.employeeCode asc, e.fullName asc
        """)
List<Employee> findActiveForWorkloadReport();
```

- [ ] **Step 5: Compile**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/scheduling/scheduling/export/ScheduleExportData.java backend/src/main/java/scheduling/scheduling/export/WorkloadReportData.java backend/src/main/java/scheduling/scheduling/repository/WorkShiftRepository.java backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java
git commit -m "feat: add export data contracts"
```

---

### Task 3: Implement Schedule Export Data Service

**Files:**

- Create: `backend/src/main/java/scheduling/scheduling/export/ScheduleExportService.java`
- Test: `backend/src/test/java/scheduling/scheduling/export/ScheduleExportServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `ScheduleExportServiceTest.java`:

```java
package scheduling.scheduling.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

class ScheduleExportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T01:02:03Z"), ZoneOffset.UTC);
    private final SchedulePeriodRepository schedulePeriodRepository = mock(SchedulePeriodRepository.class);
    private final WorkShiftRepository workShiftRepository = mock(WorkShiftRepository.class);
    private final ScheduleExportService service = new ScheduleExportService(schedulePeriodRepository, workShiftRepository, CLOCK);

    @Test
    void buildsRowsForAssignedAndUnassignedShifts() {
        SchedulePeriod period = period();
        WorkShift assigned = shift(200L, LocalDate.of(2026, 9, 21), "SANG", "Truc sang");
        assigned.setShiftAssignments(List.of(assignment(500L, assigned, "NV001", "Nguyen Van A")).stream().collect(java.util.stream.Collectors.toSet()));
        WorkShift unassigned = shift(201L, LocalDate.of(2026, 9, 22), "DEM", "Truc dem");
        unassigned.setShiftAssignments(java.util.Set.of());
        when(schedulePeriodRepository.findById(20L)).thenReturn(Optional.of(period));
        when(workShiftRepository.findExportDetailForPeriod(20L)).thenReturn(List.of(assigned, unassigned));

        ScheduleExportData data = service.buildScheduleExport(20L);

        assertThat(data.periodId()).isEqualTo(20L);
        assertThat(data.periodName()).isEqualTo("Week 39");
        assertThat(data.generatedAt()).isEqualTo(java.time.LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
        assertThat(data.rows()).hasSize(2);
        assertThat(data.rows().get(0).employeeCode()).isEqualTo("NV001");
        assertThat(data.rows().get(0).assignedSource()).isEqualTo("MANUAL");
        assertThat(data.rows().get(1).employeeCode()).isNull();
        assertThat(data.rows().get(1).assignmentStatus()).isEqualTo("UNASSIGNED");
    }

    @Test
    void missingPeriodThrowsNotFound() {
        when(schedulePeriodRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildScheduleExport(99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static SchedulePeriod period() {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Week 39")
                .startDate(LocalDate.of(2026, 9, 21))
                .endDate(LocalDate.of(2026, 9, 27))
                .versionNo(1)
                .status("PUBLISHED")
                .build();
        period.setId(20L);
        return period;
    }

    private static WorkShift shift(Long id, LocalDate date, String code, String name) {
        ShiftType type = ShiftType.builder()
                .shiftCode(code)
                .shiftName(name)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(BigDecimal.valueOf(8))
                .heavy(false)
                .overnight(false)
                .minStaff(1)
                .maxStaff(2)
                .build();
        WorkShift shift = WorkShift.builder()
                .workDate(date)
                .shiftType(type)
                .requiredStaff(1)
                .build();
        shift.setId(id);
        return shift;
    }

    private static ShiftAssignment assignment(Long id, WorkShift shift, String code, String name) {
        Employee employee = Employee.builder().employeeCode(code).fullName(name).status("ACTIVE").build();
        employee.setId(7L);
        ShiftAssignment assignment = ShiftAssignment.builder()
                .workShift(shift)
                .employee(employee)
                .assignmentStatus("ASSIGNED")
                .assignedSource("MANUAL")
                .locked(true)
                .build();
        assignment.setId(id);
        return assignment;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml "-Dtest=scheduling.scheduling.export.ScheduleExportServiceTest" test
```

Expected: compilation failure because `ScheduleExportService` does not exist.

- [ ] **Step 3: Implement service**

Create `ScheduleExportService.java`:

```java
package scheduling.scheduling.export;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@Service
public class ScheduleExportService {

    private final SchedulePeriodRepository schedulePeriodRepository;
    private final WorkShiftRepository workShiftRepository;
    private final Clock clock;

    public ScheduleExportService(
            SchedulePeriodRepository schedulePeriodRepository,
            WorkShiftRepository workShiftRepository,
            Clock clock) {
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.workShiftRepository = workShiftRepository;
        this.clock = clock;
    }

    public ScheduleExportData buildScheduleExport(Long periodId) {
        SchedulePeriod period = schedulePeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule period not found"));
        List<ScheduleExportData.Row> rows = workShiftRepository.findExportDetailForPeriod(periodId).stream()
                .flatMap(workShift -> rowsForShift(workShift).stream())
                .toList();
        return new ScheduleExportData(
                period.getId(),
                period.getPeriodName(),
                period.getStartDate(),
                period.getEndDate(),
                period.getVersionNo(),
                period.getStatus(),
                LocalDateTime.now(clock),
                rows);
    }

    private static List<ScheduleExportData.Row> rowsForShift(WorkShift workShift) {
        if (workShift.getShiftAssignments() == null || workShift.getShiftAssignments().isEmpty()) {
            return List.of(rowForUnassigned(workShift));
        }
        return workShift.getShiftAssignments().stream()
                .sorted(Comparator.comparing(assignment -> assignment.getEmployee().getEmployeeCode(), Comparator.nullsLast(String::compareTo)))
                .map(assignment -> rowForAssignment(workShift, assignment))
                .toList();
    }

    private static ScheduleExportData.Row rowForUnassigned(WorkShift workShift) {
        return new ScheduleExportData.Row(
                workShift.getWorkDate(),
                workShift.getWorkDate().getDayOfWeek().name(),
                workShift.getId(),
                workShift.getShiftType().getShiftCode(),
                workShift.getShiftType().getShiftName(),
                workShift.getShiftType().getStartTime(),
                workShift.getShiftType().getEndTime(),
                null,
                null,
                "UNASSIGNED",
                null,
                false);
    }

    private static ScheduleExportData.Row rowForAssignment(WorkShift workShift, ShiftAssignment assignment) {
        return new ScheduleExportData.Row(
                workShift.getWorkDate(),
                workShift.getWorkDate().getDayOfWeek().name(),
                workShift.getId(),
                workShift.getShiftType().getShiftCode(),
                workShift.getShiftType().getShiftName(),
                workShift.getShiftType().getStartTime(),
                workShift.getShiftType().getEndTime(),
                assignment.getEmployee().getEmployeeCode(),
                assignment.getEmployee().getFullName(),
                assignment.getAssignmentStatus(),
                assignment.getAssignedSource(),
                assignment.getLocked());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml "-Dtest=scheduling.scheduling.export.ScheduleExportServiceTest" test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/scheduling/scheduling/export/ScheduleExportService.java backend/src/test/java/scheduling/scheduling/export/ScheduleExportServiceTest.java
git commit -m "feat: build schedule export data"
```

---

### Task 4: Implement Schedule Excel Writer

**Files:**

- Create: `backend/src/main/java/scheduling/scheduling/export/ScheduleExcelWriter.java`
- Test: `backend/src/test/java/scheduling/scheduling/export/ScheduleExcelWriterTest.java`

- [ ] **Step 1: Write failing writer test**

Create `ScheduleExcelWriterTest.java`:

```java
package scheduling.scheduling.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ScheduleExcelWriterTest {

    private final ScheduleExcelWriter writer = new ScheduleExcelWriter();

    @Test
    void writesParseableScheduleWorkbook() throws Exception {
        byte[] bytes = writer.write(data());

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Schedule");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Schedule Export");
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Date");
            assertThat(sheet.getRow(6).getCell(0).getStringCellValue()).isEqualTo("2026-09-21");
            assertThat(sheet.getRow(6).getCell(7).getStringCellValue()).isEqualTo("Nguyen Van A");
        }
    }

    private static ScheduleExportData data() {
        return new ScheduleExportData(
                20L,
                "Week 39",
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 27),
                1,
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 20, 1, 2, 3),
                List.of(new ScheduleExportData.Row(
                        LocalDate.of(2026, 9, 21),
                        "MONDAY",
                        200L,
                        "SANG",
                        "Truc sang",
                        LocalTime.of(8, 0),
                        LocalTime.of(16, 0),
                        "NV001",
                        "Nguyen Van A",
                        "ASSIGNED",
                        "MANUAL",
                        true)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml "-Dtest=scheduling.scheduling.export.ScheduleExcelWriterTest" test
```

Expected: compilation failure because `ScheduleExcelWriter` does not exist.

- [ ] **Step 3: Implement writer**

Create `ScheduleExcelWriter.java`:

```java
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

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public byte[] write(ScheduleExportData data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Schedule");
            writeMetadata(sheet, data);
            writeTable(sheet, data);
            for (int i = 0; i <= 10; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write schedule Excel export", exception);
        }
    }

    private static void writeMetadata(Sheet sheet, ScheduleExportData data) {
        sheet.createRow(0).createCell(0).setCellValue("Schedule Export");
        sheet.createRow(1).createCell(0).setCellValue("Period: %s (#%d), version %d, %s".formatted(
                data.periodName(), data.periodId(), data.versionNo(), data.status()));
        sheet.createRow(2).createCell(0).setCellValue("Date range: %s to %s".formatted(
                DATE.format(data.startDate()), DATE.format(data.endDate())));
        sheet.createRow(3).createCell(0).setCellValue("Generated at: " + data.generatedAt());
    }

    private static void writeTable(Sheet sheet, ScheduleExportData data) {
        Row header = sheet.createRow(5);
        String[] columns = {"Date", "Day", "Shift code", "Shift name", "Start", "End", "Employee code", "Employee name", "Status", "Source", "Locked"};
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }
        int rowIndex = 6;
        for (ScheduleExportData.Row item : data.rows()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(DATE.format(item.workDate()));
            row.createCell(1).setCellValue(item.dayOfWeek());
            row.createCell(2).setCellValue(value(item.shiftCode()));
            row.createCell(3).setCellValue(value(item.shiftName()));
            row.createCell(4).setCellValue(item.startTime() == null ? "" : TIME.format(item.startTime()));
            row.createCell(5).setCellValue(item.endTime() == null ? "" : TIME.format(item.endTime()));
            row.createCell(6).setCellValue(value(item.employeeCode()));
            row.createCell(7).setCellValue(value(item.employeeName()));
            row.createCell(8).setCellValue(value(item.assignmentStatus()));
            row.createCell(9).setCellValue(value(item.assignedSource()));
            row.createCell(10).setCellValue(Boolean.TRUE.equals(item.locked()) ? "Yes" : "No");
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml "-Dtest=scheduling.scheduling.export.ScheduleExcelWriterTest" test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/scheduling/scheduling/export/ScheduleExcelWriter.java backend/src/test/java/scheduling/scheduling/export/ScheduleExcelWriterTest.java
git commit -m "feat: write schedule excel export"
```

---

### Task 5: Implement Schedule PDF Writer

**Files:**

- Create: `backend/src/main/java/scheduling/scheduling/export/SchedulePdfWriter.java`
- Test: `backend/src/test/java/scheduling/scheduling/export/SchedulePdfWriterTest.java`

- [ ] **Step 1: Write failing PDF test**

Create `SchedulePdfWriterTest.java`:

```java
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
    void writesParseableSchedulePdfWithPeriodText() throws Exception {
        byte[] bytes = writer.write(data());

        assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        PdfReader reader = new PdfReader(bytes);
        String text = PdfTextExtractor.getTextFromPage(reader, 1);
        reader.close();
        assertThat(text).contains("Schedule Export");
        assertThat(text).contains("Week 39");
        assertThat(text).contains("Nguyen Van A");
    }

    private static ScheduleExportData data() {
        return new ScheduleExportData(
                20L,
                "Week 39",
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 27),
                1,
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 20, 1, 2, 3),
                List.of(new ScheduleExportData.Row(
                        LocalDate.of(2026, 9, 21),
                        "MONDAY",
                        200L,
                        "SANG",
                        "Truc sang",
                        LocalTime.of(8, 0),
                        LocalTime.of(16, 0),
                        "NV001",
                        "Nguyen Van A",
                        "ASSIGNED",
                        "MANUAL",
                        true)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml "-Dtest=scheduling.scheduling.export.SchedulePdfWriterTest" test
```

Expected: compilation failure because `SchedulePdfWriter` does not exist.

- [ ] **Step 3: Implement writer**

Create `SchedulePdfWriter.java`:

```java
package scheduling.scheduling.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class SchedulePdfWriter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public byte[] write(ScheduleExportData data) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(title("Schedule Export"));
            document.add(new Paragraph("Period: %s (#%d), version %d, %s".formatted(
                    data.periodName(), data.periodId(), data.versionNo(), data.status())));
            document.add(new Paragraph("Date range: %s to %s".formatted(
                    DATE.format(data.startDate()), DATE.format(data.endDate()))));
            document.add(new Paragraph("Generated at: " + data.generatedAt()));
            document.add(new Paragraph(" "));
            document.add(table(data));
            document.close();
            return output.toByteArray();
        } catch (DocumentException | java.io.IOException exception) {
            throw new IllegalStateException("Failed to write schedule PDF export", exception);
        }
    }

    private static Paragraph title(String text) {
        Paragraph paragraph = new Paragraph(text, new Font(Font.HELVETICA, 16, Font.BOLD));
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
    }

    private static PdfPTable table(ScheduleExportData data) {
        PdfPTable table = new PdfPTable(new float[] { 2.0f, 1.8f, 2.5f, 1.5f, 3.0f, 2.0f });
        table.setWidthPercentage(100);
        header(table, "Date");
        header(table, "Shift");
        header(table, "Time");
        header(table, "Code");
        header(table, "Employee");
        header(table, "Status");
        for (ScheduleExportData.Row row : data.rows()) {
            cell(table, DATE.format(row.workDate()));
            cell(table, value(row.shiftName()));
            cell(table, timeRange(row));
            cell(table, value(row.employeeCode()));
            cell(table, row.employeeName() == null ? "Unassigned" : row.employeeName());
            cell(table, value(row.assignmentStatus()));
        }
        return table;
    }

    private static String timeRange(ScheduleExportData.Row row) {
        String start = row.startTime() == null ? "" : TIME.format(row.startTime());
        String end = row.endTime() == null ? "" : TIME.format(row.endTime());
        return start + "-" + end;
    }

    private static void header(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 10, Font.BOLD)));
        table.addCell(cell);
    }

    private static void cell(PdfPTable table, String text) {
        table.addCell(new Phrase(text == null ? "" : text, new Font(Font.HELVETICA, 9)));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml "-Dtest=scheduling.scheduling.export.SchedulePdfWriterTest" test
```

Expected: tests pass and the generated PDF is parseable with visible `Schedule Export`, period name, and employee name text.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/scheduling/scheduling/export/SchedulePdfWriter.java backend/src/test/java/scheduling/scheduling/export/SchedulePdfWriterTest.java
git commit -m "feat: write schedule pdf export"
```

---

### Task 6: Implement Workload Report Service And Excel Writer

**Files:**

- Create: `backend/src/main/java/scheduling/scheduling/export/WorkloadReportService.java`
- Create: `backend/src/main/java/scheduling/scheduling/export/WorkloadExcelWriter.java`
- Test: `backend/src/test/java/scheduling/scheduling/export/WorkloadReportServiceTest.java`
- Test: `backend/src/test/java/scheduling/scheduling/export/WorkloadExcelWriterTest.java`

- [ ] **Step 1: Write failing workload service test**

Create `WorkloadReportServiceTest.java` with repository mocks for `SchedulePeriodRepository`, `EmployeeRepository`, and `ShiftAssignmentRepository`. Use a fixed clock of `2026-09-20T01:02:03Z`, a period id `20`, two active employees `NV001` and `NV002`, and two assignments only for `NV001`. The test body must assert:

```java
assertThat(data.rows()).extracting(WorkloadReportData.Row::employeeCode)
        .containsExactly("NV001", "NV002");
assertThat(data.rows().get(0).totalShifts()).isEqualTo(2);
assertThat(data.rows().get(0).totalHours()).isEqualByComparingTo("16.0");
assertThat(data.rows().get(0).heavyShifts()).isEqualTo(1);
assertThat(data.rows().get(0).overnightShifts()).isEqualTo(1);
assertThat(data.rows().get(0).distinctWorkingDays()).isEqualTo(2);
assertThat(data.rows().get(1).totalShifts()).isZero();
assertThat(data.rows().get(1).totalHours()).isEqualByComparingTo("0.0");
```

Set up these mocks before calling `service.buildWorkloadReport(20L)`:

```java
when(schedulePeriodRepository.findById(20L)).thenReturn(Optional.of(period()));
when(employeeRepository.findActiveForWorkloadReport()).thenReturn(List.of(employee(1L, "NV001", "Nguyen Van A"), employee(2L, "NV002", "Tran Thi B")));
when(shiftAssignmentRepository.listForPeriod(20L)).thenReturn(List.of(dayShiftAssignment(), nightShiftAssignment()));
```

- [ ] **Step 2: Run service test to verify it fails**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml "-Dtest=scheduling.scheduling.export.WorkloadReportServiceTest" test
```

Expected: compilation failure because `WorkloadReportService` does not exist.

- [ ] **Step 3: Implement workload service**

Create `WorkloadReportService.java`:

```java
package scheduling.scheduling.export;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;

@Service
public class WorkloadReportService {

    private final SchedulePeriodRepository schedulePeriodRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final Clock clock;

    public WorkloadReportService(
            SchedulePeriodRepository schedulePeriodRepository,
            EmployeeRepository employeeRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            Clock clock) {
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.employeeRepository = employeeRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.clock = clock;
    }

    public WorkloadReportData buildWorkloadReport(Long periodId) {
        SchedulePeriod period = schedulePeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule period not found"));
        Map<Long, Totals> totals = new HashMap<>();
        for (ShiftAssignment assignment : shiftAssignmentRepository.listForPeriod(periodId)) {
            Long employeeId = assignment.getEmployee().getId();
            totals.computeIfAbsent(employeeId, ignored -> new Totals()).add(assignment);
        }
        List<WorkloadReportData.Row> rows = employeeRepository.findActiveForWorkloadReport().stream()
                .map(employee -> row(employee, totals.getOrDefault(employee.getId(), new Totals())))
                .toList();
        return new WorkloadReportData(
                period.getId(), period.getPeriodName(), period.getStartDate(), period.getEndDate(),
                period.getVersionNo(), period.getStatus(), LocalDateTime.now(clock), rows);
    }

    private static WorkloadReportData.Row row(Employee employee, Totals totals) {
        return new WorkloadReportData.Row(
                employee.getEmployeeCode(), employee.getFullName(), totals.totalShifts, totals.totalHours,
                totals.heavyShifts, totals.overnightShifts, totals.days.size());
    }

    private static final class Totals {
        private long totalShifts;
        private BigDecimal totalHours = BigDecimal.ZERO.setScale(1);
        private long heavyShifts;
        private long overnightShifts;
        private final Set<LocalDate> days = new HashSet<>();

        private void add(ShiftAssignment assignment) {
            totalShifts++;
            BigDecimal hours = assignment.getWorkShift().getShiftType().getDurationHours();
            if (hours != null) {
                totalHours = totalHours.add(hours);
            }
            if (Boolean.TRUE.equals(assignment.getWorkShift().getShiftType().getHeavy())) {
                heavyShifts++;
            }
            if (Boolean.TRUE.equals(assignment.getWorkShift().getShiftType().getOvernight())) {
                overnightShifts++;
            }
            days.add(assignment.getWorkShift().getWorkDate());
        }
    }
}
```

- [ ] **Step 4: Write failing workload Excel writer test**

Create `WorkloadExcelWriterTest.java` with this test body:

```java
@Test
void writesParseableWorkloadWorkbook() throws Exception {
    byte[] bytes = writer.write(data());

    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
        var sheet = workbook.getSheet("Workload");
        assertThat(sheet).isNotNull();
        assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Workload Report");
        assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Employee code");
        assertThat(sheet.getRow(6).getCell(0).getStringCellValue()).isEqualTo("NV001");
        assertThat(sheet.getRow(6).getCell(2).getNumericCellValue()).isEqualTo(2.0);
        assertThat(sheet.getRow(6).getCell(3).getNumericCellValue()).isEqualTo(16.0);
    }
}
```

Use a `data()` helper returning `WorkloadReportData` with one row: employee code `NV001`, employee name `Nguyen Van A`, total shifts `2`, total hours `BigDecimal.valueOf(16)`, heavy shifts `1`, overnight shifts `1`, and distinct working days `2`.

- [ ] **Step 5: Implement workload Excel writer**

Create `WorkloadExcelWriter.java` using the same Apache POI pattern as `ScheduleExcelWriter`, with columns:

```java
String[] columns = {"Employee code", "Employee name", "Total shifts", "Total hours", "Heavy shifts", "Overnight shifts", "Distinct working days"};
```

Write numeric cells for totals:

```java
row.createCell(2).setCellValue(item.totalShifts());
row.createCell(3).setCellValue(item.totalHours().doubleValue());
row.createCell(4).setCellValue(item.heavyShifts());
row.createCell(5).setCellValue(item.overnightShifts());
row.createCell(6).setCellValue(item.distinctWorkingDays());
```

- [ ] **Step 6: Run workload tests**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml "-Dtest=scheduling.scheduling.export.WorkloadReportServiceTest,scheduling.scheduling.export.WorkloadExcelWriterTest" test
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/scheduling/scheduling/export/WorkloadReportService.java backend/src/main/java/scheduling/scheduling/export/WorkloadExcelWriter.java backend/src/test/java/scheduling/scheduling/export/WorkloadReportServiceTest.java backend/src/test/java/scheduling/scheduling/export/WorkloadExcelWriterTest.java
git commit -m "feat: add workload excel report"
```

---

### Task 7: Add Download Controller And Security Tests

**Files:**

- Create: `backend/src/main/java/scheduling/scheduling/export/ScheduleExportController.java`
- Test: `backend/src/test/java/scheduling/scheduling/export/ScheduleExportControllerTest.java`

- [ ] **Step 1: Write failing controller test**

Create `ScheduleExportControllerTest.java` with `@WebMvcTest(ScheduleExportController.class)`, `@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })`, and these `@MockitoBean` fields: `JwtService`, `AuthService`, `AccountRepository`, `ScheduleExportService`, `ScheduleExcelWriter`, `SchedulePdfWriter`, `WorkloadReportService`, and `WorkloadExcelWriter`. Add `givenToken(String token, CurrentAccountPrincipal principal)` that stubs `jwtService.parseAccessToken(token)` and `accountRepository.findById(principal.accountId())`, matching the existing controller test pattern.

Include tests for:

```java
mockMvc.perform(get("/api/admin/schedule-periods/20/exports/schedule.xlsx")
                .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"schedule-period-20.xlsx\""));

mockMvc.perform(get("/api/admin/schedule-periods/20/exports/schedule.pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"schedule-period-20.pdf\""));

mockMvc.perform(get("/api/admin/schedule-periods/20/reports/workload.xlsx")
                .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"workload-period-20.xlsx\""));

mockMvc.perform(get("/api/admin/schedule-periods/20/exports/schedule.xlsx")
                .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
        .andExpect(status().isForbidden());

mockMvc.perform(get("/api/admin/schedule-periods/20/exports/schedule.xlsx"))
        .andExpect(status().isUnauthorized());
```

Use helper principals:

```java
private static CurrentAccountPrincipal admin() {
    return new CurrentAccountPrincipal(3L, "admin01", AccountRole.ADMIN);
}
```

- [ ] **Step 2: Run controller test to verify it fails**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml "-Dtest=scheduling.scheduling.export.ScheduleExportControllerTest" test
```

Expected: compilation failure because `ScheduleExportController` does not exist.

- [ ] **Step 3: Implement controller**

Create `ScheduleExportController.java`:

```java
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

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

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
    ResponseEntity<byte[]> downloadScheduleExcel(@PathVariable Long periodId) {
        return file(scheduleExcelWriter.write(scheduleExportService.buildScheduleExport(periodId)), XLSX,
                "schedule-period-%d.xlsx".formatted(periodId));
    }

    @GetMapping("/exports/schedule.pdf")
    ResponseEntity<byte[]> downloadSchedulePdf(@PathVariable Long periodId) {
        return file(schedulePdfWriter.write(scheduleExportService.buildScheduleExport(periodId)), MediaType.APPLICATION_PDF,
                "schedule-period-%d.pdf".formatted(periodId));
    }

    @GetMapping("/reports/workload.xlsx")
    ResponseEntity<byte[]> downloadWorkloadExcel(@PathVariable Long periodId) {
        return file(workloadExcelWriter.write(workloadReportService.buildWorkloadReport(periodId)), XLSX,
                "workload-period-%d.xlsx".formatted(periodId));
    }

    private static ResponseEntity<byte[]> file(byte[] bytes, MediaType contentType, String filename) {
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes);
    }
}
```

- [ ] **Step 4: Run controller test to verify it passes**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml "-Dtest=scheduling.scheduling.export.ScheduleExportControllerTest" test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/scheduling/scheduling/export/ScheduleExportController.java backend/src/test/java/scheduling/scheduling/export/ScheduleExportControllerTest.java
git commit -m "feat: expose export report downloads"
```

---

### Task 8: Integration Sweep And Project Plan Update

**Files:**

- Modify: `backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java` only if needed.
- Modify: `docs/project_plan.md`

- [ ] **Step 1: Run focused Phase 8 suite**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml "-Dtest=scheduling.scheduling.export.*Test" test
```

Expected: all Phase 8 tests pass.

- [ ] **Step 2: Run full backend suite**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml test
```

Expected: all backend tests pass. If `SchedulingApplicationTests` fails because new export beans add real repository dependencies to the context test, add these mocks:

```java
@MockitoBean
private ScheduleExportService scheduleExportService;

@MockitoBean
private WorkloadReportService workloadReportService;
```

Use imports:

```java
import scheduling.scheduling.export.ScheduleExportService;
import scheduling.scheduling.export.WorkloadReportService;
```

Run the full backend suite again after the mock change.

- [ ] **Step 3: Update project plan checkboxes**

In `docs/project_plan.md`, mark these Phase 8 backend items complete:

```markdown
- [x] API xuat lich Excel
- [x] API xuat lich PDF neu can
- [x] Export lich theo ky
- [x] Export bao cao tong so ca moi nhan vien
- [x] Export bao cao so gio lam moi nhan vien
- [x] Test export Excel
- [x] Test export du lieu dung filter
```

Leave `UI nut export Excel/PDF` unchecked for Phase 9.

- [ ] **Step 4: Run whitespace check**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 5: Commit final integration/docs changes**

```powershell
git add backend/src/test/java/scheduling/scheduling/SchedulingApplicationTests.java docs/project_plan.md
git commit -m "docs: mark phase 8 backend export progress"
```

If `SchedulingApplicationTests.java` was not changed, run:

```powershell
git add docs/project_plan.md
git commit -m "docs: mark phase 8 backend export progress"
```

- [ ] **Step 6: Final verification**

Run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml test
```

Expected: all backend tests pass.

Run:

```powershell
git status --short --branch
```

Expected: branch is `phase8/export-report`; only unrelated untracked docs may remain.

---

## Review Checklist

- Schedule Excel endpoint uses `/api/admin/schedule-periods/{periodId}/exports/schedule.xlsx`.
- Schedule PDF endpoint uses `/api/admin/schedule-periods/{periodId}/exports/schedule.pdf`.
- Workload Excel endpoint uses `/api/admin/schedule-periods/{periodId}/reports/workload.xlsx`.
- Endpoints authorize both `MANAGER` and `ADMIN`.
- `EMPLOYEE` and anonymous callers cannot download manager/admin exports.
- Missing period returns `404` through service lookup.
- Empty period returns a valid file with metadata and headers.
- Schedule exports include unassigned shifts.
- Workload report includes active employees with zero totals.
- Workload rows are deterministic: employee code ascending, then employee name ascending.
- PDF test parses the generated file and checks visible text.
- Frontend remains untouched.
