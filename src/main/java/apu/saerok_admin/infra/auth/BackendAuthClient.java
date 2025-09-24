package apu.saerok_admin.infra.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class BackendAuthClient {

    private final RestClient authRestClient;

    public BackendAuthClient(@Qualifier("saerokAuthRestClient") RestClient authRestClient) {
        this.authRestClient = authRestClient;
    }

    public LoginSuccess kakaoLogin(String authorizationCode) {
        KakaoLoginPayload payload = new KakaoLoginPayload(authorizationCode);
        ResponseEntity<BackendAccessTokenResponse> response = authRestClient.post()
                .uri("/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(BackendAccessTokenResponse.class);
        return toLoginSuccess(response);
    }

    public LoginSuccess appleLogin(String authorizationCode) {
        AppleLoginPayload payload = new AppleLoginPayload(authorizationCode);
        ResponseEntity<BackendAccessTokenResponse> response = authRestClient.post()
                .uri("/auth/apple/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(BackendAccessTokenResponse.class);
        return toLoginSuccess(response);
    }

    public LoginSuccess refreshAccessToken() {
        RestClient.RequestHeadersSpec<?> requestSpec = authRestClient.post()
                .uri("/auth/refresh");
        extractRefreshCookie().ifPresent(cookie -> requestSpec.header(HttpHeaders.COOKIE, cookie));
        ResponseEntity<BackendAccessTokenResponse> response = requestSpec.retrieve()
                .toEntity(BackendAccessTokenResponse.class);
        return toLoginSuccess(response);
    }

    private Optional<String> extractRefreshCookie() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        HttpServletRequest request = attributes.getRequest();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> "refreshToken".equals(cookie.getName()))
                .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                .findFirst();
    }

    private LoginSuccess toLoginSuccess(ResponseEntity<BackendAccessTokenResponse> response) {
        BackendAccessTokenResponse body = response.getBody();
        if (body == null || !StringUtils.hasText(body.accessToken())) {
            throw new IllegalStateException("백엔드에서 유효한 액세스 토큰을 받지 못했습니다.");
        }
        List<String> cookies = Optional.ofNullable(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .map(List::copyOf)
                .orElse(List.of());
        return new LoginSuccess(body.accessToken(), cookies);
    }

    private record KakaoLoginPayload(String authorizationCode) {
    }

    private record AppleLoginPayload(String authorizationCode) {
    }

    private record BackendAccessTokenResponse(String accessToken, String signupStatus) {
    }

    public record LoginSuccess(String accessToken, List<String> refreshCookies) {

        public LoginSuccess {
            if (!StringUtils.hasText(accessToken)) {
                throw new IllegalArgumentException("accessToken must not be empty");
            }
            refreshCookies = refreshCookies == null ? List.of() : List.copyOf(refreshCookies);
        }
    }
}
