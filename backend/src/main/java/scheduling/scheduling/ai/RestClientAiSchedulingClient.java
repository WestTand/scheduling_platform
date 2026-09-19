package scheduling.scheduling.ai;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;
import scheduling.scheduling.ai.dto.AiSchedulingResponseDto;

@Component
public class RestClientAiSchedulingClient implements AiSchedulingClient {

    private final RestClient restClient;

    public RestClientAiSchedulingClient(@Value("${app.ai-service-url}") String aiServiceUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(30));
        requestFactory.setReadTimeout(Duration.ofMinutes(5));
        this.restClient = RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public AiSchedulingResponseDto optimize(AiSchedulingRequestDto request) {
        AiSchedulingResponseDto response;
        try {
            response = restClient.post()
                    .uri("/api/v1/scheduling/optimize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiSchedulingResponseDto.class);
        } catch (RestClientException ex) {
            throw new AiServiceException("AI service request failed", ex);
        }
        if (response == null) {
            throw new AiServiceException("AI service returned empty response");
        }
        return response;
    }
}
