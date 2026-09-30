package scheduling.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.ai.dto.AiSchedulingResponseDto;
import scheduling.scheduling.entity.OptimizationRun;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.repository.OptimizationRunRepository;

class OptimizationRunRecorderTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);

    private final OptimizationRunRepository optimizationRunRepository = mock(OptimizationRunRepository.class);
    private final OptimizationRunRecorder recorder =
            new OptimizationRunRecorder(optimizationRunRepository, FIXED_CLOCK, new ObjectMapper());

    @Test
    void finishSuccessStoresStaffingAdvice() {
        OptimizationRun run = runningRun();

        recorder.finishSuccess(42L, response(
                "SUCCESS",
                new AiSchedulingResponseDto.StaffingAdviceDto(
                        10, 4, 21, true,
                        List.of(new AiSchedulingResponseDto.StaffingReasonDto("QUOTA_OVERTIME", 5, "need 5")))));

        assertThat(run.getStaffingAdviceJson()).contains("\"requiredEmployees\":10");
        assertThat(run.getStaffingAdviceJson()).contains("QUOTA_OVERTIME");
    }

    @Test
    void finishSuccessStoresNullWhenResponseHasNoStaffingAdvice() {
        OptimizationRun run = runningRun();

        recorder.finishSuccess(42L, response("SUCCESS", null));

        assertThat(run.getStaffingAdviceJson()).isNull();
    }

    @Test
    void finishFailureStoresStaffingAdviceForInfeasibleResponse() {
        OptimizationRun run = runningRun();

        recorder.finishFailure(42L, "INFEASIBLE", "No feasible schedule", response(
                "INFEASIBLE",
                new AiSchedulingResponseDto.StaffingAdviceDto(
                        10, 4, 21, true,
                        List.of(new AiSchedulingResponseDto.StaffingReasonDto("QUOTA_OVERTIME", 5, "need 5")))));

        assertThat(run.getStaffingAdviceJson()).contains("\"requiredEmployees\":10");
    }

    @Test
    void finishFailureStoresNullWhenResponseIsMissing() {
        OptimizationRun run = runningRun();

        recorder.finishFailure(42L, "FAILED", "AI service unavailable", null);

        assertThat(run.getStaffingAdviceJson()).isNull();
    }

    @Test
    void aiResponseWithReasonObjectsDeserializes() throws Exception {
        String payload = """
                {"schemaVersion":"1.0","runId":42,"periodId":20,"status":"INFEASIBLE","solverStatus":"INFEASIBLE",
                 "assignments":[],"totalPenalty":null,"penaltyDetails":{},"warnings":[],"diagnostics":[],
                 "staffingAdvice":{"requiredEmployees":10,"availableEmployees":4,"totalShifts":21,"exact":true,
                   "reasons":[{"code":"QUOTA_OVERTIME","requiredEmployees":5,"detail":"need 5"}]},
                 "errorMessage":"infeasible","statistics":{"wallTimeSeconds":0.1,"conflicts":0,"branches":0}}""";

        AiSchedulingResponseDto response = new ObjectMapper().readValue(payload, AiSchedulingResponseDto.class);

        assertThat(response.staffingAdvice().requiredEmployees()).isEqualTo(10);
        assertThat(response.staffingAdvice().reasons()).hasSize(1);
    }

    private OptimizationRun runningRun() {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Week 38")
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 20))
                .versionNo(1)
                .status("DRAFT")
                .build();
        period.setId(20L);
        OptimizationRun run = OptimizationRun.builder()
                .schedulePeriod(period)
                .status("RUNNING")
                .build();
        run.setId(42L);
        when(optimizationRunRepository.findById(42L)).thenReturn(Optional.of(run));
        when(optimizationRunRepository.save(any(OptimizationRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return run;
    }

    private static AiSchedulingResponseDto response(String status, AiSchedulingResponseDto.StaffingAdviceDto advice) {
        return new AiSchedulingResponseDto(
                "1.0", 42L, 20L, status, "OPTIMAL", List.of(), BigDecimal.ZERO,
                Map.of(), List.of(), List.of(), null, advice,
                new AiSchedulingResponseDto.StatisticsDto(BigDecimal.ZERO, 0, 0));
    }
}