package apu.saerok_admin.infra;

import apu.saerok_admin.web.view.ServiceHealthStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ServiceHealthClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient saerokRestClient;
    private final Clock clock;

    public ServiceHealthClient(RestClient saerokRestClient, Clock clock) {
        this.saerokRestClient = saerokRestClient;
        this.clock = clock;
    }

    public ServiceHealthStatus checkHealth() {
        LocalDateTime checkedAt = LocalDateTime.now(clock);
        try {
            ResponseEntity<Map<String, Object>> response = saerokRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .toEntity(MAP_TYPE);

            boolean alive = response.getStatusCode().is2xxSuccessful();
            String message = alive
                    ? extractStatusMessage(response.getBody())
                    : "HTTP 상태 코드 " + response.getStatusCode().value();

            return new ServiceHealthStatus(alive, message, checkedAt);
        } catch (RestClientException exception) {
            String message = exception.getMessage();
            if (message == null || message.isBlank()) {
                message = "요청 중 오류가 발생했습니다.";
            }
            return new ServiceHealthStatus(false, message, checkedAt);
        }
    }

    private String extractStatusMessage(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return "서버가 정상 응답했습니다.";
        }

        Object status = body.get("status");
        if (status != null) {
            return "상태: " + Objects.toString(status);
        }

        return "서버가 정상 응답했습니다.";
    }
}
