package apu.saerok_admin.infra.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class BackendAuthClientTest {

    private WireMockServer wireMockServer;
    private BackendAuthClient backendAuthClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/api/v1")
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        backendAuthClient = new BackendAuthClient(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void kakaoLoginSendsRequestIncludingApiPrefix() {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/auth/kakao/login"))
                .withRequestBody(equalToJson("{" +
                        "\"authorizationCode\":\"auth-code\"" +
                        "}"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withHeader("Set-Cookie", "refreshToken=abc; Path=/; HttpOnly")
                        .withBody("{" +
                                "\"accessToken\":\"backend-token\"," +
                                "\"signupStatus\":\"COMPLETED\"" +
                                "}")));

        BackendAuthClient.LoginSuccess loginSuccess = backendAuthClient.kakaoLogin("auth-code");

        assertThat(loginSuccess.accessToken()).isEqualTo("backend-token");
        assertThat(loginSuccess.refreshCookies()).contains("refreshToken=abc; Path=/; HttpOnly");

        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/auth/kakao/login")));
    }
}
