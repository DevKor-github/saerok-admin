package apu.saerok_admin.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth")
public record SocialLoginProperties(Provider kakao, Provider apple) {

    public record Provider(String clientId, URI redirectUri) {
    }
}
