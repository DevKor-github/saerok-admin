package apu.saerok_admin.infra.announcement;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.announcement.dto.AdminAnnouncementDetailResponse;
import apu.saerok_admin.infra.announcement.dto.AdminAnnouncementImagePresignRequest;
import apu.saerok_admin.infra.announcement.dto.AdminAnnouncementListResponse;
import apu.saerok_admin.infra.announcement.dto.AdminCreateAnnouncementRequest;
import apu.saerok_admin.infra.announcement.dto.AdminUpdateAnnouncementRequest;
import apu.saerok_admin.infra.announcement.dto.AnnouncementImagePresignResponse;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class AdminAnnouncementClient {

    private static final String[] ADMIN_ANNOUNCEMENT_SEGMENTS = {"admin", "announcement"};

    private final RestClient saerokRestClient;
    private final String[] missingPrefixSegments;

    public AdminAnnouncementClient(RestClient saerokRestClient, SaerokApiProps saerokApiProps) {
        this.saerokRestClient = saerokRestClient;
        List<String> missing = saerokApiProps.missingPrefixSegments();
        this.missingPrefixSegments = missing.toArray(new String[0]);
    }

    public AdminAnnouncementListResponse listAnnouncements() {
        return get(AdminAnnouncementListResponse.class);
    }

    public AdminAnnouncementDetailResponse getAnnouncement(Long announcementId) {
        return get(AdminAnnouncementDetailResponse.class, announcementId.toString());
    }

    public AdminAnnouncementDetailResponse createAnnouncement(AdminCreateAnnouncementRequest request) {
        return post(AdminAnnouncementDetailResponse.class, request);
    }

    public AdminAnnouncementDetailResponse updateAnnouncement(Long announcementId, AdminUpdateAnnouncementRequest request) {
        return put(AdminAnnouncementDetailResponse.class, request, announcementId.toString());
    }

    public void deleteAnnouncement(Long announcementId) {
        delete(announcementId.toString());
    }

    public AnnouncementImagePresignResponse generateImagePresignUrl(AdminAnnouncementImagePresignRequest request) {
        return post(AnnouncementImagePresignResponse.class, request, "image", "presign");
    }

    private <T> T get(Class<T> responseType, String... segments) {
        T response = saerokRestClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .retrieve()
                .body(responseType);

        if (response == null) {
            throw new IllegalStateException("Empty response from admin announcement API");
        }
        return response;
    }

    private <T> T post(Class<T> responseType, Object body, String... segments) {
        T response = saerokRestClient.post()
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .body(body)
                .retrieve()
                .body(responseType);

        if (response == null) {
            throw new IllegalStateException("Empty response from admin announcement API");
        }
        return response;
    }

    private <T> T put(Class<T> responseType, Object body, String... segments) {
        T response = saerokRestClient.method(HttpMethod.PUT)
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .body(body)
                .retrieve()
                .body(responseType);

        if (response == null) {
            throw new IllegalStateException("Empty response from admin announcement API");
        }
        return response;
    }

    private void delete(String... segments) {
        saerokRestClient.delete()
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .retrieve()
                .toBodilessEntity();
    }

    private URI buildUri(UriBuilder builder, String... segments) {
        if (missingPrefixSegments.length > 0) {
            builder.pathSegment(missingPrefixSegments);
        }
        builder.pathSegment(ADMIN_ANNOUNCEMENT_SEGMENTS);
        if (segments != null && segments.length > 0) {
            builder.pathSegment(segments);
        }
        return builder.build();
    }
}
