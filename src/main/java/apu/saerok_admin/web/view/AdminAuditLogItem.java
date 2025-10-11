package apu.saerok_admin.web.view;

import java.time.LocalDateTime;
import java.util.List;

public record AdminAuditLogItem(
        long id,
        LocalDateTime occurredAt,
        String adminNickname,
        String actionLabel,
        String actionDescription,
        String actionBadgeClass,
        String targetDisplay,
        String reportDisplay,
        List<MetadataEntry> metadataEntries
) {

    public record MetadataEntry(String key, String value) {
    }
}
