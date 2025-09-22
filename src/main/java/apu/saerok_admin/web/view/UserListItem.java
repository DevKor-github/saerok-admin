package apu.saerok_admin.web.view;

import java.time.LocalDateTime;

public record UserListItem(long id, String nickname, String email, LocalDateTime joinedAt,
                           String status, int postCount, int reportCount) {
}
