package apu.saerok_admin.web.view;

import java.time.LocalDateTime;
import java.util.List;

public record CollectionDetail(long id, String author, String avatarUrl, String status, int reportCount,
                               LocalDateTime createdAt, LocalDateTime updatedAt, List<String> tags,
                               String content) {
}
