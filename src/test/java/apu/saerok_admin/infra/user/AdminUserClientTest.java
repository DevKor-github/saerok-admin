package apu.saerok_admin.infra.user;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.user.dto.AdminUserListResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class AdminUserClientTest {

    private WireMockServer wireMockServer;
    private AdminUserClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        RestClient restClient = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        client = new AdminUserClient(restClient, new SaerokApiProps(wireMockServer.baseUrl(), "/api/v1"));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void listUsers_getsAdminUsersEndpoint() {
        stubFor(get(urlEqualTo("/api/v1/admin/users?q=sol&page=1&size=10"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "users": [
                                    { "id": 501, "nickname": "sol" }
                                  ],
                                  "page": 1,
                                  "size": 10,
                                  "totalElements": 1,
                                  "totalPages": 1
                                }
                                """)));

        AdminUserListResponse response = client.listUsers("sol", 1, 10);

        verify(getRequestedFor(urlEqualTo("/api/v1/admin/users?q=sol&page=1&size=10")));
        assertThat(response.users()).hasSize(1);
        assertThat(response.users().getFirst().id()).isEqualTo(501L);
        assertThat(response.users().getFirst().nickname()).isEqualTo("sol");
        assertThat(response.totalElements()).isEqualTo(1);
    }
}
