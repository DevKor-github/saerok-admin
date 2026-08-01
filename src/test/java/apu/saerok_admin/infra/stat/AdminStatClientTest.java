package apu.saerok_admin.infra.stat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.stat.dto.CurrentUserStatResponse;

class AdminStatClientTest {

    private WireMockServer wireMockServer;
    private AdminStatClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        RestClient restClient = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        client = new AdminStatClient(restClient, new SaerokApiProps(wireMockServer.baseUrl(), "/api/v1"));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void fetchCurrentUserStats_getsCurrentUserStatsEndpoint() {
        stubFor(get(urlEqualTo("/api/v1/admin/stats/current-users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "completedUserCount": 4,
                                  "signupSourceCounts": {"INSTAGRAM": 2, "UNKNOWN": 1},
                                  "activePushUserCountsByPlatform": {"IOS": 2, "ANDROID": 1}
                                }
                                """)));

        CurrentUserStatResponse response = client.fetchCurrentUserStats();

        verify(getRequestedFor(urlEqualTo("/api/v1/admin/stats/current-users")));
        assertThat(response.completedUserCount()).isEqualTo(4L);
        assertThat(response.signupSourceCounts()).containsEntry("INSTAGRAM", 2L);
        assertThat(response.activePushUserCountsByPlatform()).containsEntry("ANDROID", 1L);
    }
}
