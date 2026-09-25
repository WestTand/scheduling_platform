package scheduling.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.entity.Preference;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.SchedulingRule;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.SchedulingRuleRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

class AiSchedulingPayloadBuilderTest {

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final ShiftTypeRepository shiftTypeRepository = mock(ShiftTypeRepository.class);
    private final WorkShiftRepository workShiftRepository = mock(WorkShiftRepository.class);
    private final LeaveRequestRepository leaveRequestRepository = mock(LeaveRequestRepository.class);
    private final PreferenceRepository preferenceRepository = mock(PreferenceRepository.class);
    private final ShiftAssignmentRepository shiftAssignmentRepository = mock(ShiftAssignmentRepository.class);
    private final SchedulingRuleRepository schedulingRuleRepository = mock(SchedulingRuleRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiSchedulingPayloadBuilder builder = new AiSchedulingPayloadBuilder(
            employeeRepository,
            shiftTypeRepository,
            workShiftRepository,
            leaveRequestRepository,
            preferenceRepository,
            shiftAssignmentRepository,
            schedulingRuleRepository,
            objectMapper);

    @Test
    void buildsPayloadFromDraftPeriodData() {
        Fixture fixture = happyPath();

        AiSchedulingRequestDto payload = builder.build(fixture.period(), 900L, null);

        assertThat(payload.schemaVersion()).isEqualTo("1.0");
        assertThat(payload.runId()).isEqualTo(900L);
        assertThat(payload.period().periodId()).isEqualTo(5L);
        assertThat(payload.period().startDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(payload.period().endDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(payload.period().versionNo()).isEqualTo(1);
        assertThat(payload.period().timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(payload.employees())
                .extracting(AiSchedulingRequestDto.EmployeeDto::employeeId)
                .containsExactly(10L);
        assertThat(payload.shiftTypes()).extracting(AiSchedulingRequestDto.ShiftTypeDto::quotaCategory).containsExactly((String) null);
        assertThat(payload.shiftTypes())
                .extracting(AiSchedulingRequestDto.ShiftTypeDto::durationMinutes)
                .containsExactly(480);
        assertThat(payload.shiftTypes())
                .extracting(AiSchedulingRequestDto.ShiftTypeDto::overnight)
                .containsExactly(false);
        assertThat(payload.shiftTypes())
                .extracting(AiSchedulingRequestDto.ShiftTypeDto::heavy)
                .containsExactly(false);
        assertThat(payload.shiftTypes())
                .extracting(AiSchedulingRequestDto.ShiftTypeDto::minStaff)
                .containsExactly(1);
        assertThat(payload.shiftTypes())
                .extracting(AiSchedulingRequestDto.ShiftTypeDto::maxStaff)
                .containsExactly(2);
        assertThat(payload.workShifts()).extracting(AiSchedulingRequestDto.WorkShiftDto::startsAt).containsExactly(fixture.startsAt());
        assertThat(payload.approvedLeaves()).extracting(AiSchedulingRequestDto.ApprovedLeaveDto::leaveRequestId).containsExactly(700L);
        assertThat(payload.approvedLeaves())
                .extracting(AiSchedulingRequestDto.ApprovedLeaveDto::employeeId)
                .containsExactly(10L);
        assertThat(payload.approvedLeaves())
                .extracting(AiSchedulingRequestDto.ApprovedLeaveDto::startDate)
                .containsExactly(LocalDate.of(2026, 9, 15));
        assertThat(payload.approvedLeaves())
                .extracting(AiSchedulingRequestDto.ApprovedLeaveDto::endDate)
                .containsExactly(LocalDate.of(2026, 9, 16));
        assertThat(payload.preferences()).extracting(AiSchedulingRequestDto.PreferenceDto::preferenceId).containsExactly(800L);
        assertThat(payload.preferences())
                .extracting(AiSchedulingRequestDto.PreferenceDto::employeeId)
                .containsExactly(10L);
        assertThat(payload.preferences())
                .extracting(AiSchedulingRequestDto.PreferenceDto::shiftTypeId)
                .containsExactly(20L);
        assertThat(payload.preferences())
                .extracting(AiSchedulingRequestDto.PreferenceDto::preferenceDate)
                .containsExactly(LocalDate.of(2026, 9, 15));
        assertThat(payload.preferences())
                .extracting(AiSchedulingRequestDto.PreferenceDto::preferenceType)
                .containsExactly("WANT");
        assertThat(payload.preferences())
                .extracting(AiSchedulingRequestDto.PreferenceDto::priority)
                .containsExactly(1);
        assertThat(payload.lockedAssignments())
                .containsExactly(new AiSchedulingRequestDto.LockedAssignmentDto(600L, 10L, 30L));
        assertThat(payload.boundaryAssignments()).isEmpty();
        assertThat(payload.rules().hardRules().minRestHoursBetweenShifts()).isEqualTo(12);
        assertThat(payload.rules().hardRules().maxWorkHoursPerDay()).isEqualTo(12);
        assertThat(payload.rules().hardRules().maxShiftsPerWeek()).isEqualTo(6);
        assertThat(payload.rules().quotas().overtimeShiftsPerWeek()).isEqualTo(1);
        assertThat(payload.rules().quotas().bathingShiftsPerWeek()).isEqualTo(2);
        assertThat(payload.rules().quotas().onCallEveryNDays()).isEqualTo(6);
        assertThat(payload.rules().softWeights().fairness()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(payload.rules().softWeights().preference()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(payload.rules().softWeights().weekend()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(payload.rules().softWeights().heavyShift()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(payload.rules().softWeights().workHourBalance()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(payload.solverOptions().timeLimitSeconds()).isEqualTo(30);
        assertThat(payload.solverOptions().randomSeed()).isEqualTo(1);
        assertThat(payload.solverOptions().numWorkers()).isEqualTo(8);
    }

    @Test
    void buildPayloadRestoresVietnamOffsetForDatabaseNormalizedShiftWindows() {
        Fixture fixture = happyPath();
        fixture.workShift().setWorkDate(LocalDate.of(2042, 8, 27));
        fixture.workShift().setStartsAt(OffsetDateTime.parse("2042-08-26T17:30:00Z"));
        fixture.workShift().setEndsAt(OffsetDateTime.parse("2042-08-27T01:00:00Z"));

        AiSchedulingRequestDto payload = builder.build(fixture.period(), 900L, null);

        assertThat(payload.workShifts())
                .extracting(AiSchedulingRequestDto.WorkShiftDto::startsAt)
                .containsExactly(OffsetDateTime.parse("2042-08-27T00:30:00+07:00"));
        assertThat(payload.workShifts())
                .extracting(AiSchedulingRequestDto.WorkShiftDto::endsAt)
                .containsExactly(OffsetDateTime.parse("2042-08-27T08:00:00+07:00"));
    }

    @Test
    void rejectsTimeLimitZeroBeforeCallingAiSchema() {
        Fixture fixture = happyPath();

        assertThatThrownBy(() -> builder.build(
                        fixture.period(), 900L, new AiSchedulingRequestDto.SolverOptionsDto(0, 1, 8)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Invalid solver options");
                });
    }

    @Test
    void rejectsInactiveShiftTypeReferencedByWorkShift() {
        Fixture fixture = happyPath();
        ShiftType inactiveType = ShiftType.builder()
                .shiftName("Ca dem")
                .startTime(LocalTime.of(22, 0))
                .endTime(LocalTime.of(6, 0))
                .durationHours(new BigDecimal("8.0"))
                .overnight(true)
                .heavy(false)
                .minStaff(1)
                .maxStaff(2)
                .quotaCategory("REGULAR")
                .status("INACTIVE")
                .shiftCode("NIGHT")
                .build();
        inactiveType.setId(21L);
        WorkShift badShift = WorkShift.builder()
                .workDate(LocalDate.of(2026, 9, 16))
                .requiredStaff(1)
                .schedulePeriod(fixture.period())
                .shiftType(inactiveType)
                .startsAt(fixture.startsAt())
                .endsAt(fixture.startsAt().plusHours(8))
                .build();
        badShift.setId(31L);
        when(workShiftRepository.findForAiPayload(5L)).thenReturn(List.of(badShift));
        when(shiftAssignmentRepository.findFixedForPeriod(5L)).thenReturn(List.of());

        assertThatThrownBy(() -> builder.build(fixture.period(), 900L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Work shift 31 uses inactive shift type 21");
                });
    }

    @Test
    void rejectsFixedLockedAssignmentReferencingInactiveEmployee() {
        Fixture fixture = happyPath();
        Employee inactiveEmployee = Employee.builder()
                .employeeCode("EMP099")
                .fullName("Inactive Employee")
                .status("INACTIVE")
                .build();
        inactiveEmployee.setId(11L);
        ShiftAssignment badFixed = ShiftAssignment.builder()
                .workShift(fixture.workShift())
                .employee(inactiveEmployee)
                .assignmentStatus("ASSIGNED")
                .assignedSource("AUTO")
                .locked(true)
                .build();
        badFixed.setId(601L);
        when(shiftAssignmentRepository.findFixedForPeriod(5L)).thenReturn(List.of(badFixed));

        assertThatThrownBy(() -> builder.build(fixture.period(), 900L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason())
                            .isEqualTo("Preserved assignment 601 references inactive scheduling data");
                });
    }

    @Test
    void excludesCancelledAndRejectedPreferences() {
        Fixture fixture = happyPath();
        Preference cancelled = Preference.builder()
                .employee(fixture.employee())
                .shiftType(fixture.shiftType())
                .preferenceDate(LocalDate.of(2026, 9, 16))
                .preferenceType("AVOID")
                .priority(2)
                .status("CANCELLED")
                .build();
        cancelled.setId(801L);
        Preference rejected = Preference.builder()
                .employee(fixture.employee())
                .shiftType(fixture.shiftType())
                .preferenceDate(LocalDate.of(2026, 9, 17))
                .preferenceType("WANT")
                .priority(1)
                .status("REJECTED")
                .build();
        rejected.setId(802L);
        when(preferenceRepository.findActiveInRange(
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)))
                .thenReturn(List.of(fixture.preference(), cancelled, rejected));

        AiSchedulingRequestDto payload = builder.build(fixture.period(), 900L, null);

        assertThat(payload.preferences()).extracting(AiSchedulingRequestDto.PreferenceDto::preferenceId).containsExactly(800L);
    }

    @Test
    void rejectsMissingRequiredRule() {
        Fixture fixture = happyPath();
        when(schedulingRuleRepository.findByRuleCode("MAX_WORK_HOURS_PER_DAY"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> builder.build(fixture.period(), 900L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason())
                            .isEqualTo("Missing scheduling rule: MAX_WORK_HOURS_PER_DAY");
                });
    }

    @Test
    void validateInputsAcceptsValidPeriodData() {
        Fixture fixture = happyPath();

        assertThatCode(() -> builder.validateInputs(fixture.period(), null)).doesNotThrowAnyException();
    }

    @Test
    void weightsSnapshotMatchesBuiltPayload() {
        Fixture fixture = happyPath();

        AiSchedulingRequestDto payload = builder.build(fixture.period(), 900L, null);
        java.util.Map<String, Object> snapshot = builder.weightsSnapshot(fixture.period(), null);

        assertThat(snapshot).containsKeys("rules", "solverOptions");
        assertThat(snapshot.get("rules")).isEqualTo(payload.rules());
        assertThat(snapshot.get("solverOptions")).isEqualTo(payload.solverOptions());
    }

    @Test
    void rejectsShiftTypeWithMissingSchedulingData() {
        Fixture fixture = happyPath();
        ShiftType badType = ShiftType.builder()
                .shiftName("Ca ngay")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(null)
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(2)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .shiftCode("DAY")
                .build();
        badType.setId(20L);
        when(shiftTypeRepository.findByStatus("ACTIVE")).thenReturn(List.of(badType));

        assertThatThrownBy(() -> builder.build(fixture.period(), 900L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason())
                            .isEqualTo("Shift type 20 has incomplete scheduling data");
                });
    }

    @Test
    void rejectsWorkShiftWithMissingTimeWindow() {
        Fixture fixture = happyPath();
        WorkShift badShift = WorkShift.builder()
                .workDate(LocalDate.of(2026, 9, 16))
                .requiredStaff(1)
                .schedulePeriod(fixture.period())
                .shiftType(fixture.shiftType())
                .startsAt(null)
                .endsAt(fixture.startsAt().plusHours(8))
                .build();
        badShift.setId(32L);
        when(workShiftRepository.findForAiPayload(5L)).thenReturn(List.of(badShift));
        when(shiftAssignmentRepository.findFixedForPeriod(5L)).thenReturn(List.of());

        assertThatThrownBy(() -> builder.build(fixture.period(), 900L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Work shift 32 is missing time window");
                });
    }

    @Test
    void rejectsFixedLockedAssignmentReferencingInvalidWorkShift() {
        Fixture fixture = happyPath();
        WorkShift unknownShift = WorkShift.builder()
                .workDate(LocalDate.of(2026, 9, 17))
                .requiredStaff(1)
                .schedulePeriod(fixture.period())
                .shiftType(fixture.shiftType())
                .startsAt(fixture.startsAt())
                .endsAt(fixture.startsAt().plusHours(8))
                .build();
        unknownShift.setId(999L);
        ShiftAssignment badFixed = ShiftAssignment.builder()
                .workShift(unknownShift)
                .employee(fixture.employee())
                .assignmentStatus("ASSIGNED")
                .assignedSource("MANUAL")
                .locked(false)
                .build();
        badFixed.setId(602L);
        when(shiftAssignmentRepository.findFixedForPeriod(5L)).thenReturn(List.of(badFixed));

        assertThatThrownBy(() -> builder.build(fixture.period(), 900L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason())
                            .isEqualTo("Preserved assignment 602 references inactive scheduling data");
                });
    }

    @Test
    void dropsLeavesAndPreferencesForInactiveEmployees() {
        Fixture fixture = happyPath();
        Employee outsider = Employee.builder()
                .employeeCode("EMP099")
                .fullName("Inactive Employee")
                .status("INACTIVE")
                .build();
        outsider.setId(11L);
        LeaveRequest strangerLeave = LeaveRequest.builder()
                .employee(outsider)
                .startDate(LocalDate.of(2026, 9, 15))
                .endDate(LocalDate.of(2026, 9, 16))
                .status("APPROVED")
                .build();
        strangerLeave.setId(701L);
        Preference strangerPreference = Preference.builder()
                .employee(outsider)
                .shiftType(fixture.shiftType())
                .preferenceDate(LocalDate.of(2026, 9, 15))
                .preferenceType("WANT")
                .priority(1)
                .status("PENDING")
                .build();
        strangerPreference.setId(803L);
        when(leaveRequestRepository.findApprovedOverlapping(
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)))
                .thenReturn(List.of(fixture.leave(), strangerLeave));
        when(preferenceRepository.findActiveInRange(
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)))
                .thenReturn(List.of(fixture.preference(), strangerPreference));

        AiSchedulingRequestDto payload = builder.build(fixture.period(), 900L, null);

        assertThat(payload.approvedLeaves())
                .extracting(AiSchedulingRequestDto.ApprovedLeaveDto::leaveRequestId)
                .containsExactly(700L);
        assertThat(payload.preferences())
                .extracting(AiSchedulingRequestDto.PreferenceDto::preferenceId)
                .containsExactly(800L);
    }

    @Test
    void includesManualAssignmentsAsFixedInputs() {
        Fixture fixture = happyPath();
        ShiftAssignment manualAssignment = ShiftAssignment.builder()
                .workShift(fixture.workShift())
                .employee(fixture.employee())
                .assignmentStatus("ASSIGNED")
                .assignedSource("MANUAL")
                .locked(false)
                .build();
        manualAssignment.setId(601L);
        when(shiftAssignmentRepository.findFixedForPeriod(5L))
                .thenReturn(List.of(fixture.fixedAssignment(), manualAssignment));

        AiSchedulingRequestDto payload = builder.build(fixture.period(), 900L, null);

        assertThat(payload.lockedAssignments())
                .containsExactly(
                        new AiSchedulingRequestDto.LockedAssignmentDto(600L, 10L, 30L),
                        new AiSchedulingRequestDto.LockedAssignmentDto(601L, 10L, 30L));
    }

    @Test
    void rejectsFixedAssignmentReferencingInactiveEmployee() {
        Fixture fixture = happyPath();
        Employee inactiveEmployee = Employee.builder()
                .employeeCode("EMP099")
                .fullName("Inactive Employee")
                .status("INACTIVE")
                .build();
        inactiveEmployee.setId(11L);
        ShiftAssignment manualAssignment = ShiftAssignment.builder()
                .workShift(fixture.workShift())
                .employee(inactiveEmployee)
                .assignmentStatus("ASSIGNED")
                .assignedSource("MANUAL")
                .locked(false)
                .build();
        manualAssignment.setId(603L);
        when(shiftAssignmentRepository.findFixedForPeriod(5L)).thenReturn(List.of(manualAssignment));

        assertThatThrownBy(() -> builder.build(fixture.period(), 900L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason())
                            .isEqualTo("Preserved assignment 603 references inactive scheduling data");
                });
    }

    @Test
    void includesAssignmentsBeforePeriodAsBoundaryAssignments() {
        Fixture fixture = happyPath();
        WorkShift previousShift = WorkShift.builder()
                .workDate(fixture.period().getStartDate().minusDays(1))
                .requiredStaff(1)
                .schedulePeriod(SchedulePeriod.builder()
                        .periodName("Previous Period")
                        .startDate(fixture.period().getStartDate().minusDays(7))
                        .endDate(fixture.period().getStartDate().minusDays(1))
                        .status("PUBLISHED")
                        .build())
                .shiftType(fixture.shiftType())
                .startsAt(OffsetDateTime.of(fixture.period().getStartDate().minusDays(1), LocalTime.of(20, 0), ZoneOffset.ofHours(7)))
                .endsAt(OffsetDateTime.of(fixture.period().getStartDate(), LocalTime.of(4, 0), ZoneOffset.ofHours(7)))
                .build();
        previousShift.setId(901L);
        ShiftAssignment previousAssignment = ShiftAssignment.builder()
                .employee(fixture.employee())
                .workShift(previousShift)
                .assignmentStatus("ASSIGNED")
                .assignedSource("AUTO")
                .locked(false)
                .build();
        previousAssignment.setId(701L);
        when(shiftAssignmentRepository.findBoundaryForAiPayload(
                eq(5L), eq(fixture.period().getStartDate().minusDays(1)), eq(fixture.period().getEndDate().plusDays(1))))
                .thenReturn(List.of(previousAssignment));

        AiSchedulingRequestDto payload = builder.build(fixture.period(), 900L, null);

        assertThat(payload.boundaryAssignments()).containsExactly(new AiSchedulingRequestDto.BoundaryAssignmentDto(
                fixture.employee().getId(), fixture.shiftType().getId(), previousShift.getStartsAt(), previousShift.getEndsAt()));
    }

    private Fixture happyPath() {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Week 38")
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 20))
                .versionNo(1)
                .status("DRAFT")
                .build();
        period.setId(5L);

        Employee employee = Employee.builder()
                .employeeCode("EMP001")
                .fullName("Nguyen Van A")
                .status("ACTIVE")
                .build();
        employee.setId(10L);

        ShiftType shiftType = ShiftType.builder()
                .shiftName("Ca ngay")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(new BigDecimal("8.0"))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(2)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .shiftCode("DAY")
                .build();
        shiftType.setId(20L);

        OffsetDateTime startsAt =
                OffsetDateTime.of(2026, 9, 15, 8, 0, 0, 0, ZoneOffset.of("+07:00"));
        OffsetDateTime endsAt = startsAt.plusHours(8);

        WorkShift workShift = WorkShift.builder()
                .workDate(LocalDate.of(2026, 9, 15))
                .requiredStaff(2)
                .schedulePeriod(period)
                .shiftType(shiftType)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
        workShift.setId(30L);

        LeaveRequest leave = LeaveRequest.builder()
                .employee(employee)
                .startDate(LocalDate.of(2026, 9, 15))
                .endDate(LocalDate.of(2026, 9, 16))
                .status("APPROVED")
                .build();
        leave.setId(700L);

        Preference preference = Preference.builder()
                .employee(employee)
                .shiftType(shiftType)
                .preferenceDate(LocalDate.of(2026, 9, 15))
                .preferenceType("WANT")
                .priority(1)
                .status("PENDING")
                .build();
        preference.setId(800L);

        ShiftAssignment fixedAssignment = ShiftAssignment.builder()
                .workShift(workShift)
                .employee(employee)
                .assignmentStatus("ASSIGNED")
                .assignedSource("AUTO")
                .locked(true)
                .build();
        fixedAssignment.setId(600L);

        when(employeeRepository.findSchedulableForAi()).thenReturn(List.of(employee));
        when(shiftTypeRepository.findByStatus("ACTIVE")).thenReturn(List.of(shiftType));
        when(workShiftRepository.findForAiPayload(5L)).thenReturn(List.of(workShift));
        when(leaveRequestRepository.findApprovedOverlapping(
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)))
                .thenReturn(List.of(leave));
        when(preferenceRepository.findActiveInRange(
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)))
                .thenReturn(List.of(preference));
        when(shiftAssignmentRepository.findFixedForPeriod(5L)).thenReturn(List.of(fixedAssignment));
        stubRules();

        return new Fixture(period, employee, shiftType, workShift, leave, preference, fixedAssignment, startsAt);
    }

    private void stubRules() {
        stubHardRule("MIN_REST_HOURS_BETWEEN_SHIFTS", 12);
        stubHardRule("MAX_WORK_HOURS_PER_DAY", 12);
        stubHardRule("MAX_SHIFTS_PER_WEEK", 6);
        stubHardRule("OVERTIME_SHIFTS_PER_WEEK", 1);
        stubHardRule("BATHING_SHIFTS_PER_WEEK", 2);
        stubHardRule("ON_CALL_EVERY_N_DAYS", 6);
        stubSoftRule("FAIRNESS_WEIGHT", new BigDecimal("10.00"));
        stubSoftRule("PREFERENCE_WEIGHT", new BigDecimal("5.00"));
        stubSoftRule("WEEKEND_WEIGHT", new BigDecimal("5.00"));
        stubSoftRule("HEAVY_SHIFT_WEIGHT", new BigDecimal("8.00"));
        stubSoftRule("WORK_HOUR_BALANCE_WEIGHT", new BigDecimal("10.00"));
    }

    private void stubHardRule(String code, int value) {
        SchedulingRule rule = SchedulingRule.builder()
                .ruleCode(code)
                .ruleName(code)
                .ruleType("HARD")
                .ruleValueJson("{\"value\":" + value + "}")
                .active(true)
                .build();
        when(schedulingRuleRepository.findByRuleCode(code)).thenReturn(Optional.of(rule));
    }

    private void stubSoftRule(String code, BigDecimal weight) {
        SchedulingRule rule = SchedulingRule.builder()
                .ruleCode(code)
                .ruleName(code)
                .ruleType("SOFT")
                .ruleValueJson("{\"enabled\":true}")
                .weight(weight)
                .active(true)
                .build();
        when(schedulingRuleRepository.findByRuleCode(code)).thenReturn(Optional.of(rule));
    }

    private record Fixture(
            SchedulePeriod period,
            Employee employee,
            ShiftType shiftType,
            WorkShift workShift,
            LeaveRequest leave,
            Preference preference,
            ShiftAssignment fixedAssignment,
            OffsetDateTime startsAt) {}
}
