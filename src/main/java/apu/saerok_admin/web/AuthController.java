package apu.saerok_admin.web;

import apu.saerok_admin.config.SocialLoginProperties;
import apu.saerok_admin.infra.auth.BackendAuthClient;
import apu.saerok_admin.security.LoginSession;
import apu.saerok_admin.security.LoginSessionManager;
import apu.saerok_admin.security.OAuthStateManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.function.Function;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class AuthController {

    private final SocialLoginProperties socialLoginProperties;
    private final OAuthStateManager oAuthStateManager;
    private final BackendAuthClient backendAuthClient;
    private final LoginSessionManager loginSessionManager;

    public AuthController(
            SocialLoginProperties socialLoginProperties,
            OAuthStateManager oAuthStateManager,
            BackendAuthClient backendAuthClient,
            LoginSessionManager loginSessionManager
    ) {
        this.socialLoginProperties = socialLoginProperties;
        this.oAuthStateManager = oAuthStateManager;
        this.backendAuthClient = backendAuthClient;
        this.loginSessionManager = loginSessionManager;
    }

    @GetMapping("/login")
    public String login(Model model, HttpSession session) {
        String state = oAuthStateManager.createState(session);
        model.addAttribute("pageTitle", "로그인");
        model.addAttribute("kakaoAuthUrl", buildKakaoAuthorizeUrl(state));
        model.addAttribute("appleAuthUrl", buildAppleAuthorizeUrl(state));
        return "auth/login";
    }

    @GetMapping("/auth/callback/kakao")
    public String handleKakaoCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            HttpServletRequest request,
            HttpSession session
    ) {
        return handleSocialCallback(code, state, session, request, backendAuthClient::kakaoLogin);
    }

    @RequestMapping(value = "/auth/callback/apple", method = {RequestMethod.POST, RequestMethod.GET})
    public String handleAppleCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            HttpServletRequest request,
            HttpSession session
    ) {
        return handleSocialCallback(code, state, session, request, backendAuthClient::appleLogin);
    }

    private String handleSocialCallback(
            String code,
            String state,
            HttpSession session,
            HttpServletRequest request,
            Function<String, BackendAuthClient.LoginSuccess> loginFunction
    ) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            return "redirect:/login?error=callback";
        }
        if (!oAuthStateManager.consumeState(session, state)) {
            return "redirect:/login?error=state";
        }
        try {
            BackendAuthClient.LoginSuccess loginSuccess = loginFunction.apply(code);
            loginSessionManager.establishSession(request, new LoginSession(loginSuccess.accessToken()));
            loginSessionManager.writeRefreshCookiesToResponse(loginSuccess.refreshCookies());
            return "redirect:/";
        } catch (RestClientException | IllegalStateException exception) {
            loginSessionManager.clearSession(request);
            return "redirect:/login?error=login";
        }
    }

    private String buildKakaoAuthorizeUrl(String state) {
        SocialLoginProperties.Provider kakao = socialLoginProperties.kakao();
        return UriComponentsBuilder.fromHttpUrl("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", kakao.clientId())
                .queryParam("redirect_uri", kakao.redirectUri())
                .queryParam("state", state)
                .queryParam("scope", "account_email")
                .build(true)
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
                .build(true)
                .toUriString();
    }
}
