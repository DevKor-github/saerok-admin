package apu.saerok_admin.infra.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.notification.dto.AdminSendMessageRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class AdminNotificationClientTest {

    private WireMockServer wireMockServer;
    private AdminNotificationClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        RestClient restClient = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        client = new AdminNotificationClient(restClient, new SaerokApiProps(wireMockServer.baseUrl(), "/api/v1"));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void sendMessage_postsToAdminNotificationEndpoint() {
        stubFor(post(urlEqualTo("/api/v1/admin/notifications/messages"))
                .willReturn(aResponse().withStatus(204)));

        client.sendMessage(new AdminSendMessageRequest(List.of(501L, 502L), "Notice", "Message body"));

        verify(postRequestedFor(urlEqualTo("/api/v1/admin/notifications/messages"))
                .withRequestBody(equalToJson("""
                        {
                          "userIds": [501, 502],
                          "title": "Notice",
                          "body": "Message body"
                        }
                        """)));
    }
}
