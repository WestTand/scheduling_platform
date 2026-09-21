package scheduling.scheduling.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.ActivityLogRepository;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-with-at-least-32-characters",
        "app.auth.default-employee-password=configured-default",
})
class ActivityLogJsonbMappingTest {

    private final String username = "jsonb-log-test-manager-" + System.nanoTime();

    private Long activityLogId;
    private Long accountId;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @AfterEach
    void tearDown() {
        if (activityLogId != null) {
            activityLogRepository.deleteById(activityLogId);
        }
        if (accountId != null) {
            accountRepository.deleteById(accountId);
        } else {
            accountRepository.findByUsername(username).ifPresent(accountRepository::delete);
        }
    }

    @Test
    void persistsActivitySnapshotsToPostgresJsonbColumns() throws Exception {
        Account actor = accountRepository.save(Account.builder()
                .username(username)
                .passwordHash("hash")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build());
        accountId = actor.getId();

        ActivityLog saved = activityLogRepository.save(ActivityLog.builder()
                .account(actor)
                .action("CREATE")
                .entityType("employees")
                .entityId(999L)
                .oldValueJson(null)
                .newValueJson("{\"employeeCode\":\"EMP999\"}")
                .build());
        activityLogId = saved.getId();

        ActivityLog reloaded = activityLogRepository.findById(saved.getId()).orElseThrow();

        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.readTree(reloaded.getNewValueJson()))
                .isEqualTo(mapper.readTree("{\"employeeCode\":\"EMP999\"}"));
    }
}
