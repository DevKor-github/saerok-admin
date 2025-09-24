package apu.saerok_admin.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class SaerokAdminAuthentication extends AbstractAuthenticationToken {

    private final Object principal;
    private LoginSession loginSession;

    public SaerokAdminAuthentication(Object principal, LoginSession loginSession) {
        this(principal, loginSession, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private SaerokAdminAuthentication(
            Object principal,
            LoginSession loginSession,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.principal = principal;
        this.loginSession = loginSession;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public LoginSession getLoginSession() {
        return loginSession;
    }

    public String getAccessToken() {
        return loginSession.accessToken();
    }

    public void updateLoginSession(LoginSession loginSession) {
        this.loginSession = loginSession;
    }
}
