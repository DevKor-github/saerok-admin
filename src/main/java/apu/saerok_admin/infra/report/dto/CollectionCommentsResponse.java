package apu.saerok_admin.infra.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionCommentsResponse(List<Item> items, Boolean isMyCollection) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            Long commentId,
            Long userId,
            String nickname,
            String profileImageUrl,
            String content,
            int likeCount,
            Boolean isLiked,
            Boolean isMine,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
