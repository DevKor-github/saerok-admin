package apu.saerok_admin.web.view;

import java.time.LocalDateTime;

public record RecentReport(long id, String type, String targetId, String reason, String status, LocalDateTime createdAt) {
}
