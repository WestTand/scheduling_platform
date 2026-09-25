package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import scheduling.scheduling.admin.dto.GenerateScheduleRequest;
import scheduling.scheduling.admin.dto.OptimizeScheduleResponse;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-with-at-least-32-characters",
        "app.auth.default-employee-password=configured-default",
})
class AdminScheduleGenerationTransactionTest {

    @Autowired
    private AdminScheduleGenerationService service;

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
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private AdminOptimizationService optimizationService;

    private Long managerId;
    private Long employeeId;
    private Long shiftTypeId;
    private Long periodId;

    @AfterEach
    void tearDown() {
        if (periodId != null) {
            workShiftRepository.searchForPeriod(periodId, null, null)
                    .forEach(workShiftRepository::delete);
            schedulePeriodRepository.findById(periodId).ifPresent(schedulePeriodRepository::delete);
        }
        if (shiftTypeId != null) {
            shiftTypeRepository.findById(shiftTypeId).ifPresent(shiftTypeRepository::delete);
        }
        if (employeeId != null) {
            employeeRepository.findById(employeeId).ifPresent(employeeRepository::delete);
        }
        if (managerId != null) {
            accountRepository.findById(managerId).ifPresent(accountRepository::delete);
        }
    }

    @Test
    void generatedPeriodIsCommittedBeforeOptimizationStartsNewTransaction() {
        long unique = System.nanoTime();
        Account manager = accountRepository.save(Account.builder()
                .username("generation-tx-manager-" + unique)
                .passwordHash("hash")
                .role(AccountRole.MANAGER)
                .isActive(true)
                .status(AccountStatus.ACTIVE)
                .build());
        managerId = manager.getId();
        Employee employee = employeeRepository.save(Employee.builder()
                .employeeCode("GTX" + Long.toString(unique).substring(8))
                .fullName("Generation TX Employee")
                .status("ACTIVE")
                .build());
        employeeId = employee.getId();
        ShiftType shiftType = shiftTypeRepository.save(ShiftType.builder()
                .shiftName("Generation TX Shift " + unique)
                .shiftCode("GTXS" + Long.toString(unique).substring(8))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(BigDecimal.valueOf(8))
                .minStaff(1)
                .maxStaff(3)
                .status("ACTIVE")
                .applicableWeekdays("MON,TUE,WED,THU,FRI,SAT,SUN")
                .build());
        shiftTypeId = shiftType.getId();

        when(optimizationService.optimize(any(), any(), any())).thenAnswer(invocation -> {
            Long generatedPeriodId = invocation.getArgument(1);
            TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Boolean visibleFromNewTransaction = requiresNew.execute(status -> schedulePeriodRepository.existsById(generatedPeriodId));
            assertThat(visibleFromNewTransaction).isTrue();
            periodId = generatedPeriodId;
            return new OptimizeScheduleResponse(
                    101L, generatedPeriodId, "SUCCESS", "OPTIMAL", BigDecimal.ZERO, 1, List.of(), null, null, null);
        });

        service.generate(
                new CurrentAccountPrincipal(manager.getId(), manager.getUsername(), AccountRole.MANAGER),
                new GenerateScheduleRequest(
                        "Generation TX Period " + unique,
                        LocalDate.of(2039, 3, 7).plusDays(unique % 120),
                        LocalDate.of(2039, 3, 7).plusDays(unique % 120),
                        1,
                        List.of(employee.getId()),
                        List.of(shiftType.getId())));

        assertThat(periodId).isNotNull();
    }
}
