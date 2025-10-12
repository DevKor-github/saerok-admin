package apu.saerok_admin.infra.auth;

import apu.saerok_admin.security.BackendUnauthorizedException;
import apu.saerok_admin.security.LoginSessionManager;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Component
public class BackendAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    private final LoginSessionManager loginSessionManager;
    private final BackendAuthClient backendAuthClient;

    public BackendAuthorizationInterceptor(LoginSessionManager loginSessionManager, BackendAuthClient backendAuthClient) {
        this.loginSessionManager = loginSessionManager;
        this.backendAuthClient = backendAuthClient;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (isAuthRequest(request)) {
            return execution.execute(request, body);
        }

        HttpRequestWrapper authorizedRequest = wrapRequest(request);
        loginSessionManager.currentAccessToken()
                .ifPresent(token -> authorizedRequest.getHeaders().setBearerAuth(token));

        ClientHttpResponse response = execution.execute(authorizedRequest, body);
        if (response.getStatusCode() != HttpStatus.UNAUTHORIZED) {
            return response;
        }

        response.close();
        return retryWithRefreshedToken(request, body, execution);
    }

    private ClientHttpResponse retryWithRefreshedToken(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        Optional<String> currentToken = loginSessionManager.currentAccessToken();
        if (currentToken.isEmpty()) {
            return execution.execute(request, body);
        }

        BackendAuthClient.LoginSuccess refreshedTokens;
        try {
            refreshedTokens = backendAuthClient.refreshAccessToken();
        } catch (RestClientResponseException | ResourceAccessException | IllegalStateException ex) {
            loginSessionManager.clearCurrentSession();
            throw new BackendUnauthorizedException("토큰 갱신에 실패했습니다.", ex);
        }

        loginSessionManager.updateAccessToken(refreshedTokens.accessToken());
        loginSessionManager.writeRefreshCookiesToResponse(refreshedTokens.refreshCookies());

        HttpRequestWrapper retryRequest = wrapRequest(request);
        loginSessionManager.currentAccessToken()
                .ifPresent(token -> retryRequest.getHeaders().setBearerAuth(token));

        ClientHttpResponse retryResponse = execution.execute(retryRequest, body);
        if (retryResponse.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            retryResponse.close();
            loginSessionManager.clearCurrentSession();
            throw new BackendUnauthorizedException("세션이 만료되었습니다. 다시 로그인해주세요.");
        }

        return retryResponse;
    }

    private boolean isAuthRequest(HttpRequest request) {
        String path = request.getURI() != null ? request.getURI().getPath() : null;
        return path != null && path.contains("/auth/");
    }

    private HttpRequestWrapper wrapRequest(HttpRequest request) {
        return new HttpRequestWrapper(request) {
            private final HttpHeaders headers = new HttpHeaders();

            {
                headers.putAll(request.getHeaders());
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }
}
