package apu.saerok_admin.infra;

import apu.saerok_admin.security.LoginSessionManager;
import apu.saerok_admin.web.view.CurrentAdminProfile;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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

            List<String> backendRoles = response.roles() != null ? List.copyOf(response.roles()) : List.of();
            AdminRoleInfo roleInfo = fetchAdminRoleInfo();
            List<String> roleCodes = !roleInfo.roleCodes().isEmpty()
                    ? roleInfo.roleCodes()
                    : normalizeRoleCodes(backendRoles);

            return Optional.of(new CurrentAdminProfile(
                    response.nickname(),
                    response.email(),
                    response.profileImageUrl(),
                    roleInfo.roleDisplayNames(),
                    roleCodes,
                    roleInfo.permissionKeys()
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
            String profileImageUrl,
            List<String> roles
    ) {
    }

    private AdminRoleInfo fetchAdminRoleInfo() {
        try {
            AdminMyRoleResponse response = saerokRestClient.get()
                    .uri(uriBuilder -> buildUri(uriBuilder, "admin", "role", "me"))
                    .retrieve()
                    .body(AdminMyRoleResponse.class);

            if (response == null) {
                return AdminRoleInfo.empty();
            }

            List<String> roleDisplayNames = response.roles() == null
                    ? List.of()
                    : response.roles().stream()
                            .map(RoleSummaryResponse::displayName)
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .toList();
            List<String> roleCodes = response.roles() == null
                    ? List.of()
                    : response.roles().stream()
                            .map(RoleSummaryResponse::code)
                            .filter(StringUtils::hasText)
                            .toList();
            List<String> permissionKeys = response.permissions() == null
                    ? List.of()
                    : response.permissions().stream()
                            .map(PermissionSummaryResponse::key)
                            .filter(StringUtils::hasText)
                            .toList();
            return new AdminRoleInfo(roleDisplayNames, roleCodes, permissionKeys);
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Failed to fetch current admin roles. status={}, body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );
        } catch (RestClientException exception) {
            log.warn("Failed to fetch current admin roles.", exception);
        }
        return AdminRoleInfo.empty();
    }

    private record AdminMyRoleResponse(
            List<RoleSummaryResponse> roles,
            List<PermissionSummaryResponse> permissions
    ) {
    }

    private record RoleSummaryResponse(
            Long id,
            String code,
            String displayName,
            String description,
            Boolean builtin
    ) {
    }

    private record PermissionSummaryResponse(
            String key,
            String description
    ) {
    }

    private record AdminRoleInfo(
            List<String> roleDisplayNames,
            List<String> roleCodes,
            List<String> permissionKeys
    ) {
        private static AdminRoleInfo empty() {
            return new AdminRoleInfo(List.of(), List.of(), List.of());
        }
    }

    private List<String> normalizeRoleCodes(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (!StringUtils.hasText(role)) {
                continue;
            }
            normalized.add(role.toUpperCase(Locale.ROOT));
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.copyOf(normalized);
    }
}
