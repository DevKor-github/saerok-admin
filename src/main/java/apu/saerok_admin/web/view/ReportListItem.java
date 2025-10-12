package apu.saerok_admin.web.view;

import java.time.LocalDateTime;

public record ReportListItem(
        long reportId,
        ReportType type,
        LocalDateTime reportedAt,
        String targetSummary,
        String contentPreview,
        String collectionImageUrl,
        String reporterNickname,
        String reportedUserNickname,
        String detailPath,
        String ignoreAction,
        String deleteAction
) {

    public boolean hasContentPreview() {
        return contentPreview != null && !contentPreview.isBlank();
    }

    public boolean hasCollectionImage() {
        return collectionImageUrl != null && !collectionImageUrl.isBlank();
    }
}
