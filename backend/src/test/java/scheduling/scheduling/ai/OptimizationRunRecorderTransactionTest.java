package scheduling.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.OptimizationRun;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.OptimizationRunRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-with-at-least-32-characters",
        "app.auth.default-employee-password=configured-default",
})
class OptimizationRunRecorderTransactionTest {

    @Autowired
    private OptimizationRunRecorder recorder;

    @Autowired
    private OptimizationRunRepository optimizationRunRepository;

    @Autowired
    private SchedulePeriodRepository schedulePeriodRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FailingOptimizationRunner failingRunner;

    @BeforeEach
    void cleanUp() {
        optimizationRunRepository.deleteAll();
        schedulePeriodRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void failureRecordingSurvivesCallerRollback() {
        Account manager = accountRepository.save(Account.builder()
                .username("tx-test-manager")
                .passwordHash("hash")
                .role(AccountRole.MANAGER)
                .build());
        SchedulePeriod period = schedulePeriodRepository.save(SchedulePeriod.builder()
                .periodName("TX Test Period")
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 20))
                .createdBy(manager)
                .build());

        ResponseStatusException thrown = catchThrowableOfType(
                () -> failingRunner.runAndFail(period, manager),
                ResponseStatusException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

        List<OptimizationRun> runs = optimizationRunRepository.findAll();
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(runs.get(0).getFinishedAt()).isNotNull();
    }

    @TestConfiguration
    static class FailingRunnerConfiguration {
        @Bean
        FailingOptimizationRunner failingOptimizationRunner(OptimizationRunRecorder recorder) {
            return new FailingOptimizationRunner(recorder);
        }
    }

    @Service
    static class FailingOptimizationRunner {

        private final OptimizationRunRecorder recorder;

        FailingOptimizationRunner(OptimizationRunRecorder recorder) {
            this.recorder = recorder;
        }

        @Transactional
        public void runAndFail(SchedulePeriod period, Account manager) {
            OptimizationRun run = recorder.start(period, manager, Map.of("timeLimitSeconds", 30));
            recorder.finishFailure(run.getId(), "FAILED", "AI service unavailable", null);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable");
        }
    }
}
