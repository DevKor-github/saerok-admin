package apu.saerok_admin.infra.ad;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.ad.dto.AdImagePresignResponse;
import apu.saerok_admin.infra.ad.dto.AdminAdImagePresignRequest;
import apu.saerok_admin.infra.ad.dto.AdminAdListResponse;
import apu.saerok_admin.infra.ad.dto.AdminAdPlacementListResponse;
import apu.saerok_admin.infra.ad.dto.AdminCreateAdPlacementRequest;
import apu.saerok_admin.infra.ad.dto.AdminCreateAdRequest;
import apu.saerok_admin.infra.ad.dto.AdminCreateSlotRequest;
import apu.saerok_admin.infra.ad.dto.AdminSlotListResponse;
import apu.saerok_admin.infra.ad.dto.AdminUpdateAdPlacementRequest;
import apu.saerok_admin.infra.ad.dto.AdminUpdateAdRequest;
import apu.saerok_admin.infra.ad.dto.AdminUpdateSlotRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class AdminAdClient {

    private static final String[] ADMIN_AD_SEGMENTS = {"admin", "ad"};

    private final RestClient saerokRestClient;
    private final String[] missingPrefixSegments;

    public AdminAdClient(RestClient saerokRestClient, SaerokApiProps saerokApiProps) {
        this.saerokRestClient = saerokRestClient;
        List<String> missing = saerokApiProps.missingPrefixSegments();
        this.missingPrefixSegments = missing.toArray(new String[0]);
    }

    public AdminAdListResponse listAds() {
        return get(AdminAdListResponse.class, "list");
    }

    public AdminAdListResponse.Item createAd(AdminCreateAdRequest request) {
        return post(AdminAdListResponse.Item.class, request, "create");
    }

    public AdminAdListResponse.Item updateAd(Long adId, AdminUpdateAdRequest request) {
        return put(AdminAdListResponse.Item.class, request, adId.toString());
    }

    public void deleteAd(Long adId) {
        delete(adId.toString());
    }

    public AdImagePresignResponse generateAdImagePresignUrl(AdminAdImagePresignRequest request) {
        return post(AdImagePresignResponse.class, request, "image", "presign");
    }

    public AdminSlotListResponse listSlots() {
        return get(AdminSlotListResponse.class, "slot");
    }

    public AdminSlotListResponse.Item createSlot(AdminCreateSlotRequest request) {
        return post(AdminSlotListResponse.Item.class, request, "slot");
    }

    public AdminSlotListResponse.Item updateSlot(Long slotId, AdminUpdateSlotRequest request) {
        return put(AdminSlotListResponse.Item.class, request, "slot", slotId.toString());
    }

    public void deleteSlot(Long slotId) {
        delete("slot", slotId.toString());
    }

    public AdminAdPlacementListResponse listPlacements() {
        return get(AdminAdPlacementListResponse.class, "placement");
    }

    public AdminAdPlacementListResponse.Item createPlacement(AdminCreateAdPlacementRequest request) {
        return post(AdminAdPlacementListResponse.Item.class, request, "placement");
    }

    public AdminAdPlacementListResponse.Item updatePlacement(Long placementId, AdminUpdateAdPlacementRequest request) {
        return put(AdminAdPlacementListResponse.Item.class, request, "placement", placementId.toString());
    }

    public void deletePlacement(Long placementId) {
        delete("placement", placementId.toString());
    }

    private <T> T get(Class<T> responseType, String... segments) {
        T response = saerokRestClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .retrieve()
                .body(responseType);
        if (response == null) {
            throw new IllegalStateException("Empty response from admin ad API");
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
            throw new IllegalStateException("Empty response from admin ad API");
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
            throw new IllegalStateException("Empty response from admin ad API");
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
        builder.pathSegment(ADMIN_AD_SEGMENTS);
        builder.pathSegment(segments);
        return builder.build();
    }
}
