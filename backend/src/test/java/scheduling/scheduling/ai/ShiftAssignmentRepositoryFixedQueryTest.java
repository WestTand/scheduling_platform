package scheduling.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.OptimizationRunRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-with-at-least-32-characters",
        "app.auth.default-employee-password=configured-default",
})
@Transactional
class ShiftAssignmentRepositoryFixedQueryTest {

    @Autowired
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired
    private WorkShiftRepository workShiftRepository;

    @Autowired
    private SchedulePeriodRepository schedulePeriodRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ShiftTypeRepository shiftTypeRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OptimizationRunRepository optimizationRunRepository;

    @Test
    void findFixedForPeriodReturnsOnlyFixedRowsInPeriod() {
        Account manager = accountRepository.save(Account.builder()
                .username("fixed-query-manager")
                .passwordHash("hash")
                .role(AccountRole.MANAGER)
                .build());
        ShiftType shiftType = shiftTypeRepository.save(ShiftType.builder()
                .shiftName("Fixed Query Day")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(new BigDecimal("8.0"))
                .minStaff(1)
                .maxStaff(2)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .shiftCode("FIXED_Q_DAY")
                .build());
        Employee empOne = employeeRepository.save(Employee.builder()
                .employeeCode("FIXED_Q_001")
                .fullName("Fixed Query One")
                .status("ACTIVE")
                .build());
        Employee empTwo = employeeRepository.save(Employee.builder()
                .employeeCode("FIXED_Q_002")
                .fullName("Fixed Query Two")
                .status("ACTIVE")
                .build());
        SchedulePeriod periodA = schedulePeriodRepository.save(SchedulePeriod.builder()
                .periodName("Fixed Query Period A")
                .startDate(LocalDate.of(2031, 2, 3))
                .endDate(LocalDate.of(2031, 2, 9))
                .createdBy(manager)
                .build());
        SchedulePeriod periodB = schedulePeriodRepository.save(SchedulePeriod.builder()
                .periodName("Fixed Query Period B")
                .startDate(LocalDate.of(2031, 2, 10))
                .endDate(LocalDate.of(2031, 2, 16))
                .createdBy(manager)
                .build());
        WorkShift shiftA1 = workShiftRepository.save(WorkShift.builder()
                .workDate(LocalDate.of(2031, 2, 4))
                .requiredStaff(2)
                .schedulePeriod(periodA)
                .shiftType(shiftType)
                .build());
        WorkShift shiftA2 = workShiftRepository.save(WorkShift.builder()
                .workDate(LocalDate.of(2031, 2, 5))
                .requiredStaff(2)
                .schedulePeriod(periodA)
                .shiftType(shiftType)
                .build());
        WorkShift shiftB1 = workShiftRepository.save(WorkShift.builder()
                .workDate(LocalDate.of(2031, 2, 11))
                .requiredStaff(2)
                .schedulePeriod(periodB)
                .shiftType(shiftType)
                .build());

        ShiftAssignment lockedAuto = shiftAssignmentRepository.save(ShiftAssignment.builder()
                .workShift(shiftA1)
                .employee(empOne)
                .assignmentStatus("ASSIGNED")
                .assignedSource("AUTO")
                .locked(true)
                .build());
        ShiftAssignment manualUnlocked = shiftAssignmentRepository.save(ShiftAssignment.builder()
                .workShift(shiftA2)
                .employee(empTwo)
                .assignmentStatus("ASSIGNED")
                .assignedSource("MANUAL")
                .locked(false)
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .workShift(shiftA1)
                .employee(empTwo)
                .assignmentStatus("ASSIGNED")
                .assignedSource("AUTO")
                .locked(false)
                .build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .workShift(shiftB1)
                .employee(empOne)
                .assignmentStatus("ASSIGNED")
                .assignedSource("AUTO")
                .locked(true)
                .build());

        List<ShiftAssignment> result = shiftAssignmentRepository.findFixedForPeriod(periodA.getId());

        assertThat(result)
                .extracting(ShiftAssignment::getId)
                .containsExactlyInAnyOrder(lockedAuto.getId(), manualUnlocked.getId());
    }
}
