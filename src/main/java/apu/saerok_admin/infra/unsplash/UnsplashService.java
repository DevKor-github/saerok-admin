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
 * - random photo 조회
 * - download_location 트리거 (사용 이벤트 카운트)
 * - 브라우저에는 이미지/링크 정보만 내려준다 (키 절대 노출 X)
 */
@Service
public class UnsplashService {

    private final RestClient client;
    private final UnsplashProperties props;
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

    public Result fetchRandomBird() {
        // 1) 랜덤 사진 조회
        JsonNode node = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/photos/random")
                        .queryParam("query", "bird on branch, small bird, passerine, backyard bird, morning light")
                        .queryParam("orientation", "landscape")
                        .queryParam("content_filter", "high")
                        .queryParam("count", 1)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode photo = (node != null && node.isArray() && node.size() > 0) ? node.get(0) : node;
        if (photo == null || photo.isNull()) {
            throw new IllegalStateException("Empty response from Unsplash random photo API");
        }

        // 2) 이미지 URL (hotlink 원본)
        String raw = textAt(photo, "/urls/raw");
        String full = textAt(photo, "/urls/full");
        String regular = textAt(photo, "/urls/regular");
        String baseUrl = StringUtils.hasText(raw) ? raw : (StringUtils.hasText(full) ? full : regular);
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("Unsplash response missing image URLs");
        }
        String imageUrl = appendParams(baseUrl, 1600);

        // 3) 링크/저작자/홈 (utm_source=appName, utm_medium=referral)
        String appName = props.getAppName();
        String photoHtml = addUtm(textAt(photo, "/links/html"), appName);
        String photographerName = textAt(photo, "/user/name");
        String photographerLink = addUtm(textAt(photo, "/user/links/html"), appName);
        String unsplashHome = addUtm("https://unsplash.com", appName);

        // 4) 다운로드 트리거 (서버에서 실행)
        String downloadLocation = textAt(photo, "/links/download_location");
        if (StringUtils.hasText(downloadLocation)) {
            URI dlUri = UriComponentsBuilder.fromUriString(downloadLocation)
                    .queryParam("utm_source", StringUtils.hasText(appName) ? appName : null)
                    .queryParam("utm_medium", StringUtils.hasText(appName) ? "referral" : null)
                    .build(true).toUri();
            // 응답은 사용 안 해도 됨 (카운트 목적)
            client.get().uri(dlUri).retrieve().toBodilessEntity();
        }

        return new Result(imageUrl, photoHtml, photographerName, photographerLink, unsplashHome);
    }

    private static String textAt(JsonNode node, String pointer) {
        if (node == null) return null;
        JsonNode n = node.at(pointer);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    private static String appendParams(String base, int width) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(base)
                .queryParam("w", width)
                .queryParam("q", 80)
                .queryParam("auto", "format");
        return b.build(true).toString();
    }

    private static String addUtm(String url, String app) {
        if (!StringUtils.hasText(url)) return url;
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(url);
        if (StringUtils.hasText(app)) {
            b.queryParam("utm_source", app).queryParam("utm_medium", "referral");
        }
        return b.build(true).toString();
    }

    /**
     * 프론트로 내려줄 최소 정보.
     * - imageUrl: Unsplash CDN 원본 (img src에 그대로 hotlink)
     * - htmlLink: 사진 상세 페이지 (utm 포함)
     * - photographerName / photographerLink (utm 포함)
     * - unsplashHome (utm 포함)
     */
    public record Result(
            String imageUrl,
            String htmlLink,
            String photographerName,
            String photographerLink,
            String unsplashHome
    ) { }
}
