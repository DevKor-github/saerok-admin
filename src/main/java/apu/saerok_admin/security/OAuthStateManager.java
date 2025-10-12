package apu.saerok_admin.security;

import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class OAuthStateManager {

    public static final String ATTRIBUTE_NAME = "SAEROK_OAUTH_STATE";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String createState(HttpSession session) {
        String state = generateStateToken();
        session.setAttribute(ATTRIBUTE_NAME, state);
        return state;
    }

    public boolean consumeState(HttpSession session, String providedState) {
        if (session == null) {
            return false;
        }
        Object stored = session.getAttribute(ATTRIBUTE_NAME);
        session.removeAttribute(ATTRIBUTE_NAME);
        if (!(stored instanceof String storedState)) {
            return false;
        }
        return Objects.equals(storedState, providedState);
    }

    private String generateStateToken() {
        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
