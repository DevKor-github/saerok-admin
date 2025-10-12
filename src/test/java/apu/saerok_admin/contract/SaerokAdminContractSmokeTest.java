package apu.saerok_admin.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import apu.saerok_admin.infra.SaerokApiClientConfig;

@SpringBootTest(
  classes = {SaerokApiClientConfig.class},
  properties = "spring.main.web-application-type=none"
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SaerokAdminContractSmokeTest {

  static WireMockServer wm;

  @Autowired
  RestClient restClient;

  @BeforeAll
  void start() {
    if (wm == null) {
      wm = new WireMockServer(0);
    }
    if (!wm.isRunning()) {
      wm.start();
    }
    configureFor("localhost", wm.port());
  }

  @AfterAll
  void stop() {
    if (wm != null) wm.stop();
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("saerok.api.base-url", () -> {
      if (wm == null) {
        wm = new WireMockServer(0);
      }
      if (!wm.isRunning()) {
        wm.start();
        configureFor("localhost", wm.port());
      }
      return "http://localhost:" + wm.port();
    });
  }

  @Test
  void refreshToken_contract_smoke() {
    stubFor(post(urlEqualTo("/api/v1/auth/refresh"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type","application/json")
        .withBody("{\"accessToken\":\"dummy\",\"signupStatus\":\"COMPLETED\"}")));

    var res = restClient.post()
      .uri("/api/v1/auth/refresh")
      .retrieve()
      .toEntity(String.class);

    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(res.getBody()).contains("accessToken");
  }
}
