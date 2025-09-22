package apu.saerok_admin.web.view;

import java.time.LocalDateTime;

public record UserActivity(String type, String title, LocalDateTime createdAt, String status) {
}
