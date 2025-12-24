package apu.saerok_admin.infra.announcement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminAnnouncementListResponse(
        List<Item> announcements
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            Long id,
            String title,
            String status,
            OffsetDateTime scheduledAt,
            OffsetDateTime publishedAt,
            String adminName
    ) {
    }
}
