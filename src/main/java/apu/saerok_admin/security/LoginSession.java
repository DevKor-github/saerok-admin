package apu.saerok_admin.security;

import java.io.Serializable;

public record LoginSession(String accessToken) implements Serializable {

    public static final String ATTRIBUTE_NAME = "SAEROK_LOGIN_SESSION";
}
