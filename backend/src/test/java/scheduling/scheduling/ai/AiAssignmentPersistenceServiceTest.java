package scheduling.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;
import scheduling.scheduling.ai.dto.AiSchedulingResponseDto;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.ShiftAssignmentRepository;

class AiAssignmentPersistenceServiceTest {

    private final ShiftAssignmentRepository repository = mock(ShiftAssignmentRepository.class);
    private final AiAssignmentPersistenceService service = new AiAssignmentPersistenceService(repository);

    @Test
    void deletesUnlockedAutoAndPreservesManualAndLocked() {
        SchedulePeriod period = period(5L);

        WorkShift shift = workShift(100L, period, shiftType(1L), LocalDate.of(2026, 9, 21), 2);
        ShiftAssignment unlockedAuto = assignment(1L, shift, employee(10L), "ASSIGNED", "AUTO", false);
        ShiftAssignment unlockedManual = assignment(2L, shift, employee(10L), "ASSIGNED", "MANUAL", false);
        ShiftAssignment lockedAuto = assignment(3L, shift, employee(10L), "ASSIGNED", "AUTO", true);
        when(repository.listForPeriod(5L)).thenReturn(List.of(unlockedAuto, unlockedManual, lockedAuto));

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = snapshot(period, shift);
        AiSchedulingResponseDto response = successResponse(List.of());

        ApplyResult result = service.apply(period, snapshot, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShiftAssignment>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).deleteAll(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue()).hasSize(1);
        assertThat(deleteCaptor.getValue().get(0).getId()).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShiftAssignment>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saveCaptor.capture());
        assertThat(saveCaptor.getValue()).isEmpty();
        assertThat(result.inserted()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(0);
    }

