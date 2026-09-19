package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.OptimizeScheduleRequest;
import scheduling.scheduling.admin.dto.OptimizeScheduleResponse;
import scheduling.scheduling.ai.AiSchedulingClient;
import scheduling.scheduling.ai.AiSchedulingPayloadBuilder;
import scheduling.scheduling.ai.AiServiceException;
import scheduling.scheduling.ai.OptimizationRunRecorder;
import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;
import scheduling.scheduling.ai.dto.AiSchedulingResponseDto;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.OptimizationRun;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;

class AdminOptimizationServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final SchedulePeriodRepository schedulePeriodRepository = mock(SchedulePeriodRepository.class);
    private final AiSchedulingPayloadBuilder payloadBuilder = mock(AiSchedulingPayloadBuilder.class);
    private final OptimizationRunRecorder optimizationRunRecorder = mock(OptimizationRunRecorder.class);
    private final AiSchedulingClient aiSchedulingClient = mock(AiSchedulingClient.class);
    private final AdminOptimizationService service = new AdminOptimizationService(
            accountRepository, schedulePeriodRepository, payloadBuilder, optimizationRunRecorder, aiSchedulingClient);

    @Test
    void optimizeCreatesRunCallsAiAndReturnsSuccessWithoutMutatingAssignments() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        Account manager = managerAccount(principal);
        SchedulePeriod period = draftPeriod(5L, manager);
        Map<String, Object> weights = Map.of("fairness", BigDecimal.ONE);
        AiSchedulingRequestDto payload = testPayload();
        AiSchedulingResponseDto aiResponse = new AiSchedulingResponseDto(
                "1.0",
                900L,
                5L,
                "SUCCESS",
                "OPTIMAL",
                List.of(
                        new AiSchedulingResponseDto.AssignmentDto(10L, 100L),
                        new AiSchedulingResponseDto.AssignmentDto(11L, 101L)),
                new BigDecimal("12.50"),
                Map.of("fairness", new BigDecimal("12.50")),
                List.of(),
                List.of(),
                null,
                new AiSchedulingResponseDto.StatisticsDto(new BigDecimal("1.5"), 0, 10));
        OptimizationRun startedRun = startedRun(900L, period, manager);
        OptimizationRun finishedRun = finishedRun(900L, period, manager, "SUCCESS");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(5L)).thenReturn(Optional.of(period));
        when(payloadBuilder.prepareSnapshot(eq(period), eq(null))).thenReturn(testSnapshot());
        when(payloadBuilder.weightsSnapshotFrom(any())).thenReturn(weights);
        when(optimizationRunRecorder.start(eq(period), eq(manager), eq(weights))).thenReturn(startedRun);
        when(payloadBuilder.buildFrom(any(), eq(period), eq(900L))).thenReturn(payload);
        when(aiSchedulingClient.optimize(eq(payload))).thenReturn(aiResponse);
        when(optimizationRunRecorder.finishSuccess(eq(900L), eq(aiResponse))).thenReturn(finishedRun);

        OptimizeScheduleResponse response = service.optimize(principal, 5L, new OptimizeScheduleRequest(null));

        assertThat(response.runId()).isEqualTo(900L);
        assertThat(response.periodId()).isEqualTo(5L);
        assertThat(response.aiStatus()).isEqualTo("SUCCESS");
        assertThat(response.solverStatus()).isEqualTo("OPTIMAL");
        assertThat(response.totalPenalty()).isEqualByComparingTo("12.50");
        assertThat(response.assignmentCount()).isEqualTo(2);
        assertThat(response.errorMessage()).isNull();
        assertThat(response.startedAt()).isEqualTo(LocalDateTime.of(2026, 9, 21, 9, 0));
        assertThat(response.finishedAt()).isEqualTo(LocalDateTime.of(2026, 9, 21, 9, 1));

        InOrder inOrder = inOrder(payloadBuilder, optimizationRunRecorder, aiSchedulingClient);
        inOrder.verify(payloadBuilder).prepareSnapshot(eq(period), eq(null));
        inOrder.verify(optimizationRunRecorder).start(eq(period), eq(manager), eq(weights));
        inOrder.verify(payloadBuilder).buildFrom(any(), eq(period), eq(900L));
        inOrder.verify(aiSchedulingClient).optimize(eq(payload));
        inOrder.verify(optimizationRunRecorder).finishSuccess(eq(900L), eq(aiResponse));
        verify(accountRepository).findById(1L);
        verify(schedulePeriodRepository).findDetailById(5L);
        verify(optimizationRunRecorder, never()).finishFailure(any(), any(), any(), any());
    }

    @Test
    void optimizeReturnsInfeasibleAndStoresDiagnostics() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        Account manager = managerAccount(principal);
        SchedulePeriod period = draftPeriod(5L, manager);
        Map<String, Object> weights = Map.of("fairness", BigDecimal.ONE);
        AiSchedulingRequestDto payload = testPayload();
        AiSchedulingResponseDto aiResponse = new AiSchedulingResponseDto(
                "1.0",
                900L,
                5L,
                "INFEASIBLE",
                "INFEASIBLE",
                List.of(),
                null,
                Map.of(),
                List.of(),
                List.of(new AiSchedulingResponseDto.DiagnosticDto("NO_STAFF", "Not enough staff", null, null)),
                "Infeasible",
                new AiSchedulingResponseDto.StatisticsDto(new BigDecimal("0.5"), 3, 5));
        OptimizationRun startedRun = startedRun(900L, period, manager);
        OptimizationRun finishedRun = finishedRun(900L, period, manager, "INFEASIBLE");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(5L)).thenReturn(Optional.of(period));
        when(payloadBuilder.prepareSnapshot(eq(period), eq(null))).thenReturn(testSnapshot());
        when(payloadBuilder.weightsSnapshotFrom(any())).thenReturn(weights);
        when(optimizationRunRecorder.start(eq(period), eq(manager), eq(weights))).thenReturn(startedRun);
        when(payloadBuilder.buildFrom(any(), eq(period), eq(900L))).thenReturn(payload);
        when(aiSchedulingClient.optimize(eq(payload))).thenReturn(aiResponse);
        when(optimizationRunRecorder.finishFailure(eq(900L), eq("INFEASIBLE"), any(), eq(aiResponse)))
                .thenReturn(finishedRun);

        OptimizeScheduleResponse response = service.optimize(principal, 5L, new OptimizeScheduleRequest(null));

        assertThat(response.runId()).isEqualTo(900L);
        assertThat(response.periodId()).isEqualTo(5L);
        assertThat(response.aiStatus()).isEqualTo("INFEASIBLE");
        assertThat(response.errorMessage()).contains("Not enough staff");
        verify(optimizationRunRecorder).finishFailure(eq(900L), eq("INFEASIBLE"), contains("Not enough staff"), eq(aiResponse));
        verify(optimizationRunRecorder, never()).finishSuccess(any(), any());

        InOrder inOrder = inOrder(payloadBuilder, optimizationRunRecorder, aiSchedulingClient);
        inOrder.verify(payloadBuilder).prepareSnapshot(eq(period), eq(null));
        inOrder.verify(optimizationRunRecorder).start(eq(period), eq(manager), eq(weights));
        inOrder.verify(payloadBuilder).buildFrom(any(), eq(period), eq(900L));
        inOrder.verify(aiSchedulingClient).optimize(eq(payload));
        inOrder.verify(optimizationRunRecorder).finishFailure(eq(900L), eq("INFEASIBLE"), contains("Not enough staff"), eq(aiResponse));
    }

    @Test
    void optimizeCapsManyLongDiagnosticsWithoutThrowing() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        Account manager = managerAccount(principal);
        SchedulePeriod period = draftPeriod(5L, manager);
        Map<String, Object> weights = Map.of("fairness", BigDecimal.ONE);
        AiSchedulingRequestDto payload = testPayload();
        // First part is exactly 3999 chars ("C: " + 3996 chars): the old builder appended
        // the "; " separator before recomputing room, yielding remaining = -1 and throwing
        // StringIndexOutOfBoundsException on the next part. The remaining diagnostics push
        // the uncapped total far beyond the ~4000-char cap.
        List<AiSchedulingResponseDto.DiagnosticDto> diagnostics = new ArrayList<>();
        diagnostics.add(new AiSchedulingResponseDto.DiagnosticDto("C", "x".repeat(3996), null, null));
        for (int i = 1; i < 30; i++) {
            diagnostics.add(new AiSchedulingResponseDto.DiagnosticDto("CODE-" + i, "y".repeat(500), null, null));
        }
        AiSchedulingResponseDto aiResponse = new AiSchedulingResponseDto(
                "1.0",
                900L,
                5L,
                "INFEASIBLE",
                "INFEASIBLE",
                List.of(),
                null,
                Map.of(),
                List.of(),
                diagnostics,
                "Infeasible",
                new AiSchedulingResponseDto.StatisticsDto(new BigDecimal("0.5"), 3, 5));
        OptimizationRun startedRun = startedRun(900L, period, manager);
        OptimizationRun finishedRun = finishedRun(900L, period, manager, "INFEASIBLE");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(5L)).thenReturn(Optional.of(period));
        when(payloadBuilder.prepareSnapshot(eq(period), eq(null))).thenReturn(testSnapshot());
        when(payloadBuilder.weightsSnapshotFrom(any())).thenReturn(weights);
        when(optimizationRunRecorder.start(eq(period), eq(manager), eq(weights))).thenReturn(startedRun);
        when(payloadBuilder.buildFrom(any(), eq(period), eq(900L))).thenReturn(payload);
        when(aiSchedulingClient.optimize(eq(payload))).thenReturn(aiResponse);
        when(optimizationRunRecorder.finishFailure(eq(900L), eq("INFEASIBLE"), any(), eq(aiResponse)))
                .thenReturn(finishedRun);

        OptimizeScheduleResponse response = service.optimize(principal, 5L, new OptimizeScheduleRequest(null));

        assertThat(response.aiStatus()).isEqualTo("INFEASIBLE");
        assertThat(response.errorMessage()).isNotNull();
        assertThat(response.errorMessage().length()).isLessThanOrEqualTo(4000);
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(optimizationRunRecorder).finishFailure(eq(900L), eq("INFEASIBLE"), errorCaptor.capture(), eq(aiResponse));
        // The mocked recorder receives the pre-truncation message (service caps at ~4000;
        // the real OptimizationRunRecorder truncates to 2000 for persistence).
        assertThat(errorCaptor.getValue().length()).isLessThanOrEqualTo(4000);
        verify(optimizationRunRecorder, never()).finishSuccess(any(), any());
    }

    @Test
    void optimizeReturnsFailedWhenAiReturnsFailedStatus() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        Account manager = managerAccount(principal);
        SchedulePeriod period = draftPeriod(5L, manager);
        Map<String, Object> weights = Map.of("fairness", BigDecimal.ONE);
        AiSchedulingRequestDto payload = testPayload();
        AiSchedulingResponseDto aiResponse = new AiSchedulingResponseDto(
                "1.0",
                900L,
                5L,
                "FAILED",
                "UNKNOWN",
                List.of(),
                null,
                Map.of(),
                List.of(),
                List.of(),
                "Solver crashed",
                new AiSchedulingResponseDto.StatisticsDto(new BigDecimal("0.5"), 0, 0));
        OptimizationRun startedRun = startedRun(900L, period, manager);
        OptimizationRun finishedRun = finishedRun(900L, period, manager, "FAILED");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(5L)).thenReturn(Optional.of(period));
        when(payloadBuilder.prepareSnapshot(eq(period), eq(null))).thenReturn(testSnapshot());
        when(payloadBuilder.weightsSnapshotFrom(any())).thenReturn(weights);
        when(optimizationRunRecorder.start(eq(period), eq(manager), eq(weights))).thenReturn(startedRun);
        when(payloadBuilder.buildFrom(any(), eq(period), eq(900L))).thenReturn(payload);
        when(aiSchedulingClient.optimize(eq(payload))).thenReturn(aiResponse);
        when(optimizationRunRecorder.finishFailure(eq(900L), eq("FAILED"), any(), eq(aiResponse)))
                .thenReturn(finishedRun);

        OptimizeScheduleResponse response = service.optimize(principal, 5L, new OptimizeScheduleRequest(null));

        assertThat(response.aiStatus()).isEqualTo("FAILED");
        assertThat(response.runId()).isEqualTo(900L);
        assertThat(response.periodId()).isEqualTo(5L);
        assertThat(response.errorMessage()).contains("Solver crashed");
        verify(optimizationRunRecorder).finishFailure(eq(900L), eq("FAILED"), contains("Solver crashed"), eq(aiResponse));
        verify(optimizationRunRecorder, never()).finishSuccess(any(), any());
    }

    @Test
    void optimizeThrows502AndRecordsFailedRunWhenClientFails() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        Account manager = managerAccount(principal);
        SchedulePeriod period = draftPeriod(5L, manager);
        Map<String, Object> weights = Map.of("fairness", BigDecimal.ONE);
        AiSchedulingRequestDto payload = testPayload();
        OptimizationRun startedRun = startedRun(900L, period, manager);
        OptimizationRun failedRun = finishedRun(900L, period, manager, "FAILED");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(5L)).thenReturn(Optional.of(period));
        when(payloadBuilder.prepareSnapshot(eq(period), eq(null))).thenReturn(testSnapshot());
        when(payloadBuilder.weightsSnapshotFrom(any())).thenReturn(weights);
        when(optimizationRunRecorder.start(eq(period), eq(manager), eq(weights))).thenReturn(startedRun);
        when(payloadBuilder.buildFrom(any(), eq(period), eq(900L))).thenReturn(payload);
        when(aiSchedulingClient.optimize(eq(payload))).thenThrow(new AiServiceException("boom"));
        when(optimizationRunRecorder.finishFailure(eq(900L), eq("FAILED"), eq("boom"), eq(null)))
                .thenReturn(failedRun);

        assertThatThrownBy(() -> service.optimize(principal, 5L, new OptimizeScheduleRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);

        verify(optimizationRunRecorder).finishFailure(eq(900L), eq("FAILED"), eq("boom"), eq(null));
        verify(optimizationRunRecorder, never()).finishSuccess(any(), any());

        InOrder inOrder = inOrder(payloadBuilder, optimizationRunRecorder, aiSchedulingClient);
        inOrder.verify(payloadBuilder).prepareSnapshot(eq(period), eq(null));
        inOrder.verify(optimizationRunRecorder).start(eq(period), eq(manager), eq(weights));
        inOrder.verify(payloadBuilder).buildFrom(any(), eq(period), eq(900L));
        inOrder.verify(aiSchedulingClient).optimize(eq(payload));
        inOrder.verify(optimizationRunRecorder).finishFailure(eq(900L), eq("FAILED"), eq("boom"), eq(null));
    }

    @Test
    void optimizeRecordsFailedRunOnUnexpectedRuntimeException() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        Account manager = managerAccount(principal);
        SchedulePeriod period = draftPeriod(5L, manager);
        Map<String, Object> weights = Map.of("fairness", BigDecimal.ONE);
        AiSchedulingRequestDto payload = testPayload();
        OptimizationRun startedRun = startedRun(900L, period, manager);
        OptimizationRun failedRun = finishedRun(900L, period, manager, "FAILED");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(5L)).thenReturn(Optional.of(period));
        when(payloadBuilder.prepareSnapshot(eq(period), eq(null))).thenReturn(testSnapshot());
        when(payloadBuilder.weightsSnapshotFrom(any())).thenReturn(weights);
        when(optimizationRunRecorder.start(eq(period), eq(manager), eq(weights))).thenReturn(startedRun);
        when(payloadBuilder.buildFrom(any(), eq(period), eq(900L))).thenReturn(payload);
        when(aiSchedulingClient.optimize(eq(payload))).thenThrow(new IllegalStateException("boom"));
        when(optimizationRunRecorder.finishFailure(eq(900L), eq("FAILED"), contains("Unexpected optimization failure"), isNull()))
                .thenReturn(failedRun);

        assertThatThrownBy(() -> service.optimize(principal, 5L, new OptimizeScheduleRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);

        verify(optimizationRunRecorder)
                .finishFailure(eq(900L), eq("FAILED"), contains("Unexpected optimization failure"), isNull());
        verify(optimizationRunRecorder, never()).finishSuccess(any(), any());
    }

    @Test
    void optimizeThrows502AndRecordsFailedRunWhenAiResponseIsInvalid() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        Account manager = managerAccount(principal);
        SchedulePeriod period = draftPeriod(5L, manager);
        Map<String, Object> weights = Map.of("fairness", BigDecimal.ONE);
        AiSchedulingRequestDto payload = testPayload();
        AiSchedulingResponseDto invalidResponse = new AiSchedulingResponseDto(
                "2.0",
                900L,
                5L,
                "SUCCESS",
                "OPTIMAL",
                List.of(),
                new BigDecimal("1.00"),
                Map.of(),
                List.of(),
                List.of(),
                null,
                new AiSchedulingResponseDto.StatisticsDto(new BigDecimal("0.5"), 0, 0));
        OptimizationRun startedRun = startedRun(900L, period, manager);
        OptimizationRun failedRun = finishedRun(900L, period, manager, "FAILED");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(5L)).thenReturn(Optional.of(period));
        when(payloadBuilder.prepareSnapshot(eq(period), eq(null))).thenReturn(testSnapshot());
        when(payloadBuilder.weightsSnapshotFrom(any())).thenReturn(weights);
        when(optimizationRunRecorder.start(eq(period), eq(manager), eq(weights))).thenReturn(startedRun);
        when(payloadBuilder.buildFrom(any(), eq(period), eq(900L))).thenReturn(payload);
        when(aiSchedulingClient.optimize(eq(payload))).thenReturn(invalidResponse);
        when(optimizationRunRecorder.finishFailure(eq(900L), eq("FAILED"), contains("Invalid AI response"), eq(null)))
                .thenReturn(failedRun);

        assertThatThrownBy(() -> service.optimize(principal, 5L, new OptimizeScheduleRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);

        verify(optimizationRunRecorder).finishFailure(eq(900L), eq("FAILED"), contains("Invalid AI response"), eq(null));
        verify(optimizationRunRecorder, never()).finishSuccess(any(), any());
    }

    @Test
    void optimizeCreatesNoRunWhenValidationFails() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        Account manager = managerAccount(principal);
        SchedulePeriod period = draftPeriod(5L, manager);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(5L)).thenReturn(Optional.of(period));
        when(payloadBuilder.prepareSnapshot(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active employees to schedule"));

        assertThatThrownBy(() -> service.optimize(principal, 5L, new OptimizeScheduleRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(optimizationRunRecorder, aiSchedulingClient);
        verify(payloadBuilder, never()).buildFrom(any(), any(), any());
        verify(payloadBuilder, never()).weightsSnapshotFrom(any());
    }

    @Test
    void optimizeThrows502ForEachProtocolViolation() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        Account manager = managerAccount(principal);
        SchedulePeriod period = draftPeriod(5L, manager);
        Map<String, Object> weights = Map.of("fairness", BigDecimal.ONE);
        AiSchedulingRequestDto payload = testPayload();
        AiSchedulingResponseDto base = new AiSchedulingResponseDto(
                "1.0",
                900L,
                5L,
                "SUCCESS",
                "OPTIMAL",
                List.of(new AiSchedulingResponseDto.AssignmentDto(10L, 100L)),
                new BigDecimal("12.50"),
                Map.of("fairness", new BigDecimal("12.50")),
                List.of(),
                List.of(),
                null,
                new AiSchedulingResponseDto.StatisticsDto(new BigDecimal("1.5"), 0, 10));
        List<AiSchedulingResponseDto> variants = new ArrayList<>();
        variants.add(new AiSchedulingResponseDto(
                "2.0", base.runId(), base.periodId(), base.status(), base.solverStatus(), base.assignments(),
                base.totalPenalty(), base.penaltyDetails(), base.warnings(), base.diagnostics(),
                base.errorMessage(), base.statistics()));
        variants.add(new AiSchedulingResponseDto(
                base.schemaVersion(), 901L, base.periodId(), base.status(), base.solverStatus(), base.assignments(),
                base.totalPenalty(), base.penaltyDetails(), base.warnings(), base.diagnostics(),
                base.errorMessage(), base.statistics()));
        variants.add(new AiSchedulingResponseDto(
                base.schemaVersion(), base.runId(), 6L, base.status(), base.solverStatus(), base.assignments(),
                base.totalPenalty(), base.penaltyDetails(), base.warnings(), base.diagnostics(),
                base.errorMessage(), base.statistics()));
        variants.add(new AiSchedulingResponseDto(
                base.schemaVersion(), base.runId(), base.periodId(), "BROKEN", base.solverStatus(), base.assignments(),
                base.totalPenalty(), base.penaltyDetails(), base.warnings(), base.diagnostics(),
                base.errorMessage(), base.statistics()));
        variants.add(new AiSchedulingResponseDto(
                base.schemaVersion(), base.runId(), base.periodId(), base.status(), "BROKEN", base.assignments(),
                base.totalPenalty(), base.penaltyDetails(), base.warnings(), base.diagnostics(),
                base.errorMessage(), base.statistics()));
        variants.add(new AiSchedulingResponseDto(
                base.schemaVersion(), base.runId(), base.periodId(), base.status(), base.solverStatus(), null,
                base.totalPenalty(), base.penaltyDetails(), base.warnings(), base.diagnostics(),
                base.errorMessage(), base.statistics()));
        variants.add(new AiSchedulingResponseDto(
                base.schemaVersion(), base.runId(), base.periodId(), base.status(), base.solverStatus(),
                base.assignments(), base.totalPenalty(), null, base.warnings(), base.diagnostics(),
                base.errorMessage(), base.statistics()));
        variants.add(new AiSchedulingResponseDto(
                base.schemaVersion(), base.runId(), base.periodId(), base.status(), base.solverStatus(),
                base.assignments(), base.totalPenalty(), base.penaltyDetails(), null, base.diagnostics(),
                base.errorMessage(), base.statistics()));
        variants.add(new AiSchedulingResponseDto(
                base.schemaVersion(), base.runId(), base.periodId(), base.status(), base.solverStatus(),
                base.assignments(), base.totalPenalty(), base.penaltyDetails(), base.warnings(), null,
                base.errorMessage(), base.statistics()));
        variants.add(new AiSchedulingResponseDto(
                base.schemaVersion(), base.runId(), base.periodId(), base.status(), base.solverStatus(),
                base.assignments(), base.totalPenalty(), base.penaltyDetails(), base.warnings(),
                base.diagnostics(), base.errorMessage(), null));
        variants.add(new AiSchedulingResponseDto(
                base.schemaVersion(), base.runId(), base.periodId(), base.status(), base.solverStatus(),
                base.assignments(), null, base.penaltyDetails(), base.warnings(), base.diagnostics(),
                base.errorMessage(), base.statistics()));
        variants.add(null);
        OptimizationRun startedRun = startedRun(900L, period, manager);
        OptimizationRun failedRun = finishedRun(900L, period, manager, "FAILED");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(5L)).thenReturn(Optional.of(period));
        when(payloadBuilder.prepareSnapshot(eq(period), eq(null))).thenReturn(testSnapshot());
        when(payloadBuilder.weightsSnapshotFrom(any())).thenReturn(weights);
        when(optimizationRunRecorder.start(eq(period), eq(manager), eq(weights))).thenReturn(startedRun);
        when(payloadBuilder.buildFrom(any(), eq(period), eq(900L))).thenReturn(payload);
        when(optimizationRunRecorder.finishFailure(eq(900L), eq("FAILED"), contains("Invalid AI response"), isNull()))
                .thenReturn(failedRun);

        int expectedCalls = 0;
        for (AiSchedulingResponseDto variant : variants) {
            when(aiSchedulingClient.optimize(eq(payload))).thenReturn(variant);

            assertThatThrownBy(() -> service.optimize(principal, 5L, new OptimizeScheduleRequest(null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_GATEWAY);

            expectedCalls++;
            verify(optimizationRunRecorder, times(expectedCalls))
                    .finishFailure(eq(900L), eq("FAILED"), contains("Invalid AI response"), isNull());
        }
        verify(optimizationRunRecorder, never()).finishSuccess(any(), any());
    }

    @Test
    void optimizeRejectsNonDraftPeriodBeforeCreatingRun() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        Account manager = managerAccount(principal);
        SchedulePeriod period = draftPeriod(5L, manager);
        period.setStatus("APPROVED");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(5L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.optimize(principal, 5L, new OptimizeScheduleRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(payloadBuilder, optimizationRunRecorder, aiSchedulingClient);
    }

    @Test
    void optimizeRejectsMissingPeriod() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
        Account manager = managerAccount(principal);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(schedulePeriodRepository.findDetailById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.optimize(principal, 5L, new OptimizeScheduleRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(payloadBuilder, optimizationRunRecorder, aiSchedulingClient);
    }

    private static AiSchedulingPayloadBuilder.PayloadSnapshot testSnapshot() {
        return new AiSchedulingPayloadBuilder.PayloadSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
    }

    private static AiSchedulingRequestDto testPayload() {
        return new AiSchedulingRequestDto(
                "1.0",
                900L,
                new AiSchedulingRequestDto.PeriodDto(
                        5L, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 1, "Asia/Ho_Chi_Minh"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null);
    }

    private static OptimizationRun startedRun(Long id, SchedulePeriod period, Account manager) {
        OptimizationRun run = OptimizationRun.builder()
                .schedulePeriod(period)
                .startedBy(manager)
                .status("RUNNING")
                .startedAt(LocalDateTime.of(2026, 9, 21, 9, 0))
                .build();
        run.setId(id);
        return run;
    }

    private static OptimizationRun finishedRun(Long id, SchedulePeriod period, Account manager, String status) {
        OptimizationRun run = OptimizationRun.builder()
                .schedulePeriod(period)
                .startedBy(manager)
                .status(status)
                .startedAt(LocalDateTime.of(2026, 9, 21, 9, 0))
                .finishedAt(LocalDateTime.of(2026, 9, 21, 9, 1))
                .build();
        run.setId(id);
        return run;
    }

    private static Account managerAccount(CurrentAccountPrincipal principal) {
        Account account = Account.builder()
                .username(principal.username())
                .passwordHash("hash")
                .role(principal.role())
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(principal.accountId());
        return account;
    }

    private static SchedulePeriod draftPeriod(Long id, Account manager) {
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Week 38")
                .startDate(LocalDate.of(2026, 9, 14))
                .endDate(LocalDate.of(2026, 9, 20))
                .versionNo(1)
                .status("DRAFT")
                .createdBy(manager)
                .build();
        period.setId(id);
        return period;
    }
}
