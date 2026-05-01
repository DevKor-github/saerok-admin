package apu.saerok_admin.infra.notification.dto;

import java.util.List;

public record AdminSendMessageRequest(
        List<Long> userIds,
        String title,
        String body
) {
}
