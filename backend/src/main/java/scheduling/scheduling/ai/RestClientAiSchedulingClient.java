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
    private final int maxAttempts;

    public RestClientAiSchedulingClient(
            @Value("${app.ai-service-url}") String aiServiceUrl,
            @Value("${app.ai.connect-timeout-seconds:30}") long connectTimeoutSeconds,
            @Value("${app.ai.read-timeout-seconds:300}") long readTimeoutSeconds,
            @Value("${app.ai.max-attempts:2}") int maxAttempts) {
        this(aiServiceUrl, Duration.ofSeconds(connectTimeoutSeconds), Duration.ofSeconds(readTimeoutSeconds), maxAttempts);
    }

    RestClientAiSchedulingClient(String aiServiceUrl, Duration connectTimeout, Duration readTimeout, int maxAttempts) {
        this.restClient = buildRestClient(aiServiceUrl, connectTimeout, readTimeout);
        this.maxAttempts = maxAttempts;
    }

    private static RestClient buildRestClient(String aiServiceUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(aiServiceUrl).requestFactory(requestFactory).build();
    }

    @Override
    public AiSchedulingResponseDto optimize(AiSchedulingRequestDto request) {
        RestClientException lastFailure = null;
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                AiSchedulingResponseDto response = restClient.post()
                        .uri("/api/v1/scheduling/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(AiSchedulingResponseDto.class);
                if (response == null) {
                    throw new AiServiceException("AI service returned empty response");
                }
                return response;
            } catch (RestClientException ex) {
                lastFailure = ex;
                if (attempt == attempts) {
                    break;
                }
            }
        }
        throw new AiServiceException("AI service request failed after " + attempts + " attempt(s)", lastFailure);
    }
}
