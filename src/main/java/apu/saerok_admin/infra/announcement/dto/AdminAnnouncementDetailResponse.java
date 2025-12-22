package apu.saerok_admin.infra.announcement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminAnnouncementDetailResponse(
        Long id,
        String title,
        String content,
        String status,
        OffsetDateTime scheduledAt,
        OffsetDateTime publishedAt,
        Boolean sendNotification,
        String pushTitle,
        String pushBody,
        String inAppBody,
        String adminName,
        List<Image> images
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(
            String objectKey,
            String contentType,
            String imageUrl
    ) {
    }
}
