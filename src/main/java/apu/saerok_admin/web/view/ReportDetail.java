package apu.saerok_admin.web.view;

import java.time.LocalDateTime;
import java.util.List;

public record ReportDetail(long id, String type, String status, String reporter, String reason,
                           LocalDateTime createdAt, String targetSummary, List<String> attachments,
                           String previewTitle, String previewContent) {
}
