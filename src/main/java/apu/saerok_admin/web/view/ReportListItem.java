package apu.saerok_admin.web.view;

import java.time.LocalDateTime;

public record ReportListItem(long id, String type, String targetId, String reason, String status, LocalDateTime createdAt) {
}
