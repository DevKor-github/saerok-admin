package apu.saerok_admin.infra;

import apu.saerok_admin.security.LoginSessionManager;
import apu.saerok_admin.web.view.CurrentAdminProfile;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

@Component
public class CurrentAdminClient {

    private static final Logger log = LoggerFactory.getLogger(CurrentAdminClient.class);

    private final RestClient saerokRestClient;
    private final List<String> missingPrefixSegments;
    private final LoginSessionManager loginSessionManager;

    public CurrentAdminClient(
            RestClient saerokRestClient,
            SaerokApiProps saerokApiProps,
            LoginSessionManager loginSessionManager
    ) {
        this.saerokRestClient = saerokRestClient;
        this.missingPrefixSegments = saerokApiProps.missingPrefixSegments();
        this.loginSessionManager = loginSessionManager;
    }

    public Optional<CurrentAdminProfile> fetchCurrentAdminProfile() {
        if (loginSessionManager.currentAccessToken().isEmpty()) {
            return Optional.empty();
        }

        try {
            BackendUserProfileResponse response = saerokRestClient.get()
                    .uri(uriBuilder -> buildUri(uriBuilder, "user", "me"))
                    .retrieve()
                    .body(BackendUserProfileResponse.class);

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(new CurrentAdminProfile(
                    response.nickname(),
                    response.email(),
                    response.profileImageUrl()
            ));
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Failed to fetch current admin profile. status={}, body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );
        } catch (RestClientException exception) {
            log.warn("Failed to fetch current admin profile.", exception);
        }

        return Optional.empty();
    }

    private URI buildUri(UriBuilder builder, String... segments) {
        if (!missingPrefixSegments.isEmpty()) {
            builder.pathSegment(missingPrefixSegments.toArray(String[]::new));
        }
        builder.pathSegment(segments);
        return builder.build();
    }

    private record BackendUserProfileResponse(
            String nickname,
            String email,
            String profileImageUrl
    ) {
    }
}
