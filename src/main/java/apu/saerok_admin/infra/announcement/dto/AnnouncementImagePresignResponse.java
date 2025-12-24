package apu.saerok_admin.infra.announcement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnnouncementImagePresignResponse(
        String presignedUrl,
        String objectKey,
        String imageUrl
) {
}
