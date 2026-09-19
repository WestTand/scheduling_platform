package scheduling.scheduling.admin.dto;

import jakarta.validation.Valid;
import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;

public record OptimizeScheduleRequest(@Valid AiSchedulingRequestDto.SolverOptionsDto solverOptions) {}
