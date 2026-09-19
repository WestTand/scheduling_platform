package scheduling.scheduling.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
import scheduling.scheduling.entity.OptimizationRun;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;

@Service
public class AdminOptimizationService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("SUCCESS", "INFEASIBLE", "FAILED");
    private static final Set<String> ALLOWED_SOLVER_STATUSES =
            Set.of("OPTIMAL", "FEASIBLE", "INFEASIBLE", "UNKNOWN", "MODEL_INVALID");

    private final AccountRepository accountRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final AiSchedulingPayloadBuilder payloadBuilder;
    private final OptimizationRunRecorder optimizationRunRecorder;
    private final AiSchedulingClient aiSchedulingClient;

    public AdminOptimizationService(
            AccountRepository accountRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            AiSchedulingPayloadBuilder payloadBuilder,
            OptimizationRunRecorder optimizationRunRecorder,
            AiSchedulingClient aiSchedulingClient) {
        this.accountRepository = accountRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.payloadBuilder = payloadBuilder;
        this.optimizationRunRecorder = optimizationRunRecorder;
        this.aiSchedulingClient = aiSchedulingClient;
    }

    // Deliberately no @Transactional: the AI HTTP call is long-lived and must not hold a DB transaction; run persistence is handled by the recorder's REQUIRES_NEW methods.
    public OptimizeScheduleResponse optimize(
            CurrentAccountPrincipal principal, Long periodId, OptimizeScheduleRequest request) {
        Account manager = requireManager(principal);
        SchedulePeriod period = schedulePeriodRepository.findDetailById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule period not found"));
        if (!"DRAFT".equals(period.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only DRAFT periods can be optimized");
        }
        AiSchedulingRequestDto.SolverOptionsDto options =
                request == null || request.solverOptions() == null ? null : request.solverOptions();

        payloadBuilder.validateInputs(period, options);

        Map<String, Object> weights = payloadBuilder.weightsSnapshot(period, options);
        OptimizationRun run = optimizationRunRecorder.start(period, manager, weights);

        AiSchedulingRequestDto payload = payloadBuilder.build(period, run.getId(), options);

        AiSchedulingResponseDto response;
        try {
            response = aiSchedulingClient.optimize(payload);
        } catch (AiServiceException ex) {
            optimizationRunRecorder.finishFailure(run.getId(), "FAILED", ex.getMessage(), null);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable");
        }

        String violation = validateProtocol(response, run, period);
        if (violation != null) {
            optimizationRunRecorder.finishFailure(run.getId(), "FAILED", "Invalid AI response: " + violation, null);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid AI response");
        }

        if ("SUCCESS".equals(response.status())) {
            OptimizationRun finished = optimizationRunRecorder.finishSuccess(run.getId(), response);
            return new OptimizeScheduleResponse(
                    finished.getId(),
                    period.getId(),
                    "SUCCESS",
                    response.solverStatus(),
                    response.totalPenalty(),
                    response.assignments().size(),
                    response.warnings(),
                    null,
                    finished.getStartedAt(),
                    finished.getFinishedAt());
        }

        String errorMessage = combineErrorMessage(response);
        OptimizationRun finished =
                optimizationRunRecorder.finishFailure(run.getId(), response.status(), errorMessage, response);
        return new OptimizeScheduleResponse(
                finished.getId(),
                period.getId(),
                response.status(),
                response.solverStatus(),
                response.totalPenalty(),
                response.assignments().size(),
                response.warnings(),
                errorMessage,
                finished.getStartedAt(),
                finished.getFinishedAt());
    }

    private static String validateProtocol(
            AiSchedulingResponseDto response, OptimizationRun run, SchedulePeriod period) {
        if (response == null) {
            return "null response";
        }
        if (!"1.0".equals(response.schemaVersion())) {
            return "schemaVersion must be 1.0";
        }
        if (response.runId() == null || !response.runId().equals(run.getId())) {
            return "runId mismatch";
        }
        if (response.periodId() == null || !response.periodId().equals(period.getId())) {
            return "periodId mismatch";
        }
        if (response.status() == null || !ALLOWED_STATUSES.contains(response.status())) {
            return "unsupported status";
        }
        if (response.solverStatus() == null || !ALLOWED_SOLVER_STATUSES.contains(response.solverStatus())) {
            return "unsupported solverStatus";
        }
        if (response.assignments() == null) {
            return "assignments is null";
        }
        if (response.penaltyDetails() == null) {
            return "penaltyDetails is null";
        }
        if (response.warnings() == null) {
            return "warnings is null";
        }
        if (response.diagnostics() == null) {
            return "diagnostics is null";
        }
        if (response.statistics() == null) {
            return "statistics is null";
        }
        if ("SUCCESS".equals(response.status()) && response.totalPenalty() == null) {
            return "totalPenalty is null for SUCCESS";
        }
        return null;
    }

    private static String combineErrorMessage(AiSchedulingResponseDto response) {
        List<String> parts = new ArrayList<>();
        if (response.diagnostics() != null) {
            for (AiSchedulingResponseDto.DiagnosticDto diagnostic : response.diagnostics()) {
                if (parts.size() >= 20) {
                    break;
                }
                if (diagnostic != null && StringUtils.hasText(diagnostic.message())) {
                    String code = diagnostic.code() == null ? "" : diagnostic.code() + ": ";
                    parts.add(code + diagnostic.message());
                }
            }
        }
        if (StringUtils.hasText(response.errorMessage())) {
            parts.add(response.errorMessage());
        }
        if (parts.isEmpty()) {
            return response.status();
        }
        StringBuilder capped = new StringBuilder();
        for (String part : parts) {
            int separatorLength = capped.length() > 0 ? 2 : 0;
            int remaining = Math.max(0, 4000 - capped.length() - separatorLength);
            if (remaining <= 0) {
                break;
            }
            if (separatorLength > 0) {
                capped.append("; ");
            }
            capped.append(part.substring(0, Math.min(part.length(), remaining)));
        }
        return capped.toString();
    }

    private Account requireManager(CurrentAccountPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required"));
        if (account.getRole() != AccountRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required");
        }
        return account;
    }
}
