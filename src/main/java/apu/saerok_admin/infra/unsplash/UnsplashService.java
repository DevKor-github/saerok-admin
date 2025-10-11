package apu.saerok_admin.infra.unsplash;

import apu.saerok_admin.config.UnsplashProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 서버 측에서만 Unsplash API 를 호출한다.
 * - random photo 조회 (orientation: landscape | portrait)
 * - download_location 트리거 (사용 이벤트 카운트)
 * - 브라우저에는 이미지/링크 정보만 내려준다 (키 절대 노출 X)
 */
@Service
public class UnsplashService {

    private final RestClient client;
    private final UnsplashProperties props;
    @SuppressWarnings("unused")
    private final ObjectMapper mapper;

    public UnsplashService(UnsplashProperties props, ObjectMapper mapper) {
        this.props = Objects.requireNonNull(props, "UnsplashProperties must not be null");
        this.mapper = mapper;
        if (!StringUtils.hasText(props.getAccessKey())) {
            throw new IllegalStateException("Unsplash access key is not configured. Set unsplash.access-key.");
        }
        this.client = RestClient.builder()
                .baseUrl("https://api.unsplash.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Client-ID " + props.getAccessKey())
                .defaultHeader("Accept-Version", "v1")
                .build();
    }

    /** orientation 값에 따라 한 장만 내려준다 (기본: landscape). */
    public Photo fetchRandomBird(String orientation) {
        String normalized = normalizeOrientation(orientation);
        return fetchOne(normalized);
    }

    private Photo fetchOne(String orientation) {
        JsonNode node = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/photos/random")
                        .queryParam("query", "bird on branch, small bird, passerine, morning light")
                        .queryParam("orientation", orientation)   // landscape | portrait
                        .queryParam("content_filter", "high")
                        .queryParam("count", 1)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode photo = (node != null && node.isArray() && node.size() > 0) ? node.get(0) : node;
        if (photo == null || photo.isNull()) {
            throw new IllegalStateException("Empty response from Unsplash random photo API (" + orientation + ")");
        }

        String baseUrl = chooseBaseUrl(photo);
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("Unsplash response missing image URLs (" + orientation + ")");
        }

        // 링크/저작자/홈 (utm_source=appName, utm_medium=referral)
        String appName = props.getAppName();
        String photoHtml = addUtm(textAt(photo, "/links/html"), appName);
        String photographerName = textAt(photo, "/user/name");
        String photographerLink = addUtm(textAt(photo, "/user/links/html"), appName);
        String unsplashHome     = addUtm("https://unsplash.com", appName);

        // 다운로드 트리거 (필수)
        String downloadLocation = textAt(photo, "/links/download_location");
        if (StringUtils.hasText(downloadLocation)) {
            URI dlUri = UriComponentsBuilder.fromUriString(downloadLocation)
                    .queryParam("utm_source", StringUtils.hasText(appName) ? appName : null)
                    .queryParam("utm_medium", StringUtils.hasText(appName) ? "referral" : null)
                    .build(true).toUri();
            client.get().uri(dlUri).retrieve().toBodilessEntity(); // 응답 바디 불필요
        }

        return new Photo(baseUrl, photoHtml, photographerName, photographerLink, unsplashHome);
    }

    private static String normalizeOrientation(String o) {
        if (!StringUtils.hasText(o)) return "landscape";
        String v = o.trim().toLowerCase();
        if ("portrait".equals(v)) return "portrait";
        return "landscape";
    }

    private static String chooseBaseUrl(JsonNode photo) {
        String raw = textAt(photo, "/urls/raw");
        String full = textAt(photo, "/urls/full");
        String regular = textAt(photo, "/urls/regular");
        if (StringUtils.hasText(raw)) return raw;
        if (StringUtils.hasText(full)) return full;
        return regular;
    }

    private static String textAt(JsonNode node, String pointer) {
        if (node == null) return null;
        JsonNode n = node.at(pointer);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    private static String addUtm(String url, String app) {
        if (!StringUtils.hasText(url)) return url;
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(url);
        if (StringUtils.hasText(app)) {
            b.queryParam("utm_source", app).queryParam("utm_medium", "referral");
        }
        return b.build(true).toString();
    }

    /** 단일 사진 메타 */
    public record Photo(
            String imageUrl,          // 원본 CDN base URL (프론트에서 w/h/fit=crop 적용)
            String htmlLink,          // 사진 상세 페이지 (utm 포함)
            String photographerName,
            String photographerLink,  // 작가 페이지 (utm 포함)
            String unsplashHome       // 홈 (utm 포함)
    ) {}
}
