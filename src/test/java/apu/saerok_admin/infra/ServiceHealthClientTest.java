package apu.saerok_admin.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import apu.saerok_admin.web.view.ServiceHealthStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class ServiceHealthClientTest {

    private static final LocalDateTime FIXED_DATE_TIME = LocalDateTime.of(2024, 6, 1, 21, 0);

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    private ServiceHealthClient client;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2024-06-01T12:00:00Z"), ZoneId.of("Asia/Seoul"));
        client = new ServiceHealthClient(restClient, fixedClock);
    }

    @Test
    void checkHealthReturnsAliveStatusWithResponseMessageWhenHealthEndpointIsUp() {
        ResponseEntity<Map<String, Object>> response = ResponseEntity.ok(Map.of("status", "UP"));
        when(restClient.get().uri(anyString()).retrieve().toEntity(any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        ServiceHealthStatus status = client.checkHealth();

        assertThat(status.alive()).isTrue();
        assertThat(status.message()).isEqualTo("상태: UP");
        assertThat(status.checkedAt()).isEqualTo(FIXED_DATE_TIME);
    }

    @Test
    void checkHealthReturnsDownStatusWhenHealthEndpointRespondsWithServerError() {
        ResponseEntity<Map<String, Object>> response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        when(restClient.get().uri(anyString()).retrieve().toEntity(any(ParameterizedTypeReference.class)))
                .thenReturn(response);

        ServiceHealthStatus status = client.checkHealth();

        assertThat(status.alive()).isFalse();
        assertThat(status.message()).isEqualTo("HTTP 상태 코드 500");
        assertThat(status.checkedAt()).isEqualTo(FIXED_DATE_TIME);
    }

    @Test
    void checkHealthReturnsFailureStatusWithExceptionMessageWhenRequestFails() {
        when(restClient.get().uri(anyString()).retrieve().toEntity(any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("연결 실패"));

        ServiceHealthStatus status = client.checkHealth();

        assertThat(status.alive()).isFalse();
        assertThat(status.message()).isEqualTo("연결 실패");
        assertThat(status.checkedAt()).isEqualTo(FIXED_DATE_TIME);
    }

    @Test
    void checkHealthReturnsGenericFailureMessageWhenExceptionMessageIsBlank() {
        when(restClient.get().uri(anyString()).retrieve().toEntity(any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("   "));

        ServiceHealthStatus status = client.checkHealth();

        assertThat(status.alive()).isFalse();
        assertThat(status.message()).isEqualTo("요청 중 오류가 발생했습니다.");
        assertThat(status.checkedAt()).isEqualTo(FIXED_DATE_TIME);
    }
}
