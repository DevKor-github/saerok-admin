package apu.saerok_admin.infra.ad.dto;

public record AdminCreateAdRequest(
        String name,
        String memo,
        String objectKey,
        String contentType,
        String targetUrl
) {
}
