package scheduling.scheduling.ai;

import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;
import scheduling.scheduling.ai.dto.AiSchedulingResponseDto;

public interface AiSchedulingClient {
    AiSchedulingResponseDto optimize(AiSchedulingRequestDto request);
}
