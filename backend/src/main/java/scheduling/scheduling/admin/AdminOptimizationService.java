package scheduling.scheduling.admin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.OptimizeScheduleRequest;
import scheduling.scheduling.admin.dto.OptimizeScheduleResponse;
import scheduling.scheduling.ai.AiApplyException;
import scheduling.scheduling.ai.AiAssignmentPersistenceService;
import scheduling.scheduling.ai.AiSchedulingClient;
import scheduling.scheduling.ai.AiSchedulingPayloadBuilder;
import scheduling.scheduling.ai.AiServiceException;
import scheduling.scheduling.ai.ApplyResult;
import scheduling.scheduling.ai.OptimizationRunRecorder;
import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;
import scheduling.scheduling.ai.dto.AiSchedulingResponseDto;
import scheduling.scheduling.audit.ActivityLogService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.OptimizationRun;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;

@Service
public class AdminOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(AdminOptimizationService.class);

    private static final Set<String> ALLOWED_STATUSES = Set.of("SUCCESS", "INFEASIBLE", "FAILED");
    private static final Set<String> ALLOWED_SOLVER_STATUSES =
            Set.of("OPTIMAL", "FEASIBLE", "INFEASIBLE", "UNKNOWN", "MODEL_INVALID");

    private final AccountRepository accountRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final AiSchedulingPayloadBuilder payloadBuilder;
    private final OptimizationRunRecorder optimizationRunRecorder;
    private final AiSchedulingClient aiSchedulingClient;
    private final AiAssignmentPersistenceService assignmentPersistenceService;
    private final ActivityLogService activityLogService;

    public AdminOptimizationService(
            AccountRepository accountRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            AiSchedulingPayloadBuilder payloadBuilder,
            OptimizationRunRecorder optimizationRunRecorder,
            AiSchedulingClient aiSchedulingClient,
            AiAssignmentPersistenceService assignmentPersistenceService,
            ActivityLogService activityLogService) {
        this.accountRepository = accountRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.payloadBuilder = payloadBuilder;
        this.optimizationRunRecorder = optimizationRunRecorder;
        this.aiSchedulingClient = aiSchedulingClient;
        this.assignmentPersistenceService = assignmentPersistenceService;
        this.activityLogService = activityLogService;
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
        List<Long> employeeIds = request == null ? null : request.employeeIds();

        AiSchedulingPayloadBuilder.PayloadSnapshot snapshot = employeeIds == null || employeeIds.isEmpty()
                ? payloadBuilder.prepareSnapshot(period, options)
                : payloadBuilder.prepareSnapshot(period, options, employeeIds);

        Map<String, Object> weights = payloadBuilder.weightsSnapshotFrom(snapshot);
        OptimizationRun run = optimizationRunRecorder.start(period, manager, weights);

        AiSchedulingRequestDto payload = payloadBuilder.buildFrom(snapshot, period, run.getId());

        AiSchedulingResponseDto response = null;
        try {
            response = aiSchedulingClient.optimize(payload);

            String violation = validateProtocol(response, run, period);
            if (violation != null) {
                OptimizationRun failed = optimizationRunRecorder.finishFailure(
                        run.getId(), "FAILED", "Invalid AI response: " + violation, null);
                recordOptimizationRun(manager, failed);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid AI response");
            }

            if ("SUCCESS".equals(response.status())) {
                ApplyResult applyResult = assignmentPersistenceService.apply(period, snapshot, response);
                log.debug("Applied AI assignments: {} inserted, {} skipped", applyResult.inserted(), applyResult.skipped());
                OptimizationRun finished = optimizationRunRecorder.finishSuccess(run.getId(), response);
                recordOptimizationRun(manager, finished);
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
            recordOptimizationRun(manager, finished);
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
        } catch (AiServiceException ex) {
            OptimizationRun failed = optimizationRunRecorder.finishFailure(run.getId(), "FAILED", ex.getMessage(), null);
            recordOptimizationRun(manager, failed);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable");
        } catch (AiApplyException ex) {
            OptimizationRun failed = optimizationRunRecorder.finishFailure(
                    run.getId(), "FAILED", "Failed to apply AI assignments: " + ex.getMessage(), response);
            recordOptimizationRun(manager, failed);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            try {
                OptimizationRun failed = optimizationRunRecorder.finishFailure(
                        run.getId(), "FAILED", "Unexpected optimization failure: " + ex, null);
                recordOptimizationRun(manager, failed);
            } catch (RuntimeException recordingFailure) {
                // Do not mask the original failure if the safety-net recording itself fails.
                log.warn("Failed to record failed optimization run {}", run.getId(), recordingFailure);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable", ex);
        }
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
        if ("SUCCESS".equals(response.status())) {
            Set<String> seen = new HashSet<>();
            for (AiSchedulingResponseDto.AssignmentDto item : response.assignments()) {
                if (item == null) {
                    return "null assignment item";
                }
                if (item.employeeId() == null || item.workShiftId() == null) {
                    return "assignment has null ids";
                }
                String pair = item.employeeId() + ":" + item.workShiftId();
                if (!seen.add(pair)) {
                    return "duplicate assignment pair";
                }
            }
        } else if (!response.assignments().isEmpty()) {
            return "assignments present for non-SUCCESS";
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
                    parts.add(formatDiagnostic(diagnostic));
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

    private static String formatDiagnostic(AiSchedulingResponseDto.DiagnosticDto diagnostic) {
        if (!StringUtils.hasText(diagnostic.code())) {
            return diagnostic.message();
        }
        if (diagnostic.employeeId() == null && diagnostic.workShiftId() == null) {
            return diagnostic.code() + ": " + diagnostic.message();
        }
        StringBuilder builder = new StringBuilder("[").append(diagnostic.code());
        if (diagnostic.employeeId() != null) {
            builder.append(" employeeId=").append(diagnostic.employeeId());
        }
        if (diagnostic.workShiftId() != null) {
            builder.append(" workShiftId=").append(diagnostic.workShiftId());
        }
        return builder.append("] ").append(diagnostic.message()).toString();
    }

    private void recordOptimizationRun(Account manager, OptimizationRun finished) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("runId", finished.getId());
        snapshot.put("periodId", finished.getSchedulePeriod().getId());
        snapshot.put("status", finished.getStatus());
        if (finished.getTotalPenalty() != null) {
            snapshot.put("totalPenalty", finished.getTotalPenalty());
        }
        try {
            activityLogService.record(manager, "OPTIMIZE", "optimization_runs", finished.getId(), null, snapshot);
        } catch (RuntimeException ignored) {
            // Optimization status is authoritative; audit failures must not rewrite or mask it.
        }
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
