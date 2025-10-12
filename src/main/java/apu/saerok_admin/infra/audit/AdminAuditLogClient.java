package apu.saerok_admin.infra.audit;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.audit.dto.AdminAuditLogListResponse;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class AdminAuditLogClient {

    private static final String[] ADMIN_AUDIT_SEGMENTS = {"admin", "audit"};
    private static final String LOGS_SEGMENT = "logs";

    private final RestClient saerokRestClient;
    private final String[] missingPrefixSegments;

    public AdminAuditLogClient(RestClient saerokRestClient, SaerokApiProps saerokApiProps) {
        this.saerokRestClient = saerokRestClient;
        this.missingPrefixSegments = saerokApiProps.missingPrefixSegments().toArray(new String[0]);
    }

    public AdminAuditLogListResponse listAuditLogs(Integer page, Integer size) {
        AdminAuditLogListResponse response = saerokRestClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, page, size))
                .retrieve()
                .body(AdminAuditLogListResponse.class);

        if (response == null) {
            throw new IllegalStateException("Empty response from admin audit log API");
        }

        return response;
    }

    private URI buildUri(UriBuilder builder, Integer page, Integer size) {
        if (missingPrefixSegments.length > 0) {
            builder.pathSegment(missingPrefixSegments);
        }
        builder.pathSegment(ADMIN_AUDIT_SEGMENTS);
        builder.pathSegment(LOGS_SEGMENT);

        if (page != null && size != null) {
            builder.queryParam("page", page);
            builder.queryParam("size", size);
        }

        return builder.build();
    }
}
