package apu.saerok_admin.infra.announcement.dto;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

public record AdminCreateAnnouncementRequest(
        String title,
        String content,
        LocalDateTime scheduledAt,
        Boolean publishNow,
        Boolean sendNotification,
        String pushTitle,
        String pushBody,
        String inAppBody,
        @Valid
        List<AdminAnnouncementImageRequest> images
) {
}
