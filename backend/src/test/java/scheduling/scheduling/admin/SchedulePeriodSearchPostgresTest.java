package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-with-at-least-32-characters",
        "app.auth.default-employee-password=configured-default",
})
@Transactional
class SchedulePeriodSearchPostgresTest {

    @Autowired
    private SchedulePeriodRepository schedulePeriodRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void searchForAdminWithDateRangeWorksOnPostgres() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Account manager = accountRepository.save(Account.builder()
                .username("date-filter-manager-" + suffix)
                .passwordHash("hash")
                .role(AccountRole.MANAGER)
                .build());
        SchedulePeriod period = schedulePeriodRepository.save(SchedulePeriod.builder()
                .periodName("Date Filter Probe " + suffix)
                .startDate(LocalDate.of(2033, 5, 4))
                .endDate(LocalDate.of(2033, 5, 10))
                .createdBy(manager)
                .build());

        List<SchedulePeriod> withDates = schedulePeriodRepository.searchForAdmin(
                "DRAFT", LocalDate.of(2033, 5, 1), LocalDate.of(2033, 5, 31));

        assertThat(withDates).extracting(SchedulePeriod::getPeriodName).contains(period.getPeriodName());
    }

    @Test
    void searchForAdminWithoutDatesWorksOnPostgres() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Account manager = accountRepository.save(Account.builder()
                .username("date-filter-manager-" + suffix)
                .passwordHash("hash")
                .role(AccountRole.MANAGER)
                .build());
        SchedulePeriod period = schedulePeriodRepository.save(SchedulePeriod.builder()
                .periodName("Date Filter Probe " + suffix)
                .startDate(LocalDate.of(2033, 5, 4))
                .endDate(LocalDate.of(2033, 5, 10))
                .createdBy(manager)
                .build());

        List<SchedulePeriod> withoutDates = schedulePeriodRepository.searchForAdmin("DRAFT", null, null);

        assertThat(withoutDates).extracting(SchedulePeriod::getPeriodName).contains(period.getPeriodName());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void findDetailByIdFetchesEmployeePoolForUseOutsideTransaction() {
        SchedulePeriod detached = new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Account manager = accountRepository.save(Account.builder()
                    .username("pool-fetch-manager-" + suffix)
                    .passwordHash("hash")
                    .role(AccountRole.MANAGER)
                    .build());
            Employee employee = employeeRepository.save(Employee.builder()
                    .employeeCode("PF" + suffix)
                    .fullName("Pool Fetch Employee")
                    .status("ACTIVE")
                    .build());
            SchedulePeriod period = SchedulePeriod.builder()
                    .periodName("Pool Fetch Probe " + suffix)
                    .startDate(LocalDate.of(2034, 6, 1))
                    .endDate(LocalDate.of(2034, 6, 7))
                    .createdBy(manager)
                    .employeePool(new LinkedHashSet<>(List.of(employee)))
                    .build();
            SchedulePeriod saved = schedulePeriodRepository.saveAndFlush(period);
            entityManager.clear();
            return schedulePeriodRepository.findDetailById(saved.getId()).orElseThrow();
        });

        assertThatCode(() -> assertThat(detached.getEmployeePool()).extracting(Employee::getId).hasSize(1))
                .doesNotThrowAnyException();
    }
}