    @Test
    void insertsAiAssignmentsAsAutoAssignedUnlocked() {
        SchedulePeriod period = period(5L);
        ShiftType day = shiftType(1L);
        WorkShift shift100 = workShift(100L, period, day, LocalDate.of(2026, 9, 21), 2);
        WorkShift shift101 = workShift(101L, period, day, LocalDate.of(2026, 9, 22), 2);
        when(repository.listForPeriod(5L)).thenReturn(List.of());

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = snapshotOf(
                List.of(employee(10L), employee(11L)),
                List.of(day),
                List.of(shift100, shift101),
                List.of());
        AiSchedulingResponseDto response = successResponse(List.of(
                new AiSchedulingResponseDto.AssignmentDto(10L, 100L),
                new AiSchedulingResponseDto.AssignmentDto(11L, 101L)));

        ApplyResult result = service.apply(period, snapshot, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShiftAssignment>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saveCaptor.capture());
        assertThat(saveCaptor.getValue()).hasSize(2);
        for (ShiftAssignment row : saveCaptor.getValue()) {
            assertThat(row.getAssignedSource()).isEqualTo("AUTO");
            assertThat(row.getAssignmentStatus()).isEqualTo("ASSIGNED");
            assertThat(row.getLocked()).isEqualTo(false);
        }
        assertThat(result.inserted()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(0);
    }

    @Test
    void skipsAiAssignmentsCoveredByPreservedPairs() {
        SchedulePeriod period = period(5L);
        WorkShift shift = workShift(100L, period, shiftType(1L), LocalDate.of(2026, 9, 21), 2);
        ShiftAssignment manual = assignment(2L, shift, employee(10L), "ASSIGNED", "MANUAL", false);
        when(repository.listForPeriod(5L)).thenReturn(List.of(manual));

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = snapshot(period, shift);
        AiSchedulingResponseDto response = successResponse(
                List.of(new AiSchedulingResponseDto.AssignmentDto(10L, 100L)));

        ApplyResult result = service.apply(period, snapshot, response);

        assertThat(result.inserted()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShiftAssignment>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saveCaptor.capture());
        assertThat(saveCaptor.getValue()).isEmpty();
    }

    @Test
    void rejectsUnknownEmployee() {
        SchedulePeriod period = period(5L);
        WorkShift shift = workShift(100L, period, shiftType(1L), LocalDate.of(2026, 9, 21), 2);
        when(repository.listForPeriod(5L)).thenReturn(List.of());

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = snapshot(period, shift);
        AiSchedulingResponseDto response = successResponse(
                List.of(new AiSchedulingResponseDto.AssignmentDto(99L, 100L)));

        assertThatThrownBy(() -> service.apply(period, snapshot, response))
                .isInstanceOf(AiApplyException.class)
                .hasMessageContaining("employee");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void rejectsUnknownShift() {
        SchedulePeriod period = period(5L);
        WorkShift shift = workShift(100L, period, shiftType(1L), LocalDate.of(2026, 9, 21), 2);
        when(repository.listForPeriod(5L)).thenReturn(List.of());

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = snapshot(period, shift);
        AiSchedulingResponseDto response = successResponse(
                List.of(new AiSchedulingResponseDto.AssignmentDto(10L, 999L)));

        assertThatThrownBy(() -> service.apply(period, snapshot, response))
                .isInstanceOf(AiApplyException.class)
                .hasMessageContaining("shift");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void rejectsDuplicateAiPairs() {
        SchedulePeriod period = period(5L);
        WorkShift shift = workShift(100L, period, shiftType(1L), LocalDate.of(2026, 9, 21), 2);
        when(repository.listForPeriod(5L)).thenReturn(List.of());

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = snapshot(period, shift);
        AiSchedulingResponseDto response = successResponse(List.of(
                new AiSchedulingResponseDto.AssignmentDto(10L, 100L),
                new AiSchedulingResponseDto.AssignmentDto(10L, 100L)));

        assertThatThrownBy(() -> service.apply(period, snapshot, response))
                .isInstanceOf(AiApplyException.class)
                .hasMessageContaining("Duplicate");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void rejectsOverstaffedShift() {
        SchedulePeriod period = period(5L);
        WorkShift shift = workShift(100L, period, shiftType(1L), LocalDate.of(2026, 9, 21), 1);
        when(repository.listForPeriod(5L)).thenReturn(List.of());

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = snapshotOf(
                List.of(employee(10L), employee(11L)),
                List.of(shift.getShiftType()),
                List.of(shift),
                List.of());
        AiSchedulingResponseDto response = successResponse(List.of(
                new AiSchedulingResponseDto.AssignmentDto(10L, 100L),
                new AiSchedulingResponseDto.AssignmentDto(11L, 100L)));

        assertThatThrownBy(() -> service.apply(period, snapshot, response))
                .isInstanceOf(AiApplyException.class)
                .hasMessageContaining("required staff");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void rejectsOverlappingPreservedAssignment() {
        SchedulePeriod period = period(5L);
        LocalDate date = LocalDate.of(2026, 9, 21);
        WorkShift morning = workShift(100L, period, shiftType(1L), date, 2);
        WorkShift midday = workShift(
                101L, period, shiftType(2L, LocalTime.of(12, 0), LocalTime.of(20, 0), "8.0"), date, 2);
        ShiftAssignment preserved = assignment(2L, morning, employee(10L), "ASSIGNED", "MANUAL", false);
        when(repository.listForPeriod(5L)).thenReturn(List.of(preserved));

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = snapshotOf(
                List.of(employee(10L)),
                List.of(morning.getShiftType(), midday.getShiftType()),
                List.of(morning, midday),
                List.of());
        AiSchedulingResponseDto response = successResponse(
                List.of(new AiSchedulingResponseDto.AssignmentDto(10L, 101L)));

        assertThatThrownBy(() -> service.apply(period, snapshot, response))
                .isInstanceOf(AiApplyException.class)
                .hasMessageContaining("overlaps");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void rejectsApprovedLeaveConflict() {
        SchedulePeriod period = period(5L);
        LocalDate date = LocalDate.of(2026, 9, 21);
        WorkShift shift = workShift(100L, period, shiftType(1L), date, 2);
        when(repository.listForPeriod(5L)).thenReturn(List.of());

        LeaveRequest leave = leave(7L, employee(10L), date.minusDays(1), date.plusDays(1));
        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = snapshotOf(
                List.of(employee(10L)),
                List.of(shift.getShiftType()),
                List.of(shift),
                List.of(leave));
        AiSchedulingResponseDto response = successResponse(
                List.of(new AiSchedulingResponseDto.AssignmentDto(10L, 100L)));

        assertThatThrownBy(() -> service.apply(period, snapshot, response))
                .isInstanceOf(AiApplyException.class)
                .hasMessageContaining("leave");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void rejectsDailyHoursExceeded() {
        SchedulePeriod period = period(5L);
        LocalDate date = LocalDate.of(2026, 9, 21);
        WorkShift early = workShift(
                100L, period, shiftType(1L, LocalTime.of(0, 0), LocalTime.of(8, 0), "8.0"), date, 2);
        WorkShift late = workShift(
                101L, period, shiftType(2L, LocalTime.of(8, 0), LocalTime.of(16, 0), "8.0"), date, 2);
        when(repository.listForPeriod(5L)).thenReturn(List.of());

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = snapshotOf(
                List.of(employee(10L)),
                List.of(early.getShiftType(), late.getShiftType()),
                List.of(early, late),
                List.of());
        AiSchedulingResponseDto response = successResponse(List.of(
                new AiSchedulingResponseDto.AssignmentDto(10L, 100L),
                new AiSchedulingResponseDto.AssignmentDto(10L, 101L)));

        assertThatThrownBy(() -> service.apply(period, snapshot, response))
                .isInstanceOf(AiApplyException.class)
                .hasMessageContaining("work hours");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void wrapsUniqueViolationAsApplyException() {
        SchedulePeriod period = period(5L);
        WorkShift shift = workShift(100L, period, shiftType(1L), LocalDate.of(2026, 9, 21), 2);
        when(repository.listForPeriod(5L)).thenReturn(List.of());
        when(repository.saveAll(any())).thenThrow(new DataIntegrityViolationException("dup"));

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = snapshot(period, shift);
        AiSchedulingResponseDto response = successResponse(
                List.of(new AiSchedulingResponseDto.AssignmentDto(10L, 100L)));

        assertThatThrownBy(() -> service.apply(period, snapshot, response))
                .isInstanceOf(AiApplyException.class)
                .hasMessageContaining("uniqueness");
    }

    private static SchedulePeriod period(Long id) {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Sep 2026")
                .startDate(LocalDate.of(2026, 9, 21))
                .endDate(LocalDate.of(2026, 9, 27))
                .versionNo(1)
                .status("DRAFT")
                .build();
        period.setId(id);
        return period;
    }

    private static Employee employee(Long id) {
        Employee employee = Employee.builder()
                .employeeCode("EMP" + id)
                .fullName("Employee " + id)
                .status("ACTIVE")
                .build();
        employee.setId(id);
        return employee;
    }

    private static ShiftType shiftType(Long id) {
        return shiftType(id, LocalTime.of(8, 0), LocalTime.of(16, 0), "8.0");
    }

    private static ShiftType shiftType(Long id, LocalTime start, LocalTime end, String durationHours) {
        ShiftType shiftType = ShiftType.builder()
                .shiftName("Day " + id + "-" + start)
                .startTime(start)
                .endTime(end)
                .durationHours(new BigDecimal(durationHours))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(5)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .build();
        shiftType.setId(id);
        return shiftType;
    }

    private static WorkShift workShift(
            Long id, SchedulePeriod period, ShiftType shiftType, LocalDate workDate, int requiredStaff) {
        WorkShift shift = WorkShift.builder()
                .workDate(workDate)
                .requiredStaff(requiredStaff)
                .schedulePeriod(period)
                .shiftType(shiftType)
                .build();
        shift.setId(id);
        return shift;
    }

    private static ShiftAssignment assignment(
            Long id,
            WorkShift shift,
            Employee employee,
            String status,
            String source,
            boolean locked) {
        ShiftAssignment assignment = ShiftAssignment.builder()
                .workShift(shift)
                .employee(employee)
                .assignmentStatus(status)
                .assignedSource(source)
                .locked(locked)
                .build();
        assignment.setId(id);
        return assignment;
    }

    private static AiSchedulingPayloadBuilder.PayloadSnapshot snapshot(
            SchedulePeriod period, WorkShift shift) {
        return snapshotOf(
                List.of(employee(10L)),
                List.of(shift.getShiftType()),
                List.of(shift),
                List.of());
    }

    private static AiSchedulingPayloadBuilder.PayloadSnapshot snapshotOf(
            List<Employee> employees,
            List<ShiftType> shiftTypes,
            List<WorkShift> workShifts,
            List<LeaveRequest> leaves) {
        return new AiSchedulingPayloadBuilder.PayloadSnapshot(
                employees,
                shiftTypes,
                workShifts,
                leaves,
                List.of(),
                List.of(),
                List.of(),
                new AiSchedulingRequestDto.RulesDto(
                        new AiSchedulingRequestDto.HardRulesDto(8, 12, 6),
                        new AiSchedulingRequestDto.QuotasDto(2, 2, 7),
                        new AiSchedulingRequestDto.SoftWeightsDto(
                                BigDecimal.ONE,
                                BigDecimal.ONE,
                                BigDecimal.ONE,
                                BigDecimal.ONE,
                                BigDecimal.ONE)),
                new AiSchedulingRequestDto.SolverOptionsDto(30, 1, 8));
    }

    private static LeaveRequest leave(Long id, Employee employee, LocalDate start, LocalDate end) {
        LeaveRequest leave = LeaveRequest.builder()
                .employee(employee)
                .startDate(start)
                .endDate(end)
                .status("APPROVED")
                .build();
        leave.setId(id);
        return leave;
    }

    private static AiSchedulingResponseDto successResponse(
            List<AiSchedulingResponseDto.AssignmentDto> assignments) {
        return new AiSchedulingResponseDto(
                "1.0",
                900L,
                5L,
                "SUCCESS",
                "OPTIMAL",
                assignments,
                BigDecimal.ZERO,
                Map.of(),
                List.of(),
                List.of(),
                null,
                new AiSchedulingResponseDto.StatisticsDto(BigDecimal.ZERO, 0, 0));
    }
}
