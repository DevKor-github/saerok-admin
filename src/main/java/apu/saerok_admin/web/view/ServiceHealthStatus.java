package apu.saerok_admin.web.view;

import java.time.LocalDateTime;

public record ServiceHealthStatus(boolean alive, String message, LocalDateTime checkedAt) {
}
