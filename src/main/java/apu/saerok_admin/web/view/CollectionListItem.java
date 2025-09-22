package apu.saerok_admin.web.view;

import java.time.LocalDateTime;

public record CollectionListItem(long id, String author, String excerpt, String status,
                                 int reportCount, LocalDateTime createdAt) {
}
