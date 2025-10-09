package apu.saerok_admin.infra.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportedCollectionListResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            Long reportId,
            LocalDateTime reportedAt,
            Long collectionId,
            UserMini reporter,
            UserMini reportedUser
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserMini(
            Long userId,
            String nickname
    ) {
    }
}
