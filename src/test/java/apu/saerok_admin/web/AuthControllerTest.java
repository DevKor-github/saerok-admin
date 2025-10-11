package apu.saerok_admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import apu.saerok_admin.config.SocialLoginProperties;
import apu.saerok_admin.config.UnsplashProperties;
import apu.saerok_admin.infra.CurrentAdminClient;
import apu.saerok_admin.infra.auth.BackendAuthClient;
import apu.saerok_admin.security.LoginSession;
import apu.saerok_admin.security.LoginSessionManager;
import apu.saerok_admin.security.OAuthStateManager;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientResponseException;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({OAuthStateManager.class, AuthControllerTest.TestConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackendAuthClient backendAuthClient;

    @MockBean
    private LoginSessionManager loginSessionManager;

    @MockBean
    private CurrentAdminClient currentAdminClient;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        given(currentAdminClient.fetchCurrentAdminProfile()).willReturn(Optional.empty());
    }

    @Test
    void kakaoCallbackExchangesCodeAndEstablishesSession() throws Exception {
        session.setAttribute(OAuthStateManager.ATTRIBUTE_NAME, "expected-state");
        List<String> cookies = List.of("refreshToken=abc; Path=/; HttpOnly");
        BackendAuthClient.LoginSuccess loginSuccess = new BackendAuthClient.LoginSuccess("access-token", cookies);
        given(backendAuthClient.kakaoLogin("auth-code")).willReturn(loginSuccess);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/callback/kakao")
                                .param("code", "auth-code")
                                .param("state", "expected-state")
                                .session(session)
                )
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/"));

        ArgumentCaptor<LoginSession> sessionCaptor = ArgumentCaptor.forClass(LoginSession.class);
        verify(loginSessionManager).establishSession(any(HttpServletRequest.class), sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().accessToken()).isEqualTo("access-token");
        verify(loginSessionManager).writeRefreshCookiesToResponse(cookies);
    }

    @Test
    void kakaoCallbackWithInvalidStateRedirectsToLoginError() throws Exception {
        session.setAttribute(OAuthStateManager.ATTRIBUTE_NAME, "expected-state");

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/callback/kakao")
                                .param("code", "auth-code")
                                .param("state", "different")
                                .session(session)
                )
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login?error=state"));

        verifyNoInteractions(backendAuthClient);
        verify(loginSessionManager, never()).establishSession(any(HttpServletRequest.class), any(LoginSession.class));
    }

    @Test
    void kakaoCallbackWithBackendErrorMessagePropagatesToLoginPage() throws Exception {
        session.setAttribute(OAuthStateManager.ATTRIBUTE_NAME, "expected-state");
        RestClientResponseException backendException = new RestClientResponseException(
                "Backend error",
                400,
                "Bad Request",
                new HttpHeaders(),
                "{\"status\":400,\"message\":\"백엔드에서 온 오류\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        given(backendAuthClient.kakaoLogin("auth-code")).willThrow(backendException);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/callback/kakao")
                                .param("code", "auth-code")
                                .param("state", "expected-state")
                                .session(session)
                )
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        "/login?error=login&message=%EB%B0%B1%EC%97%94%EB%93%9C%EC%97%90%EC%84%9C%20%EC%98%A8%20%EC%98%A4%EB%A5%98"
                ));

        verify(loginSessionManager).clearSession(any(HttpServletRequest.class));
    }

    @Test
    void socialCallbackWithoutExistingSessionRedirectsToSessionError() throws Exception {
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/callback/kakao")
                                .param("code", "auth-code")
                                .param("state", "expected-state")
                )
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login?error=session"));

        verifyNoInteractions(backendAuthClient);
        verifyNoInteractions(loginSessionManager);
    }

    @Test
    void appleCallbackAcceptsFormPost() throws Exception {
        session.setAttribute(OAuthStateManager.ATTRIBUTE_NAME, "state-token");
        List<String> cookies = List.of("refreshToken=xyz; Path=/; HttpOnly");
        BackendAuthClient.LoginSuccess loginSuccess = new BackendAuthClient.LoginSuccess("apple-token", cookies);
        given(backendAuthClient.appleLogin("apple-code")).willReturn(loginSuccess);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/callback/apple")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("code", "apple-code")
                                .param("state", "state-token")
                                .session(session)
                )
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/"));

        verify(loginSessionManager).establishSession(any(HttpServletRequest.class), eq(new LoginSession("apple-token")));
        verify(loginSessionManager).writeRefreshCookiesToResponse(cookies);
    }

    @Test
    void headRequestDoesNotMutateSessionState() throws Exception {
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/login")
                                .session(session)
                )
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("auth/login"));

        String state = (String) session.getAttribute(OAuthStateManager.ATTRIBUTE_NAME);
        assertThat(state).isNotNull();

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/login")
                                .session(session)
                )
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        assertThat(session.getAttribute(OAuthStateManager.ATTRIBUTE_NAME)).isEqualTo(state);
    }

    static class TestConfig {

        @Bean
        SocialLoginProperties socialLoginProperties() {
            return new SocialLoginProperties(
                    new SocialLoginProperties.Provider("kakao-client", URI.create("http://localhost/auth/callback/kakao")),
                    new SocialLoginProperties.Provider("apple-client", URI.create("http://localhost/auth/callback/apple"))
            );
        }

        @Bean
        UnsplashProperties unsplashProperties() {
            UnsplashProperties properties = new UnsplashProperties();
            properties.setAccessKey("test-access-key");
            properties.setAppName("test-app");
            return properties;
        }
    }
}
