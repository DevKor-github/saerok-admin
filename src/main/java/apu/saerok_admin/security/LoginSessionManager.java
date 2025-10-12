package apu.saerok_admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class LoginSessionManager {

    private static final String PRINCIPAL = "SaerokAdmin";

    public void establishSession(HttpServletRequest request, LoginSession loginSession) {
        HttpSession session = request.getSession(true);
        session.setAttribute(LoginSession.ATTRIBUTE_NAME, loginSession);

        SaerokAdminAuthentication authentication = createAuthentication(loginSession);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    public Optional<LoginSession> findSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object attribute = session.getAttribute(LoginSession.ATTRIBUTE_NAME);
        if (attribute instanceof LoginSession loginSession) {
            return Optional.of(loginSession);
        }
        return Optional.empty();
    }

    public Optional<LoginSession> currentSession() {
        ServletRequestAttributes attributes = currentRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        HttpSession session = attributes.getRequest().getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object attribute = session.getAttribute(LoginSession.ATTRIBUTE_NAME);
        if (attribute instanceof LoginSession loginSession) {
            return Optional.of(loginSession);
        }
        return Optional.empty();
    }

    public Optional<String> currentAccessToken() {
        return currentSession().map(LoginSession::accessToken);
    }

    public void updateAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            clearCurrentSession();
            return;
        }
        ServletRequestAttributes attributes = currentRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        LoginSession updatedSession = new LoginSession(accessToken);
        session.setAttribute(LoginSession.ATTRIBUTE_NAME, updatedSession);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof SaerokAdminAuthentication adminAuthentication) {
            adminAuthentication.updateLoginSession(updatedSession);
        }
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
        );
    }

    public void clearSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(LoginSession.ATTRIBUTE_NAME);
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
        SecurityContextHolder.clearContext();
    }

    public void clearCurrentSession() {
        ServletRequestAttributes attributes = currentRequestAttributes();
        if (attributes != null) {
            HttpSession session = attributes.getRequest().getSession(false);
            if (session != null) {
                session.removeAttribute(LoginSession.ATTRIBUTE_NAME);
                session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            }
        }
        SecurityContextHolder.clearContext();
    }

    public void writeRefreshCookiesToResponse(List<String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return;
        }
        ServletRequestAttributes attributes = currentRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletResponse response = attributes.getResponse();
        if (response == null) {
            return;
        }
        cookies.forEach(cookie -> response.addHeader(HttpHeaders.SET_COOKIE, cookie));
    }

    public void deleteRefreshCookie(HttpServletResponse response, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .maxAge(Duration.ZERO)
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public SaerokAdminAuthentication createAuthentication(LoginSession loginSession) {
        return new SaerokAdminAuthentication(PRINCIPAL, loginSession);
    }

    private ServletRequestAttributes currentRequestAttributes() {
        return (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    }
}
