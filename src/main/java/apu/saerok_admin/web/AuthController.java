package apu.saerok_admin.web;

import apu.saerok_admin.config.SocialLoginProperties;
import apu.saerok_admin.config.UnsplashProperties;
import apu.saerok_admin.infra.auth.BackendAuthClient;
import apu.saerok_admin.security.LoginSession;
import apu.saerok_admin.security.LoginSessionManager;
import apu.saerok_admin.security.OAuthStateManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SocialLoginProperties socialLoginProperties;
    private final OAuthStateManager oAuthStateManager;
    private final BackendAuthClient backendAuthClient;
    private final LoginSessionManager loginSessionManager;
    private final ObjectMapper objectMapper;

    // ▼ 추가: Unsplash 설정
    private final UnsplashProperties unsplashProperties;

    public AuthController(
            SocialLoginProperties socialLoginProperties,
            OAuthStateManager oAuthStateManager,
            BackendAuthClient backendAuthClient,
            LoginSessionManager loginSessionManager,
            ObjectMapper objectMapper,
            UnsplashProperties unsplashProperties // ← 추가 주입
    ) {
        this.socialLoginProperties = socialLoginProperties;
        this.oAuthStateManager = oAuthStateManager;
        this.backendAuthClient = backendAuthClient;
        this.loginSessionManager = loginSessionManager;
        this.objectMapper = objectMapper;
        this.unsplashProperties = unsplashProperties; // ← 보관
    }

    @RequestMapping(value = "/login", method = {RequestMethod.GET, RequestMethod.HEAD})
    public String login(HttpServletRequest request, HttpServletResponse response, Model model) {
        model.addAttribute("pageTitle", "로그인");

        String state;
        if (HttpMethod.HEAD.matches(request.getMethod())) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);

            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                state = (String) existingSession.getAttribute(OAuthStateManager.ATTRIBUTE_NAME);
                log.debug("HEAD /login request reusing OAuth state token for session {}", existingSession.getId());
            } else {
                state = null;
                log.debug("HEAD /login request without existing session; skipping OAuth state token creation.");
            }
        } else {
            HttpSession session = request.getSession(true);
            String existingState = (String) session.getAttribute(OAuthStateManager.ATTRIBUTE_NAME);
            if (existingState == null) {
                state = oAuthStateManager.createState(session);
                log.debug("Created new OAuth state token for session {}", session.getId());
            } else {
                state = existingState;
                log.debug("Reusing existing OAuth state token for session {}", session.getId());
            }
        }

        String effectiveState = state != null ? state : "";
        model.addAttribute("kakaoAuthUrl", buildKakaoAuthorizeUrl(effectiveState));
        model.addAttribute("appleAuthUrl", buildAppleAuthorizeUrl(effectiveState));

        // ▼ 추가: 템플릿에서 Unsplash 키/앱명을 읽을 수 있도록 전달
        model.addAttribute("unsplashAccessKey", unsplashProperties.getAccessKey());
        model.addAttribute("unsplashAppName", unsplashProperties.getAppName());

        resolveLoginError(request).ifPresent(error -> model.addAttribute("loginError", error));

        return "auth/login";
    }

    @GetMapping("/auth/callback/kakao")
    public String handleKakaoCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            HttpServletRequest request
    ) {
        return handleSocialCallback(request, code, state, backendAuthClient::kakaoLogin);
    }

    @RequestMapping(value = "/auth/callback/apple", method = {RequestMethod.POST, RequestMethod.GET})
    public String handleAppleCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            HttpServletRequest request
    ) {
        return handleSocialCallback(request, code, state, backendAuthClient::appleLogin);
    }

    private String handleSocialCallback(
            HttpServletRequest request,
            String code,
            String state,
            Function<String, BackendAuthClient.LoginSuccess> loginFunction
    ) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            log.warn("OAuth callback received without an existing session. Redirecting to login with session error.");
            return "redirect:/login?error=session";
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            log.warn("OAuth callback received without required parameters. codePresent={}, statePresent={}",
                    StringUtils.hasText(code), StringUtils.hasText(state));
            return "redirect:/login?error=callback";
        }
        if (!oAuthStateManager.consumeState(session, state)) {
            log.warn("State validation failed for session {} and state {}", session.getId(), state);
            return "redirect:/login?error=state";
        }
        try {
            log.info("Processing social login with session {}", session.getId());
            BackendAuthClient.LoginSuccess loginSuccess = loginFunction.apply(code);
            log.info("Social login succeeded for session {}. Writing access token and refresh cookies.", session.getId());
            loginSessionManager.establishSession(request, new LoginSession(loginSuccess.accessToken()));
            loginSessionManager.writeRefreshCookiesToResponse(loginSuccess.refreshCookies());
            return "redirect:/";
        } catch (RestClientException | IllegalStateException exception) {
            log.error("Failed to complete social login due to backend error: {}", exception.getMessage(), exception);
            loginSessionManager.clearSession(request);
            String backendMessage = extractBackendErrorMessage(exception).orElse(null);
            UriComponentsBuilder redirectUriBuilder = UriComponentsBuilder.fromPath("/login")
                    .queryParam("error", "login");
            if (StringUtils.hasText(backendMessage)) {
                redirectUriBuilder.queryParam("message", backendMessage);
            }
            return "redirect:" + redirectUriBuilder.toUriString();
        }
    }

    private Optional<String> extractBackendErrorMessage(Exception exception) {
        if (exception instanceof RestClientResponseException responseException) {
            String responseBody = responseException.getResponseBodyAsString();
            if (StringUtils.hasText(responseBody)) {
                try {
                    BackendErrorResponse errorResponse = objectMapper.readValue(responseBody, BackendErrorResponse.class);
                    if (StringUtils.hasText(errorResponse.message())) {
                        return Optional.of(errorResponse.message());
                    }
                } catch (JsonProcessingException parsingException) {
                    log.debug("Failed to parse backend error response body: {}", responseBody, parsingException);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<LoginError> resolveLoginError(HttpServletRequest request) {
        String errorCode = request.getParameter("error");
        if (!StringUtils.hasText(errorCode)) {
            return Optional.empty();
        }

        String backendMessage = request.getParameter("message");
        String title = "로그인 실패";
        String message;

        switch (errorCode) {
            case "session" -> {
                title = "세션 오류";
                message = "로그인 세션이 만료되었어요. 다시 로그인해 주세요.";
            }
            case "callback" -> {
                message = "로그인에 필요한 정보가 확인되지 않았어요. 다시 시도해 주세요.";
            }
            case "state" -> {
                message = "로그인 요청이 만료되었어요. 다시 시도해 주세요.";
            }
            case "login" -> {
                if (StringUtils.hasText(backendMessage)) {
                    message = backendMessage;
                } else {
                    message = "로그인 처리 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요.";
                }
            }
            default -> {
                message = "로그인에 실패했어요. 다시 시도해 주세요.";
            }
        }

        return Optional.of(new LoginError(title, message));
    }

    private record BackendErrorResponse(int status, String message) {}

    private record LoginError(String title, String message) {}

    private String buildKakaoAuthorizeUrl(String state) {
        SocialLoginProperties.Provider kakao = socialLoginProperties.kakao();
        return UriComponentsBuilder.fromHttpUrl("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", kakao.clientId())
                .queryParam("redirect_uri", kakao.redirectUri())
                .queryParam("state", state)
                .queryParam("scope", "openid account_email")
                .encode()
                .toUriString();
    }

    private String buildAppleAuthorizeUrl(String state) {
        SocialLoginProperties.Provider apple = socialLoginProperties.apple();
        return UriComponentsBuilder.fromHttpUrl("https://appleid.apple.com/auth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", apple.clientId())
                .queryParam("redirect_uri", apple.redirectUri())
                .queryParam("scope", "openid email name")
                .queryParam("response_mode", "form_post")
                .queryParam("state", state)
                .encode()
                .toUriString();
    }
}
