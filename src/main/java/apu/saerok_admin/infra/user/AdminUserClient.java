package apu.saerok_admin.infra.user;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.user.dto.AdminUserListResponse;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class AdminUserClient {

    private static final String[] ADMIN_USERS_SEGMENTS = {"admin", "users"};

    private final RestClient saerokRestClient;
    private final String[] missingPrefixSegments;

    public AdminUserClient(RestClient saerokRestClient, SaerokApiProps saerokApiProps) {
        this.saerokRestClient = saerokRestClient;
        List<String> missing = saerokApiProps.missingPrefixSegments();
        this.missingPrefixSegments = missing.toArray(new String[0]);
    }

    public AdminUserListResponse listUsers(String query, int page, int size) {
        AdminUserListResponse response = saerokRestClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, query, page, size))
                .retrieve()
                .body(AdminUserListResponse.class);

        if (response == null) {
            throw new IllegalStateException("Empty response from admin user API");
        }
        return response;
    }

    private URI buildUri(UriBuilder builder, String query, int page, int size) {
        if (missingPrefixSegments.length > 0) {
            builder.pathSegment(missingPrefixSegments);
        }
        builder.pathSegment(ADMIN_USERS_SEGMENTS);
        if (StringUtils.hasText(query)) {
            builder.queryParam("q", query.trim());
        }
        builder.queryParam("page", page);
        builder.queryParam("size", size);
        return builder.build();
    }
}
