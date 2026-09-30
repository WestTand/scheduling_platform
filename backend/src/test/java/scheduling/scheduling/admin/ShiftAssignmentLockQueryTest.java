package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.hibernate.resource.jdbc.spi.StatementInspector;
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
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-with-at-least-32-characters",
        "app.auth.default-employee-password=configured-default",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "scheduling.scheduling.admin.ShiftAssignmentLockQueryTest$SqlRecorder",
})
@Transactional
class ShiftAssignmentLockQueryTest {

    /** Captures emitted SQL so the locking clause of the lock query can be asserted. */
    public static final class SqlRecorder implements StatementInspector {

        private static final List<String> SQL = new ArrayList<>();

        @Override
        public String inspect(String sql) {
            SQL.add(sql);
            return sql;
        }

        static String lastSelectAgainstShiftAssignments() {
            return SQL.stream()
                    .filter(sql -> sql.startsWith("select"))
                            .filter(sql -> sql.contains("from shift_assignments"))
                    .reduce((first, second) -> second)
                    .orElseThrow();
        }
    }

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
    private EntityManager entityManager;

    @Test
    void findAllByIdInForUpdateReturnsRowsOrderedByIdWithFetchesPopulated() {
        Account manager = accountRepository.save(Account.builder()
                .username("lock-query-manager")
                .passwordHash("hash")
                .role(AccountRole.MANAGER)
                .build());
        ShiftType shiftType = shiftTypeRepository.save(ShiftType.builder()
                .shiftName("Lock Query Day")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(new BigDecimal("8.0"))
                .minStaff(1)
                .maxStaff(2)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .shiftCode("LOCK_Q_DAY")
                .build());
        SchedulePeriod period = schedulePeriodRepository.save(SchedulePeriod.builder()
                .periodName("Lock Query Period")
                .startDate(LocalDate.of(2035, 3, 4))
                .endDate(LocalDate.of(2035, 3, 10))
                .createdBy(manager)
                .build());
        Employee requester = employeeRepository.save(Employee.builder()
                .employeeCode("LOCK_Q_REQ")
                .fullName("Lock Query Requester")
                .status("ACTIVE")
                .build());
        Employee target = employeeRepository.save(Employee.builder()
                .employeeCode("LOCK_Q_TGT")
                .fullName("Lock Query Target")
                .status("ACTIVE")
                .build());
        WorkShift requesterShift = workShiftRepository.save(WorkShift.builder()
                .workDate(LocalDate.of(2035, 3, 4))
                .requiredStaff(1)
                .schedulePeriod(period)
                .shiftType(shiftType)
                .build());
        WorkShift targetShift = workShiftRepository.save(WorkShift.builder()
                .workDate(LocalDate.of(2035, 3, 5))
                .requiredStaff(1)
                .schedulePeriod(period)
                .shiftType(shiftType)
                .build());
        ShiftAssignment requesterAssignment = shiftAssignmentRepository.save(ShiftAssignment.builder()
                .workShift(requesterShift)
                .employee(requester)
                .assignmentStatus("ASSIGNED")
                .assignedSource("AUTO")
                .locked(false)
                .build());
        ShiftAssignment targetAssignment = shiftAssignmentRepository.save(ShiftAssignment.builder()
                .workShift(targetShift)
                .employee(target)
                .assignmentStatus("ASSIGNED")
                .assignedSource("AUTO")
                .locked(false)
                .build());

        entityManager.clear();
        List<ShiftAssignment> result = shiftAssignmentRepository.findAllByIdInForUpdate(
                List.of(targetAssignment.getId(), requesterAssignment.getId()));

        assertThat(result).extracting(ShiftAssignment::getId)
                .containsExactly(requesterAssignment.getId(), targetAssignment.getId());
        assertThat(result.get(0).getWorkShift().getId()).isEqualTo(requesterShift.getId());
        assertThat(result.get(0).getWorkShift().getSchedulePeriod().getId()).isEqualTo(period.getId());
        assertThat(result.get(0).getEmployee().getId()).isEqualTo(requester.getId());
        assertThat(result.get(1).getWorkShift().getId()).isEqualTo(targetShift.getId());
        assertThat(result.get(1).getWorkShift().getSchedulePeriod().getId()).isEqualTo(period.getId());
        assertThat(result.get(1).getEmployee().getId()).isEqualTo(target.getId());
        PersistenceUnitUtil loaded = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(loaded.isLoaded(result.get(0), "workShift")).isTrue();
        assertThat(loaded.isLoaded(result.get(0).getWorkShift(), "schedulePeriod")).isTrue();
        assertThat(loaded.isLoaded(result.get(0), "employee")).isTrue();
        assertThat(loaded.isLoaded(result.get(1), "workShift")).isTrue();
        assertThat(loaded.isLoaded(result.get(1).getWorkShift(), "schedulePeriod")).isTrue();
        assertThat(loaded.isLoaded(result.get(1), "employee")).isTrue();

        String sql = SqlRecorder.lastSelectAgainstShiftAssignments();
        assertThat(sql).contains("for update");
        assertThat(sql).doesNotContain("shift_types");
    }
}