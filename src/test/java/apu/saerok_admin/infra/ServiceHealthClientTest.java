package apu.saerok_admin.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import apu.saerok_admin.web.view.ServiceHealthStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.test.web.client.MockRestServiceServer;

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
        ResponseEntity<String> response = ResponseEntity.ok("I am healthy");
        when(restClient.get().uri(any(Function.class)).retrieve().toEntity(eq(String.class)))
                .thenReturn(response);

        ServiceHealthStatus status = client.checkHealth();

        assertThat(status.alive()).isTrue();
        assertThat(status.message()).isEqualTo("I am healthy");
        assertThat(status.checkedAt()).isEqualTo(FIXED_DATE_TIME);
    }

    @Test
    void checkHealthReturnsAliveStatusWithDefaultMessageWhenBodyIsBlank() {
        ResponseEntity<String> response = ResponseEntity.ok("   ");
        when(restClient.get().uri(any(Function.class)).retrieve().toEntity(eq(String.class)))
                .thenReturn(response);

        ServiceHealthStatus status = client.checkHealth();

        assertThat(status.alive()).isTrue();
        assertThat(status.message()).isEqualTo("서버가 정상 응답했습니다.");
        assertThat(status.checkedAt()).isEqualTo(FIXED_DATE_TIME);
    }

    @Test
    void checkHealthReturnsDownStatusWhenHealthEndpointRespondsWithServerError() {
        ResponseEntity<String> response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        when(restClient.get().uri(any(Function.class)).retrieve().toEntity(eq(String.class)))
                .thenReturn(response);

        ServiceHealthStatus status = client.checkHealth();

        assertThat(status.alive()).isFalse();
        assertThat(status.message()).isEqualTo("HTTP 상태 코드 500");
        assertThat(status.checkedAt()).isEqualTo(FIXED_DATE_TIME);
    }

    @Test
    void checkHealthReturnsFailureStatusWithExceptionMessageWhenRequestFails() {
        when(restClient.get().uri(any(Function.class)).retrieve().toEntity(eq(String.class)))
                .thenThrow(new RestClientException("연결 실패"));

        ServiceHealthStatus status = client.checkHealth();

        assertThat(status.alive()).isFalse();
        assertThat(status.message()).isEqualTo("연결 실패");
        assertThat(status.checkedAt()).isEqualTo(FIXED_DATE_TIME);
    }

    @Test
    void checkHealthReturnsGenericFailureMessageWhenExceptionMessageIsBlank() {
        when(restClient.get().uri(any(Function.class)).retrieve().toEntity(eq(String.class)))
                .thenThrow(new RestClientException("   "));

        ServiceHealthStatus status = client.checkHealth();

        assertThat(status.alive()).isFalse();
        assertThat(status.message()).isEqualTo("요청 중 오류가 발생했습니다.");
        assertThat(status.checkedAt()).isEqualTo(FIXED_DATE_TIME);
    }

    @Test
    void checkHealthUsesHealthPathWhenBaseUrlContainsApiPrefix() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost/api/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient prefixedRestClient = builder.build();
        ServiceHealthClient prefixedClient = new ServiceHealthClient(prefixedRestClient, fixedClock);

        server.expect(requestTo("http://localhost/health"))
                .andRespond(withSuccess("OK", MediaType.TEXT_PLAIN));

        ServiceHealthStatus status = prefixedClient.checkHealth();

        assertThat(status.alive()).isTrue();
        assertThat(status.message()).isEqualTo("OK");
        assertThat(status.checkedAt()).isEqualTo(FIXED_DATE_TIME);
        server.verify();
    }
}
