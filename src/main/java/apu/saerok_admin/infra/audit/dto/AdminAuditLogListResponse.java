package apu.saerok_admin.infra.audit.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AdminAuditLogListResponse(
        List<Item> items
) {

    public record Item(
            Long id,
            LocalDateTime createdAt,
            UserMini admin,
            String action,
            String targetType,
            Long targetId,
            Long reportId,
            Map<String, Object> metadata
    ) {
    }

    public record UserMini(
            Long id,
            String nickname
    ) {
    }
}
