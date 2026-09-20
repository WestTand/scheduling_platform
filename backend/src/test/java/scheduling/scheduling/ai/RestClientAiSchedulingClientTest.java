package scheduling.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.ai.dto.AiSchedulingRequestDto;

class RestClientAiSchedulingClientTest {

    private static final String SUCCESS_JSON = """
            {
              "schemaVersion":"1.0",
              "runId":900,
              "periodId":5,
              "status":"SUCCESS",
              "solverStatus":"OPTIMAL",
              "assignments":[],
              "totalPenalty":0,
              "penaltyDetails":{},
              "warnings":[],
              "diagnostics":[],
              "statistics":{"wallTimeSeconds":0,"conflicts":0,"branches":0}
            }
            """;

    @Test
    void retriesTransientServerFailureThenReturnsResponse() throws IOException {
        AtomicInteger counter = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/scheduling/optimize", exchange -> {
            exchange.getRequestBody().readAllBytes();
            int call = counter.incrementAndGet();
            if (call == 1) {
                sendJson(exchange, 503, "{\"detail\":\"try again\"}");
            } else {
                sendJson(exchange, 200, SUCCESS_JSON);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            RestClientAiSchedulingClient client = new RestClientAiSchedulingClient(
                    "http://localhost:" + port, Duration.ofSeconds(1), Duration.ofSeconds(2), 2);

            var response = client.optimize(emptyRequest());

            assertThat(response.status()).isEqualTo("SUCCESS");
            assertThat(counter.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void stopsAfterConfiguredAttemptCount() throws IOException {
        AtomicInteger counter = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/scheduling/optimize", exchange -> {
            exchange.getRequestBody().readAllBytes();
            counter.incrementAndGet();
            sendJson(exchange, 503, "{\"detail\":\"try again\"}");
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            RestClientAiSchedulingClient client = new RestClientAiSchedulingClient(
                    "http://localhost:" + port, Duration.ofSeconds(1), Duration.ofSeconds(2), 3);

            assertThatThrownBy(() -> client.optimize(emptyRequest()))
                    .isInstanceOf(AiServiceException.class);
            assertThat(counter.get()).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsRepeatedServerFailuresToAiServiceException() throws IOException {
        AtomicInteger counter = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/scheduling/optimize", exchange -> {
            exchange.getRequestBody().readAllBytes();
            counter.incrementAndGet();
            sendJson(exchange, 503, "{\"detail\":\"try again\"}");
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            RestClientAiSchedulingClient client = new RestClientAiSchedulingClient(
                    "http://localhost:" + port, Duration.ofSeconds(1), Duration.ofSeconds(2), 2);

            assertThatThrownBy(() -> client.optimize(emptyRequest()))
                    .isInstanceOf(AiServiceException.class)
                    .hasMessageContaining("after 2 attempt");
            assertThat(counter.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static AiSchedulingRequestDto emptyRequest() {
        return new AiSchedulingRequestDto(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
