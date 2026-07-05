package apu.saerok_admin.infra.dex;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.dex.dto.AdminBirdDetailResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdImagePresignRequest;
import apu.saerok_admin.infra.dex.dto.AdminBirdImagePresignResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdListResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdUpsertRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class AdminDexClient {

    private static final String[] ADMIN_DEX_BIRD_SEGMENTS = {"admin", "dex", "birds"};

    private final RestClient saerokRestClient;
    private final String[] missingPrefixSegments;

    public AdminDexClient(RestClient saerokRestClient, SaerokApiProps saerokApiProps) {
        this.saerokRestClient = saerokRestClient;
        List<String> missing = saerokApiProps.missingPrefixSegments();
        this.missingPrefixSegments = missing.toArray(new String[0]);
    }

    public AdminBirdListResponse listBirds(String query, int page, int size) {
        AdminBirdListResponse response = saerokRestClient.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = buildBaseUri(uriBuilder);
                    if (StringUtils.hasText(query)) {
                        builder.queryParam("q", query.trim());
                    }
                    builder.queryParam("page", page);
                    builder.queryParam("size", size);
                    return builder.build();
                })
                .retrieve()
                .body(AdminBirdListResponse.class);
        if (response == null) {
            throw new IllegalStateException("Empty response from admin dex API");
        }
        return response;
    }

    public AdminBirdDetailResponse getBird(Long birdId) {
        AdminBirdDetailResponse response = saerokRestClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, birdId.toString()))
                .retrieve()
                .body(AdminBirdDetailResponse.class);
        if (response == null) {
            throw new IllegalStateException("Empty response from admin dex API");
        }
        return response;
    }

    public AdminBirdDetailResponse createBird(AdminBirdUpsertRequest request) {
        AdminBirdDetailResponse response = saerokRestClient.post()
                .uri(uriBuilder -> buildUri(uriBuilder))
                .body(request)
                .retrieve()
                .body(AdminBirdDetailResponse.class);
        if (response == null) {
            throw new IllegalStateException("Empty response from admin dex API");
        }
        return response;
    }

    public AdminBirdDetailResponse updateBird(Long birdId, AdminBirdUpsertRequest request) {
        AdminBirdDetailResponse response = saerokRestClient.method(HttpMethod.PUT)
                .uri(uriBuilder -> buildUri(uriBuilder, birdId.toString()))
                .body(request)
                .retrieve()
                .body(AdminBirdDetailResponse.class);
        if (response == null) {
            throw new IllegalStateException("Empty response from admin dex API");
        }
        return response;
    }

    public AdminBirdImagePresignResponse generateImagePresignUrl(AdminBirdImagePresignRequest request) {
        AdminBirdImagePresignResponse response = saerokRestClient.post()
                .uri(uriBuilder -> buildUri(uriBuilder, "image", "presign"))
                .body(request)
                .retrieve()
                .body(AdminBirdImagePresignResponse.class);
        if (response == null) {
            throw new IllegalStateException("Empty response from admin dex image API");
        }
        return response;
    }

    private URI buildUri(UriBuilder builder, String... segments) {
        UriBuilder base = buildBaseUri(builder);
        if (segments != null && segments.length > 0) {
            base.pathSegment(segments);
        }
        return base.build();
    }

    private UriBuilder buildBaseUri(UriBuilder builder) {
        if (missingPrefixSegments.length > 0) {
            builder.pathSegment(missingPrefixSegments);
        }
        return builder.pathSegment(ADMIN_DEX_BIRD_SEGMENTS);
    }
}
