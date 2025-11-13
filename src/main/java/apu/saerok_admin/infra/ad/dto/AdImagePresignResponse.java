package apu.saerok_admin.infra.ad.dto;

public record AdImagePresignResponse(
        String presignedUrl,
        String objectKey
) {
}
