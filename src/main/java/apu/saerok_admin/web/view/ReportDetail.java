package apu.saerok_admin.web.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ReportDetail(
        long reportId,
        ReportType type,
        LocalDateTime reportedAt,
        Person reporter,
        Person reportedUser,
        Collection collection,
        List<Comment> comments,
        ReportedComment reportedComment,
        String ignoreAction,
        String deleteAction
) {

    public record Person(Long userId, String nickname) {
    }

    public record Collection(
            long collectionId,
            Long birdId,
            String birdKoreanName,
            String birdScientificName,
            LocalDate discoveredDate,
            Double latitude,
            Double longitude,
            String locationAlias,
            String address,
            String note,
            String accessLevel,
            String accessLevelLabel,
            Long likeCount,
            Long commentCount,
            Boolean liked,
            Boolean mine,
            Long authorId,
            String authorNickname,
            String authorProfileImageUrl,
            String imageUrl
    ) {
    }

    public record Comment(
            Long commentId,
            Long userId,
            String nickname,
            String content,
            int likeCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

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
