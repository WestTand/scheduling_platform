package scheduling.scheduling.admin.dto;

import jakarta.validation.Valid;
import java.util.List;
import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;

public record OptimizeScheduleRequest(
        @Valid AiSchedulingRequestDto.SolverOptionsDto solverOptions,
        List<Long> employeeIds) {
    public OptimizeScheduleRequest(AiSchedulingRequestDto.SolverOptionsDto solverOptions) {
        this(solverOptions, null);
    }
}
