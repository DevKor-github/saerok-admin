package apu.saerok_admin.infra;

import apu.saerok_admin.web.view.ServiceHealthStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ServiceHealthClient {

    private final RestClient saerokRestClient;
    private final Clock clock;

    public ServiceHealthClient(RestClient saerokRestClient, Clock clock) {
        this.saerokRestClient = saerokRestClient;
        this.clock = clock;
    }

    public ServiceHealthStatus checkHealth() {
        LocalDateTime checkedAt = LocalDateTime.now(clock);
        try {
            ResponseEntity<String> response = saerokRestClient.get()
                    .uri(uriBuilder -> uriBuilder.replacePath("/health").replaceQuery(null).build())
                    .retrieve()
                    .toEntity(String.class);

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

    private String extractStatusMessage(String body) {
        if (body == null || body.isBlank()) {
            return "서버가 정상 응답했습니다.";
        }

        return body;
    }
}
