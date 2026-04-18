package apu.saerok_admin.infra.notification;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.notification.dto.AdminSendMessageRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class AdminNotificationClient {

    private static final String[] ADMIN_NOTIFICATIONS_SEGMENTS = {"admin", "notifications"};

    private final RestClient saerokRestClient;
    private final String[] missingPrefixSegments;

    public AdminNotificationClient(RestClient saerokRestClient, SaerokApiProps saerokApiProps) {
        this.saerokRestClient = saerokRestClient;
        List<String> missing = saerokApiProps.missingPrefixSegments();
        this.missingPrefixSegments = missing.toArray(new String[0]);
    }

    public void sendMessage(AdminSendMessageRequest request) {
        saerokRestClient.post()
                .uri(uriBuilder -> buildUri(uriBuilder, "messages"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private URI buildUri(UriBuilder builder, String... segments) {
        if (missingPrefixSegments.length > 0) {
            builder.pathSegment(missingPrefixSegments);
        }
        builder.pathSegment(ADMIN_NOTIFICATIONS_SEGMENTS);
        if (segments != null && segments.length > 0) {
            builder.pathSegment(segments);
        }
        return builder.build();
    }
}
