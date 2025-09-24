package apu.saerok_admin.infra.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import apu.saerok_admin.security.BackendUnauthorizedException;
import apu.saerok_admin.security.LoginSession;
import apu.saerok_admin.security.LoginSessionManager;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class BackendAuthorizationInterceptorTest {

    private BackendAuthClient backendAuthClient;
    private LoginSessionManager loginSessionManager;
    private BackendAuthorizationInterceptor interceptor;
    private MockHttpServletRequest servletRequest;
    private MockHttpServletResponse servletResponse;

    @BeforeEach
    void setUp() {
        backendAuthClient = mock(BackendAuthClient.class);
        loginSessionManager = new LoginSessionManager();
        interceptor = new BackendAuthorizationInterceptor(loginSessionManager, backendAuthClient);
        servletRequest = new MockHttpServletRequest();
        servletResponse = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest, servletResponse));
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute(LoginSession.ATTRIBUTE_NAME, new LoginSession("old-token"));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void attachesBearerTokenAndRefreshesWhenUnauthorized() throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://localhost/api/resource");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        MockClientHttpResponse unauthorized = new MockClientHttpResponse(new byte[0], HttpStatus.UNAUTHORIZED);
        MockClientHttpResponse success = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        org.mockito.Mockito.when(execution.execute(any(HttpRequest.class), any(byte[].class)))
                .thenReturn(unauthorized)
                .thenReturn(success);
        List<String> cookies = List.of("refreshToken=new; Path=/; HttpOnly");
        org.mockito.Mockito.when(backendAuthClient.refreshAccessToken())
                .thenReturn(new BackendAuthClient.LoginSuccess("new-token", cookies));

        ClientHttpResponse response = interceptor.intercept(request, new byte[0], execution);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(execution, times(2)).execute(requestCaptor.capture(), any(byte[].class));
        List<HttpRequest> captured = requestCaptor.getAllValues();
        assertThat(captured.get(0).getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer old-token");
        assertThat(captured.get(1).getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer new-token");
        assertThat(servletResponse.getHeaderValues(HttpHeaders.SET_COOKIE)).containsExactlyElementsOf(cookies);
        LoginSession updated = (LoginSession) servletRequest.getSession(false).getAttribute(LoginSession.ATTRIBUTE_NAME);
        assertThat(updated.accessToken()).isEqualTo("new-token");
    }

    @Test
    void clearsSessionAndThrowsWhenRefreshFails() throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://localhost/api/metrics");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        MockClientHttpResponse unauthorized = new MockClientHttpResponse(new byte[0], HttpStatus.UNAUTHORIZED);
        org.mockito.Mockito.when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(unauthorized);
        org.mockito.Mockito.when(backendAuthClient.refreshAccessToken())
                .thenThrow(new RestClientResponseException("unauthorized", HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, null, null));

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
                .isInstanceOf(BackendUnauthorizedException.class);

        verify(execution, times(1)).execute(any(HttpRequest.class), any(byte[].class));
        verifyNoMoreInteractions(execution);
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            assertThat(session.getAttribute(LoginSession.ATTRIBUTE_NAME)).isNull();
        }
        assertThat(servletResponse.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
}
