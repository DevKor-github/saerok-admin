package apu.saerok_admin.infra.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportedCommentDetailResponse(
        Long reportId,
        ReportedComment comment,
        CollectionDetailResponse collection,
        CollectionCommentsResponse comments
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReportedComment(
            Long commentId,
            Long userId,
            String nickname,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
