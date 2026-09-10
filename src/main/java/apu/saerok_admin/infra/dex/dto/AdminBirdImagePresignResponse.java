package apu.saerok_admin.infra.dex.dto;

public record AdminBirdImagePresignResponse(
        String presignedUrl,
        String objectKey
) {
}
