package apu.saerok_admin.web.view;

import java.time.LocalDateTime;

public record CommentListItem(long id, String author, long parentId, String excerpt,
                              int reportCount, LocalDateTime createdAt) {
}
