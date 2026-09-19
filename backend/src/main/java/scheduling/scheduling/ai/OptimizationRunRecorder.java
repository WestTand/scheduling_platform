package scheduling.scheduling.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.ai.dto.AiSchedulingResponseDto;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.OptimizationRun;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.repository.OptimizationRunRepository;

@Service
public class OptimizationRunRecorder {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;

    private final OptimizationRunRepository optimizationRunRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public OptimizationRunRecorder(
            OptimizationRunRepository optimizationRunRepository, Clock clock, ObjectMapper objectMapper) {
        this.optimizationRunRepository = optimizationRunRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OptimizationRun start(SchedulePeriod period, Account manager, Object weightsSnapshot) {
        OptimizationRun run = OptimizationRun.builder()
                .schedulePeriod(period)
                .schedulingRule(null)
                .startedBy(manager)
                .status("RUNNING")
                .weightsJson(writeValue(weightsSnapshot))
                .startedAt(LocalDateTime.now(clock))
                .build();
        return optimizationRunRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OptimizationRun finishSuccess(Long runId, AiSchedulingResponseDto response) {
        OptimizationRun run = findRun(runId);
        run.setStatus("SUCCESS");
        run.setFinishedAt(LocalDateTime.now(clock));
        run.setTotalPenalty(response.totalPenalty());
        run.setPenaltyDetailsJson(writeValue(response.penaltyDetails()));
        return optimizationRunRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OptimizationRun finishFailure(
            Long runId, String status, String errorMessage, AiSchedulingResponseDto response) {
        OptimizationRun run = findRun(runId);
        run.setStatus(status);
        run.setFinishedAt(LocalDateTime.now(clock));
        run.setErrorMessage(truncate(errorMessage));
        run.setPenaltyDetailsJson(response == null ? null : writeValue(response.penaltyDetails()));
        return optimizationRunRepository.save(run);
    }

    private OptimizationRun findRun(Long runId) {
        return optimizationRunRepository
                .findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Optimization run not found"));
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize optimization run payload", ex);
        }
    }

    private static String truncate(String value) {
        if (value != null && value.length() > ERROR_MESSAGE_MAX_LENGTH) {
            return value.substring(0, ERROR_MESSAGE_MAX_LENGTH);
        }
        return value;
    }
}
