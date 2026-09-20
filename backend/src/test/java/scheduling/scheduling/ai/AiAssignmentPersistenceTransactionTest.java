package scheduling.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;
import scheduling.scheduling.ai.dto.AiSchedulingResponseDto;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

/**
 * Proves {@link AiAssignmentPersistenceService#apply} is all-or-nothing: the
 * unlocked AUTO delete plus a failing insert roll back together.
 *
 * <p>NOT {@code @Transactional}: the test must read committed state after the
 * service transaction rolls back, otherwise the assertion would be meaningless.
 * Fixtures use far-future 2032 dates and unique names, disjoint from every
 * other test's range; cleanup deletes only created rows by ID.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-with-at-least-32-characters",
        "app.auth.default-employee-password=configured-default",
})
class AiAssignmentPersistenceTransactionTest {

    private static final String MANAGER_USERNAME = "ai-rollback-manager";
    private static final String EMPLOYEE_USERNAME = "ai-rollback-employee";
    private static final String EMPLOYEE_CODE = "AI_RB_001";
    private static final String SHIFT_NAME = "AI Rollback Day";
    private static final String SHIFT_CODE = "AI_RB_DAY";
    private static final String PERIOD_NAME = "AI Rollback Period";
    private static final LocalDate PERIOD_START = LocalDate.of(2032, 5, 3);
    private static final LocalDate PERIOD_END = LocalDate.of(2032, 5, 9);
    private static final long UNKNOWN_EMPLOYEE_ID = 999999L;

    @Autowired
    private AiAssignmentPersistenceService persistence;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SchedulePeriodRepository schedulePeriodRepository;

    @Autowired
    private ShiftTypeRepository shiftTypeRepository;

    @Autowired
    private WorkShiftRepository workShiftRepository;

    @Autowired
    private ShiftAssignmentRepository shiftAssignmentRepository;

    private final List<Long> assignmentIds = new ArrayList<>();
    private final List<Long> workShiftIds = new ArrayList<>();
    private Long periodId;
    private Long employeeAccountId;
    private Long managerId;
    private Long employeeId;
    private Long shiftTypeId;

    @BeforeEach
    void setUp() {
        deleteLeftoversByKeys();
        assignmentIds.clear();
        workShiftIds.clear();
    }

    @AfterEach
    void tearDown() {
        for (Long id : assignmentIds) {
            shiftAssignmentRepository.findById(id).ifPresent(shiftAssignmentRepository::delete);
        }
        for (Long id : workShiftIds) {
            workShiftRepository.findById(id).ifPresent(workShiftRepository::delete);
        }
        if (periodId != null) {
            schedulePeriodRepository.findById(periodId).ifPresent(schedulePeriodRepository::delete);
        }
        if (employeeAccountId != null) {
            accountRepository.findById(employeeAccountId).ifPresent(accountRepository::delete);
        }
        if (managerId != null) {
            accountRepository.findById(managerId).ifPresent(accountRepository::delete);
        }
        if (employeeId != null) {
            employeeRepository.findById(employeeId).ifPresent(employeeRepository::delete);
        }
        if (shiftTypeId != null) {
            shiftTypeRepository.findById(shiftTypeId).ifPresent(shiftTypeRepository::delete);
        }
        periodId = null;
        employeeAccountId = null;
        managerId = null;
        employeeId = null;
        shiftTypeId = null;
        assignmentIds.clear();
        workShiftIds.clear();
    }

    @Test
    void failedApplyRollsBackAssignmentChanges() {
        Account manager = accountRepository.save(Account.builder()
                .username(MANAGER_USERNAME)
                .passwordHash("hash")
                .role(AccountRole.MANAGER)
                .isActive(true)
                .status(AccountStatus.ACTIVE)
                .build());
        managerId = manager.getId();

        Employee employee = employeeRepository.save(Employee.builder()
                .employeeCode(EMPLOYEE_CODE)
                .fullName("AI Rollback Employee")
                .status("ACTIVE")
                .build());
        employeeId = employee.getId();

        Account employeeAccount = accountRepository.save(Account.builder()
                .username(EMPLOYEE_USERNAME)
                .passwordHash("hash")
                .role(AccountRole.EMPLOYEE)
                .isActive(true)
                .status(AccountStatus.ACTIVE)
                .employee(employee)
                .build());
        employeeAccountId = employeeAccount.getId();

        ShiftType shiftType = shiftTypeRepository.save(ShiftType.builder()
                .shiftName(SHIFT_NAME)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(new BigDecimal("8.0"))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(5)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .shiftCode(SHIFT_CODE)
                .build());
        shiftTypeId = shiftType.getId();

        SchedulePeriod period = schedulePeriodRepository.save(SchedulePeriod.builder()
                .periodName(PERIOD_NAME)
                .startDate(PERIOD_START)
                .endDate(PERIOD_END)
                .status("DRAFT")
                .createdBy(manager)
                .build());
        periodId = period.getId();

        WorkShift shiftA = workShiftRepository.save(WorkShift.builder()
                .workDate(LocalDate.of(2032, 5, 4))
                .requiredStaff(1)
                .schedulePeriod(period)
                .shiftType(shiftType)
                .startsAt(OffsetDateTime.of(LocalDate.of(2032, 5, 4), LocalTime.of(8, 0), ZoneOffset.UTC))
                .endsAt(OffsetDateTime.of(LocalDate.of(2032, 5, 4), LocalTime.of(16, 0), ZoneOffset.UTC))
                .build());
        WorkShift shiftB = workShiftRepository.save(WorkShift.builder()
                .workDate(LocalDate.of(2032, 5, 5))
                .requiredStaff(1)
                .schedulePeriod(period)
                .shiftType(shiftType)
                .startsAt(OffsetDateTime.of(LocalDate.of(2032, 5, 5), LocalTime.of(8, 0), ZoneOffset.UTC))
                .endsAt(OffsetDateTime.of(LocalDate.of(2032, 5, 5), LocalTime.of(16, 0), ZoneOffset.UTC))
                .build());
        workShiftIds.add(shiftA.getId());
        workShiftIds.add(shiftB.getId());

        ShiftAssignment autoAssignment = shiftAssignmentRepository.save(ShiftAssignment.builder()
                .workShift(shiftA)
                .employee(employee)
                .assignmentStatus("ASSIGNED")
                .assignedSource("AUTO")
                .locked(false)
                .build());
        ShiftAssignment manualAssignment = shiftAssignmentRepository.save(ShiftAssignment.builder()
                .workShift(shiftB)
                .employee(employee)
                .assignmentStatus("ASSIGNED")
                .assignedSource("MANUAL")
                .locked(false)
                .build());
        assignmentIds.add(autoAssignment.getId());
        assignmentIds.add(manualAssignment.getId());

        long autoCountBefore = shiftAssignmentRepository.listForPeriod(periodId).stream()
                .filter(row -> "AUTO".equals(row.getAssignedSource()))
                .count();
        assertThat(autoCountBefore).isEqualTo(1);

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = new AiSchedulingPayloadBuilder.PayloadSnapshot(
                List.of(employee),
                List.of(shiftType),
                List.of(shiftA, shiftB),
                List.of(),
                List.of(),
                List.of(manualAssignment),
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

        AiSchedulingResponseDto response = new AiSchedulingResponseDto(
                "1.0",
                900L,
                periodId,
                "SUCCESS",
                "OPTIMAL",
                List.of(new AiSchedulingResponseDto.AssignmentDto(UNKNOWN_EMPLOYEE_ID, shiftA.getId())),
                BigDecimal.ZERO,
                Map.of(),
                List.of(),
                List.of(),
                null,
                new AiSchedulingResponseDto.StatisticsDto(BigDecimal.ZERO, 0, 0));

        AiApplyException thrown = catchThrowableOfType(
                () -> persistence.apply(period, snapshot, response),
                AiApplyException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("unknown employee");

        assertThat(shiftAssignmentRepository.findById(autoAssignment.getId())).isPresent();
        assertThat(shiftAssignmentRepository.findById(manualAssignment.getId())).isPresent();

        List<ShiftAssignment> after = shiftAssignmentRepository.listForPeriod(periodId);
        assertThat(after).hasSize(2);
        assertThat(after.stream()
                        .filter(row -> "AUTO".equals(row.getAssignedSource()))
                        .count())
                .isEqualTo(autoCountBefore);
        assertThat(shiftAssignmentRepository
                .existsByWorkShiftIdAndEmployeeId(shiftA.getId(), UNKNOWN_EMPLOYEE_ID))
                .isFalse();
    }

    private void deleteLeftoversByKeys() {
        List<SchedulePeriod> periods = schedulePeriodRepository.searchForAdmin(null, null, null);
        for (SchedulePeriod period : periods) {
            if (!PERIOD_NAME.equals(period.getPeriodName())) {
                continue;
            }
            shiftAssignmentRepository.deleteAll(shiftAssignmentRepository.listForPeriod(period.getId()));
            workShiftRepository.deleteAll(workShiftRepository.searchForPeriod(period.getId(), null, null));
            schedulePeriodRepository.delete(period);
        }
        accountRepository.findByUsername(EMPLOYEE_USERNAME).ifPresent(accountRepository::delete);
        accountRepository.findByUsername(MANAGER_USERNAME).ifPresent(accountRepository::delete);
        for (Employee employee : employeeRepository.search(EMPLOYEE_CODE, null)) {
            if (EMPLOYEE_CODE.equals(employee.getEmployeeCode())) {
                employeeRepository.delete(employee);
            }
        }
        shiftTypeRepository.findByShiftName(SHIFT_NAME).ifPresent(shiftTypeRepository::delete);
    }
}
